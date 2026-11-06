# Languages and Russian word puzzles

Completed implementation and acceptance evidence are in [LOCALIZATION_PROGRESS.md](LOCALIZATION_PROGRESS.md).
The shared catalogs cover interface text, game names, rules, hints, walkthroughs, techniques, training,
achievements, sharing and reminders. Menus select word editions matching the interface language;
each open or saved board keeps its original vocabulary and identity.

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

`PuzzleRegistry.forLanguage` selects 26 language-neutral puzzles and seven matching word editions for
Daily/Random, Archive and statistics. Continue, Today's Three, next-puzzle suggestions and training
use that selection too. The full registry stays available for saved-board lookup and restored routes.
Hidden editions keep their saves and custom-order positions and reappear when switching back.
Coverage achievements accept either edition of each word family; puzzle-specific records remain separate.
Home previews reuse each family's real motif, with fixed Russian positions for letter-based diagrams.

New Levels also select matching word editions. English and Russian keep separate progress and saved
boards, including language-neutral puzzles. Plans deal the frozen 33-family prefix before choosing an
edition, avoiding duplicate families; their seed namespace is separate from previous levels.
Existing levels are available through **Previous levels**, with their original boards, seeds and progress
unchanged. An open level route keeps its captured language across interface changes and recreation.

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

### Initial implementation validation on 2026-11-06

Final isolated Chromium/WebKit checks pass under both English and Russian system locales: live
picker changes, persisted overrides, return to system, service-worker cache contents, actual offline
reloads and home/archive/statistics/share navigation. All seven Russian boards keep their content and
saved progress when the interface changes; Cyrillic key-down input, physical-layout codes and Ё
normalization are exercised alongside unchanged English input. All eleven bundled font files are
cached before disconnecting, with no incomplete font bodies, font failures, page errors or external
requests. No personal browser profile or account was used.

The Russian 40-game guidance checks cover all 160 cases per engine at 375×537, dark theme and 2× text:
WebKit passed its original matrix; Chromium combines 159 original passes with a reviewed focused
Who's Who rules rerun after that case timed out. The original timeout remains in the evidence.
Another 32 focused screenshots were visually reviewed without findings, covering short-screen hint
actions and tall-screen Mini/Lexicon keyboards in both languages/engines at 1×/2×. Tutorial prose
scrolling, landscape, temporary zero-room resizing and portrait recovery also pass. Earlier shell
coverage includes both themes, tablet layouts, training, achievements and populated sharing/statistics.
The evidence and full acceptance ledger are in [LOCALIZATION_PROGRESS.md](LOCALIZATION_PROGRESS.md).

Annual JVM/Chromium/WebKit parity passes 45,625 boards per engine: all 40 puzzles for 365 days at
three tiers, plus 365 five-board levels. Fresh [CI web-parity](https://github.com/ReOT20/daybook/actions/runs/42159833645/job/127702631901)
confirms the full comparison on clean PR merge `35597233`, containing source `2327ad6b`.
Original English pins and frozen saves remain unchanged.
Russian hint walks finish all 7,665 sampled boards without mistakes, stalls or losses; their measured
fallback rates remain separate in [TEACHING.md](TEACHING.md). Catalog/font checks pass 10 tests,
including ten-language generation, and verifier self-checks pass 8. Only English and Russian ship.

The fresh [CI build on `2327ad6b`](https://github.com/ReOT20/daybook/actions/runs/42159833645/job/127702631641)
executed the full JVM suite and passed the guide and release builds.
Earlier combined JVM evidence accounts for 227 classes / 1,486 methods, with no unresolved failures;
that separate coverage map combines checkpoints and changed-class reruns. The minified local-test
APK `1.39.2` for source `55c79d98` builds with the persistent local test certificate and passes
signature, artifact and 16 KiB alignment checks. App code/resources and build inputs are unchanged
through verified `2327ad6b`; its closing follow-up changes documentation only. It is not production signed.

The complete [KVM workflow](https://github.com/ReOT20/daybook/actions/runs/42159833649) passed all
nine stages on clean PR merge `35597233`, containing `2327ad6b`: all 144 frozen saves loaded unchanged;
all 14 mixed English/Russian identities survived eight lifecycle cases. Its targeted JVM stage reported
137 tests with four skips and no failures/errors. All six Settings screenshots were reviewed without
clipping or malformed Cyrillic. All four Chromium/WebKit × English/Russian offline cases also passed,
with all eleven fonts cached and no page errors or external requests. The native home capture is
reviewed and checked in at the required half scale.

The complete annual comparison passes with the bounded 1,800-second page and 90-minute CI job limits;
coverage and comparisons are unchanged. The API 34 pass does not prove the older Android
preference/migration path or a physical Android 17 device. Physical devices and real Safari were not
used; Linux WebKit is the documented approximation in `AGENTS.md`.
