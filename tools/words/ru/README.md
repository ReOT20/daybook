# Russian word content, revision 1

This is a separate, frozen Russian vocabulary, independent of the UI language and all English
corpora. The registry appends separate `words-ru`, `wordwheel-ru`, `mini-ru`, `codeword-ru`,
`hexwords-ru`, `dwindle-ru`, and `cipher-ru` puzzle identities.
Never select this dictionary by the player's display language: an existing board must keep its
word content when the display language changes.

`python3 tools/words/ru/build.py` generates `core/words/RussianWordData.kt`, attribution assets
and `COUNTS.json`. `--check` compares those outputs without writing. It needs only Python's
standard library and the committed frozen inputs; no network or build-time package dependency.
The English build script and its inputs are not read or changed.

## Canonical spelling and API

`RussianWords` uses the lower-case 32-letter alphabet
`абвгдежзийклмнопрстуфхцчшщъыьэюя`. **Ё is folded into Е** for guessing, letter domains,
word identity, sorting and tile counts. Stress marks are removed. `spelling(word)` retains proper
Ё spelling in explanatory prose. Latin lookalikes are never transliterated into Cyrillic.

`answers(length)` returns the frozen common-noun answer lists, lengths 3–7. `grid(length)` returns a separate broader pool of screened, clued words, including familiar nouns, verbs, adjectives, adverbs and function words. `guesses`
contains the broader accepted vocabulary, including inflections and words unsuitable as printed
goals. `clue` requires a known answer. `isWord`, `spellable` and `gridWordsFrom` are pure;
spellable checks multiplicity, so two copies of a letter require two tiles.

`answer(seed,length)` uses the app's stable `Rng`. `dailyAnswer(dayIndex,length,stream)` shuffles one
fixed answer list independently each round using its own namespace. Day indices are nonnegative
and start at the Russian variant's immutable cutover date, 2026-11-06. There are no repeats within
a round: 437 days at four letters, 632 at five. Separate difficulty streams use separate shuffles. This does not read or change English daily seeds.
The sorted data and selector become a puzzle contract after release; future changes need new
content revisions and board pins, not silent edits to existing days.

`roots(5/6/7)` contains 568/690/220 roots with at least 4/6/8 screened spellable words. Every
seven-letter root also has a six-letter word. These are **vocabulary feasibility checks**. They
do not prove layout success, uniqueness, teacher completeness, balanced tiers or generation
latency. Each integrated game must retain its own independent proofs, measured search limits,
year samples and English pins. A 32-letter domain needs `Long` or an equivalent set: an `Int`
expression such as `(1 shl alphabet.length)-1` wraps at 32.

## Frozen inputs and screening

- `curated.tsv`: the unchanged 2,406 common-noun answer words, with canonical word, proper spelling, short Russian clue,
  wordfreq Zipf familiarity score and source URL. Exact per-clue attribution is also packaged as
  `assets/word-lists/ru/CLUE-SOURCES.tsv`.
- `grid.tsv`: 4,471 clued grid words (256 / 699 / 1,161 / 1,212 / 1,143 at lengths 3–7), including
  every answer word. The broader noun pool uses the same sources/screen at Zipf frequency ≥2.5;
  `grid-exclude.txt` removes unsuitable terms and rare homographs. `grid-common.tsv` supplies
  original standalone clues for 210 reviewed familiar word inputs across parts of speech. Every
  resulting grid word must also be accepted by the broad dictionary. `grid-clue-overrides.tsv`
  replaces selected weak secondary definitions. This expansion never inserts into or reorders
  Lexicon's answer lists.
- `guesses.txt.gz`: 123,725 sorted canonical words, lengths 3–7. Lower-case words and inflections
  extracted from the pinned Russian Wiktionary dump were accepted only if the pinned Hunspell
  dictionary recognized them, then combined with lower-case Hunspell base entries. Inflections,
  uncommon words and grim words remain legal guesses. Upper-case proper-name entries were not
  blindly lowercased into the candidate pool.
- `exclude.txt`: explicit answer/grid noun exclusions, including the breakfast-table screen from
  `../grim.txt`, proper names/brands, rare homographs, colloquial abbreviations and weak
  inflection/derivation clues. It does not restrict typed guesses. Reviewed common non-noun grid entries such as ЕСТЬ and БЕЛЫЙ are explicit overrides of exclusions that applied to their rare noun homographs.
- `clue-overrides.tsv`: original short clues replacing technical or secondary dictionary senses.
  These changes are identified in `curated.tsv`; other clues are extracted/adapted from the
  attributed Russian Wiktionary definition. This is a data preparation input, not a second runtime
  dictionary.

