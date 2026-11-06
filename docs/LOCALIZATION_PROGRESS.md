# Localization implementation and acceptance

The active work extends the initial shell translation on `feat/russian-localization`, based on
`chore/shared-agent-guidance`. Neither the initial pass nor an individual green check completes this work.
The existing guidance in `AGENTS.md`, puzzle standards, saved-state contracts and original English pins apply.

Required before completion:

- [ ] Android system per-app language and in-app language picker, plus web picker, persistence, restarts,
  return to system language, and switching without losing navigation or progress.
- [x] One keyed catalog contract for both platforms: complete locale/key/placeholder/plural validation,
  metadata-driven supported languages and Android locale list, CLDR plurals and calendar names;
  demonstrate ten-language generation without runtime source edits.
- [ ] Translate all in-app names, rules, keyboard help, walkthroughs, hints, techniques, training,
  achievements, finish text, notifications, sharing and dynamic board labels.
- [ ] Separate playable Russian `words-ru`, `wordwheel-ru`, `mini-ru`, `codeword-ru`, `hexwords-ru`,
  `dwindle-ru`, `cipher-ru` with frozen attributed corpora, native word clues, input and walkthroughs;
  independent proofs, calibrated tiers, pins, guides, save round trips, and JVM/browser parity.
- [ ] Repeatable fail-closed version verification covering the minified signed APK, emulator installation,
  frozen saves, language lifecycle, isolated Chromium/WebKit, and actual service-worker offline reloads.
- [ ] Render and review changed screens, RU variants, hints and long text at narrow/short, tall, landscape,
  tablet and large-text settings, with bundled glyphs and no external requests.
- [ ] Full relevant JVM contracts and web comparison green; original English pins and old saves unchanged.
- [ ] Commit and push coherent changes on the existing fork branch; update draft PR2 and documentation;
  provide an installable APK using the same persistent local test certificate as the installed build.

Source implementation is complete and undergoing final verification: shared catalogs; language picker/native
LocaleManager; calendar metadata; all game names/rules/walkthroughs/teachers; training and achievement prose;
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
- Java17/SDK37/build-tools36 available under `/tmp/daybook-toolchain`; Gradle needs local-socket sandbox
  escalation. Babel2.17 is build-time only, installed in `/tmp/daybook-pw` along with the browser tools.
- This host has no `/dev/kvm`; software emulator crashes before app installation. Do not report native
  runtime checks passed. The new KVM GitHub workflow is the intended path to native proof.
- Python catalog/source/font checks pass (10 tests), including ten-language generation and service-worker
  precaching of all eleven bundled fonts. The catalog has 3,462 keys per language. Latest combined
  Android checkpoint: 141 tests, zero failures/errors and four
  opt-in probe skipped. This includes retained-language/share behavior, exact English share goldens,
  frozen Russian data and independent solver samples, generators, native walkthrough fixtures,
  historical Today's three achievements, and the narrow-screen board-font guard.
- All original 33 × 9 English board pins match. Seven new nine-board pin sets are appended. A separate
  comparison preserves 649 complete Cipher hint steps byte-for-byte across 90 seeded English boards.
  All 48 walks of the 16 logic teacher families (three tiers each) pass. All 40 guide pages and their
  native diagrams pass GuideTest; generated pages were rendered in light/dark without overflow.
- Russian annual hint measurements pass on 7,665 boards (365 days × three tiers × seven editions),
  counting all clue/answer-list fallbacks and any loss. All sampled walks finished, with no mistake,
  missing hint, stalled move or loss. Separate Russian rates are recorded in `TEACHING.md`.
- The JVM half of annual parity passes: 45,625 boards, including all 40 puzzles and 365 five-board
  levels. Comparison with both browser engines is pending.
- Broad JVM coverage completed on an immutable checkpoint: 224 classes / 1,473 tests, 13 opt-in
  assumption skips. Its three stale contract failures are corrected and rerun: family accent spacing
  (4 tests), the visible-only Dwindle dictionary argument (6), and explicit coverage of seven Russian
  technique-name identities (3). The remaining pin/serialization/save classes pass separately (10).
  Latest source discovery has 227 classes / 1,486 methods; evidence combines checkpoints and fresh
  changed-class checks rather than claiming a single fresh all-tests invocation.
- Earlier service-worker picker checks passed in both engines, but a later cold offline WebKit run
  exposed a late-font caching race. All bundled fonts are now explicitly precached; final offline
  checks must verify the actual cache before disconnecting. Each original-game browser matrix completed
  139 cases without errors or off-site requests. Final 40-game large-text/Unicode and resize checks,
  rebuilt minified APK and native CI gates are still running or pending. The rendered hint-row sizing
  crash is corrected in source and must pass the final browser artifact; no release-verification claim yet.

This file is a working acceptance ledger. Replace this progress section with final evidence when every
required item is complete; do not weaken the requirements to match partial implementation.
