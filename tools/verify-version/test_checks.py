"""Small independent checks for failure paths in the verification harness."""
import json
import struct
import sys
import tempfile
import unittest
from pathlib import Path

from artifact import arm64_alignment
from check import test_results
from android import Device, RUSSIAN_PUZZLES, retained_fixture_batch

sys.path.insert(0, str(Path(__file__).resolve().parents[1] / "render"))
from render import registry_ids


def elf(alignment=16384, file_offset=0, address=0):
    data = bytearray(120)
    data[:6] = b"\x7fELF\x02\x01"
    struct.pack_into("<H", data, 18, 183)
    struct.pack_into("<Q", data, 32, 64)
    struct.pack_into("<HH", data, 54, 56, 1)
    struct.pack_into("<I", data, 64, 1)
    struct.pack_into("<QQ", data, 72, file_offset, address)
    struct.pack_into("<Q", data, 112, alignment)
    return data


class ArtifactChecks(unittest.TestCase):
    def test_16k_and_larger_load_segments(self):
        self.assertEqual([16384], arm64_alignment(elf()))
        self.assertEqual([65536], arm64_alignment(elf(65536)))

    def test_4k_and_incongruent_segments_fail(self):
        for data in [elf(4096), elf(16385), elf(address=4096), elf(65536, address=16384)]:
            with self.assertRaises(ValueError):
                arm64_alignment(data)


class RegistryDiscoveryChecks(unittest.TestCase):
    def test_shared_files_delegates_and_hyphenated_ids_keep_registry_order(self):
        registry = "val all: List<PuzzleType> = listOf(RussianWords, Words, RussianCipher,)"
        source = '''
            object Words : PuzzleType { override val id = "words" }
            object RussianCipher : PuzzleType by Cipher { override val id = "cipher-ru" }
            object RussianWords : PuzzleType by Words { override val id = "words-ru" }
        '''
        self.assertEqual(["words-ru", "words", "cipher-ru"], registry_ids(registry, [source]))
        with self.assertRaises(KeyError):
            registry_ids(registry, [source.replace("object RussianCipher", "object Missing")])
        with self.assertRaises(ValueError):
            registry_ids(registry, [source.replace('"words-ru"', '"words"')])


class RuntimeEvidenceChecks(unittest.TestCase):
    def test_retained_batch_covers_all_native_and_english_word_games(self):
        directory = Path(__file__).resolve().parents[2] / "app/src/test/resources/saves"
        batch = retained_fixture_batch(directory)
        ids = {entry["key"].split("|")[0] for entry in batch}
        expected = set(RUSSIAN_PUZZLES) | {puzzle.removesuffix("-ru") for puzzle in RUSSIAN_PUZZLES}
        self.assertEqual(expected, ids)
        self.assertEqual(14, len(batch))
        for entry in batch:
            puzzle, tier, _seed = entry["key"].split("|")
            self.assertEqual("STANDARD", tier)
            self.assertEqual("ru" if puzzle in RUSSIAN_PUZZLES else "en",
                             entry["game"]["state"].get("contentLanguage", "en"))

    def test_missing_native_fixture_cannot_claim_retention(self):
        directory = Path(__file__).resolve().parents[2] / "app/src/test/resources/saves"
        batch = retained_fixture_batch(directory)
        with tempfile.TemporaryDirectory() as temporary:
            path = Path(temporary)
            (path / "fixtures.json").write_text(json.dumps(
                [entry for entry in batch if not entry["key"].startswith("mini-ru|")]))
            with self.assertRaisesRegex(RuntimeError, "mini-ru"):
                retained_fixture_batch(path)

    def test_missing_or_all_skipped_contract_is_not_a_pass(self):
        with tempfile.TemporaryDirectory() as directory:
            path = Path(directory)
            for content in [None, '<testsuite name="ExampleCatalogTest" tests="1" skipped="1"/>']:
                if content:
                    (path / "TEST-example.xml").write_text(content)
                with self.assertRaises(RuntimeError):
                    test_results(path, ["*Catalog*Test"])
            (path / "TEST-example.xml").write_text('<testsuite name="ExampleCatalogTest" tests="2" skipped="1"/>')
            self.assertEqual(2, test_results(path, ["*Catalog*Test"])["tests"])

    def test_system_locale_rejects_arbitrary_shell_content_before_adb(self):
        device = Device("must-not-run", {}, Path("/unused"), 1)
        with self.assertRaises(ValueError):
            device.system_language("ru; unsafe")
        with self.assertRaises(ValueError):
            device.set_system_app_language("ru; unsafe")

    def test_wrong_architecture_or_no_headers_fail(self):
        for data in [b"not ELF", elf()[:64], elf()]:
            if len(data) == 120:
                struct.pack_into("<H", data, 18, 62)  # x86_64 is not an arm64 check
            with self.assertRaises((ValueError, struct.error)):
                arm64_alignment(data)


if __name__ == "__main__":
    unittest.main()
