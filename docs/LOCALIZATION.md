# Russian localization

This first pass localizes the app shell: home, navigation, settings, archive, calendars, statistics,
play/hint/walkthrough/training controls, finish/share text and reminders. Puzzle names, rules, clues,
teaching explanations, walkthrough frames, technique and achievement content, and word-game corpora
remain English. Settings says so in Russian. The remaining work is in `docs/TODO.md`.

## Text and identity

`core/i18n/Text.kt` selects Russian for a `ru` language tag, including regional variants, and English
otherwise. The platform seam reads Android's device locale or the browser's language. There is no new
preference or saved field; restart/reload after changing the system language. A language picker and live
switching are outside this first pass.

`tr(english, russian)` keeps complete bilingual messages at their call sites. Preserve the English
argument exactly when extracting existing text. `russianPlural` returns the noun form, without the count;
11–14 take the plural even where 1–4 otherwise would not. Translate whole sentences rather than joining
English grammatical fragments. Labels are presentation getters, never stored enum names.

Language must not enter generators, seed hashing, registry order, vocabulary indexes, serialized state,
route or preference keys. English word lists remain unchanged. A Russian word board needs a separate
content-language identity before new corpora can be introduced. English-only content should not interpolate
Russian display labels.

Android keeps `DateTimeFormatter` and its existing locale behavior, with the locale included in the cache
key. The web's supported patterns are tested in `CalendarText.kt`; `MMMM` uses Russian date forms
(`5 ноября`), while `LLLL` is a standalone month (`ноябрь 2026`). Weekday captions stay Monday first.

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
chars += [0x2013, 0x2014, 0x2018, 0x2019, 0x201c, 0x201d, 0x201e, 0x2022,
          0x2026, 0x2032, 0x2033, 0x2039, 0x203a, 0x2212]
with TemporaryDirectory() as tmp:
    for name, weight in [("regular", 400), ("medium", 500), ("semibold", 600), ("bold", 700)]:
        static = Path(tmp) / f"noto_sans_{name}.ttf"
        instantiateVariableFont(TTFont(source), {"wght": weight}, updateFontNames=True).save(static)
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
