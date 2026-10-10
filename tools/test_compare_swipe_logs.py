#!/usr/bin/env python3
import tempfile
import unittest
from pathlib import Path

import compare_swipe_logs as c

BASE = (
    "TR_SWIPE_BASELINE dataset=120 condition=clean-centerline group=all top1=100/120 top1_pct=83.3 top3=110/120 top3_pct=91.7\n"
    "TR_SWIPE_BASELINE_GROUP dataset=20 condition=clean-centerline group=core top1=19/20 top1_pct=95.0 top3=20/20 top3_pct=100.0\n"
    "TR_SWIPE_MISS condition=clean-centerline group=core target=x rank=2 top5=a,b\n"
    "TR_SWIPE_BASELINE dataset=20 synthetic-centerline targets top1=15/20 (75.0%) top3=18/20\n"
)
EXP = BASE.replace("top1_pct=83.3", "top1_pct=85.0").replace("top3_pct=91.7", "top3_pct=90.0")


class CompareTests(unittest.TestCase):
    def test_parse_only_structured_baseline_lines(self):
        self.assertEqual(2, len(c.parse(BASE)))

    def test_deltas_in_percentage_points(self):
        rows = c.compare(c.parse(BASE), c.parse(EXP))
        row = next(r for r in rows if r[1] == "all")
        self.assertAlmostEqual(1.7, row[4], places=1)
        self.assertAlmostEqual(-1.7, row[7], places=1)

    def run_compare(self, base, experiment, *extra):
        with tempfile.TemporaryDirectory() as temp:
            a, b = Path(temp) / "base.log", Path(temp) / "experiment.log"
            a.write_text(base, encoding="utf-8")
            b.write_text(experiment, encoding="utf-8")
            return c.main([str(a), str(b), *extra])

    def test_max_drop_detects_top3_regression(self):
        self.assertEqual(1, self.run_compare(BASE, EXP, "--max-drop", "1.0"))

    def test_max_drop_tolerates_small_change(self):
        self.assertEqual(0, self.run_compare(BASE, EXP, "--max-drop", "2.0"))

    def test_no_rows_returns_two(self):
        self.assertEqual(2, self.run_compare("nothing", "nothing"))


if __name__ == "__main__":
    unittest.main()
