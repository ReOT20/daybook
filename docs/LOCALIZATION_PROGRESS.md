# Localization implementation and acceptance

## Tester follow-up: language menus and icons

The 2026-11-06 test feedback identified mixed-language word menus and placeholder Russian tile icons.
The follow-up selects 33 games per interface language, keeps all 40 identities available for saved boards,
preserves hidden custom-order slots, and replaces all seven Russian placeholders with real family motifs.
Continue, daily suggestions, archive, statistics and training use the same selection. Coverage achievements
accept either edition while puzzle-specific records retain their full IDs.

New Levels have separate English/Russian progress and seed namespaces; all original levels stay available
through Previous levels. Open routes retain their captured lane, including during crossfades. All 10,000
levels per language deal five unique matching families. Legacy plans, seeds and raw store entries are unchanged.

- [x] Final Android/Wasm build and 149 JVM tests in 19 classes pass, with zero failures/errors and four
  opt-in skips. Evidence: `/tmp/daybook-language-menu-fix-final-build/`.
- [x] Before the Levels follow-up, 16 browser cases / 100 screenshots cover both engines, both phone sizes,
  themes and 1×/2× text. All seven Russian motifs are visually reviewed; actual Home taps open matching EN/RU
  Lexicon boards. Live switches retain 14 frozen records, both freshly played boards and the full stored order,
  with no browser errors or external requests. Evidence: `/tmp/daybook-language-menu-fix/acceptance-summary.json`.
- [ ] Final Levels runtime checks on the updated distribution, native verifier, refreshed README home image,
  signed `1.39.3` APK and draft PR update.

## Initial implementation acceptance

