# DedaAI

Voice assistant "Deda" for Ray-Ban Meta smart glasses + an Android phone,
backed by Gemini Live. This file is the project's folder map — what each
top-level folder is for, and where its own truth lives.

## Folders

| Folder | What it is | Docs |
|--------|------------|------|
| `android/` | the Android/Kotlin app itself — DAT SDK, Gemini Live client, the wake/standby/tap state machine | owned and edited only inside its own build session |
| `docs/` | GitHub Pages source (`uvuruna.github.io/DedaAI`) — install funnel, the two illustrated guides, the design/decisions history | [DECISIONS.md](docs/DECISIONS.md) · [PLAN.md](docs/PLAN.md) |
| `release/` | build-to-GitHub-release pipeline (`publish.py` / `publish.cmd`) and the two illustrated-guide builders | — |
| `wakeword-training/` | offline training pipeline for the two openWakeWord wake/stop-phrase models | [SETUP-REPORT.md](wakeword-training/SETUP-REPORT.md) |
| `tests/` | this project's 3 guards (old-name, docs nav-chain, `.kt` size report) | [run_guards.py](tests/run_guards.py) |
| `UV/` | the owner's inbox — free-form specs/images, read at session start, never edited | — |

## Where the truth lives

- **What the app must do (the MVP behaviour contract):** [CLAUDE.md](CLAUDE.md).
- **What the app does, grouped and briefly (the functionality register):**
  [FUNCTIONALITIES.md](FUNCTIONALITIES.md).
- **Why it does it that way, dated:** [docs/DECISIONS.md](docs/DECISIONS.md).
- **The design and research history (long-form, not an index):** [docs/PLAN.md](docs/PLAN.md).
- **Known gaps, read before a hardware test:** [OPEN-ISSUES.md](OPEN-ISSUES.md).
- **The Android source itself:** `android/` — this file and the rest of the
  root docs describe it from the outside; they never restate its internals,
  and no session outside `android/`'s own edits that folder.

## Connections

- Uses: nothing outside this project — the app ships with no server of ours
  (root constitution: "we build for OTHERS").
- Used by: the owner and one other Ray-Ban Meta owner (Slovenia), each with
  their own free Gemini key.
