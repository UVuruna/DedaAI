# DedaAI — Functionalities

The main functionalities of the app, grouped by kinship, each described
briefly: what it does and how it works. This is the first document of its
kind in the monorepo (owner's order, 2026-08-21) — every project gets one.
Statuses: no mark = shipped and working · **(planned)** = designed but not
in the app yet. The dated reasoning behind each design lives in
[docs/DECISIONS.md](docs/DECISIONS.md).

## 1. Conversation

- **Ask about what you see.** The core loop: say the wake phrase, ask a
  question, hear the answer through the glasses. On wake the app takes
  exactly ONE full-resolution photo through the glasses' camera — before
  any audio starts — and attaches it as the visual context when the first
  question begins. The camera is never open during the conversation itself
  (an open camera stream mutes the glasses' audio link — hardware-proven).
- **Live voice dialogue.** Audio streams both ways over a Gemini Live
  WebSocket session; the user's own free Gemini API key powers it — the
  app ships no key and runs no server of ours.
- **Wake and stop phrases.** "Hej Deda" starts a conversation, "Ćao Deda" ends it — <!-- lang-ok: the product's fixed phrases -->
  fixed in every language, never translated. The stop is
  recognized by the model itself (a declared `end_conversation` tool call),
  with a transcript match as fallback.
- **Conversation ends, all of them normal.** Stop phrase · N seconds of
  silence (Settings, default 15 s) · Gemini's own free-tier session limit
  closing the socket — that last one is treated as a normal goodbye, never
  an error. There is deliberately NO forced session-length cap.

## 2. Glasses controls

- **Touchpad taps.** While music plays, all taps stay 100% native to the
  music app — Deda never interferes. When nothing is playing (tap mode):
  double tap toggles Deda standby; single tap while Deda is on turns Deda
  off and the music it paused resumes by itself.
- **Notification.** A persistent notification mirrors Deda's state
  (asleep / listening / talking) and carries two buttons: on/off toggle
  and full quit.
- **Home screen.** The same on/off switch and quit button, plus tips and
  a settings gear.
- **Spoken feedback.** Entering/leaving standby is announced through the
  glasses in the chosen language (TTS, with tone signals as fallback).
- **Quit is real.** Quit stops the foreground service, releases the media
  session, the microphone, the Bluetooth SCO route and the audio focus,
  and removes the notification — the glasses fall back to being a plain
  Bluetooth headset, as if the app were not installed.

## 3. Music coexistence

- **Standby pauses music, leaving resumes it.** Entering standby takes
  transient audio focus (music apps pause); abandoning it on exit lets
  the same music resume on its own. Deda never force-stops or restarts
  another app's playback and holds no notification access (a declared
  notification listener makes Play Protect hard-block sideloaded installs).

## 4. Languages

- **Serbian, Slovenian, English** — switchable at runtime in Settings,
  independent of the phone's system language. Per-language: the assistant's
  reply language (system prompt), standby/farewell announcements,
  notification texts, and in-app strings. The wake/stop phrases stay the
  same words in every language.

## 5. Setup and sharing

- **Own key, free.** Each user creates a free Gemini API key and pastes it
  in Settings; an in-app illustrated guide (per language) walks through it.
- **Install funnel.** GitHub Pages landing with one big download button and
  a QR code; an in-app "share Deda" action sends the link; the install
  guide is bundled in the app too.
- **Signed releases.** Sideload-friendly: properly release-signed APK on
  GitHub Releases at a stable URL — no "disable security" instructions,
  ever.

## 6. Updates

- **In-app update.** On app open (at most once per 6 h) — and on a button
  in Settings — the app compares its version with the latest GitHub
  release (`deda-version.json` on the stable release URL). A newer version
  shows a one-tap banner: the APK downloads inside the app and Android's
  installer opens over it. No background polling; the check runs only when
  the user opens the app or asks for it.

## 7. Voice commands (0.1.3)

- **Off by default** — a Settings toggle turns them on and asks for their
  permissions (call, contacts, SMS). While off, the tools are not even
  declared to the model.
- **Call someone.** Spoken name → address-book lookup; several matches
  become a spoken numbered list to pick from. The call is placed directly,
  its audio rides the glasses like any headset call, and Deda steps aside
  (turns off, releasing the audio route) once the call starts.
- **Send an SMS.** Dictate → Deda reads the message back → only a clear
  spoken yes sends it. No confirmation, no send — enforced in the model's
  standing instructions.
- **Alarms, timers, navigation.** Set hands-free through the phone's own
  clock app; "navigate to..." starts turn-by-turn guidance that speaks
  through the glasses, with Deda stepping aside after the handoff.
- **Honesty rule.** Every tool reports a status; a failed or unpermitted
  action is said plainly, never pretended. Reading INCOMING messages is
  deliberately absent: the permissions it needs sit on Google's sideload
  hard-block list (see [OPEN-ISSUES.md](OPEN-ISSUES.md)).

## 8. Settings

- Gemini API key (only the user's own text is ever shown) · assistant
  language · camera mode per conversation · microphone route (glasses /
  phone) · activation mode (glasses tap / notification button) · silence
  timeout · voice commands on/off · reset. Internal machinery (system
  prompts, persona style texts) is deliberately NOT exposed to users.

## 9. Planned — designed, not in the app yet

- **Trained wake-word models.** Two openWakeWord models exist but their
  recall was weak; a new mixed-voice training recipe (Serbian voice +
  ~900 English voices pronouncing phonetic respellings) plus the owner's
  real recordings is in progress. The shipping stop-gap is Android's
  `SpeechRecognizer` (see [OPEN-ISSUES.md](OPEN-ISSUES.md)).
- **Two personality styles.** The same factual answers delivered in two
  different characters (an analyst; a second style under review) — prompt
  addenda only, never different facts. The "Hej Baba" wake phrase waits
  on the wake-word models. <!-- lang-ok: the fixed wake phrase -->
- **Journal (working name).** A passive background collector: periodic
  one-shot photos plus the conversations themselves, assembled into a day
  diary with spoken recall. Proposal under owner review.
- **"Take a picture" command** — pending one hardware probe (a one-shot
  capture during a glasses-mic conversation has never been proven safe
  against the camera-mutes-mic regression).
