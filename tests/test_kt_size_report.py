"""Report: android/ Kotlin files over ~1000 lines. Report-only — ALWAYS
exits 0 (scaffolding brief item 2c).

`android/` is owned and built only inside its own session; this project does
not enforce a structure law over code it never edits. This just gives that
other session, and anyone reading the guard output here, a cheap standing
size reading without pretending to own or block on it.

Run standalone or via tests/run_guards.py.
"""

from __future__ import annotations

import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parent.parent
ANDROID = PROJECT / "android"

LINE_WALL = 1000

# Generated/build directories that might exist on disk despite being
# gitignored (e.g. after a `gradlew compileDebugKotlin` run) — skipped so
# the report stays "seconds-cheap" and never reports generated stubs.
SKIP_DIR_NAMES = {".git", ".gradle", ".kotlin", "build", "keystore"}


def find_oversized() -> list[tuple[str, int]]:
    if not ANDROID.is_dir():
        return []
    found: list[tuple[str, int]] = []
    for path in ANDROID.rglob("*.kt"):
        parts = path.relative_to(ANDROID).parts[:-1]
        if any(part in SKIP_DIR_NAMES for part in parts):
            continue
        try:
            text = path.read_text(encoding="utf-8", errors="replace")
        except OSError:
            continue
        lines = text.count("\n") + 1
        if lines > LINE_WALL:
            found.append((path.relative_to(PROJECT).as_posix(), lines))
    return sorted(found, key=lambda row: -row[1])


def main() -> int:
    print(f"=== .kt SIZE REPORT (report-only, never blocks; wall {LINE_WALL}) ===")
    oversized = find_oversized()
    if not oversized:
        print(f"  none — every android/ Kotlin file is under ~{LINE_WALL} lines")
    else:
        for rel, lines in oversized:
            print(f"  {lines:>6} lines  {rel}")
    return 0


def test_gate():
    assert main() == 0


if __name__ == "__main__":
    sys.exit(main())
