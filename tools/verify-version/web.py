#!/usr/bin/env python3
"""Language pickers, offline reloads and native Russian word boards in both browser engines."""
import argparse
import json
import sys
from pathlib import Path
from urllib.parse import urlparse

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "render"))
import render as R

LANGUAGE_KEY = "daybook_launch.language"
FONTS = {f"noto_sans_cyrillic_{weight}.ttf" for weight in ("regular", "medium", "semibold", "bold")}
CACHED_FONTS = (FONTS | {"noto_sans_symbols2.ttf", "noto_sans_math.ttf", "noto_sans_symbols_arrows.ttf"}
                | {f"fredoka_{weight}.ttf" for weight in ("regular", "medium", "semibold", "bold")})
RUSSIAN_WORD_IDS = ("words-ru", "wordwheel-ru", "dwindle-ru", "hexwords-ru", "mini-ru", "codeword-ru", "cipher-ru")
RUSSIAN_ALPHABET = "абвгдежзийклмнопрстуфхцчшщъыьэюя"
SAVED_KEY = "daybook.saved_games"


def wait_ready(page):
    # A cold Wasm compile can outlast a screenshot's settling window on a shared CI runner.
    page.wait_for_function("() => !document.getElementById('loading')", timeout=60000)


def saved_state(page, puzzle, predicate="state.moves > 0"):
    """Read the real debounced save, rather than accepting a painted keyboard as proof of input."""
    expression = """([key, prefix]) => {
        const game = JSON.parse(localStorage.getItem(key) || '[]').map(JSON.parse)
            .find(game => game.key.startsWith(prefix + '|'));
        if (!game) return false;
        const state = game.game.state;
        return (%s) ? state : false;
    }""" % predicate
    return page.wait_for_function(expression, arg=[SAVED_KEY, puzzle], timeout=15000).json_value()


def board_snapshot(state):
    # Selection is deliberately removed when a play screen is left. Everything that describes the board or a move
    # must survive language changes and reloads, including its explicit contentLanguage and the player's letters.
    return {key: value for key, value in state.items() if key not in ("cursor", "selected")}


def layout_key(page, key, code):
    """Exercise a Russian layout's Unicode key with an independent Latin physical code."""
    page.evaluate("""([key, code]) => {
        let target = document.activeElement;
        while (target?.shadowRoot?.activeElement) target = target.shadowRoot.activeElement;
        if (!target || target === document.body) throw new Error('the app canvas has no keyboard focus');
        for (const type of ['keydown', 'keyup']) target.dispatchEvent(new KeyboardEvent(type,
            {key, code, bubbles: true, composed: true, cancelable: true}));
    }""", [key, code])


