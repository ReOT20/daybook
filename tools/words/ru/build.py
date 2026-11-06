#!/usr/bin/env python3
"""Generate the isolated Russian word library from frozen, attributed source inputs.

No downloads or third-party Python packages. Use --check in CI; the English corpus is never read
or rewritten. These sorted lists and daily selectors become a puzzle identity contract once used.
"""
from __future__ import annotations
import argparse
from collections import Counter
import csv
import gzip
import hashlib
import json
from pathlib import Path
import re
import sys

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[2]
OUTPUT = ROOT / 'app/src/main/java/com/joebywan/daybook/core/words/RussianWordData.kt'
ASSETS = ROOT / 'app/src/main/assets/word-lists/ru'
ALPHABET = 'абвгдежзийклмнопрстуфхцчшщъыьэюя'
MIN_ANSWERS = {3: 150, 4: 400, 5: 600, 6: 550, 7: 500}
MIN_GRID = {3: 250, 4: 650, 5: 1100, 6: 1150, 7: 1100}
MIN_GUESSES = {3: 1000, 4: 5000, 5: 18000, 6: 37000, 7: 58000}


def words_in(name: str) -> set[str]:
    return {w for line in (HERE / name).read_text().splitlines() for w in line.split('#')[0].split()}


def normalize(word: str) -> str:
    return word.lower().replace('ё', 'е')


def spellable(word: str, wheel: str) -> bool:
    letters = Counter(wheel)
    return all(n <= letters[c] for c, n in Counter(word).items())


def escaped(text: str) -> str:
    return text.replace('\\', '\\\\').replace('"', '\\"').replace('$', '\\$').replace('\n', '\\n').replace('\t', '\\t')


def packed(name: str, entries: list[str], separator: str = ' ') -> list[str]:
    # Keep both UTF-8 constants and JVM method bodies small; chunks are below 20KB in UTF-8.
    chunks, current, size = [], [], 0
    for item in entries:
        current.append(item)
        size += len(item.encode()) + len(separator)
        if size >= 16000:
            chunks.append(separator.join(current))
            current, size = [], 0
    if current:
        chunks.append(separator.join(current))
    lines = [f'    val {name}: List<String> = listOf(']
    lines += [f'        "{escaped(chunk)}",' for chunk in chunks]
    lines += [f'    ).joinToString("{escaped(separator)}").split("{escaped(separator)}")', '']
    return lines


