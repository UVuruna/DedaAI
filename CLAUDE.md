# DedaAI

Voice assistant **"Deda"** for Ray-Ban Meta smart glasses, paired to an Android
phone and backed by Gemini Live: look at something, ask, get a spoken answer
through the glasses. Android/Kotlin GUI+FEATURE project. Public repo
`github.com/UVuruna/DedaAI`; Pages `uvuruna.github.io/DedaAI` (source `/docs`).
Main (⭐) project, owner decree 2026-08-19.

This file inherits the monorepo constitution (`../../CLAUDE.md`) and may only
ADD or TIGHTEN its rules — never loosen them. Keep it under 6,000 bytes:
everything longer lives in `docs/`.

profiles: android-phone
installable: yes

## Stack

- Language: Kotlin, native Android (`android/`, Gradle) — DAT SDK (Meta
  Wearables Device Access Toolkit) for the glasses' camera/mic/speakers.
- AI: Gemini Live (WebSocket), one free API key per user — no server of ours.
- Wake word: openWakeWord (TFLite/ONNX) models trained but NOT YET wired in;
  the shipping stop-gap is Android's own `SpeechRecognizer` — see
  [OPEN-ISSUES.md](OPEN-ISSUES.md).
- Languages: sr / sl / en, switchable in Settings.

## MVP behaviour contract

- **Wake → one photo, then audio.** The wake phrase (fixed, every language)
  triggers exactly ONE real photo (`StreamSession.capturePhoto()`, full
  resolution) BEFORE the Gemini Live audio session opens; the photo is sent
  once the user asks their first question. Camera and conversation audio
  never run at once — an open camera stream during a live conversation once
  muted the glasses' mic, which is the regression this whole design avoids.
<!-- lang-ok: fixed stop phrase, quoted verbatim, never translated -->
- **Session ends only by:** "Ćao Deda" (matched by a Gemini function-tool
  call, transcript match kept as fallback) · N seconds of silence (Settings,
  default 15) · Gemini's own free-tier limit closing the socket, treated as a
  normal end (farewell + standby), never an error. **No forced
  session-length cap** — an earlier owner-imposed max-minutes timer was
  removed by his own order.
- **Taps.** While music plays, single/double/triple tap are 100% native to
  the music app — Deda never touches them. Deda is activated by the wake
  word, a notification button, or (in tap-activation mode) a double tap while
  nothing is playing; activating pauses music via audio focus. Once active: a
  double tap turns Deda off, a single tap turns Deda off AND resumes music.
- **Quit is real.** A notification action / HomeScreen button stops the
  foreground service, releases the media-button session, closes the SCO
  route, cancels the notification — the glasses behave like a plain
  Bluetooth headset again, as if the app were not installed.
<!-- lang-ok: fixed wake/stop phrases, quoted verbatim, never translated -->
- **Languages.** sr/sl/en. Wake ("Hej Deda") and stop ("Ćao Deda") are NEVER
  translated. Only the standby/farewell announcements are per-language.
  Downloadable extra languages and a second ("Baba") voice are explicitly
  NOT MVP.

Full dated rationale for each of these: [docs/DECISIONS.md](docs/DECISIONS.md).

## Resource law (owner decree 2026-08-20)

Nothing is taken for good. Mic, camera, SCO route, audio focus, wake listener,
foreground service: acquired at the state transition that needs them, released
by the transition that leaves it — never held "just in case", never by a
daemon that outlives the user's intent. Every state has a working exit, and
quit must always work (see "Quit is real"). Born of the 2026-08-19 wedge:
one stuck state held camera + SCO + mic for 46+ minutes and survived
swipe-close. Rationale: docs/DECISIONS.md (2026-08-20).

## How to build

```
cd android
gradlew assembleRelease
```

Release signing reads four env vars — `DEDA_KEYSTORE`, `DEDA_KEYSTORE_PASS`,
`DEDA_KEY_ALIAS`, `DEDA_KEY_PASS` — and falls back to the debug keystore when
they are unset, so a local build still works without them. **Never automatic**
(root Law 4): only on the owner's explicit word, in that session.

**We build for OTHERS, never for this machine** (owner decree 2026-08-16):
the shipped app leans on nothing of this monorepo — no `rules/`, no hooks, no
owner-machine paths. It carries its own Gradle wrapper and reads secrets only
from env vars or a gitignored `Secrets.kt` the user fills in themselves.

## How to test

```
python tests/run_guards.py --fast   the 3 guards below (PostToolUse hook)
python tests/run_guards.py          same 3 guards, unconditionally (Stop hook)
```

## Guards in this project

Deliberately minimal — this project does not carry the structure/config-section
guards other projects do; `android/` is Kotlin, not Python, and is owned by a
build session, not by these hooks.

1. **Old-name guard** — no tracked file outside `android/` and outside
   `docs/PLAN.md` / `docs/DECISIONS.md` (history, allowed to say what the
   product used to be called) may say the two retired names.
2. **Docs nav-chain** — every relative link in `README.md` resolves to a file
   that exists.
3. **.kt size report** — lists `android/` Kotlin files over ~1000 lines.
   Report-only, always exits 0; the other agent that owns `android/` decides
   what to do with it.

## Entry points

| Path | Role |
|------|------|
| `android/` | the Android app (owned/edited only under its own session) |
| `docs/` | GitHub Pages source: install funnel, key/install guides, QR |
| `release/` | `publish.py`/`publish.cmd` (GitHub release), the guide builders |
| `wakeword-training/` | offline training for the two openWakeWord models |
| `tests/run_guards.py` | the 3 guards above |

## Docs

- `README.md` — what it is, requirements, install, the name story
- `docs/DECISIONS.md` — dated owner decrees, read before arguing with one
- `docs/PLAN.md` — the design + research history (long-form, not an index)
- `OPEN-ISSUES.md` — known gaps, read before a hardware test session
- `___folder.md` — the folder map and where each truth lives
