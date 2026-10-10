#!/usr/bin/env python3
"""Offline regression tests for dictionary generation and Turkish corpus normalization."""

import unittest
from pathlib import Path
from tempfile import TemporaryDirectory

import generate_assets as assets


class TurkishCoverageTests(unittest.TestCase):
    def test_aosp_dictionary_uses_turkish_case_folding(self) -> None:
        with TemporaryDirectory() as temp_dir:
            path = Path(temp_dir) / "main_tr.combined"
            path.write_text(
                "word=IĞDIR,f=220,flags=0\n"
                "word=İSTANBUL,f=240,flags=0\n"
                "word=Ankara’da,f=210,flags=0\n",
                encoding="utf-8",
            )
            rows = assets.parse_aosp_combined(path, 1_000_000, "tr")

        words = {word for word, _ in rows}
        self.assertEqual({"ığdır", "istanbul", "ankara'da"}, words)
        self.assertTrue(all(freq > 0 for _, freq in rows))

    def test_morphology_merge_filters_existing_invalid_and_caps_frequency(self) -> None:
        with TemporaryDirectory() as temp_dir:
            path = Path(temp_dir) / "morphology.tsv"
            path.write_text(
                "kitap\t99999999\n"
                "kitaplar\t99999999\n"
                "EVLER\t1000\n"
                "evler\t2000\n"
                "IĞDIR\t5000\n"
                "geçersiz!\t2000\n"
                "çöp kelime\t2000\n",
                encoding="utf-8",
            )
            merged = assets.merge_morphology_forms(
                [("kitap", 1_000_000), ("ev", 50_000)],
                path,
                "tr",
                max_new_forms=10,
            )

        rows = dict(merged)
        self.assertEqual(1, sum(1 for word, _ in merged if word == "kitap"))
        self.assertEqual(1, sum(1 for word, _ in merged if word == "evler"))
        self.assertIn("kitaplar", rows)
        self.assertIn("ığdır", rows)
        self.assertNotIn("geçersiz!", rows)
        self.assertNotIn("çöp kelime", rows)
        self.assertLessEqual(rows["kitaplar"], 2_000)

    def test_morphology_merge_honours_max_new_forms(self) -> None:
        with TemporaryDirectory() as temp_dir:
            path = Path(temp_dir) / "morphology.tsv"
            path.write_text(
                "evler\t100\nevde\t90\nevimiz\t80\n",
                encoding="utf-8",
            )
            merged = assets.merge_morphology_forms([("ev", 50_000)], path, "tr", 2)
        self.assertEqual({"ev", "evler", "evde"}, {word for word, _ in merged})

    def test_turkish_dotted_and_dotless_i_are_lowercased_correctly(self) -> None:
        self.assertEqual("ığdır istanbul", assets.lower_word("tr", "IĞDIR İSTANBUL"))

    def test_curly_apostrophe_normalizes_to_dictionary_apostrophe(self) -> None:
        words = assets.dictionary_tokens("Ankara’da geldim.", "tr", assets.WORD_RE["tr"])
        self.assertEqual(["ankara'da", "geldim"], words)

    def test_heldout_coverage_counts_surface_tokens_and_types(self) -> None:
        sentence_id = next(str(i) for i in range(1000) if assets.is_heldout_sentence(str(i)))
        coverage = assets.VocabularyCoverage()
        selected = assets.observe_heldout_sentence(
            coverage, sentence_id, "Merhaba dünya dünya", {"merhaba"}, assets.WORD_RE["tr"], "tr"
        )
        self.assertTrue(selected)
        self.assertEqual(3, coverage.total_tokens)
        self.assertEqual(1, coverage.known_tokens)
        self.assertEqual(2, coverage.oov_tokens)
        self.assertEqual(2, coverage.total_types)
        self.assertEqual(1, coverage.oov_types)
        self.assertEqual(2, coverage.oov_counts["dünya"])


    def test_coverage_breakdown_separates_apostrophe_and_plain_words(self) -> None:
        coverage = assets.VocabularyCoverage()
        coverage.observe(
            ["ankara'da", "evde", "tom'un", "tom'da", "tom'da"],
            {"ankara'da", "evde"},
        )
        breakdown = assets.coverage_breakdown(coverage)
        self.assertEqual(
            {"tokens": 4, "oov_tokens": 3, "types": 3, "oov_types": 2},
            breakdown["apostrophe"],
        )
        self.assertEqual(
            {"tokens": 1, "oov_tokens": 0, "types": 1, "oov_types": 0},
            breakdown["plain"],
        )
        self.assertEqual(3, breakdown["apostrophe"]["oov_tokens"])
        self.assertEqual(3, coverage.oov_tokens)


    def test_non_heldout_sentence_does_not_enter_coverage(self) -> None:
        sentence_id = next(str(i) for i in range(1000) if not assets.is_heldout_sentence(str(i)))
        coverage = assets.VocabularyCoverage()
        selected = assets.observe_heldout_sentence(
            coverage, sentence_id, "Merhaba dünya", {"merhaba"}, assets.WORD_RE["tr"], "tr"
        )
        self.assertFalse(selected)
        self.assertEqual(0, coverage.total_tokens)
        self.assertEqual(0, coverage.oov_tokens)


if __name__ == "__main__":
    unittest.main()
