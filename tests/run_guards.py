"""Guard runner for DedaAI — deliberately minimal (scaffolding brief item 2).

Wired via `.claude/settings.json`:
    PostToolUse -> python tests/run_guards.py --fast
    Stop        -> python tests/run_guards.py

Unlike the heavier per-project runners elsewhere in this monorepo, DedaAI
carries exactly 3 guards and all 3 are cheap enough to run every time — the
`--fast` flag and the bare Stop-hook invocation run the identical set. There
is no structure/config-section/clone-guard layer here: `android/` is Kotlin,
owned and built by its own session, never edited through these Python hooks.

Exit 2 on any guard failure — that is what makes the Stop hook BLOCK. The
.kt size report can never fail (report-only); a failure here can only come
from the old-name gate or the docs nav-chain gate.
"""

from __future__ import annotations

import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent

GUARDS = [
    "tests/test_old_name.py",
    "tests/test_doc_links.py",
    "tests/test_kt_size_report.py",
]

EXIT_BLOCK = 2


def main(argv: list[str]) -> int:
    import pytest

    targets = [str(PROJECT_ROOT / guard) for guard in GUARDS]
    code = pytest.main(["-q", "--no-header", "-p", "no:cacheprovider", *targets])
    if code != 0:
        print("\nGUARD FAILURE — fix the violation above before continuing.",
              file=sys.stderr)
        return EXIT_BLOCK
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
