"""Overnight wake-word training driver: hej_deda + cao_deda.

Owner's order 2026-08-19: train on the GTX 1650 via CUDA. Stages, each
idempotent so the script can simply be re-run after any failure:
  A. Swap the CPU torch build for the CUDA one (same version number, so pip
     must uninstall first -- '+cpu' vs '+cu126' is a local version tag).
  B. Download training data into data/ (MIT RIRs; AudioSet backgrounds, now
     parquet-packed on HF -- the notebook's bal_train09.tar no longer exists;
     ACAV100M negative features, 16.4 GB).
  C. Per model: generate clips -> augment -> train -> .onnx
The .cmd wrapper redirects everything to training.log.
"""
import io
import os
import shutil
import subprocess
import sys
import time
import traceback

BASE = os.path.dirname(os.path.abspath(__file__))
PY = os.path.join(BASE, 'venv', 'Scripts', 'python.exe')
DATA = os.path.join(BASE, 'data')
TRAIN = os.path.join('openWakeWord', 'openwakeword', 'train.py')
# ~2.8 GB, the same budget as the old single tar the notebook used.
AUDIOSET_PARQUETS = ['data/bal_train/%02d.parquet' % i for i in (9, 10, 11, 12)]


def log(msg):
    print('[%s] %s' % (time.strftime('%Y-%m-%d %H:%M:%S'), msg), flush=True)


def run(args):
    log('RUN: ' + ' '.join(args))
    rc = subprocess.call(args, cwd=BASE)
    log('rc=%d' % rc)
    return rc


# ---- stage A: CUDA torch ----------------------------------------------------

def cuda_ok():
    out = subprocess.run(
        [PY, '-c', 'import torch; print(torch.__version__, torch.cuda.is_available())'],
        capture_output=True, text=True)
    log('torch check: ' + (out.stdout or out.stderr).strip())
    return 'True' in out.stdout


def ensure_cuda_torch():
    if cuda_ok():
        return True
    run([PY, '-m', 'pip', 'uninstall', '-y', 'torch', 'torchaudio'])
    for index in ('cu126', 'cu128'):
        if run([PY, '-m', 'pip', 'install', 'torch', 'torchaudio',
                '--index-url', 'https://download.pytorch.org/whl/' + index]) == 0:
            break
    else:
        log('CUDA install failed from every index -- restoring CPU torch')
        run([PY, '-m', 'pip', 'install', 'torch', 'torchaudio'])
    if cuda_ok():
        log('CUDA active')
        return True
    log('CUDA NOT available -- continuing on CPU (slower, still works)')
    return False


# ---- stage B: data downloads ------------------------------------------------

def have(dirpath, min_files):
    return os.path.isdir(dirpath) and len(os.listdir(dirpath)) >= min_files


def dl_rirs():
    out = os.path.join(DATA, 'mit_rirs')
    if have(out, 250):
        log('RIRs already present')
        return
    from huggingface_hub import snapshot_download
    log('downloading MIT RIRs (~100 MB)...')
    tmp = os.path.join(DATA, '_hf_rirs')
    snapshot_download('davidscripka/MIT_environmental_impulse_responses',
                      repo_type='dataset', allow_patterns=['16khz/*.wav'], local_dir=tmp)
    os.makedirs(out, exist_ok=True)
    src = os.path.join(tmp, '16khz')
    for name in os.listdir(src):
        shutil.move(os.path.join(src, name), os.path.join(out, name))
    shutil.rmtree(tmp, ignore_errors=True)
    log('RIRs: %d files' % len(os.listdir(out)))


