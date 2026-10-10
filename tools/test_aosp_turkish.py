#!/usr/bin/env python3
"""Offline parser tests for the AOSP Turkish wordlist reader."""
import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

import generate_assets as assets

MAX_COUNT = 1_000_000


def parse(text: str):
    with TemporaryDirectory() as temp_dir:
        path = Path(temp_dir) / "main_tr.combined"
        path.write_text(text, encoding="utf-8")
        return assets.parse_aosp_combined(path, MAX_COUNT, "tr")


class AospTurkishParsingTests(unittest.TestCase):
    def test_capital_dotted_and_dotless_i(self):
        rows = parse(
            "word=ŞANLIURFA,f=200,flags=0\n"
            "word=ÖĞRENCİ,f=210,flags=0\n"
            "word=IŞIK,f=220,flags=0\n"
            "word=ÜMİT,f=230,flags=0\n"
        )
        self.assertEqual({"şanlıurfa", "öğrenci", "ışık", "ümit"}, {w for w, _ in rows})

    def test_higher_frequency_byte_means_higher_approximate_count(self):
        rows = dict(parse("word=sık,f=240,flags=0\nword=nadir,f=210,flags=0\n"))
        self.assertGreater(rows["sık"], rows["nadir"])

    def test_zero_frequency_still_gets_positive_count(self):
        rows = dict(parse("word=nadirkelime,f=0,flags=0\n"))
        self.assertGreaterEqual(rows["nadirkelime"], 1)

    def test_garbage_lines_are_ignored(self):
        rows = parse("not a word\n\n   \nword=geçerli,f=100,flags=0\n")
        self.assertEqual(["geçerli"], [w for w, _ in rows])

    def test_curly_apostrophe_is_normalized(self):
        rows = parse("word=Türkiye’de,f=200,flags=0\n")
        self.assertIn("türkiye'de", {w for w, _ in rows})

    def test_turkish_case_variants_never_create_dotted_i_word(self):
        rows = parse("word=ISIK,f=200,flags=0\nword=ısık,f=100,flags=0\n")
        words = {w for w, _ in rows}
        self.assertIn("ısık", words)
        self.assertNotIn("isik", words)


if __name__ == "__main__":
    unittest.main()