def generate() -> tuple[dict[Path, str], dict]:
    answer_rows = list(csv.DictReader((HERE / 'curated.tsv').open(), delimiter='\t'))
    rows = list(csv.DictReader((HERE / 'grid.tsv').open(), delimiter='\t'))
    source = json.loads((HERE / 'SOURCE.json').read_text())
    for name, info in source['frozen_inputs'].items():
        assert hashlib.sha256((HERE / name).read_bytes()).hexdigest() == info['sha256'], f'update reviewed SOURCE.json hash: {name}'
    assert rows and list(rows[0]) == ['word', 'spelling', 'clue', 'frequency', 'source']
    words = [row['word'] for row in rows]
    assert words == sorted(set(words)), 'curated.tsv must be sorted and unique'
    excluded = words_in('exclude.txt')
    grid_excluded = words_in('grid-exclude.txt')
    answer_words = [r['word'] for r in answer_rows]
    assert answer_words == sorted(set(answer_words))
    assert set(answer_words).issubset(words)
    common = {line.split('\t')[0] for line in (HERE / 'grid-common.tsv').read_text().splitlines() if line and not line.startswith('#')}
    clues = set()
    for row in rows:
        word, spelling, clue = (row[k] for k in ['word', 'spelling', 'clue'])
        assert 3 <= len(word) <= 7 and all(c in ALPHABET for c in word), word
        assert normalize(spelling) == word, (word, spelling)
        assert word not in excluded or word in common, f'excluded grid word without reviewed override: {word}'
        assert word not in grid_excluded or word in answer_words or word in common, f'excluded grid word: {word}'
        assert 10 <= len(clue) <= 170 and not re.search(r'(?<![а-я])' + word + r'(?![а-я])', normalize(clue)), (word, clue)
        assert clue.casefold() not in clues, f'duplicate clue: {word}: {clue}'
        assert '\t' not in clue and '\n' not in clue
        assert row['source'] == 'original:Daybook contributors' or row['source'].startswith('https://ru.wiktionary.org/wiki/'), word
        clues.add(clue.casefold())
    with gzip.open(HERE / 'guesses.txt.gz', 'rt') as f:
        guesses = f.read().splitlines()
    assert guesses == sorted(set(guesses)), 'guesses must be sorted and unique'
    assert set(words).issubset(guesses), 'every answer must be accepted'
    assert all(3 <= len(w) <= 7 and all(c in ALPHABET for c in w) for w in guesses)
    answers_by_length = {n: [w for w in answer_words if len(w) == n] for n in range(3, 8)}
    grid_by_length = {n: [w for w in words if len(w) == n] for n in range(3, 8)}
    guesses_by_length = {n: [w for w in guesses if len(w) == n] for n in range(3, 8)}
    for n in range(3, 8):
        assert len(answers_by_length[n]) >= MIN_ANSWERS[n], f'answer diversity shrank at {n}'
        assert len(grid_by_length[n]) >= MIN_GRID[n], f'grid diversity shrank at {n}'
        assert len(guesses_by_length[n]) >= MIN_GUESSES[n], f'guess coverage shrank at {n}'
    # Wheel roots pass the real minimum word-count shapes. Expert also has another six-letter word.
    # This is vocabulary feasibility, not a proof that any particular crossword layout/teacher succeeds.
    roots = {}
    signatures = {w: Counter(w) for w in words}
    for n, minimum in [(5, 4), (6, 6), (7, 8)]:
        roots[n] = []
        for root in grid_by_length[n]:
            letters = signatures[root]
            possible = [w for w in words if len(w) <= n and all(k <= letters[c] for c, k in signatures[w].items())]
            if len(possible) >= minimum and (n != 7 or any(len(w) == 6 for w in possible)):
                roots[n].append(root)
        assert len(roots[n]) >= 30, f'wheel root diversity shrank at {n}: {len(roots[n])}'
    lines = ['package com.joebywan.daybook.core.words', '',
             '// GENERATED by tools/words/ru/build.py. Do not edit: sorted order is a content contract.',
             '// Russian Wiktionary contributors / CC BY-SA 4.0; LibreOffice Russian dictionary / permissive.',
             '// Full provenance and licenses: tools/words/ru/ and APK assets/word-lists/ru/.',
             '// Kept separate from every English word list and from the display language.', '',
             'internal object RussianWordData {']
    for n in range(3, 8):
        lines += packed(f'ANSWERS_{n}', answers_by_length[n])
        lines += packed(f'GRID_{n}', grid_by_length[n])
        lines += packed(f'GUESSES_{n}', guesses_by_length[n])
    for n in range(5, 8):
        lines += packed(f'ROOTS_{n}', roots[n])
    lines += packed('CLUES', [r['word'] + '\t' + r['spelling'] + '\t' + r['clue'] for r in rows], '\n')
    lines[-1] = '}'
    stats = {'answers': {str(n): len(answers_by_length[n]) for n in range(3, 8)},
             'grid': {str(n): len(grid_by_length[n]) for n in range(3, 8)},
             'guesses': {str(n): len(guesses_by_length[n]) for n in range(3, 8)},
             'wheel_roots': {str(n): len(roots[n]) for n in range(5, 8)}}
    sources = 'word\tspelling\tsource\n' + ''.join(r['word'] + '\t' + r['spelling'] + '\t' + r['source'] + '\n' for r in rows)
    assert stats == source['counts'], f'update reviewed SOURCE.json counts: {stats}'
    outputs = {OUTPUT: '\n'.join(lines) + '\n', ASSETS / 'CLUE-SOURCES.tsv': sources,
               HERE / 'COUNTS.json': json.dumps(stats, ensure_ascii=False, indent=2) + '\n'}
    for path in [HERE / 'NOTICE.txt', HERE / 'SOURCE.json', *(HERE / 'vendor').iterdir()]:
        outputs[ASSETS / path.name] = path.read_text()
    web_assets = ROOT / 'web/src/wasmJsMain/resources/word-lists/ru'
    for path, content in list(outputs.items()):
        if path.parent == ASSETS:
            outputs[web_assets / path.name] = content
    return outputs, stats


def main() -> None:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check', action='store_true', help='fail when generated outputs differ')
    args = parser.parse_args()
    outputs, stats = generate()
    stale = []
    for path, content in outputs.items():
        if args.check:
            if not path.exists() or path.read_text() != content:
                stale.append(str(path.relative_to(ROOT)))
        else:
            path.parent.mkdir(parents=True, exist_ok=True)
            path.write_text(content)
    if stale:
        sys.exit('Regenerate Russian corpus: python3 tools/words/ru/build.py\n' + '\n'.join(stale))
    print(json.dumps(stats, ensure_ascii=False))


if __name__ == '__main__':
    main()
