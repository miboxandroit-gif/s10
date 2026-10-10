#!/usr/bin/env python3
"""Compare Kinetica Turkish swipe benchmark logs across two builds.

Percentages are percentage points, not relative percentages.
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

PAIR = re.compile(r"([A-Za-z0-9_]+)=(\S+)")
VALID_MARKERS = ("TR_SWIPE_BASELINE ", "TR_SWIPE_BASELINE_GROUP ")


def parse(text: str) -> dict[tuple[str, str], tuple[float, float]]:
    out = {}
    for line in text.splitlines():
        if not line.startswith(VALID_MARKERS):
            continue
        fields = dict(PAIR.findall(line))
        if not {"condition", "group", "top1_pct", "top3_pct"} <= fields.keys():
            continue
        try:
            out[(fields["condition"], fields["group"])] = (
                float(fields["top1_pct"]), float(fields["top3_pct"])
            )
        except ValueError:
            continue
    return out


def compare(base: dict, experiment: dict) -> list[tuple]:
    rows = []
    for key in sorted(base.keys() & experiment.keys()):
        b1, b3 = base[key]
        e1, e3 = experiment[key]
        rows.append((key[0], key[1], b1, e1, e1 - b1, b3, e3, e3 - b3))
    return rows


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("base", help="baseline CI log or extracted report")
    parser.add_argument("experiment", help="experiment CI log or extracted report")
    parser.add_argument(
        "--max-drop", type=float, default=None,
        help="exit 1 if overall Top-1 or Top-3 falls by more than this many percentage points",
    )
    args = parser.parse_args(argv)
    try:
        base_text = Path(args.base).read_text(encoding="utf-8-sig")
        experiment_text = Path(args.experiment).read_text(encoding="utf-8-sig")
    except OSError as exc:
        print(f"Unable to read benchmark input: {exc}", file=sys.stderr)
        return 2
    base, experiment = parse(base_text), parse(experiment_text)
    rows = compare(base, experiment)
    if not rows:
        print("No comparable TR_SWIPE_BASELINE rows found.", file=sys.stderr)
        return 2
    missing_base = sorted(experiment.keys() - base.keys())
    missing_experiment = sorted(base.keys() - experiment.keys())
    print(f"{'condition':30} {'group':20} {'Top-1 base->exp (Δpp)':26} {'Top-3 base->exp (Δpp)':26}")
    failed = False
    for cond, group, b1, e1, d1, b3, e3, d3 in rows:
        print(f"{cond:30} {group:20} {b1:5.1f}->{e1:5.1f} ({d1:+5.1f})   {b3:5.1f}->{e3:5.1f} ({d3:+5.1f})")
        if args.max_drop is not None and group == "all" and (d1 < -args.max_drop or d3 < -args.max_drop):
            failed = True
    if missing_base:
        print(f"NOTE: {len(missing_base)} row(s) only exist in experiment: {missing_base}", file=sys.stderr)
    if missing_experiment:
        print(f"NOTE: {len(missing_experiment)} row(s) only exist in baseline: {missing_experiment}", file=sys.stderr)
    if failed:
        print(f"REGRESSION: overall Top-1/Top-3 dropped by more than {args.max_drop} percentage points", file=sys.stderr)
        return 1
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
