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
- **Guided hardware test of the MVP still pending.** The wake/standby/tap
  state machine and every item above need one owner-present, step-by-step
  test on the actual glasses + phone. Standing process rule since a
  regression cost the owner a stuck phone (2026-08-19): nothing that touches
  audio, Bluetooth or the camera reaches his phone without an immediate
  2-minute guided test right after install, and a rollback release always
  stays available on GitHub Releases.
