# Languages and Russian word puzzles

Implementation and final acceptance are tracked in [LOCALIZATION_PROGRESS.md](LOCALIZATION_PROGRESS.md).
The shared catalogs cover interface text, game names, rules, hints, walkthroughs, techniques, training,
achievements, sharing and reminders. Display language and puzzle vocabulary are separate choices.

## Language selection

Settings offers System default, English and Русский. Android 13+ uses `LocaleManager`, the same setting
shown in Android's per-app language screen; its supported list is generated in `res/xml/locale_config.xml`.
Older Android keeps the choice in `daybook_language` preferences and applies it before an Activity or
reminder receiver starts. The choice migrates to the system API after an OS upgrade. Recreating an
Activity retains the existing navigation and saved-board mechanisms.

The web persists its choice in `daybook_launch.language`, or follows `navigator.language` for System
default. Changing it recomposes the current app, updates the document language, and keeps navigation
and progress. Regional tags first match an exact catalog, then their base language; unsupported tags
use English. Neither platform sends a language choice to a server.

## Catalog contract

`translations/languages.json` is the supported-language list. Each `translations/<tag>/*.json` file is a
domain of message keys; domain filenames are organizational, not a runtime API. Every language must
have exactly the English key set and the same named arguments. Translate complete messages, including
their grammar, rather than concatenate translated English fragments.

```kotlin
text("count.hints", "count" to hints) // Resolve at the display boundary.
message("count.hints", "count" to hints) // Keep a descriptor while reasoning or composing prose.
```

Plural entries are maps of CLDR cardinal categories (`one`, `few`, `many`, `other`, as needed by the
language); `count` must be an integer. The generator uses pinned Babel/Unicode CLDR data for integer
plural rules and Gregorian calendar names. Android keeps its native `DateTimeFormatter`, including the
locale in its cache key. Web date patterns distinguish date forms (`5 ноября`) from standalone months
(`ноябрь 2026`); weekday captions stay Monday first.

`Message.english`, length, indexing and `toString()` remain canonical English. Teachers use text length
and distinct reasons in generation gates: never resolve the display locale inside those decisions.
Forward `nudgeMessage`/`explanationMessage`, preserve nested descriptors, and call `.resolve()` at the UI.
If canonical prose is a deduplication key, use `.distinctBy { it.english }`, not descriptor identity.
Persisted celebrations use the small `SavedText` catalog recipe; old payload defaults remain readable.
User words, board answers, identifiers and fixture literals are data, not translation keys.

### Adding a language

1. Add its BCP-47 tag and native name to `translations/languages.json`.
2. Translate the complete English catalog into `translations/<tag>/`, retaining keys and arguments.
   Provide that language's CLDR plural forms for every plural message.
3. Run `python -m pip install -r tools/i18n/requirements.txt`, then `python tools/i18n/build.py`.
   Commit the generated Kotlin catalogs/calendar/plural metadata and Android locale list.
4. Run `python -m unittest discover -s tools/i18n -p 'test_*.py'` and
   `python tools/i18n/build.py --check`. Missing keys/forms/arguments, duplicate keys, unknown literal
   call-site keys, stale generated files and missing bundled glyphs fail the checks. The generator test
   exercises ten languages with different plural systems without changing runtime source.
5. Supply offline font coverage for any new script and render the actual UI, including hints and large
   text. The version verifier is documented in [VERIFY_VERSION.md](VERIFY_VERSION.md).

Do not edit generated catalogs or reintroduce bilingual `tr()` call sites or Russian-specific grammar
helpers. Catalog generation is build-time Python; the Android/web app has no Babel runtime dependency.

## Content identity and continuity

English word lists, original puzzle IDs, seeds, old board pins and saved-game fixtures stay unchanged.
Each Russian word game has its own permanent ID: `words-ru`, `wordwheel-ru`, `mini-ru`, `codeword-ru`,
`hexwords-ru`, `dwindle-ru`, `cipher-ru`. Its saved state explicitly contains `contentLanguage = "ru"`;
old saves omit the new field and default to English. Switching the interface never changes a board's
words, alphabet, answer, daily identity or record. Existing level pools and deals retain their frozen
prefixes; new IDs are appended. Today's three adds the Russian editions from 2026-11-06; earlier dates
keep their original three and earned achievements. Each catalog schedule has its own no-repeat cycle.

The frozen corpus, provenance, licenses, vocabulary screening and reproducible build commands are in
[tools/words/ru/README.md](../tools/words/ru/README.md). Guesses use a broad dictionary, while answers
and clued grid words use separate reviewed lists. Input normalizes Ё to Е; prose keeps proper spelling.
Mini, Codeword and Cipher ship separately proved frozen tables. Native block layouts are calibrated for
Russian vocabulary while retaining the original uniqueness and teaching gates. Russian Mini uses the
same 5×5 / 6×6 / 7×7 sizes and clue-share bands; it does not claim to have the English alternate-clue set.