Frozen answer candidates were lower-case Hunspell base lemmas, Russian Wiktionary nouns with a lemma
entry (not a `form-of` / `alt-of` sense), and wordfreq 3.1.1 Russian Zipf frequency at least 3.2.
Rare, obsolete, dialectal, vulgar, insulting and proper-name sense tags/categories were rejected.
The explicit exclusions remove common homographs whose frequency belongs to a name or another
part of speech. Clues reject marked unsuitable senses, retain a short definition, strip stress,
domain labels and parenthetical detail, and use explicit original overrides for selected common
words. Definitions which merely say “action of [verb]” are excluded. The expanded noun grid follows the same screen at the stated lower threshold, with separately reviewed common-word additions. The build checks sorted
uniqueness, coverage, canonical spelling, duplicate clues, literal answer leakage, minimum corpus
sizes and wheel-root feasibility. A raw statistical dictionary is not treated as a curated answer
list. As with the existing English corpus, substantive future editorial corrections change content
and must be reviewed before release.

## Sources and licensing

`SOURCE.json` records exact versions, snapshot, extractor commits, URLs and SHA-256 checksums.
The input dump is deliberately not committed (272.5 MB compressed). The frozen TSV and gzip
inputs are sufficient to rebuild the shipped data; source URLs/checksums make the original source
snapshot identifiable and verifiable.

1. LibreOffice `dictionaries`, commit `30dd499a102c930b6386d9b0b44ed13e9d4cf8d2`,
   `ru_RU/ru_RU.dic` and `.aff`. Copyright 1997–2008 Alexander I. Lebedev.
   The complete permissive notice is in `vendor/LICENSE-HUNSPELL.txt`. The output is a modified
   subset: lower-case length selection, canonical Ё→Е, deduplication and screening.
2. Russian Wiktionary contributors, dump 2026-11-01, extracted 2026-11-02 by Wiktextract/Kaikki.
   Russian Wiktionary's siteinfo rights API identifies CC BY-SA 4.0. Definitions and extracted
   vocabulary, and adaptations of them, are distributed under CC BY-SA 4.0. See
   `vendor/CC-BY-SA-4.0.txt`, the per-entry source URLs, and each source page's history for authors.
3. Robyn Speer, `wordfreq` 3.1.1, used only while preparing familiarity scores. Code is Apache 2.0;
   frequency data is CC BY-SA 4.0. Its complete README, source acknowledgements and SUBTLEX
   citations are retained in `vendor/WORDFREQ-README.md`, with the upstream code notice beside
   it. SUBTLEX is freely available data. Wordfreq acknowledges Google Books, Leeds, Wikipedia,
   ParaCrawl, OpenSubtitles and SUBTLEX authors; retain those acknowledgements when redistributing.

Generated vocabulary/definitions remain separately licensed data; application code retains its
repository license. `NOTICE.txt`, license texts, upstream acknowledgements, source metadata and
per-clue source attribution are bundled as Android assets and as static web resources. The app
does not download a dictionary at runtime.

## Frozen puzzle tables

Russian Mini and Codeword each ship 365 sorted entries per tier; Russian Cipher ships 668 Standard,
1,000 Hard and 1,000 Expert sums. Standard's finite noun-pair search exhausted all 95,266 pairs.
The shared runtime re-proves the selected entry. `RussianCrosswordRulesTest` checks corpus/table
invariants and independent letter-by-letter solver samples; Cipher's complete table is proved and
classified again. New IDs have their own board pins and saved-game captures.

The offline builder reuses the existing filler, exact solvers and teacher gates. Mini draws two
Standard block densities (4/6), three Hard densities (8/10/12), and three Expert densities (15/17/19).
Codeword keeps the original Standard/Hard layouts and uses 52-block Expert patterns. All remain
half-turn symmetric, connected and fully checked, with distinct clued words. Grid sizes, uniqueness,
Mini clue-share bands and Codeword/Cipher teaching-depth requirements are unchanged.

To reproduce these tables before first release, set `DAYBOOK_RUSSIAN_BUILD` to `mini`, `codeword`, or
`cipher`, `DAYBOOK_RUSSIAN_PER_TIER` to `365` for the two crosswords or `1000` for Cipher, and
`DAYBOOK_RUSSIAN_OUTPUT` to an absolute temporary `.kt` path, then run:

```bash
./gradlew :app:testDebugUnitTest --tests '*RussianWordPuzzlesBuildTest*' --rerun
```

The seed is fixed at 20261106; batches are consumed in draw order even with parallel workers. The
builder requires at least 365 entries per tier (unless explicitly running a smaller probe). Sorted
list indices are part of board identity: after release, do not reorder/rebuild them in place. A future
content correction or expansion needs a versioned content set while existing saves remain readable.

To measure the Russian teachers separately from the English vocabulary, set
`DAYBOOK_RUSSIAN_TEACHING_DAYS=365` and run `:app:testDebugUnitTest --tests '*RussianWordGamesTest*'
with `--rerun`. Its `RU_HINTS` output records every board, hint, fallback, technique and loss from
the cutover date on each tier. The follower applies each hint's public move and submits complete
Lexicon guesses. Lexicon can exhaust its guess limit; those losses are counted, never discarded.
Every other family must finish without mistakes, stalled moves or missing hints.
