"""Every shipped catalog and Russian clue must render using bundled fonts while offline."""
import json
import re
import runpy
import struct
import unittest
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
READ_CMAP = runpy.run_path(str(ROOT / 'tools/subset-font.py'))['read_cmap']
WEB = ROOT / 'web/src/wasmJsMain/composeResources/font'


def characters(value):
    if isinstance(value, str):
        return set(map(ord, value))
    if isinstance(value, dict):
        return set().union(*(characters(v) for v in value.values()))
    if isinstance(value, list):
        return set().union(*(characters(v) for v in value))
    return set()


def cmap(path):
    data = path.read_bytes()
    for i in range(struct.unpack_from('>H', data, 4)[0]):
        tag, _, offset, size = struct.unpack_from('>4sIII', data, 12 + 16 * i)
        if tag == b'cmap':
            return set(READ_CMAP(data[offset:offset + size]))
    raise ValueError(f'No cmap in {path}')


class FontCoverageTest(unittest.TestCase):
    def test_shipped_catalogs_calendars_and_russian_words_have_bundled_glyphs(self):
        needed = set()
        for path in (ROOT / 'translations').rglob('*.json'):
            needed |= characters(json.loads(path.read_text()))
        needed |= set(map(ord, (ROOT / 'app/src/main/java/com/joebywan/daybook/core/i18n/generated/CalendarNames.kt').read_text()))
        for path in (ROOT / 'tools/words/ru').glob('*.tsv'):
            needed |= set(map(ord, path.read_text()))
        symbols = set().union(*(cmap(path) for path in WEB.glob('noto_sans_*.ttf') if 'cyrillic' not in path.name))
        for weight in ('regular', 'medium', 'semibold', 'bold'):
            covered = symbols | cmap(WEB / f'fredoka_{weight}.ttf') | cmap(WEB / f'noto_sans_cyrillic_{weight}.ttf')
            missing = sorted(c for c in needed - covered if not chr(c).isspace())
            self.assertEqual([], [f'U+{c:04X} {chr(c)}' for c in missing], weight)

    def test_service_worker_precaches_every_bundled_font(self):
        worker = (ROOT / 'web/src/wasmJsMain/resources/sw.js').read_text()
        shell = re.search(r'const SHELL = \[(.*?)\];', worker, re.S).group(1)
        cached = set(re.findall(r"'([^']+\.ttf)'", shell))
        package = re.search(r'packageOfResClass = "([^"]+)"', (ROOT / 'web/build.gradle.kts').read_text()).group(1)
        expected = {f'composeResources/{package}/font/{path.name}' for path in WEB.glob('*.ttf')}
        self.assertEqual(expected, cached, 'new or renamed fonts must be available after the first online visit')

    def test_android_and_web_use_identical_localized_fonts(self):
        for path in WEB.glob('noto_sans_cyrillic_*.ttf'):
            self.assertEqual(path.read_bytes(), (ROOT / 'app/src/main/res/font' / path.name).read_bytes(), path.name)


if __name__ == '__main__':
    unittest.main()
