import json
import tempfile
import unittest
from pathlib import Path

from build import generate, load, validate_references


class CatalogBuildTest(unittest.TestCase):
    def fixture(self, languages=('en', 'ru')):
        directory = tempfile.TemporaryDirectory()
        self.addCleanup(directory.cleanup)
        root = Path(directory.name)
        (root / 'languages.json').write_text(json.dumps([{'tag': tag, 'nativeName': tag} for tag in languages]))
        for tag in languages:
            (root / tag).mkdir()
            (root / tag / 'common.json').write_text(json.dumps({'welcome': 'Hello {name}'}))
        return root

    def test_add_ten_languages_without_runtime_source_changes(self):
        tags = ('en', 'ru', 'de', 'es', 'fr', 'it', 'pt', 'pl', 'ar', 'ja')
        files, count, found = generate(self.fixture(tags))
        self.assertEqual((count, tuple(found)), (1, tags))
        self.assertIn('Language("ja", "ja")', files['Catalogs.kt'])
        self.assertIn('"ar" -> when', files['PluralRules.kt'])
        self.assertIn('"few"', files['PluralRules.kt'])
        self.assertIn('"ja" to CalendarNames(', files['CalendarNames.kt'])
        self.assertIn('"lundi"', files['CalendarNames.kt'])
        self.assertIn('"月曜日"', files['CalendarNames.kt'])

    def test_calendar_metadata_preserves_case_and_standalone_forms(self):
        files, _, _ = generate(self.fixture())
        names = files['CalendarNames.kt']
        self.assertIn('"Monday", "Tuesday", "Wednesday"', names)
        self.assertIn('"M", "T", "W", "T", "F", "S", "S"', names)
        self.assertIn('"Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс"', names)
        self.assertIn('months = listOf("января"', names)
        self.assertIn('standaloneMonths = listOf("январь"', names)

    def test_missing_translations_fail(self):
        root = self.fixture()
        (root / 'ru/common.json').write_text('{}')
        with self.assertRaisesRegex(ValueError, 'missing.*welcome'):
            generate(root)

    def test_renamed_argument_fails(self):
        root = self.fixture()
        (root / 'ru/common.json').write_text('{"welcome":"Привет, {person}"}')
        with self.assertRaisesRegex(ValueError, 'placeholders'):
            generate(root)

    def test_missing_language_plural_categories_fail(self):
        root = self.fixture()
        for tag in ('en', 'ru'):
            (root / tag / 'common.json').write_text(json.dumps({'count': {'one': '{count}', 'other': '{count}'}}))
        with self.assertRaisesRegex(ValueError, 'missing CLDR forms'):
            generate(root)

    def test_source_key_typos_and_retired_inline_translation_fail(self):
        root = self.fixture()
        source = root / 'Example.kt'
        for call in ('text', 'message', 'savedText', 'SavedText'):
            source.write_text(f'val caption = {call}("missing.key")')
            with self.subTest(call=call), self.assertRaisesRegex(ValueError, 'unknown translation key'):
                validate_references([source], {'welcome'})
        source.write_text('val caption = tr("Settings", "Настройки")')
        with self.assertRaisesRegex(ValueError, 'inline bilingual'):
            validate_references([source], {'welcome'})
        source.write_text('val caption = message("welcome", "name" to name)')
        validate_references([source], {'welcome'})

    def test_duplicate_keys_fail_instead_of_silently_overwriting(self):
        root = self.fixture()
        path = root / 'en/common.json'
        path.write_text('{"welcome":"First", "welcome":"Second"}')
        with self.assertRaisesRegex(ValueError, 'duplicate JSON key'):
            load(path)


if __name__ == '__main__':
    unittest.main()