def dl_audioset():
    out = os.path.join(DATA, 'audioset_16k')
    if have(out, 2000):
        log('AudioSet backgrounds already present')
        return
    import numpy as np
    import pyarrow.parquet as pq
    import soundfile as sf
    from math import gcd
    from scipy.signal import resample_poly
    from huggingface_hub import hf_hub_download
    os.makedirs(out, exist_ok=True)
    tmp = os.path.join(DATA, '_hf_audioset')
    for rel in AUDIOSET_PARQUETS:
        log('AudioSet: downloading %s (~0.7 GB)...' % rel)
        path = hf_hub_download('agkphysics/AudioSet', rel, repo_type='dataset', local_dir=tmp)
        log('AudioSet: decoding %s' % os.path.basename(path))
        n_new = 0
        for batch in pq.ParquetFile(path).iter_batches(batch_size=64, columns=['audio']):
            for item in batch.column('audio').to_pylist():
                base = os.path.basename(item.get('path') or '') or ('clip_%d' % n_new)
                dest = os.path.join(out, os.path.splitext(base)[0] + '.wav')
                if os.path.exists(dest):
                    continue
                try:
                    audio, sr = sf.read(io.BytesIO(item['bytes']), dtype='float32',
                                        always_2d=True)
                    mono = audio.mean(axis=1)
                    if sr != 16000:
                        g = gcd(sr, 16000)
                        mono = resample_poly(mono, 16000 // g, sr // g)
                    sf.write(dest, (np.clip(mono, -1, 1) * 32767).astype(np.int16),
                             16000, subtype='PCM_16')
                    n_new += 1
                except Exception as e:
                    log('  skipped one clip: %s' % e)
        log('AudioSet: %s -> +%d clips (%d total)' % (rel, n_new, len(os.listdir(out))))
    shutil.rmtree(tmp, ignore_errors=True)


def dl_features():
    from huggingface_hub import hf_hub_download
    for fn, gb in (('validation_set_features.npy', 0.2),
                   ('openwakeword_features_ACAV100M_2000_hrs_16bit.npy', 16.4)):
        dest = os.path.join(DATA, fn)
        if os.path.exists(dest) and os.path.getsize(dest) > gb * 0.9e9:
            log('%s already present' % fn)
            continue
        log('downloading %s (~%.1f GB)...' % (fn, gb))
        hf_hub_download('davidscripka/openwakeword_features', fn,
                        repo_type='dataset', local_dir=DATA)
    log('feature files present')


# ---- stage C: training ------------------------------------------------------

FEATURE_NPYS = ('positive_features_train', 'negative_features_train',
                'positive_features_test', 'negative_features_test')


def ensure_16k(clip_root):
    """openWakeWord expects 16 kHz clips but the Serbian piper voice renders
    at 22050 -- resample in place whatever is off (cheap header check first).
    Cost the owner a day on 2026-08-19; now guarded after every generation."""
    import numpy as np
    import soundfile as sf
    from math import gcd
    from scipy.signal import resample_poly
    fixed = 0
    for sub in ('positive_train', 'positive_test', 'negative_train', 'negative_test'):
        d = os.path.join(clip_root, sub)
        if not os.path.isdir(d):
            continue
        for name in os.listdir(d):
            if not name.endswith('.wav'):
                continue
            f = os.path.join(d, name)
            try:
                if sf.info(f).samplerate == 16000:
                    continue
                data, sr = sf.read(f, dtype='float32', always_2d=True)
                g = gcd(sr, 16000)
                mono = resample_poly(data.mean(axis=1), 16000 // g, sr // g)
                sf.write(f, (np.clip(mono, -1, 1) * 32767).astype(np.int16),
                         16000, subtype='PCM_16')
                fixed += 1
            except Exception as e:
                log('  resample skip %s: %s' % (name, e))
    if fixed:
        log('%s: %d clips resampled to 16 kHz' % (clip_root, fixed))


def drop_partial_features(clip_root):
    """A crashed augment run leaves some feature .npy files but not all four;
    the augment stage skips itself whenever the first one exists, so leftovers
    silently starve training (cost two runs on 2026-08-19). All or nothing."""
    if all(os.path.exists(os.path.join(clip_root, n + '.npy')) for n in FEATURE_NPYS):
        return
    removed = 0
    if os.path.isdir(clip_root):
        for name in os.listdir(clip_root):
            if name.endswith('.npy') and '_features_' in name:
                os.remove(os.path.join(clip_root, name))
                removed += 1
    if removed:
        log('%s: removed %d partial feature files (crash leftovers)' % (clip_root, removed))


def train(cfg):
    import yaml
    with open(os.path.join(BASE, cfg), encoding='utf-8') as f:
        conf = yaml.safe_load(f)
    name = conf['model_name']
    outdir = os.path.normpath(os.path.join(BASE, conf['output_dir']))
    clip_root = os.path.join(outdir, name)
    onnx = os.path.join(outdir, name + '.onnx')
    if os.path.exists(onnx):
        log('%s: model already trained (%s)' % (cfg, onnx))
        return True
    for stage in ('--generate_clips', '--augment_clips', '--train_model'):
        if stage == '--augment_clips':
            ensure_16k(clip_root)          # piper renders 22050; oww wants 16k
            drop_partial_features(clip_root)  # crash leftovers fool the skip
        if run([PY, TRAIN, '--training_config', cfg, stage]) != 0:
            log('%s %s FAILED -- stopping this model' % (cfg, stage))
            return False
    ok = os.path.exists(onnx)
    log('%s: %s' % (cfg, ('DONE -> ' + onnx) if ok else 'finished but no .onnx found!'))
    return ok


def main():
    if '--smoke' in sys.argv:
        log('=== smoke-only run ===')
        shutil.rmtree(os.path.join(BASE, 'output_smoke_deda'), ignore_errors=True)
        ok = train('smoke_deda.yaml')
        log('=== smoke %s ===' % ('PASSED' if ok else 'FAILED'))
        return 0 if ok else 1
    log('=== wake-word training driver started ===')
    free = shutil.disk_usage(BASE).free / 1e9
    log('free disk on this drive: %.1f GB' % free)
    feats = os.path.join(DATA, 'openwakeword_features_ACAV100M_2000_hrs_16bit.npy')
    # The 16.4 GB feature file dominates the budget -- once it is already on
    # disk, the rest (clips + augmented features) fits in a few GB.
    need = 6 if os.path.exists(feats) and os.path.getsize(feats) > 15e9 else 26
    if free < need:
        log('ABORT: under %d GB free' % need)
        return 1
    ensure_cuda_torch()
    dl_rirs()
    dl_audioset()
    dl_features()
    # Owner's rule (2026-08-19): the whole pipeline must pass at toy scale
    # before any hours-long run. Fresh smoke every time -- it is minutes.
    shutil.rmtree(os.path.join(BASE, 'output_smoke_deda'), ignore_errors=True)
    if not train('smoke_deda.yaml'):
        log('SMOKE FAILED -- real training NOT started')
        return 1
    log('smoke passed end-to-end -- starting the real training')
    results = {}
    for cfg in ('hej_deda.yaml', 'cao_deda.yaml'):
        # Re-check space per model -- clips + features for one full model eat
        # real gigabytes and the first model's output shrinks what the second
        # one has to work with (pregled 7).
        free = shutil.disk_usage(BASE).free / 1e9
        if free < 5:
            log('ABORT before %s: only %.1f GB free' % (cfg, free))
            results[cfg] = False
            continue
        results[cfg] = train(cfg)
    log('=== SUMMARY: %s ===' % results)
    return 0 if all(results.values()) else 1


if __name__ == '__main__':
    try:
        sys.exit(main())
    except Exception:
        log('CRASH:\n' + traceback.format_exc())
        sys.exit(2)
