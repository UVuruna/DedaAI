# DedaAI — Open Issues

Known gaps, read before a hardware test session and before marking any item
below fixed. Each line: what is unresolved and what would unblock it.

<!-- old-name-ok: OPEN-ISSUES.md names the old project path in the resolved
     leftover-checkout note below — see tests/test_old_name.py ALLOWED. -->
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
- **RESOLVED 2026-08-21 — Leftover pre-move checkout is gone.** The old
  location `U:\Coding\Meta RayBan AI` with its checkout of the upstream
  sample this app started from disappeared from disk during the 2026-08-21
  session — not deleted by the session's own tooling (its move script
  found the path already missing); presumed deleted by the owner by hand.
  NOTE: the pre-move git history (the 15 commits from before the monorepo
  move) lived only in that checkout and was not bundled first — it is
  gone with the folder.
- **Android Developer Verification — researched 2026-08-21, no urgent
  deadline for THIS project.** The Sept 30 2026 enforcement covers only 7
  named stores in BR/ID/SG/TH — direct sideloads like ours are explicitly
  out of scope "yet"; the global phase is undated ("2027 and beyond").
  There is a FREE "Limited Distribution" hobbyist tier (no ID, no fee, up
  to 20 devices authorized by QR/link; the developer's name is not shown
  to users), generally available since Aug 2026. Standing plan: register
  the free tier + DedaAI's package name and signing-key SHA-256 at leisure
  before the global phase, then authorize the handful of family devices;
  re-check the program page through 2027 — an unregistered app's UPDATES
  fail once enforcement reaches a device, so this must land before that
  day. What unblocks: the owner's word to register (needs his Google
  account with 2-step verification and a Payments profile).
- **Voice commands (0.1.3) need their sideload probe on the owner's
  phone.** In one guided pass: (1) 0.1.3 installs cleanly with
  CALL_PHONE/READ_CONTACTS/SEND_SMS declared (none are on the hard-block
  list — F-Droid dialer precedent — but this is our first probe); (2) the
  SMS "Allow restricted settings" one-time step — record the exact path
  Samsung shows; (3) call audio actually reaches the glasses (a One UI
  8.5 Qualcomm BT bug is documented on this phone family); (4) dual-SIM:
  without a chosen PhoneAccount the system SIM picker may appear —
  decide if v2 needs the picker-bypass (READ_PHONE_STATE); (5) "take a
  picture" during a glasses-mic session — unproven against the
  camera-mutes-mic regression, so the command stayed out of 0.1.3.
- **Wake-word retraining runs on the new mixed recipe.** The venv's
  editable installs died with the old checkout's deletion (2026-08-21,
  caught by smoke, re-pointed same day). The English-phonetic companion
  configs (*_en.yaml) + merge stage are in; smoke of the mixed pipeline
  gates the full overnight run. Real recordings from the owner (see
  UV/snimanje-uzoraka.md) are the honest measurement set — no model
  replaces the SpeechRecognizer stop-gap before passing it.
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