def word_boards(page, server, screenshot, opposite_language):
    """Real generated boards: Unicode input, no Latin fallback, content/UI independence and offline resume."""
    missing = set(RUSSIAN_WORD_IDS) - set(R.puzzle_ids())
    if missing:
        raise AssertionError(f"Russian word games missing from registry: {sorted(missing)}")
    snapshots = {}
    keyboard = []

    def open_board(puzzle):
        page.goto(server.url + "?date=2026-11-05&puzzle=" + puzzle, timeout=60000)
        wait_ready(page)
        R.settle(page, 20)

    for puzzle in RUSSIAN_WORD_IDS:
        open_board(puzzle)
        screenshot("offline-" + puzzle)
        if puzzle in ("words-ru", "mini-ru", "codeword-ru"):
            if puzzle != "words-ru":
                page.keyboard.press("ArrowRight")  # The board's own first editable square/number.
                page.wait_for_timeout(150)
            # Playwright's physical-key map does not contain Cyrillic keys; type(non-ASCII) sends only
            # insertText. Dispatch the layout's actual key/code pair to exercise Compose's KeyDown path.
            layout_key(page, "я", "KeyZ")
            condition = {"words-ru": "state.current === 'я'", "mini-ru": "state.letters.includes('я')",
                         "codeword-ru": f"state.letters.includes({RUSSIAN_ALPHABET.index('я')})"}[puzzle]
            first = saved_state(page, puzzle, condition)
            # Ё is canonically Е in this corpus. KeyQ would be 'q' in a Latin physical-key fallback.
            layout_key(page, "ё", "KeyQ")
            condition = {"words-ru": "state.current === 'яе'",
                         "mini-ru": "state.letters.includes('я') && state.letters.includes('е')",
                         "codeword-ru": f"state.letters[state.selected - 1] === {RUSSIAN_ALPHABET.index('е')}"}[puzzle]
            saved_state(page, puzzle, condition)
            # On a Russian layout physical H types Р. It must reach the board before the parent's Hint shortcut.
            layout_key(page, "р", "KeyH")
            condition = {"words-ru": "state.current === 'яер'",
                         "mini-ru": "state.letters.includes('я') && state.letters.includes('е') && state.letters.includes('р')",
                         "codeword-ru": f"state.letters[state.selected - 1] === {RUSSIAN_ALPHABET.index('р')}"}[puzzle]
            state = saved_state(page, puzzle, "(" + condition + ") && (game.game.hints || 0) === 0")
            if state.get("contentLanguage") != "ru":
                raise AssertionError(f"{puzzle}: Russian keys reached a non-Russian board")
            keyboard.append({"puzzle": puzzle, "unicode_key": "я", "physical_code": "KeyZ", "normalized_key": "ё", "normalized_code": "KeyQ",
                             "shortcut_key": "р", "shortcut_code": "KeyH", "hints_after_typing": 0,
                             "first_state": first, "saved_state": state})
        else:
            # Three explicit taps request a hint, its explanation, then its move. A save proves the actual
            # registered Russian state was drawn and playable, including games without a letter-entry row.
            for _ in range(3):
                page.touchscreen.tap(323, 505)
                R.settle(page, 4)
            state = saved_state(page, puzzle)
        if state.get("contentLanguage") != "ru":
            raise AssertionError(f"{puzzle}: saved state lost its Russian content language")
        snapshots[puzzle] = board_snapshot(state)
        screenshot("offline-" + puzzle + "-played")

    # Existing English identities must still select English content under a Russian UI.
    for puzzle in ("words", "mini", "codeword"):
        open_board(puzzle)
        if puzzle != "words":
            page.keyboard.press("ArrowRight")
            page.wait_for_timeout(150)
        page.keyboard.press("z")
        condition = {"words": "state.current === 'z'", "mini": "state.letters.includes('z')",
                     "codeword": "state.letters.includes(25)"}[puzzle]
        state = saved_state(page, puzzle, condition)
        if state.get("contentLanguage", "en") != "en":
            raise AssertionError(f"{puzzle}: changing UI language changed English board content")
        snapshots[puzzle] = board_snapshot(state)
        screenshot("offline-" + puzzle + "-english-content")

    page.goto(server.url + "?screen=settings", timeout=60000)
    wait_ready(page)
    R.settle(page, 20)
    choose(page, opposite_language)
    for puzzle, snapshot in snapshots.items():
        open_board(puzzle)
        resumed = board_snapshot(saved_state(page, puzzle))
        if resumed != snapshot:
            raise AssertionError(f"{puzzle}: board or typed progress changed when UI language changed")
        screenshot("offline-" + puzzle + "-language-switched")
    return {"boards": snapshots, "keyboard": keyboard}


