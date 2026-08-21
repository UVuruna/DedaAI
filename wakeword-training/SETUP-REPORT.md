# Wake-Word Training Environment — Setup Report

Prepared 2026-08-19. Goal: train two custom openWakeWord models for the Serbian
wake phrases "Hej Deda" and "Ćao Deda" <!-- lang-ok: the wake phrases themselves -->
on this Windows 11 PC.

> **Storage note (2026-08-21, owner's order):** the heavy, gitignored parts
> of this folder — `data/`, `venv/`, `output_*/`, `openWakeWord/`,
> `piper-sample-generator/` (~24 GB) — physically live on `V:\DedaAI\
> wakeword-training\` now; what sits here are directory junctions to them,
> so every command below keeps working unchanged. The negative datasets and
> the venv are phrase-independent and stay reusable for the planned
> "Hej Baba"/"Ćao Baba" models. <!-- lang-ok: the planned wake phrases -->

**Verdict: feasible, natively on Windows, with real Serbian TTS.** The environment
is installed and smoke-tested end-to-end short of the actual training run. The
only hard blocker is the final `.tflite` export (needs Python <= 3.10); training
to `.onnx` has no blockers.

---

## Hardware / tooling found

| Item | Status |
|---|---|
| GPU | NVIDIA GeForce GTX 1650, 4 GB VRAM, driver 591.86, CUDA 13.1 |
| Python versions | 3.12.7, 3.13.2, 3.14.6 (no 3.9-3.11 installed) |
| Chosen Python | **3.12.7** (`py -3.12`) — newest with full wheel coverage for this stack |
| torch in venv | 2.13.0 **+cpu** — CUDA build deliberately skipped (the cu13x wheel alone is ~2.5 GB, over the download budget for this setup pass). See "GPU" below. |

## What is installed (all verified working)

- `venv\` — Python 3.12.7 virtual environment (1.5 GB on disk). Activate:
  `venv\Scripts\activate` (or call `venv\Scripts\python.exe` directly).
- `openWakeWord\` — clone of dscripka/openWakeWord (v0.6.0), installed editable.
  The four feature-extractor models (`embedding_model.onnx/.tflite`,
  `melspectrogram.onnx/.tflite`, ~5 MB total) are already downloaded into
  `openWakeWord\openwakeword\resources\models\`.
- `piper-sample-generator\` — clone of **rhasspy**/piper-sample-generator v3.2.0
  (the fork the official training notebook uses; the dscripka fork is the obsolete
  v1), installed editable. Key packages: piper-tts 1.3.0 (ships Windows wheels with
  embedded espeak-ng — this is why the notebook's "Linux only" warning is obsolete),
  torch 2.13.0+cpu, onnxruntime 1.29.0, audiomentations 0.33.0, speechbrain 1.1.0,
  datasets 5.0.1, scipy 1.16.3 (pinned, see quirks), webrtcvad-wheels 2.0.14.
- `piper-sample-generator\voices\sr_RS-serbski_institut-medium.onnx` (+ .json) —
  the **Serbian Piper voice** (77 MB, 2 speakers, espeak voice `sr`, 22.05 kHz).
- `piper-sample-generator\generate_samples.py` — **compatibility shim written by
  this setup**: openWakeWord's `train.py` imports `generate_samples` from the repo
  root (a v1-era API with a built-in default model); v3 moved it into the package
  and made `model` a required argument. The shim restores the old entry point and
  binds the Serbian voice (override with the `PIPER_SAMPLE_MODEL` env var).
- `hej_deda.yaml`, `cao_deda.yaml` — ready-to-run training configs (with
  hand-written Serbian adversarial negatives; curate them as you like).
- `smoke-test\` — generated Serbian samples proving the TTS path works
  (listen to them: `hej_deda\0.wav` etc.). Delete freely.
- `data\` — empty; the training-run downloads (below) go here.

## Serbian answer (the key question)

**Real Serbian synthesis works natively.** piper-sample-generator v3 accepts any
standard Piper `.onnx` voice, and `sr_RS-serbski_institut-medium` exists in
rhasspy/piper-voices with genuine Serbian espeak phonemization (`sr`). Smoke test
produced correct-length clips for both phrases on this machine. No
English-phonetic approximation ("hey dedda") is *required*.

Caveat: the Serbian voice has only **2 speakers** (vs 904 for the English
LibriTTS generator), so voice diversity is low. Mitigations already in the
configs: `augmentation_rounds: 2`. Optional extra: also generate English-phonetic
positives ("hey dedda" / "chow dedda") with the multi-speaker English generator
(`en_US-libritts_r-medium.pt`, 156 MB from the piper-sample-generator v2.0.0
release; set `PIPER_SAMPLE_MODEL` to its path for a second `--generate_clips`
pass into the same output dirs) and mix both sample sets. Recommended if the
model over-fits to the two synthetic voices.

Second caveat: openWakeWord's automatic adversarial-phrase generation is
English-phoneme based (CMU dict; Serbian words go through an English
DeepPhonemizer fallback that auto-downloads a ~60 MB model on first run). It will
not crash, but its output is English-ish — that is why the configs carry
hand-written Serbian `custom_negative_phrases`.

## Exact commands to run training

Everything from `u:\Coding\Meta RayBan AI\wakeword-training\` with the venv Python.

**Step 0 — one-time data downloads (~4 GB + 16 GB, deferred by design):** into `data\`:

1. MIT room impulse responses (~100 MB): HF dataset `davidscripka/MIT_environmental_impulse_responses` -> `data\mit_rirs\` as 16 kHz WAVs.
2. Background noise (~2.4 GB): `bal_train09.tar` from HF `agkphysics/AudioSet` -> extract, resample to 16 kHz -> `data\audioset_16k\`.
3. Background music (~1 hr streamed): HF `rudraml/fma` (small) -> 16 kHz -> `data\fma\`.
4. Negative features (**16.4 GB**): `openwakeword_features_ACAV100M_2000_hrs_16bit.npy` from HF `davidscripka/openwakeword_features` -> `data\`.
5. Validation features (~200 MB): `validation_set_features.npy`, same HF repo -> `data\`.

Cells 8-10 of `openWakeWord\notebooks\automatic_model_training.ipynb` contain
copy-paste code for exactly these five downloads (adjust output paths to `data\`).
Steps 1-3 feed augmentation; 4-5 feed training. Keep them for future retraining.

**Steps 1-3 — per model** (shown for hej_deda; repeat with `cao_deda.yaml`):

```bat
cd /d "u:\Coding\Meta RayBan AI\wakeword-training"
venv\Scripts\python.exe openWakeWord\openwakeword\train.py --training_config hej_deda.yaml --generate_clips
venv\Scripts\python.exe openWakeWord\openwakeword\train.py --training_config hej_deda.yaml --augment_clips
venv\Scripts\python.exe openWakeWord\openwakeword\train.py --training_config hej_deda.yaml --train_model
```

Output: `output_hej_deda\hej_deda.onnx` (and `output_cao_deda\cao_deda.onnx`).
Generation is resumable — re-running `--generate_clips` continues until the
configured counts exist.

## Expected training time (per model)

| Stage | CPU only (current venv) | With GPU torch |
|---|---|---|
| Generate 12k clips (10k train + 2k val) | ~30-60 min (piper medium is faster than realtime on CPU) | ~10-20 min |
| Augment + compute features | ~1-2 h | ~30 min |
| Train 50k steps (tiny DNN head) | ~2-5 h (bottleneck: memmap reads of the 16 GB npy — put `data\` on the NVMe if possible) | <1 h |

Reference point: the official notebook trains a smaller run (5k samples, 10k
steps) in under 1 hour on a free Colab T4. Colab remains a zero-setup fallback:
the same notebook, upload the Serbian voice, done.

## GPU

The GTX 1650 (Turing, sm_75) is supported by current PyTorch CUDA builds. To use
it: `venv\Scripts\python.exe -m pip install torch torchaudio --index-url
https://download.pytorch.org/whl/cu126` (~2.5 GB download; skipped now to stay
under the setup budget). 4 GB VRAM is plenty — the trained head is tiny and ONNX
voice synthesis is per-clip. Worth doing before the real run, not required.

