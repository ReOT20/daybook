# Verify a version without a physical device

Run from the repository root with Java 17, the Android SDK, and the Python environment below:

```bash
python tools/verify-version/check.py
```

This is a development check. By default it builds the current minified release, aligns a copy for
16 KiB pages, and signs that copy with a disposable local test key. **That APK is not a release or an
update for an installed production app.** The key lives only in a private temporary directory and is
deleted on exit. The application ID and the official certificate pin are never changed.

To check a production APK instead, including its pinned production certificate:

```bash
python tools/verify-version/check.py --apk /absolute/path/daybook.apk
```

This verifies the supplied artifact's signature and runtime behavior. It cannot prove that a supplied
APK was built from the current checkout; the report records both the artifact hash and checkout commit.

The command fails if a required check or prerequisite is missing. It does not turn an unavailable
emulator into a pass. `--preflight` checks prerequisites without building or launching anything.
`--full` also runs the whole JVM suite and the existing 365-day JVM/Chromium/WebKit board comparison;
the normal CI workflow already runs those longer checks separately.

## Prerequisites

Set `JAVA_HOME` to Java 17 and `ANDROID_HOME` to an SDK containing the repository's build packages
(`platforms;android-37.0`, `build-tools;36.0.0`), platform-tools, command-line tools, and:

```bash
sdkmanager 'emulator' 'system-images;android-34;google_apis;x86_64'
python3 -m venv /tmp/daybook-verify-env
/tmp/daybook-verify-env/bin/pip install -r tools/verify-version/requirements.txt
/tmp/daybook-verify-env/bin/python -m playwright install chromium webkit
```

Use that environment's Python for the command. On a fresh Linux CI runner, Playwright's
`install --with-deps chromium webkit` also installs system libraries. Local no-sudo WebKit setup is
described in [the rendering guide](../tools/render/README.md).

Linux needs accessible `/dev/kvm` for a practical native run. `--software-emulator` explicitly opts
into much slower CPU emulation, with a 15-minute boot timeout. The image is the API 34 `google_apis`
`x86_64` image used by the existing release save gate; a Play Store image cannot provide `adb root`.
The Android command-line tools must belong to `ANDROID_HOME`, rather than being symlinked from a
different SDK: `avdmanager` discovers images relative to its actual installation.
The temporary filesystem needs at least 10 GiB free for an AVD. On machines with a small `/tmp`
tmpfs, set `TMPDIR` to a private directory on a disk-backed volume before running the command.

No existing AVD, adb server, attached device, browser profile, or account is used. The harness creates
and removes its own AVD and adb server, pins every adb call to its emulator serial, disables adb's
automatic network-device pairing, and allows no USB serials. Root, date changes, app installation,
and save injection happen only inside this disposable emulator.

## Checks and evidence

The default command checks:

- Catalog keys and placeholders with `tools/i18n/build.py --check`; localization, language, catalog,
  board pins, save compatibility, serialization, share-card, guide, tier-note, and Russian word-game
  data/rules JVM tests. Menu selection, hidden-edition ordering, localized daily suggestions and
  independent language-level plans/progress/routes are included.
- The actual APK with `apksigner verify`, non-debuggable application/package and permission checks,
  `zipalign -c -P 16`, and every arm64 ELF `LOAD` segment's alignment and address/offset congruence.
- Installation and unchanged rewriting of frozen saves using the existing release save checker.
- Android system English/Russian, explicit in-app language overrides, process restarts, return to
  system language, native Android Settings/`LocaleManager` agreement, and language/save retention
  through an in-place reinstall, with native home captures in both languages.
- Live language-picker changes in Chromium and WebKit, persisted overrides, return to the browser's
  language, bundled Cyrillic fonts, no requests off localhost, service-worker cache contents, and
  actual offline reloads and navigation through home, archive, statistics, and share screens.
- All seven separately identified Russian word boards offline under both UI languages; actual
  Cyrillic key-down input, Russian-layout physical key codes and Ё normalization in Lexicon, Mini
  and Codeword, including Р on physical H without triggering Hint; saved progress and
  content-language retention after changing UI language. Existing
  English Lexicon, Mini and Codeword identities retain English content and Latin input as well.
- The first-visit tutorial at 2× text size in both languages, a landscape resize, a temporary zero-room
  board and return to portrait. The last case checks recovery from a squeezed window, rather than
  promising that every control fits a 240-pixel-high viewport.

The browser stage can also run by itself against an already built distribution:

```bash
python tools/verify-version/web.py --out /tmp/daybook-browser-verification
```

That command proves only the browser cases; it does not substitute for the Android gate.
The render harness discovers all registered puzzle IDs, including language variants sharing one
source file, so new variants are included without maintaining a separate rendering list.

Logs, screenshots and `report.json` go to a fresh `/tmp/daybook-version-*` directory, or a fresh
directory passed with `--out`. The report remains failed/incomplete if any stage fails. Inspect the
screenshots for clipping: canvas paint, locale and network assertions cannot prove every label fits.
The disposable APK remains clearly named `daybook-TEST-ONLY.apk`; do not distribute it.

[The verification workflow](../.github/workflows/verify-version.yml) runs the same command on a KVM
runner for relevant pull requests and manual dispatches. It needs no signing or publishing secrets,
has read-only repository permissions, and uploads the logs/screenshots even after failure.
