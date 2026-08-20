# DedaAI — Open Issues

Known gaps, read before a hardware test session and before marking any item
below fixed. Each line: what is unresolved and what would unblock it.

<!-- old-name-ok: OPEN-ISSUES.md must name the leftover folder and its old
     path so the owner can find and delete it — see tests/test_old_name.py
     ALLOWED. -->
- **Wake-word models trained but NOT integrated.** Both openWakeWord models
  exist (`wakeword-training/models/`, exported to ONNX): `hej_deda` —
  accuracy 0.78, recall 0.56; `cao_deda` — accuracy 0.86, recall 0.73. Both
  recall numbers are weak (a real miss rate against the actual wake phrase),
  so the app currently runs on a stop-gap instead: Android's own
  `SpeechRecognizer` in a restart loop. Wiring the trained models in
  (onnxruntime-android) needs an accuracy check on real SCO/glasses-mic
  audio first — do not swap the stop-gap out blind.
- **Slovenian TTS quality unverified.** Serbian pronunciation was confirmed
  acceptable on-device (2026-08-18, phone test); Slovenian has not had the
  same on-device listening pass yet.
- **Real free-tier Gemini Live session-length limit unmeasured.** The
  documented figures (roughly 2 min for audio+video, ~15 min audio-only)
  come from Firebase/Vertex docs, not confirmed against the Google AI Studio
  Developer API free tier this app actually uses.
- **Leftover pre-move checkout at the OLD project path.** A `VisionClaw`
  folder — the upstream sample this app started from — is still on disk at
  the project's old location, `U:\Coding\Meta RayBan AI\` (where the project
  lived before its move into this repo as `android/`). It was locked and
  could not be deleted at the time of writing; delete it once it is free.
- **Wake listening: which microphone — glasses, phone, or both? (owner will
  decide; sketch only, decreed 2026-08-20 during the dead-glasses-mic
  session).** The glasses' mic died physically and the product was deaf with
  it; a phone-mic path would have kept Deda alive. Implementation sketch,
  bound by the Resource law (CLAUDE.md — listen only on demand, release on
  exit):
  - Wake listener exists ONLY in standby, started by an explicit user action
    (notification button, app, tap mode) and torn down on quit — never a
    permanent daemon.
  - Option A: a Settings choice — wake mic = Glasses / Phone / Both.
  - Option B: no setting — always listen on both in standby, two detectors in
    parallel (glasses SCO route + phone built-in mic), first to recognize the
    phrase wins and its route carries the conversation.
  - Either way, playback stays on the glasses' speakers; only the mic route
    varies. A dead glasses mic then degrades the product to "phone-mic Deda"
    instead of killing it.
  - Key risk to verify FIRST on hardware: Android may not deliver two live
    capture streams (SCO + built-in) at once — concurrent-capture rules and
    `setPreferredDevice` behaviour decide whether Option B is even possible;
    if not, Option A with a per-session fallback (glasses route fails →
    offer phone mic) gives the same rescue.
  - Battery cost of a held-open SCO link during standby is the other number
    to measure before choosing.
  - Relation to the 2026-08-18 "standby and music never mix" decree: that
    decree rejected listening THROUGH music; it does not decide which mic
    listens in normal standby, so this choice stays open without touching it.
- **Guided hardware test of the MVP still pending.** The wake/standby/tap
  state machine and every item above need one owner-present, step-by-step
  test on the actual glasses + phone. Standing process rule since a
  regression cost the owner a stuck phone (2026-08-19): nothing that touches
  audio, Bluetooth or the camera reaches his phone without an immediate
  2-minute guided test right after install, and a rollback release always
  stays available on GitHub Releases.
