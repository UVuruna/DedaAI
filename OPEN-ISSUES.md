# DedaAI — Open Issues

Known gaps, read before a hardware test session and before marking any item
below fixed. Each line: what is unresolved and what would unblock it.

<!-- old-name-ok: OPEN-ISSUES.md names the old project path in the resolved
     leftover-checkout note below — see tests/test_old_name.py ALLOWED. -->
- **Wake-word models retrained and measured 2026-08-22 — usable on clean
  speech, not yet proven on real glasses audio.** Two numbers per model, and
  they must never be quoted for each other. CLEAN speech through the
  streaming pipeline (how the phone actually listens, peak score per clip,
  n=200): `hej_deda` recall **0.935** at threshold 0.5 (0.925 at 0.7, 0.910
  at 0.9) with the hand-written near-miss phrases ("hej deko", "ej deda")
  firing 2.5 % / 2.0 % / 1.5 %; `cao_deda` recall **0.975** at 0.5 (0.955 at
  0.7) with near-misses firing 0.5 % / 0.0 %. The trainer's own validation
  set is noise- and reverb-augmented single windows — a stress test — and
  reads much lower: hej 0.645, cao 0.760. The earlier "the model misses half
  the wake phrases" reading came from that stress metric alone and was wrong
  about clean speech; the old model reached 0.810 clean, the retrained one
  0.935. Recommended starting threshold: **0.7 for hej_deda, 0.5 for
  cao_deda** — re-decide once real recordings exist. Integration
  (onnxruntime-android) still waits on a measurement over real SCO/glasses
  audio; until then the app runs Android's own `SpeechRecognizer` in a
  restart loop.
- **The recall/false-accept trade was the root cause, and it is fixed in the
  configs.** `target_false_positives_per_hour: 0.2` with
  `max_negative_weight: 1500` bought silence with deafness: the old hej model
  scored 0.521 on the stress set and 0.810 clean. At 1.0 FP/hr, negative
  weight 300 and layer_size 96 — same clips, only the trade changed — the
  same model reads 0.645 stress / 0.935 clean, and the share of augmented
  clips it scores near zero fell from 45 % to 23 %. The measurement sits in
  a comment beside the parameters; do not tighten them blind.
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
- **Background activity starts: alarm/timer and navigation are unproven
  with the screen off.** Calls now go through `TelecomManager` and are not
  affected, but `AlarmClock` and `google.navigation` have no non-activity
  API, and Android 10+ refuses a background activity start silently. The
  app has no exemption (no SYSTEM_ALERT_WINDOW, no full-screen intent — a
  foreground service is NOT one). What unblocks: the hardware test tells us
  whether they work with the phone locked; if they do not, the choice is
  the owner's — a one-time "Display over other apps" grant, or dropping
  those two commands to "we cannot do that with the screen off".
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
  gates the full overnight run. The 2026-08-22 run merged 7,000 English
  positives into `hej_deda` at full scale (the fix holds outside smoke) and
  produced a model in 1 h 38 min. **The driver now measures what it built**
  (`run_training.py --evaluate`): openWakeWord announces its own accuracy
  through `logging.info`, which never reaches the driver's log, so the whole
  point of the first overnight run was lost — a run must always end in a
  number. Real recordings from the owner (see UV/snimanje-uzoraka.md) are
  still the honest measurement set — no model replaces the SpeechRecognizer
  stop-gap before passing it.
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
