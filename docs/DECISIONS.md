# DedaAI — Decisions

Every owner decree this project is built on, one line each, dated. Mined
from [PLAN.md](PLAN.md) and the `.claude/sessions/2f764c3a-*.md` ledger.
`CLAUDE.md` states only the laws a session must obey; the reasoning and the
report each one came from live here — read the entry before arguing with it.

- **2026-08-18/19** — One photo per question, no continuous camera stream
  during a conversation. An earlier design kept a video stream open for the
  whole conversation; on hardware it muted the glasses' microphone the
  moment it ran alongside the Gemini audio session. The fix, and the
  standing rule: one real photo captured BEFORE audio starts, camera and
  conversation audio never open at the same time.
- **2026-08-19** — No forced session cap. An owner-imposed max-minutes timer
  existed for one build and was removed by his own order the same day.
  Sessions end only by the stop phrase, a silence timeout (Settings,
  default 15 s), or Gemini's own free-tier limit closing the socket — the
  last of those is a normal end, not an error.
- **2026-08-19** — No security-disabling install instructions. The fix
  offered for Chrome/Play Protect distrusting a debug-signed APK was
  "have the user turn off Samsung Auto Blocker" — the owner refused it on
  the spot. The real fix is a proper release signature (env-var-driven
  keystore) plus `minifyEnabled`/`shrinkResources`, never asking a user to
  lower their phone's guard.
- **2026-08-19** — No embedded API key. One build shipped with the owner's
  own key as a fallback default; he ordered it removed — every user
  creates and pastes their own free Gemini key, the app never carries a
  working key inside it.
- **2026-08-18** — Wake and stop phrases are fixed in every language: "Hej
  Deda" starts a conversation, "Ćao Deda" ends it, unchanged in sr/sl/en
  (both are short and phonetically close to English, which is what the
  detector models are trained on). Only the standby/farewell announcements
  are translated per language.
- **2026-08-18** — Standby and music never mix. Entering standby takes
  transient audio focus and pauses music; the wake-word microphone always
  runs on the glasses' route while standby is on. An earlier idea to also
  listen through music (phone-mic mode over an open glasses link) was
  explicitly rejected — standby means the user is actively using the
  glasses, not idly wearing them while music plays.
- **2026-08-18** — Settled tap design (after a dedicated research workflow
  proved per-gesture routing impossible on stock Android's single
  addressed-media-session model): while music plays, single/double/triple
  tap stay 100% native to the music app. Deda claims the media buttons only
  when nothing is playing; there, a double tap toggles Deda and a single
  tap exits Deda back to music.
- **2026-08-19** — MVP excludes downloadable extra languages and a second,
  "Baba" (grandma) female voice. sr/sl/en with the one "Deda" voice is the
  whole language scope for v1; both are explicitly out of scope, not
  merely deferred silently.
- **2026-08-16, standing** — Builds only on the owner's explicit word, in
  that session (root constitution Law 4). This project's own build-word
  gate had to learn his actual phrasing twice: "pravi apk" (2026-08-19,
  "apk" wasn't in the recognized word list) and "bill" (2026-08-19, his
  phone's voice keyboard turned "build" into "bill").
- **2026-08-20** — Resource law: nothing is taken for good, nothing runs as
  an unstoppable background daemon. Decreed after the glasses-mic forensic
  session: the 2026-08-19 build (pre-rename package) wedged in its TALKING
  state and held the camera stream + Bluetooth SCO + the mic foreground
  service for 46+ minutes with no exit path, surviving swipe-close — the
  owner's phone needed a night of ADB surgery before the glasses would even
  pair again. (The glasses' dead microphone itself proved to be a physical
  fault — a blocked acoustic inlet, measured and documented on 2026-08-20 —
  but the wedge was real, ours, and is exactly what this law forbids.)
  Standing rule for every future session: each resource is acquired by the
  state transition that needs it and released by the transition that leaves
  it; no acquisition without its release in the same state machine; every
  state has a working exit; quit tears everything down. A held resource with
  no live user intent behind it is a bug, even when nothing visibly breaks.
- **2026-08-20** — No notification listener in the app, ever. Google Play
  Protect hard-blocks a sideloaded APK that declares one (“sensitive
  data”, no Install-anyway offered) — the owner hit exactly that dialog.
  The listener only powered handing single taps back to the music app;
  music paused by Deda resumes via the released transient audio focus
  instead. Trade accepted: a tap cannot START music that was not playing.
