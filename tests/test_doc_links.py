"""Gate: every relative link in README.md resolves to a file that exists.

Deliberately narrow (scaffolding brief item 2b) — not the full recursive
__about__/__flow navigation-chain walk other projects run in this monorepo,
just the one link surface a reader actually starts from.

Run standalone or via tests/run_guards.py.
"""

from __future__ import annotations

import re
import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parent.parent
README = PROJECT / "README.md"

# Matches both [text](target) and ![alt](target); group 1 is the target.
LINK_RE = re.compile(r"!?\[[^\]]*\]\(([^)]+)\)")


def _skip(target: str) -> bool:
    target = target.strip()
    return not target or target.startswith(("http://", "https://",
                                              "mailto:", "#"))


def find_broken() -> list[str]:
    if not README.is_file():
        return [f"{README.name} does not exist at the project root"]
    text = README.read_text(encoding="utf-8")
    broken: list[str] = []
    for number, line in enumerate(text.splitlines(), 1):
        for match in LINK_RE.finditer(line):
            target = match.group(1).strip()
            if _skip(target):
                continue
            # Drop a trailing "title" and any #fragment before resolving.
            target = target.split(" ", 1)[0].split("#", 1)[0]
            if not target:
                continue
            if not (README.parent / target).resolve().exists():
                broken.append(f"README.md:{number}: {match.group(0)} -> "
                               f"{target} (missing)")
    return broken


def main() -> int:
    print("=== DOCS NAV-CHAIN GATE ===")
    broken = find_broken()
    if broken:
        print(f"  FAIL  {len(broken)} link(s) in README.md do not resolve:")
        for hit in broken:
            print(f"        {hit}")
        print("\nDOCS NAV-CHAIN GATE FAILED — fix the link or the missing "
              "file.")
        return 1
    print("  PASS  every relative link in README.md resolves to a real file")
    return 0


def test_gate():
    assert main() == 0


if __name__ == "__main__":
    sys.exit(main())
