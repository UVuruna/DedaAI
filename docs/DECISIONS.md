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
