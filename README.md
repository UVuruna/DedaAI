# DedaAI

A voice assistant for Ray-Ban Meta smart glasses: look at something, say "Hej
Deda", ask your question, and get a spoken answer back through the glasses —
powered by Gemini Live, with your own free API key. Android app, sr/sl/en.

## Why "Deda"

<!-- lang-ok-begin: the two fixed wake/stop phrases, quoted verbatim, never translated -->
"Deda" is Serbian/Slovenian for **grandpa** — the assistant everyone in the
family already has a name for. You call him over by saying "Hej Deda"; he
steps back and stops listening when you say "Ćao Deda". No app icon to hunt
for, no screen to look at — just the two phrases, worn on your face.
<!-- lang-ok-end -->

## Table of Contents

- [What it does](#what)
- [Requirements](#requirements)
- [Install](#install)
- [Your own Gemini key](#key)
- [Languages](#languages)
- [Privacy](#privacy)
- [Build from source](#build)
- [Status](#status)

<a id="what"></a>
## What it does

Look at something, ask a question out loud, get a spoken answer through the
glasses' speakers — no phone in hand, no screen. Each question sends **one**
high-resolution photo (never a live video stream) taken the moment you start
talking, so the glasses' microphone is never sharing the Bluetooth link with
a running camera feed. Say the wake phrase to start a conversation; ask
several questions in a row without repeating it. Say the stop phrase, go
quiet for a while, or just let Gemini's own session limit close it — either
way Deda goes back to listening for the wake phrase only.

<a id="requirements"></a>
## Requirements

- **Ray-Ban Meta glasses**, Gen 1 (verified) — the camera, microphones and
  speakers come from the glasses; the phone does everything else.
- **An Android phone**, with **Developer Mode enabled in the Meta AI app**:
  Meta AI app → Settings → App Info → tap the version number 5 times.
- Your own free Gemini API key (below) — no payment, no card, ever.

<a id="install"></a>
## Install

Open this on the phone (or scan the QR):

### 👉 **https://uvuruna.github.io/DedaAI/**

![Install QR code](docs/qr.png)

The page walks through downloading and installing the APK step by step,
including the "Play Protect: install anyway" / Samsung Auto Blocker prompts
Android shows for an app that isn't from a store.

<a id="key"></a>
## Your own Gemini key

Deda needs a personal Google Gemini API key to talk — free, no credit card,
about two minutes to create. The in-app Guide button, and the
[illustrated guide on the install site](https://uvuruna.github.io/DedaAI/vodic.html),
walk through it screen by screen in Serbian, Slovenian and English. The key
is pasted once, in Settings, and stays there.

<a id="languages"></a>
## Languages

Serbian, Slovenian, English — switchable any time in Settings. The wake and
stop phrases stay the same in every language; only Deda's own replies and
announcements change.

<a id="privacy"></a>
## Privacy

Your Gemini key lives only on your phone. There is no DedaAI server —
questions and answers go straight from your phone to Google's Gemini API and
back, over your own key and your own free quota. Nothing passes through any
server of ours, because none exists.

<a id="build"></a>
## Build from source

```
cd android
gradlew assembleRelease
```

Release signing reads four environment variables and falls back to the debug
keystore when they are unset, so a local build still works without them:

| Variable | What it holds |
|---|---|
| `DEDA_KEYSTORE` | path to the release keystore |
| `DEDA_KEYSTORE_PASS` | keystore password |
| `DEDA_KEY_ALIAS` | signing key alias |
| `DEDA_KEY_PASS` | signing key password |

<a id="status"></a>
## Status

**MVP.** Wake word, one-photo-per-question vision, multi-turn Gemini Live
conversation, and the tap/notification standby switch all work end to end in
testing. The trained wake-word models are not wired in yet — a stop-gap
recognizer stands in for them — and a full guided hardware test is still
pending. Full list, and what would unblock each item: [OPEN-ISSUES.md](OPEN-ISSUES.md).

## More

- [docs/DECISIONS.md](docs/DECISIONS.md) — every owner decree behind this
  design, dated
- [docs/PLAN.md](docs/PLAN.md) — the design and research history
- [CLAUDE.md](CLAUDE.md) — the MVP behaviour contract and stack, for coding
  sessions
- [___folder.md](___folder.md) — the project's folder map
