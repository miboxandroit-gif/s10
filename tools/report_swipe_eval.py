#!/usr/bin/env python3
"""Print the Turkish swipe baseline emitted by RealDictionaryTest from JUnit XML."""

from __future__ import annotations

import sys
import xml.etree.ElementTree as ET
from pathlib import Path


def main() -> int:
    root = Path(sys.argv[1]) if len(sys.argv) > 1 else Path("app/build/test-results/testDebugUnitTest")
    if not root.exists():
        print("TR_SWIPE_BASELINE unavailable: no JUnit test-results directory")
        return 0
    found = False
    for path in sorted(root.rglob("TEST-*.xml")):
        try:
            report = ET.parse(path).getroot()
        except (ET.ParseError, OSError) as exc:
            print(f"warning: unable to read {path}: {exc}")
            continue
        for node in report.iter():
            if node.tag not in ("system-out", "system-err") or not node.text:
                continue
            for line in node.text.splitlines():
                if any(
                    marker in line
                    for marker in ("TR_SWIPE_BASELINE", "TR_SWIPE_MISS", "TR_SWIPE_DATASET",
                        "TR_SWIPE_LATENCY", "TR_SWIPE_DETERMINISM", "TR_SWIPE_SEGMENT_BOUND",
                        "TR_SWIPE_TRACE", "TR_SWIPE_TRACE_DETAIL", "TR_SWIPE_VISIT",
                        "TR_PAIR", "TR_COVERAGE", "TR_NEXT_WORD", "TR_MORPHOLOGY")
                ):
                    print(line.strip())
                    found = True
    if not found:
        print("TR_SWIPE_BASELINE unavailable: benchmark did not emit a result (it may have been skipped or failed before execution)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