The linked external player-guide prose remains English, with native Russian word diagrams for the new
games. In-app rules, walkthroughs and hints are translated. The guide buttons identify English prose.

Fixed chrome uses the existing `CappedFontScale` policy. Explanatory body text retains the player's
font scale; archive/statistics layouts and share previews keep their established scrolling behavior.

## Fonts

Fredoka remains unchanged for the English interface. Russian uses four static Noto Sans subsets
(400/500/600/700) on both platforms. The web preloads them before showing text, together with its existing
symbol fonts, so Cyrillic never requires a font request to another server. The fonts include Latin-1,
all 66 Russian letters including Ёё, and the punctuation already covered by Fredoka's web subset.

Source: Noto Sans variable font version 2.015, copyright 2022 The Noto Project Authors
(`https://github.com/notofonts/latin-greek-cyrillic`), under SIL OFL 1.1.
Source SHA-256: `fb53504de62e767a54695416123d68d67f71c9b69722175184ad56e7dd13547b`.
The source was available locally as `/usr/share/fonts/google-noto-vf/NotoSans[wght].ttf`.
Notices remain in each font's name table and `docs/fonts/LICENSE-NOTO-OFL.txt`.

Regenerate using fontTools at build time and the existing subsetter; no app dependency is added:

```python
from pathlib import Path
from runpy import run_path
from tempfile import TemporaryDirectory
from shutil import copyfile
from fontTools.ttLib import TTFont
from fontTools.varLib.instancer import instantiateVariableFont

subset = run_path("tools/subset-font.py")["subset"]
source = "/path/to/NotoSans[wght].ttf"  # version and checksum above
chars = list(range(0x20, 0x7f)) + list(range(0xa0, 0x100))
chars += list(range(0x410, 0x450)) + [0x401, 0x451]
chars += [0x3a9, 0x3c9, 0x3b2]  # Greek letters in attributed Russian word clues.
chars += [0x2013, 0x2014, 0x2018, 0x2019, 0x201c, 0x201d, 0x201e, 0x2022,
          0x2026, 0x2032, 0x2033, 0x2039, 0x203a, 0x2212]
with TemporaryDirectory() as tmp:
    for name, weight in [("regular", 400), ("medium", 500), ("semibold", 600), ("bold", 700)]:
        static = Path(tmp) / f"noto_sans_{name}.ttf"
        instantiateVariableFont(TTFont(source, recalcTimestamp=False), {"wght": weight}, updateFontNames=True).save(static)
        web = Path("web/src/wasmJsMain/composeResources/font") / f"noto_sans_cyrillic_{name}.ttf"
        subset(str(static), str(web), chars)
        copyfile(web, Path("app/src/main/res/font") / web.name)
```

## Checks

Run `LocalizationTest`, `CalendarLocalizationTest` and the existing English-facing tests, including
reminders, next steps, sharing, achievements, levels, serialization, saved-game compatibility and board pins.
Compile Android and Wasm and assemble the release variant, following `AGENTS.md` and `docs/WEB_BUILD.md`.
For visual checks use a Russian device/browser locale, both themes, narrow and wide windows and large text.
Use `tools/render/render.py --locale ru-RU` (its default is `en-US`). The glyph-request check accepts a
locale after its output and distribution directories. Do not re-pin boards or rewrite save fixtures
to make a translation pass.

### Validation on 2026-11-05

Headless Chromium 156 and WebKit 27.2 covered 72 distinct locale/layout cases across Russian and
English: welcome/Start, daily/levels/training home modes, settings through the lower controls,
archives, populated statistics, hints, walkthrough, training celebration, finish/share and tablet play.
Sizes included 375x537, 360x640, 390x844, 844x390, 768x1024 and 1024x768, both themes and 2x text.
The shortest 375x537 viewport was also checked at 2x for archive, welcome and share. Targeted reruns
verified the fixes found during review, including deep per-puzzle statistics and English comparisons.

Every case blocked external page requests, loaded all four Cyrillic weights locally for Russian
(none for English), and redrew after going offline. No page errors, font failures or external requests
were observed. The existing glyph harness also passed all 32 language/browser/screen combinations.
Only isolated browser contexts were used, with service workers blocked and no personal profile.

Android compilation, 33 focused tests and final unsigned release assembly passed alongside the Wasm
distribution. Native Android visual checks, real-device Safari and the full unit suite were not run.
Headless Linux WebKit is an approximation of Safari, as described in `AGENTS.md`.
