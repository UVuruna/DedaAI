# Meta Ray-Ban AI Assistant — Working Plan

> Working document. Written before context compaction — everything needed to
> continue the work must live here, not in conversation history.

---

## 1. What we are building

An Android app for **Ray-Ban Meta Gen 1 (RW4006)** + **Samsung S25 Ultra**.
The glasses provide camera and microphone, the phone drives the AI, and the
answer plays back through the glasses' speakers.

Core flow that must work: **look at something → ask "what is this?" → get a
spoken answer.**

End goal: activation by a **custom wake word**, without taking the phone out.

The app must be installable for a second user (the owner's friend in Slovenia,
Samsung, model not yet known) using **his own** API key — with no shared server
and no cost to anyone.

### Constraints that do not change without discussion

- **No payment of any kind.** No subscription, no hosting, no credit card.
- Three languages: **Serbian, English, Slovenian**, switchable in-app.
- No self-hosted server.

---

## 2. Starting point in the code

The repo is already cloned into `VisionClaw/` (source: `Intent-Lab/VisionClaw`,
224 commits).

**We start from commit `675a0bf`** (Aug 6 2026, "Attach the camera frame to
delegated tasks"), **not from HEAD.**

Reason: HEAD (`d77cb79`, Aug 11) pivoted to an architecture with a LiveKit room,
a Python agent and a TypeScript gateway — the app no longer holds a Gemini key
and instead requires either someone else's hosted backend or a self-hosted one.
That needs Fly.io (no free tier since 2026, card required) and the Anthropic API
(no free tier). Impossible for free.

`675a0bf` is the last commit where the app itself opens a WebSocket to Gemini
Live. The only credential required is a free Gemini API key.

Both versions use the same `mwdat 0.4.0`, `compileSdk 35`, `minSdk 31`,
AGP 8.6.0, Kotlin 2.1.20 — no difference in toolchain requirements.

### Code layout at `675a0bf`

Everything lives under
`VisionClaw/samples/CameraAccessAndroid/app/src/main/java/com/meta/wearable/dat/externalsampleapps/cameraaccess/`
(referred to below as `$P`).

| Path | Role |
|---|---|
| `gemini/GeminiConfig.kt` | model id, URL, constants (frame interval, JPEG quality) |
| `gemini/GeminiLiveService.kt` | WebSocket client: `sendSetupMessage()`, `sendAudio()`, `sendVideoFrame()` |
| `gemini/GeminiSessionViewModel.kt` | session lifecycle, `sendVideoFrameIfThrottled()` (line 194) |
| `gemini/AudioManager.kt` | `AudioRecord` 16 kHz input, `AudioTrack` 24 kHz output |
| `openclaw/*` | the `execute` tool and OpenClaw bridge — **we remove this** |
| `settings/SettingsManager.kt` | SharedPreferences, `DEFAULT_SYSTEM_PROMPT` (line 59) |
| `stream/StreamViewModel.kt` | frames from glasses and phone (calls the throttle at 114 and 241) |
| `ui/ControlsRow.kt` | buttons: Stop / Capture / **AI** / Live |
| `wearables/*` | DAT SDK registration and glasses session |
| `webrtc/*` | POV sharing to a browser — left untouched |

---

## 3. Facts established by research

Do not re-research these. If reality contradicts any of them, measure and
correct this document.

### Gemini free tier
- **No card means no charge.** The free tier requires no billing account; when
  the quota runs out the API returns 429, not an invoice.
- `gemini-2.5-flash`: ~10 requests/min, ~250/day.
- Live API session length: **audio+video ~2 minutes**, audio-only ~15 minutes
  per connection. *This number comes from Firebase/Vertex documentation — it is
  not confirmed to apply identically to the Developer API free tier. Measure.*
- Model used in the code: `gemini-2.5-flash-native-audio-preview-12-2025`.

### Other services (for the variant we are NOT building)
- A Claude Max subscription **does not include** API access. Max 20x carries a
  $200/month Agent SDK credit, but only for the Agent SDK path, not raw API.
- LiveKit Build: free 5,000 WebRTC min + 1,000 agent min per month.
- Fly.io: **no free tier since 2026**, card required.

### Meta DAT SDK — what third-party apps get
- YES: 12 MP camera, microphones (5-mic array) over **HFP, 8 kHz mono**, speakers
- YES: standard media events — pause / resume / stop
- NO: custom gestures (temple tap, swipe)
- NO: Meta AI integration, HUD display, Neural Band
- NO: access to the low-power chip that listens for "Hey Meta"

**Consequence for the wake word:** Meta's own wake word is cheap because it runs
on a dedicated always-on chip inside the glasses, and the phrase never leaves
the glasses as data — the glasses start their own session. Third-party apps
cannot access that chip and cannot intercept the event. Our detection therefore
requires an open SCO link plus processing on the phone, which is fundamentally
more expensive. This is why a user-facing on/off switch is mandatory.

### Behaviour of the app as it stands
- **No wake word** — grepping the whole project returns zero hits.
- The session is started by an on-screen button (`ControlsRow.kt:53`).
- Speech detection runs server-side at Google: `silenceDurationMs 500`,
  `START_OF_ACTIVITY_INTERRUPTS` (the user can interrupt mid-sentence).
- **No request classification** — a frame is sent every second for as long as
  the session is alive, regardless of whether the question is visual.
- The only tool is `execute`, routed to an OpenClaw gateway we do not have.
  Without it the model promises an action and then apologises.
- The system prompt states plainly: no memory, no search, no actions.

---

## 4. Phases

### Phase 0 — prerequisites (owner)

Gradle cannot even sync without these.

1. `gh auth refresh -s read:packages`
   The current token has `gist, read:org, repo, workflow`. The DAT SDK ships via
   GitHub Packages, which requires `read:packages` even for public repos.
2. A Gemini API key from https://aistudio.google.com/apikey (free, no card).

---

### Phase 1 — base app

Goal: the first working "what am I looking at?" in **phone camera mode**.

Phone first, deliberately — so that "the model does not understand Serbian" can
be told apart from "the glasses mic is 8 kHz and therefore weak".

#### 1a. Branch and configuration
```
git checkout -b rayban-ai 675a0bf
```
- `samples/CameraAccessAndroid/local.properties` → `github_token=<gh auth token>`
- copy `$P/Secrets.kt.example` → `$P/Secrets.kt`, insert the Gemini key
- verify both are gitignored

#### 1b. "Frame on question" mode
**Problem:** `sendVideoFrameIfThrottled()` sends a frame every second while the
session is active, regardless of whether the user asked anything — 60 images per
minute of silence. Evidence that this floods the context: the setup message had
to enable `contextWindowCompression` with an 80,000-token sliding window.

**Fix:** a third mode alongside the existing `videoStreamingEnabled` switch, and
make it the **default**:

| Mode | When a frame is sent |
|---|---|
| `STREAM` | 1/s (existing behaviour, keep as an option) |
| `ON_QUESTION` | 1–2 frames on start-of-speech detection — **default** |
| `OFF` | never |

Start-of-speech detection runs **locally** from input signal level in
`AudioManager` — it does not wait for Google. The frame must be in context
before the model starts answering, and Google answers 500 ms after speech ends;
therefore send on **speech start**, not on speech end.

Effect: 60 frames/min → 1–2 per question.

#### 1c. `AiProvider` interface
Extract a thin abstraction so the Gemini backend can be swapped without touching
the rest of the app. Do not build a second implementation now — just put the
seam in the right place.

#### 1d. Trilingual switch
`GeminiConfig.systemInstruction` already reads from `SettingsManager`, so the
prompt is runtime-configurable rather than a constant. Build on that.

- Settings: choose **Serbian / English / Slovenian**
- each language carries its own system prompt and voice language setting
- default English (fewest surprises on the first test), then switch and compare

#### 1e. Remove `execute`
- drop the tool declaration from `sendSetupMessage()`
- take `openclaw/` out of the execution path
- rewrite the system prompt: a pure voice assistant with vision, making no
  promises about messages, lists, reminders or web search

Without this the model says "sure, sending that message", the call fails, and it
then apologises.

#### 1f. First build and test
```
cd "samples/CameraAccessAndroid" && ./gradlew assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```
Test: "Start on Phone" → AI button → point the phone at an object → "what is
this?"

**Phase exit criterion:** the answer plays through the speaker and correctly
describes what the camera is pointed at.

---

### Phase 2 — glasses

1. Meta AI app → Settings → App Info → tap the version number 5 times → enable
   Developer Mode
2. In our app: "Start Streaming", then the AI button
3. Measure: **actual session length** (does `ON_QUESTION` make the session count
   as audio-only and raise the limit from ~2 to ~15 min), Serbian recognition
   quality over the 8 kHz mic, and stability of simultaneous video + microphone
   over Bluetooth SCO (a known problem in the DAT community)

**Risk:** DAT Developer Mode is not confirmed working on Gen 1 RW4006. If it
fails, the app still works in phone mode and this becomes a separate problem to
solve.

---

### Phase 3 — wake word (owner's priority)

**Owner's specification (2026-08-18 evening) — the state machine to build:**

```
OFF ──(phone button | double-tap | )──▶ STANDBY  ── says welcome clip ("Deda te sluša")
STANDBY: wake-word detector runs on the glasses mic (SCO); no Gemini session, no cost.
STANDBY ──("Hej Deda")──▶ TALKING  — Gemini Live session opens, camera frame on question
TALKING ──("Ćao Deda")──▶ ?  — owner said "Ćao Deda" = switch listening OFF (farewell clip)
STANDBY/TALKING ──(phone button | double-tap)──▶ OFF — says farewell clip ("Deda više ne sluša")
```
Clarified with the owner (2026-08-18, late): TALKING is a live Gemini session —
several questions in a row, no need to repeat "Halo Deda". It ends either by
"Ćao Deda" (→ STANDBY, farewell clip) or automatically after N seconds of
silence (N adjustable in Settings, default ~15 s). Double-tap / phone button =
real OFF ("Deda više ne sluša"). Wake phrases (final, owner 2026-08-18): **"Hej Deda"** (start) / **"Ćao Deda"** (stop) in
ALL languages (one model each, both words are international); only the
welcome/farewell clips are per language:
  sr: "Deda te sluša" / "Deda više ne sluša"
  en: "Deda is listening" / "Deda is not listening anymore"
  sl: "Deda posluša" / "Deda ne posluša več"

Building blocks:
1. Wake phrases: TWO custom openWakeWord models — "Hej Deda" and "Ćao Deda"
   (train in Colab; 8 kHz headset audio upsampled — measure).
2. Welcome / farewell: pre-recorded clips (generated once, shipped in the APK)
   rather than Android TTS — Serbian TTS voices are not reliably installed.
3. Double-tap: MediaSession that stays active while STANDBY/TALKING and catches
   KEYCODE_MEDIA_NEXT (double-tap on the glasses = "next track"). Spike first:
   verify the key actually arrives on Gen 1 when nothing is playing.
4. Glasses mic route (already built, setting "Microphone: Glasses") is a
   prerequisite: the wake word must be heard from the glasses, not the phone.


Cannot start before the session works — there would be nothing to trigger.

**Engine: openWakeWord** (TensorFlow Lite, open source, Android port exists).
Not Porcupine: their free tier is a single device with a watermarked model, and
custom keywords on personal accounts are restricted — which breaks on the second
device, and we need Slovenia to work too.

**This does not go through the DAT SDK.** The glasses present themselves to the
phone as an ordinary Bluetooth headset; Android reads that microphone through
the standard path (SCO). DAT is only needed afterwards, for the camera.

**Two power modes, both to be implemented (owner's explicit request):**

| Mode | Behaviour |
|---|---|
| **Phone microphone** | the phone listens on its own mic; the glasses are untouched until the word is recognised. Cheaper for the glasses, hears worse from a pocket. |
| **Standby switch** | an in-app toggle; while on, it listens continuously on the glasses mic and drains the battery faster. While off, it does not listen at all. The user decides. |

**Useful side effect:** with a wake word the session only starts when called and
ends after the conversation — it never reaches the duration limit at all. The
Phase 2 concern dissolves on its own.

**Steps:**
1. Integrate openWakeWord (TFLite) + reading the Bluetooth SCO microphone
2. Pick a name and train the model (Colab, ~1h)
   Naming criteria: 2–3 syllables, clear consonants, phonetics close to English
   because the models are trained on it. **Name chosen by the owner: "Deda"**
   (2026-08-18). Two syllables, clear stops — phonetically fine. Risk: "deda"
   is an everyday Serbian word (grandpa), so false triggers in normal
   conversation are likely — measure the false-accept rate in step 3 and be
   ready to require a short pause or a second word ("Deda, ...") if needed.
   The system prompt already tells the model its name is Deda.
3. Measure accuracy on 8 kHz audio (models expect 16 kHz → upsampling → unknown
   accuracy loss)
4. Implement both power modes
5. **Fallback if the wake word proves unreliable:** capture media play/pause
   from the glasses button via `MediaButtonReceiver`. Unverified on Gen 1;
   downside — it pauses music if any is playing.

**Owner's idea: use the touchpad HOLD gesture to toggle Deda standby.**
Researched 2026-08-18 — not feasible:
- HOLD (tap-and-hold) is handled inside the glasses / Meta AI app. In the Meta
  AI app it can be reassigned (Device settings → Gestures → Touchpad) to
  Spotify, a playlist or a contact call, but not disabled and not routed to
  the phone as a key event. It never reaches Android as `KEYCODE_VOICE_ASSIST`
  or a long-press `HEADSETHOOK`, and DAT exposes no gesture events at all
  (FAQ: "custom gesture controls like taps and swipes aren't offered").
- What DOES reach the phone: single tap = play/pause, double tap = next
  (standard AVRCP media keys). Those an app can catch with an active
  `MediaSession` — but only while it is the active media session, and it
  steals them from music playback. That is the existing step-5 fallback.
  **Owner's decision (2026-08-18): double-tap becomes the Deda standby
  toggle**; losing "next track" while our session is active is accepted.
  Caveat: during a phone call the glasses' taps go to the telephony stack
  (answer / hang up), not to us — the toggle only works outside calls.
  Verify on Gen 1 whether double-tap even reaches the phone when no media
  is playing (KEYCODE_MEDIA_NEXT needs an active MediaSession to land).
- Conclusion: the standby on/off switch stays in the app UI; the wake word
  "Deda" is the hands-free path. HOLD → Spotify is a user-side setting with
  no effect on us.

**SDK version note:** `mwdat-core` used here is 0.4.0; upstream is at 0.9.0
(2026-08). Stay on 0.4.0 to match `675a0bf`; upgrade only if the build or the
Gen 1 device forces it.

---

### Phase 4 — optional, only once Phases 1–3 hold

**A `look()` tool** — real request classification, performed by the model
itself. Instead of pushing images at it, declare a tool the model calls when it
needs vision. "What is this?" → call → frame is sent. "Which country is Belgrade
in?" → no image at all.

Unverified: whether Gemini Live allows a tool response to trigger sending a
frame. Cost: ~0.5–1 s extra latency on visual questions.

---

## 5. Open questions

| # | Question | How it gets resolved |
|---|---|---|
| 1 | Real free-tier Live session duration limit | measurement, Phase 2 |
| 2 | Does `ON_QUESTION` make the session audio-only | measurement, Phase 2 |
| 3 | Does DAT Developer Mode work on Gen 1 RW4006 | **Yes** — verified 2026-08-18, Meta AI app 285.0.0.18.161 shows "DAT SDK version 0.9.0.26.0", glasses stream reaches the app |
| 4 | Wake word accuracy at 8 kHz | Phase 3 |
| 5 | The assistant's name | **Deda** (chosen 2026-08-18) |
| 6 | Serbian and Slovenian pronunciation quality | Serbian verified 2026-08-18 on phone (owner: "sve radi"); Slovenian pending |

---

## 6. Commands

```bash
# where everything lives
cd "u:/Coding/Meta RayBan AI/VisionClaw"

# branch
git checkout -b rayban-ai 675a0bf

# build
cd samples/CameraAccessAndroid && ./gradlew assembleDebug

# install
adb install -r app/build/outputs/apk/debug/app-debug.apk

# logs
adb logcat -s CameraAccess:V GeminiLiveService:V
```

Tooling already present on the machine: Android Studio + JBR JDK 21,
SDK `android-35`, build-tools up to 36, adb 35.0.2, git 2.53, gh 2.86, node 22,
python 3.13. `java` and `gradle` are not on PATH — use the Gradle wrapper.


### Phase 3 — touchpad tap: WHAT IS AND ISN'T POSSIBLE (settled 2026-08-18, workflow deda-tap-routing + device tests)

FACT (AVRCP OperationIds measured on Gen 1 + S25 Ultra):
- single tap = 68/70 (PLAY/PAUSE, gated by the playback state the glasses observe)
- double tap = 75 (FORWARD/NEXT, unconditional)
- triple tap = 76 (BACKWARD/PREVIOUS)
All are one AVRCP PASS_THROUGH to a SINGLE "addressed player" (the media-button
session). Android routes per-session, never per-gesture.

CONSEQUENCE: "double tap = Deda ALWAYS (even during music) AND single tap keeps
controlling music" is NOT achievable. To get double tap we must BE the sole
addressed player; while music actually plays, the music app is that session and
a silent "heartbeat" does NOT reliably steal it (measured: double-tap still
skipped the Spotify track). Stealing it would also route single tap to us.

DESIGN (implemented, commit 21569c5):
- While music plays: we do NOT interfere. single = pause, double = next,
  triple = previous — all native to the music app, reliable. Deda is NOT
  toggled by tap in this state.
- While nothing plays: Deda claims the buttons once (1 s silence, then sits
  PAUSED). double tap = toggle Deda; single = resume last music app; triple =
  previous. (Deda can only be the addressed player right after it emitted audio,
  so the FIRST turn-on may need the in-app toggle or the wake word.)
- PRIMARY control = wake word "Hej Deda" / "Ćao Deda" (independent of the media
  button channel) + in-app / notification toggle. Tap is a best-effort bonus.

OPEN constraint for the wake-word phase: on a single-link BT headset, opening
SCO (glasses mic) collapses/mutes A2DP (music). So "music on glasses + listen
for Hej Deda on glasses mic" is a conflict; phone-mic mode avoids it. Measure.
→ RESOLVED by design (owner, 2026-08-18, after /compact): **standby and music
never mix.** Standby means the user is actively using the glasses; music is
paused on entry (audio focus) and resumes on exit. The SCO/A2DP conflict never
arises, and standby listening runs on the GLASSES mic as originally specified.


### Phase 3b/3c — standby state machine: BUILT 2026-08-18 (stop-gap wake engine)

What runs now (compiles; device test pending):
- `deda/DedaController` — OFF/STANDBY/TALKING. Entering STANDBY takes transient
  audio focus (music pauses); losing focus to another app = user chose music →
  Deda turns itself off. STANDBY holds the glasses-mic route (`util/HeadsetRoute`,
  ref-counted, shared with the session's capture).
- Wake phrases: `deda/WakeWordListener` — **stop-gap engine**: the phone's
  Google speech recogniser in a restart loop (sr/sl/en, Cyrillic + diacritics
  normalised in `WakePhrases`). The start phrase opens the Gemini session; the
  stop phrase is read from Gemini's own input transcription during TALKING —
  no second microphone. The trained openWakeWord models remain the durable
  plan; this exists so the whole machine is testable today.
- Timers: TALKING closes after N s of silence (default 15, Settings) and is
  force-closed after M min (default 15, Settings).
- Two activation modes (`settings/DedaActivationMode`, owner decision): GLASSES_TAP
  (double tap toggles Deda while nothing plays; single tap while Deda is on =
  exit standby + music resumes) or NOTIFICATION (Deda never touches the media
  buttons; the switch is the notification button). Mode changes apply
  immediately (service re-reads on every start).
- `gemini/GeminiSession` — the session extracted from the ViewModel into a
  singleton so the background service can open/close it; the ViewModel is a
  thin wrapper. Notification shows three states (asleep/listening/talking).

Still open for Phase 3 completion: train the two openWakeWord models (accuracy
at 8 kHz SCO audio = risk #4), pre-recorded greeting clips instead of TTS, and
the on-device test of: recogniser actually recording via SCO, and taps while
SCO is up still arriving as AVRCP (not HFP call controls).