def short_layout(page, server, screenshot):
    """Keep the formerly crashing 2× tutorial and zero-room resize in the repeatable gate."""
    page.goto(server.url + "?screen=settings", timeout=60000)
    wait_ready(page)
    R.settle(page, 20)
    language = choose(page, "")
    page.evaluate("key => localStorage.setItem(key, '[]')", R.SEEN_KEY)
    page.goto(server.url + "?date=2026-11-05&puzzle=sudoku&fontscale=2", timeout=60000)
    first = screenshot("2x-tutorial-entry")
    page.touchscreen.tap(281, 505)
    if screenshot("2x-tutorial-next") == first:
        raise AssertionError("the 2× tutorial's Next control did not advance")
    page.mouse.move(187, 420)
    page.mouse.wheel(0, 1000)
    screenshot("2x-tutorial-caption-end")
    for width, height, name in [(812, 375, "landscape"), (375, 240, "zero-room-resize"), (375, 537, "portrait-restored")]:
        page.set_viewport_size({"width": width, "height": height})
        screenshot("2x-tutorial-" + name)
    return {"language": language, "font_scale": 2, "viewports": ["375x537", "812x375", "375x240", "375x537"]}


def warm_cache(page):
    page.wait_for_function("() => navigator.serviceWorker.controller !== null", timeout=30000)
    # The first-page worker caches late-loaded Wasm/fonts after Compose starts. Prove every required resource
    # is stored before disconnecting, rather than mistaking an already-painted canvas for offline startup.
    page.wait_for_function(r"""async requiredFonts => {
        const urls = performance.getEntriesByType('resource').map(r => r.name)
            .filter(u => new URL(u).origin === location.origin && /\.(wasm|m?js|ttf)$/.test(new URL(u).pathname));
        if (!urls.some(u => u.endsWith('.wasm'))) return false;
        urls.push(new URL('./', location.href).href);
        if (!(await Promise.all(urls.map(u => caches.match(u)))).every(Boolean)) return false;
        // A resource still loading has no PerformanceResourceTiming entry in WebKit. Require all eleven
        // bundled fonts' completed cache entries before disconnecting, including the install precache.
        const cached = (await Promise.all((await caches.keys()).map(async n =>
            (await (await caches.open(n)).keys()).map(r => new URL(r.url).pathname.split('/').pop())))).flat();
        return requiredFonts.every(name => cached.includes(name));
    }""", arg=sorted(CACHED_FONTS), timeout=45000)
    return page.evaluate("async () => (await Promise.all((await caches.keys()).map(async n => (await (await caches.open(n)).keys()).map(r => r.url)))).flat()")


def choose(page, language):
    # Compose's canvas has no text DOM. A wrong/moved target fails the storage+live-language assertions below.
    height = page.viewport_size["height"]
    page.touchscreen.tap(187, 112)  # Settings' first card, Language.
    R.settle(page, 8)
    page.touchscreen.tap(187, height / 2 + {"": -44, "en": -12, "ru": 20}[language])
    page.wait_for_function("([key, value]) => localStorage.getItem(key) === value", arg=[LANGUAGE_KEY, language], timeout=10000)
    expected = language or page.evaluate("navigator.language")
    page.wait_for_function("lang => document.documentElement.lang === lang", arg=expected, timeout=10000)
    return expected