Completed on 2026-11-06 on `feat/russian-localization`, based on `chore/shared-agent-guidance`.
The existing `AGENTS.md` guidance, puzzle standards, saved-state contracts and original English pins
are preserved. [PR2](https://github.com/ReOT20/daybook/pull/2) remains a draft stacked on the guidance
branch. The verified source and harness are `2327ad6b`; the closing follow-up changes documentation only.

Acceptance:

- [x] Android system per-app language and in-app language picker, plus web picker, persistence, restarts,
  return to system language, and switching without losing navigation or progress.
- [x] One keyed catalog contract for both platforms: complete locale/key/placeholder/plural validation,
  metadata-driven supported languages and Android locale list, CLDR plurals and calendar names;
  demonstrate ten-language generation without runtime source edits.
- [x] Translate all in-app names, rules, keyboard help, walkthroughs, hints, techniques, training,
  achievements, finish text, notifications, sharing and dynamic board labels.
- [x] Separate playable Russian `words-ru`, `wordwheel-ru`, `mini-ru`, `codeword-ru`, `hexwords-ru`,
  `dwindle-ru`, `cipher-ru` with frozen attributed corpora, native word clues, input and walkthroughs;
  independent proofs, calibrated tiers, pins, guides, save round trips, and JVM/browser parity.
- [x] Repeatable fail-closed version verification covering the minified signed APK, emulator installation,
  frozen saves, language lifecycle, isolated Chromium/WebKit, and actual service-worker offline reloads.
- [x] Render and review changed screens, RU variants, hints and long text at narrow/short, tall, landscape,
  tablet and large-text settings, with bundled glyphs and no external requests.
- [x] Full relevant JVM contracts and web comparison green; original English pins and old saves unchanged.
- [x] Commit and push coherent changes on the existing fork branch; update draft PR2 and documentation;
  provide an installable APK using the same persistent local test certificate as the installed build.

Native API 34 verification, the full CI build, annual parity and the complete end-to-end workflow pass
on `2327ad6b`. The implementation includes
shared catalogs; language picker/native LocaleManager; calendar metadata; all game
names/rules/walkthroughs/teachers; training and achievement prose;
retained-language celebrations/share cards; attributed Russian vocabulary; seven registered Russian editions;
and the automated verifier. Mini and Codeword each have 365 frozen boards per tier. Cipher has
668 / 1,000 / 1,000; its finite answer-pair build exhausted 95,266 pairs. Every table entry passed its
uniqueness and teaching gates. Russian block patterns were calibrated without changing English patterns
or weakening proof/difficulty requirements. There are no builder bootstrap tables left.

Constraints and evidence:

- `Message.english`, `.length` and `.toString()` remain canonical English. Teacher text-length gates
  affect generation: resolve Russian only at UI boundaries. Hints must retain typed nested descriptors.
- Display language never selects content. Existing ids, serializers, corpus order, seeds and level deal
  versions stay stable. Append state fields only with English defaults; RU IDs keep separate records.
- User authorized CLI build-dependency downloads and isolated local headless tests. No personal browser,
  account session, or physical device was used. Local test signing key is outside the checkout.
- Verification uses Java 17, Android SDK 37/build-tools 36 and build-time Babel 2.17; no app runtime
  dependency was added. Reproduction instructions are in `VERIFY_VERSION.md`.
- The complete [KVM workflow](https://github.com/ReOT20/daybook/actions/runs/42159833649)
  passed all nine stages on clean PR merge `35597233`, containing source `2327ad6b`.
  Its minified APK loaded all 144 frozen saves unchanged,
  with zero lost/changed. All 14 mixed English/Russian saved identities survived eight API 34 language,
  restart and in-place reinstall cases. All six native Settings screenshots were reviewed without
  clipping or malformed Cyrillic. Its targeted JVM stage reported 137 tests with four skips and zero
  failures/errors. All four Chromium/WebKit × English/Russian cases passed actual offline checks,
  with eleven cached fonts per case and no page errors or external requests. Evidence:
  `/tmp/daybook-ci2327-native/report.json`, `android-save-check.log` and `web/results.json`.
  The pinned-date native home capture is reviewed and replaces `docs/screenshots/home.png` at 540×1200.
- Python catalog/source/font checks pass (10 tests), including ten-language generation and service-worker
  precaching of all eleven bundled fonts; verifier self-checks pass (8 tests). The shipped English and
  Russian catalogs each have 3,462 keys. Ten-language generation demonstrates the catalog contract;
  it does not claim ten translated interfaces are shipped.
- All original 33 × 9 English board pins match. Seven new nine-board pin sets are appended. A separate
  comparison preserves 649 complete Cipher hint steps byte-for-byte across 90 seeded English boards.
  All 48 walks of the 16 logic teacher families (three tiers each) pass. All 40 guide pages and their
  native diagrams pass GuideTest; generated pages were rendered in light/dark without overflow.
- Russian annual hint measurements pass on 7,665 boards (365 days × three tiers × seven editions),
  counting all clue/answer-list fallbacks and any loss. All sampled walks finished, with no mistake,
  missing hint, stalled move or loss. Separate Russian rates are recorded in `TEACHING.md`.
- Annual JVM/browser parity passes in Chromium and WebKit: 45,625 identical boards in each engine,
  covering all 40 puzzles at three tiers for 365 days and 365 five-board levels. Evidence:
  `/tmp/daybook-final-year-parity/jvm.txt`, `browsers.log` and `browsers-webkit.log`.
  Fresh CI confirms the same full comparison on clean PR merge `35597233`, containing `2327ad6b`:
  [web-parity job 127702631901](https://github.com/ReOT20/daybook/actions/runs/42159833645/job/127702631901).
  Its complete log is `/tmp/daybook-ci2327-native/parity-full-job.log`: all 41 pages pass in each engine.
- Combined JVM evidence accounts for 227 classes / 1,486 methods: 1,471 passes, 15 opt-in assumption
  skips and no unresolved failures. It covers retained-language/share behavior, English share goldens,
  Russian proofs/generators/walkthroughs, historical achievements, levels, pins, serialization and saves.
  This combines immutable checkpoints and changed-class reruns, not one fresh full-suite invocation.
  Evidence: `/tmp/daybook-localization-review/jvm-broad-119/combined-coverage/coverage.json`.
  Separately, the fresh [CI build on `2327ad6b`](https://github.com/ReOT20/daybook/actions/runs/42159833645/job/127702631641)
  executed the complete JVM suite and passed the player-guide and release builds. Its test task was
  not restored from cache; log: `/tmp/daybook-ci2327-native/build-full-job.log`.
- Final browser acceptance passes all four Chromium/WebKit × English/Russian cases, with actual offline
  reloads/navigation, picker persistence, seven Russian boards, Cyrillic keyboard input, and saved-board
  retention across UI-language changes. All eleven font files are cached before disconnecting; every
  requested font body completed, with no font failures, page errors or external requests. Evidence:
  `/tmp/daybook-final-browser-rerun/acceptance-summary.json` and `report.json`.
- The 40-game Russian 2×-text guidance checks cover all 160 cases per engine at 375×537 in dark mode.
  WebKit passed all 160 in its original matrix. Chromium passed 159 there; its Who's Who rules case
  timed out and then passed a focused rerun whose eight screenshots were reviewed without findings.
  The original timeout remains in the matrix evidence; this is combined Chromium coverage.
  Final focused visual review covers 32 screenshots with no findings: short-screen hint actions,
  tall-screen Mini/Lexicon keyboards at 1×/2×, and scrollable tutorial prose. Both languages/engines also
  pass landscape, zero-room and portrait-recovery probes. Earlier shell coverage includes both themes,
  tablets and populated statistics/sharing. Evidence: `/tmp/daybook-guidance-final40-font2/`,
  `/tmp/daybook-whoswho-rules-closure/`,
  `/tmp/daybook-final-browser-finish/visual-review.json` and `/tmp/daybook-final-short-layout-recheck/`.
- The R8-minified local-test APK builds and passes signature, 16 KiB ZIP/arm64 alignment and artifact
  checks. Version `1.39.2` / code `2`, source `55c79d98`; SHA-256
  `ac391f805d1dc2370dee2dd6b0469130efe7c48760f05c41fca871ca6acd0fc5`.
  App code, resources, web, catalogs, Gradle inputs and version are unchanged through verified `2327ad6b`.
  It uses the same persistent local test certificate as the earlier test build, not the production key.
  Evidence: `/tmp/daybook-final-artifact-verification.json`; APK:
  `app/build/outputs/apk/local-test/daybook-russian-1.39.2-local-test.apk`.

The parity helper now allows 1,800 seconds per page and the CI job 90 minutes, retaining all boards,
comparisons and failure checks. The complete annual comparison passes with these bounds.
The API 34 native pass does not establish runtime behavior on older Android or a physical Android 17
device. No physical-device or real Safari claim is made; Linux WebKit is the documented browser
approximation. External player-guide prose remains English, as stated in `LOCALIZATION.md`.