## Quirks fixed during setup (do not undo)

1. **scipy pinned to <1.17** (1.16.3 installed): `acoustics 0.2.6` imports
   `scipy.special.sph_harm`, removed in scipy 1.17+. With scipy 1.18 `train.py`
   crashes on import.
2. **webrtcvad -> webrtcvad-wheels**: upstream `webrtcvad` has no cp312 wheels and
   needs MSVC to build; `webrtcvad-wheels` is a drop-in (same import name).
   `piper-sample-generator` was therefore installed with `--no-deps` after its
   deps were installed manually.
3. **generate_samples shim** (see above) bridging train.py's v1 API to v3.
4. `tts_batch_size` in the YAML is ignored by the ONNX voice path (harmless).

## Open blockers

1. **`.tflite` export does not work in this venv** (ONNX export is unaffected).
   `train.py --convert_to_tflite` needs `tensorflow-cpu==2.8.1` + `onnx_tf==1.10.0`,
   which have no Python 3.12 wheels (TF 2.8.1 tops out at 3.10; onnx_tf is
   abandoned). Options, in order of preference:
   a. Ship the `.onnx` model — onnxruntime works on Android and is a first-class
      openWakeWord format. Likely no fix needed at all.
   b. Install Python 3.10 (`winget install Python.Python.3.10`), make a second
      tiny venv with `tensorflow-cpu==2.8.1 onnx_tf==1.10.0 onnx protobuf<4`, and
      run only the conversion there.
   c. Convert on Google Colab (the official notebook does this).
2. **~20 GB of training data not yet downloaded** (deliberate — Step 0 above).
3. **CPU-only torch installed** — fine but slow; optional CUDA upgrade above.
4. Voice diversity: 2 Serbian speakers; mitigation options described in the
   Serbian section. Judge after testing the first trained model on real voice.