def check_locale(browser, engine, server, out, locale):
    prefix = f"{engine}-{locale}"
    result = {"engine": engine, "system_locale": locale, "errors": [], "external": [], "fonts": [], "screenshots": []}
    context = browser.new_context(viewport={"width": 375, "height": 537}, device_scale_factor=2,
                                  has_touch=True, locale=locale, color_scheme="light", reduced_motion="reduce")
    seed = {"daybook_launch.welcomed": "yes", "daybook_launch.show_timer": "off", R.SEEN_KEY: json.dumps(R.puzzle_ids())}
    context.add_init_script("const seed = %s; for (const k in seed) if (localStorage.getItem(k) === null) localStorage.setItem(k, seed[k]);" % json.dumps(seed))
    context.on("request", lambda request: urlparse(request.url).hostname not in ("127.0.0.1", None)
               and result["external"].append(request.url))
    context.route("**/*", lambda route: route.continue_() if urlparse(route.request.url).hostname == "127.0.0.1" else route.abort())
    page = context.new_page()

    def console(message):
        if message.type == "error" and not any(rx.search(message.text) for rx in R.IGNORED_CONSOLE):
            if engine != "webkit" or not R.WEBKIT_SCREENSHOT_CSP.search(message.text):
                result["errors"].append(message.text)

    page.on("console", console)
    page.on("pageerror", lambda error: result["errors"].append(str(error)))
    page.on("response", lambda response: response.url.endswith(".ttf") and result["fonts"].append(
        [Path(urlparse(response.url).path).name, response.status]))

    def screenshot(name):
        wait_ready(page)
        png, _ = R.settle(page, 12)
        path = out / f"{prefix}-{name}.png"
        path.write_bytes(png)
        result["screenshots"].append(path.name)
        return png

    try:
        page.goto(server.url + "?date=2026-11-05&screen=settings", timeout=60000)
        screenshot("system-settings")
        if page.evaluate("document.documentElement.lang") != locale:
            raise AssertionError("initial language did not follow navigator.language")
        navigation = page.evaluate("performance.timeOrigin")
        target = "ru" if locale == "en-US" else "en"
        choose(page, target)
        if page.evaluate("performance.timeOrigin") != navigation:
            raise AssertionError("in-app language selection unexpectedly reloaded the page")
        screenshot("explicit-language-live")
        result["cached_resources"] = warm_cache(page)
        context.set_offline(True)
        page.reload(timeout=60000)
        page.wait_for_function("lang => document.documentElement.lang === lang", arg=target, timeout=30000)
        screenshot("offline-reload")
        choose(page, "")
        screenshot("offline-system-default")
        page.reload(timeout=60000)
        page.wait_for_function("lang => document.documentElement.lang === lang", arg=locale, timeout=30000)
        screenshot("offline-system-reload")
        # Navigation itself must work offline, and exercise the app's real date/name/share glyph paths.
        for name, query in [("home", ""), ("archive", "screen=archive&day=2026-11-04"),
                            ("stats", "screen=stats"), ("share", "puzzle=target&finish=0&share")]:
            page.goto(server.url + "?date=2026-11-05&" + query, timeout=60000)
            screenshot("offline-" + name)
        result["word_boards"] = word_boards(page, server, screenshot, target)
        result["short_layout"] = short_layout(page, server, screenshot)
        loaded = {name for name, status in result["fonts"] if status == 200}
        if not FONTS <= loaded:
            raise AssertionError(f"missing bundled fonts needed for offline language switching: {sorted(FONTS - loaded)}")
    except Exception as error:
        result["errors"].append(f"{type(error).__name__}: {str(error).splitlines()[0]}")
        result["language_at_failure"] = page.evaluate("({stored: localStorage.getItem('daybook_launch.language'), document: document.documentElement.lang, system: navigator.language})")
        result["cache_at_failure"] = page.evaluate("async () => (await Promise.all((await caches.keys()).map(async n => (await (await caches.open(n)).keys()).map(r => r.url)))).flat()")
        path = out / f"{prefix}-failure.png"
        page.screenshot(path=str(path))
        result["screenshots"].append(path.name)
    finally:
        context.close()
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--out", type=Path, required=True)
    parser.add_argument("--dist", type=Path, default=R.DEFAULT_DIST)
    args = parser.parse_args()
    args.out.mkdir(parents=True, exist_ok=True)
    from playwright.sync_api import sync_playwright
    results = []
    with R.Server(args.dist) as server, sync_playwright() as pw:
        for engine in R.BROWSERS:
            browser = R.launch(pw, engine)
            try:
                for locale in ("en-US", "ru-RU"):
                    result = check_locale(browser, engine, server, args.out, locale)
                    results.append(result)
                    print(json.dumps(result, ensure_ascii=False), flush=True)
            finally:
                browser.close()
    (args.out / "results.json").write_text(json.dumps(results, ensure_ascii=False, indent=2) + "\n")
    failures = [result for result in results if result["errors"] or result["external"]]
    print(f"{len(results)} browser/locale cases, {len(failures)} failures; screenshots need visual review")
    return bool(failures)


if __name__ == "__main__":
    raise SystemExit(main())
