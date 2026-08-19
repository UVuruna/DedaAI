"""Gate: no tracked file outside android/ still calls this product by an
old name.

DedaAI was subtree-merged from a fork of Intent-Lab/VisionClaw, and the
project itself lived at a different local path before the move into this
repo — under "Meta RayBan AI". `android/` (owned and edited only inside its
own build session) and the two history docs that describe what the project
WAS called are exempt; everywhere else, a survivor is a regression a user or
the owner could actually read.

Uses `git ls-files` so it only ever looks at TRACKED files and gets
.gitignore (venvs, build output, downloaded datasets) excluded for free;
falls back to a manual walk with a conservative skip-list if git itself is
unreachable — "cannot tell" always means scan more, never less.

Run standalone or via tests/run_guards.py.
"""

from __future__ import annotations

import os
import re
import subprocess
import sys
from pathlib import Path

PROJECT = Path(__file__).resolve().parent.parent

# android/ is owned and edited only inside its own session (scaffolding
# brief zones); the two history docs are explicitly allowed to say what the
# project used to be called.
SKIP_TOP_DIRS = {"android"}
SKIP_FILES = {"docs/PLAN.md", "docs/DECISIONS.md"}

# Only used if `git ls-files` itself is unreachable (see `_tracked_files`).
FALLBACK_SKIP_DIR_NAMES = {
    ".git", "__pycache__", ".pytest_cache", "venv", ".venv", "node_modules",
    ".gradle", ".kotlin", "build", "keystore", ".claude", "UV", "_local",
    "openWakeWord", "piper-sample-generator", "smoke-test", "shots", "data",
}

OLD_NAMES = (
    (re.compile(r"Vision\s*Claw", re.IGNORECASE), "VisionClaw"),
    (re.compile(r"Meta\s*Ray[\s-]?Ban\s*AI", re.IGNORECASE), "Meta RayBan AI"),
)

# A line is allowed only when its file is listed AND the line contains one
# of its fragments — a blanket file exemption would hide a real regression
# anywhere else in that same file. Each entry says why it is permanently
# correct; none of these are product copy, all are pre-existing.
ALLOWED: dict[str, tuple[str, ...]] = {
    # Must name the leftover folder and its old path so the owner can find
    # and delete it (OPEN-ISSUES.md, scaffolding brief item 6).
    "OPEN-ISSUES.md": ("Leftover pre-move checkout", r"U:\Coding\Meta RayBan AI"),
    # Explains AND ignores the actual leftover fork folder by its real name.
    ".gitignore": ("VisionClaw fork this app was born from", "/VisionClaw/"),
    # A dated record of where the training environment was actually set up
    # from — an operational report, not product copy.
    "wakeword-training/SETUP-REPORT.md": (r"u:\Coding\Meta RayBan AI",),
    # Pre-existing: APK_SRC still joins the pre-subtree-merge sibling path.
    # Outside this scaffolding brief's scope (item 7 covers only qr.png) —
    # flagged for the coordinator in the report instead of fixed here.
    "release/publish.py": ("'VisionClaw'",),
    # Pre-existing: still assumes the pre-merge working directory. Same note
    # as publish.py above — flagged, not fixed, by this brief.
    "wakeword-training/run_training.cmd": (r"u:\Coding\Meta RayBan AI",),
    "wakeword-training/hej_deda.yaml": (r"u:\Coding\Meta RayBan AI",),
    "wakeword-training/cao_deda.yaml": (r"u:\Coding\Meta RayBan AI",),
}


def _allowed(rel: str, line: str) -> bool:
    return any(fragment in line for fragment in ALLOWED.get(rel, ()))


def _tracked_files() -> list[Path] | None:
    """Every git-tracked file under PROJECT, honouring .gitignore for free.
    None when git itself is unreachable — the caller falls back to a walk."""
    try:
        result = subprocess.run(
            ["git", "ls-files"], cwd=PROJECT, capture_output=True,
            text=True, timeout=30)
    except (OSError, subprocess.SubprocessError):
        return None
    if result.returncode != 0:
        return None
    return [PROJECT / line for line in result.stdout.splitlines() if line]


def _walked_files() -> list[Path]:
    found = []
    for current, directories, files in os.walk(PROJECT):
        directories[:] = [d for d in directories
                           if d not in FALLBACK_SKIP_DIR_NAMES
                           and not d.startswith("output_")]
        found.extend(Path(current) / name for name in files)
    return found


def find_survivors() -> list[str]:
    files = _tracked_files()
    if files is None:
        files = _walked_files()
    survivors: list[str] = []
    for path in files:
        try:
            rel = path.resolve().relative_to(PROJECT).as_posix()
        except ValueError:
            continue
        if rel.split("/", 1)[0] in SKIP_TOP_DIRS or rel in SKIP_FILES:
            continue
        try:
            text = path.read_text(encoding="utf-8")
        except (OSError, UnicodeDecodeError):
            continue                     # binary or unreadable: not shipped text
        for number, line in enumerate(text.splitlines(), 1):
            for pattern, label in OLD_NAMES:
                if pattern.search(line) and not _allowed(rel, line):
                    survivors.append(
                        f"{rel}:{number}: ({label}) {line.strip()[:120]}")
    return survivors


def main() -> int:
    print("=== OLD NAME GATE ===")
    survivors = find_survivors()
    if survivors:
        print(f"  FAIL  {len(survivors)} line(s) still name an old identity:")
        for hit in survivors:
            print(f"        {hit}")
        print("\nOLD NAME GATE FAILED — fix the line, or add it to ALLOWED "
              "with the reason it is permanently correct. Never widen "
              "ALLOWED to a whole file.")
        return 1
    print("  PASS  no tracked file outside android/ (and the two history "
          "docs) names an old identity")
    return 0


def test_gate():
    assert main() == 0


if __name__ == "__main__":
    sys.exit(main())
