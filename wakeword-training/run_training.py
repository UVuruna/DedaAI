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

# ---- English-phonetic positives (owner's order 2026-08-21) ------------------
# The Serbian voice has only 2 speakers and recall paid for it (hej 0.56 /
# cao 0.73). libritts_r brings ~904 English speakers; the *_en.yaml configs
# make them pronounce the Serbian phrases through respellings ("hey dedda",
# "chow dedda"). Their positive clips merge into the main model's clip dirs
# BEFORE augmentation, so one model trains on both voices' positives.

EN_VOICE = os.path.join('piper-sample-generator', 'models',
                        'en_US-libritts_r-medium.pt')
EN_MIX = {
    'hej_deda.yaml': 'hej_deda_en.yaml',
    'cao_deda.yaml': 'cao_deda_en.yaml',
    'smoke_deda.yaml': 'smoke_deda_en.yaml',
}


def clear_dir_contents(path):
    """Empty a directory WITHOUT removing the directory itself — the output
    dirs are junctions to V:\\DedaAI (2026-08-21) and rmtree on a junction
    would tear the link down instead of the data behind it."""
    if not os.path.isdir(path):
        return
    for name in os.listdir(path):
        p = os.path.join(path, name)
        if os.path.isdir(p) and not os.path.islink(p):
            shutil.rmtree(p, ignore_errors=True)
        else:
            try:
                os.remove(p)
            except OSError:
                pass


def merge_positives(src_root, dst_root):
    """Copy the English pass's positive clips into the main clip dirs, with a
    prefix so the two generations can never collide or overwrite."""
    copied = 0
    for sub in ('positive_train', 'positive_test'):
        s = os.path.join(src_root, sub)
        d = os.path.join(dst_root, sub)
        if not os.path.isdir(s):
            continue
        os.makedirs(d, exist_ok=True)
        for name in os.listdir(s):
            if name.endswith('.wav'):
                shutil.copy2(os.path.join(s, name), os.path.join(d, 'en_' + name))
                copied += 1
    return copied


def en_clips_complete(en_root, conf_en):
    """Did the EN pass actually put its clips on disk? (train.py's own resume
    rule: >95 % of target counts as done.)

    The EN pass MUST be judged by the disk, never by the exit code. It loads
    the piper `.pt` VITS voice on CUDA in the same process that
    openwakeword/data.py imports speechbrain into, and on Windows that pair
    fastfails ucrtbase.dll with 0xC0000409 (STATUS_STACK_BUFFER_OVERRUN)
    during DLL detach -- AFTER every clip is written and closed, with no
    Python traceback and no exit path (not even os._exit) able to dodge it.
    Measured 2026-08-21: clips 30/10/30/10 on disk, rc=3221226505; the same
    command with CUDA disabled exits 0, and the Serbian pass never trips it
    because its voice is an .onnx. Judging by rc alone silently threw away
    every English voice -- the whole point of the recipe.
    """
    want = {'positive_train': conf_en['n_samples'],
            'positive_test': conf_en['n_samples_val']}
    have = {}
    for sub in ('positive_train', 'positive_test'):
        d = os.path.join(en_root, sub)
        have[sub] = (len([n for n in os.listdir(d) if n.endswith('.wav')])
                     if os.path.isdir(d) else 0)
    short = {k: '%d/%d' % (have[k], v)
             for k, v in want.items() if have[k] < 0.95 * v}
    return (not short), have, short


def generate_english_positives(main_cfg, conf_main):
    """Generation-only run of the *_en.yaml companion, merged into the main
    model's clips. Missing voice or a failed generation degrades to the
    Serbian-only behaviour instead of killing the run."""
    import yaml
    en_cfg = EN_MIX.get(main_cfg)
    if not en_cfg or not os.path.exists(os.path.join(BASE, en_cfg)):
        return
    if not os.path.exists(os.path.join(BASE, EN_VOICE)):
        log('EN voice missing (%s) -- Serbian-only positives this run' % EN_VOICE)
        return
    with open(os.path.join(BASE, en_cfg), encoding='utf-8') as f:
        conf_en = yaml.safe_load(f)
    en_root = os.path.join(
        os.path.normpath(os.path.join(BASE, conf_en['output_dir'])),
        conf_en['model_name'])
    dst_root = os.path.join(
        os.path.normpath(os.path.join(BASE, conf_main['output_dir'])),
        conf_main['model_name'])
    marker = os.path.join(en_root, '.merged')
    if os.path.exists(marker):
        log('EN positives already merged for %s' % main_cfg)
        return
    env = dict(os.environ, PIPER_SAMPLE_MODEL=os.path.join(BASE, EN_VOICE))
    log('EN positives: generating via %s (libritts_r, ~904 speakers)...' % en_cfg)
    rc = subprocess.call(
        [PY, TRAIN, '--training_config', en_cfg, '--generate_clips'],
        cwd=BASE, env=env)
    log('rc=%d' % rc)
    ok, have, short = en_clips_complete(en_root, conf_en)
    if not ok:
        log('EN generation FAILED (rc=%d, short: %s) -- continuing with '
            'Serbian-only positives' % (rc, short))
        return
    if rc != 0:
        log('EN generation exited rc=%d (0x%08X) but every clip is on disk '
            '(%s) -- known teardown fastfail, merging anyway'
            % (rc, rc & 0xFFFFFFFF, have))
    n = merge_positives(en_root, dst_root)
    with open(marker, 'w', encoding='utf-8') as f:
        f.write('merged %d clips\n' % n)
    log('EN positives: merged %d clips into %s' % (n, dst_root))


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


def model_paths(cfg):
    """cfg -> (conf, name, output dir, clip dir, exported .onnx). Every phase
    needs the same five, and deriving them twice is how the two copies drift."""
    import yaml
    with open(os.path.join(BASE, cfg), encoding='utf-8') as f:
        conf = yaml.safe_load(f)
    name = conf['model_name']
    outdir = os.path.normpath(os.path.join(BASE, conf['output_dir']))
    return conf, name, outdir, os.path.join(outdir, name),         os.path.join(outdir, name + '.onnx')


def train(cfg):
    conf, name, outdir, clip_root, onnx = model_paths(cfg)
    if os.path.exists(onnx):
        log('%s: model already trained (%s)' % (cfg, onnx))
        return True
    for stage in ('--generate_clips', '--augment_clips', '--train_model'):
        if stage == '--augment_clips':
            generate_english_positives(cfg, conf)  # extra voices join first
            ensure_16k(clip_root)          # piper renders 22050; oww wants 16k
            drop_partial_features(clip_root)  # crash leftovers fool the skip
        if run([PY, TRAIN, '--training_config', cfg, stage]) != 0:
            log('%s %s FAILED -- stopping this model' % (cfg, stage))
            return False
    ok = os.path.exists(onnx)
    log('%s: %s' % (cfg, ('DONE -> ' + onnx) if ok else 'finished but no .onnx found!'))
    if ok:
        try:
            evaluate(cfg)
        except Exception as e:   # a lost number must not lose the model
            log('evaluate %s failed: %s' % (cfg, e))
    return ok


def clean_stream_scores(onnx, clip_root, limit=200):
    """Score CLEAN clips the way the phone will: openWakeWord's own
    predict_clip (chunked streaming + the 1 s silence padding short clips need),
    peak score per clip.

    The feature arrays the trainer validates on are noise- and reverb-augmented
    single windows -- a stress test, not the operating point, and reading them
    as "the model is deaf" was wrong (2026-08-22: the same model reads 0.645
    there and 0.935 on clean speech). Both numbers get logged from now on.

    The negatives here are the WHOLE negative_test set: openWakeWord's
    auto-generated adversarial texts (the bulk) plus the hand-written Serbian
    near-misses from custom_negative_phrases, which cannot be told apart by
    filename. Do not report this rate as "hej deko fires N%" -- it is the
    adversarial set as a whole (pregled 18).
    """
    import numpy as np
    from openwakeword.model import Model
    model = Model(wakeword_models=[onnx], inference_framework='onnx')
    key = list(model.models.keys())[0]

    def peaks(sub, want_en=None):
        d = os.path.join(clip_root, sub)
        paths = sorted(os.path.join(d, n) for n in os.listdir(d)
                       if n.endswith('.wav')
                       and (want_en is None or n.startswith('en_') == want_en))
        out = []
        for path in paths[:limit]:
            model.reset()
            frames = model.predict_clip(path)
            out.append(max((float(f[key]) for f in frames), default=0.0))
        return (np.array(out), len(paths)) if out else (None, len(paths))

    return peaks('positive_test', want_en=False), peaks('negative_test')


def evaluate(cfg, thresholds=(0.5, 0.7, 0.9), onnx_override=None):
    """Measure the finished .onnx and WRITE THE NUMBER DOWN.

    openWakeWord's trainer computes accuracy/recall itself but announces them
    through logging.info, which never reaches this driver's log -- the whole
    point of an overnight run was silently lost on 2026-08-21. The driver
    therefore scores the exported model on the same held-out features the
    trainer used (positive_features_test / negative_features_test) and logs
    recall + false-accept rate per threshold, so a run always ends in a
    number. Synthetic clips only: the honest measurement set is still the
    owner's real recordings (UV/snimanje-uzoraka.md).
    """
    import numpy as np
    import onnxruntime as ort
    _, name, _, clip_root, onnx = model_paths(cfg)
    if onnx_override:
        onnx = os.path.abspath(onnx_override)
        name = '%s [%s]' % (name, os.path.basename(os.path.dirname(onnx)))
    pos_f = os.path.join(clip_root, 'positive_features_test.npy')
    neg_f = os.path.join(clip_root, 'negative_features_test.npy')
    for f in (onnx, pos_f, neg_f):
        if not os.path.exists(f):
            log('evaluate %s: missing %s -- no number this run' % (name, f))
            return None
    sess = ort.InferenceSession(onnx, providers=['CPUExecutionProvider'])
    inp = sess.get_inputs()[0].name

    def scores(path):
        feats = np.load(path, mmap_mode='r')
        out = np.empty(len(feats), dtype='float32')
        for i in range(len(feats)):
            x = np.asarray(feats[i], dtype='float32')[None, ...]
            out[i] = sess.run(None, {inp: x})[0].ravel()[0]
        return out

    pos, neg = scores(pos_f), scores(neg_f)
    log('evaluate %s: %d positive / %d negative held-out clips'
        % (name, len(pos), len(neg)))
    # Which positives came from the English-phonetic pass: the feature rows are
    # written in the same glob order the clips sit in, so the `en_` prefix the
    # merge step gave them still identifies them -- but only while the counts
    # line up, so the split is skipped rather than guessed if they do not.
    from pathlib import Path as _Path
    names = [c.name for c in _Path(os.path.join(clip_root, 'positive_test')).glob('*.wav')]
    en = np.array([n.startswith('en_') for n in names]) if len(names) == len(pos) else None
    if en is None:
        log('evaluate %s: %d clips vs %d feature rows -- no per-source split'
            % (name, len(names), len(pos)))
    rows = []
    for th in thresholds:
        recall = float((pos >= th).mean())
        false_accept = float((neg >= th).mean())
        accuracy = float(((pos >= th).sum() + (neg < th).sum()) / (len(pos) + len(neg)))
        split = ''
        if en is not None and en.any():
            split = ' (sr %.3f / en %.3f)' % ((pos[~en] >= th).mean(), (pos[en] >= th).mean())
        log('evaluate %s: threshold %.2f -> recall %.3f%s, false accepts %.3f, '
            'accuracy %.3f' % (name, th, recall, split, false_accept, accuracy))
        rows.append({'threshold': th, 'recall': recall,
                     'false_accepts': false_accept, 'accuracy': accuracy})
    # A model that answers ~0 to a real wake phrase is deaf, not unsure, and no
    # threshold rescues it -- the share of those is the number that told us the
    # recipe, not the data, was wrong (2026-08-22).
    log('evaluate %s: %.1f%% of augmented clips score under 0.01 '
        '(that set is noise + reverb, not clean speech)'
        % (name, 100.0 * float((pos < 0.01).mean())))
    # The operating point: clean speech through the library's own streaming
    # scorer, beside how often the adversarial negatives trigger it.
    clean_pos = clean_neg = None
    try:
        (clean_pos, n_pos_all), (clean_neg, n_neg_all) = clean_stream_scores(onnx, clip_root)
    except (ImportError, OSError, KeyError, ValueError) as e:
        # Loud on purpose: a run without this number is a run that did not
        # answer the only question it was started for.
        log('evaluate %s: !! CLEAN-AUDIO PASS FAILED (%s: %s) -- this run has '
            'NO operating-point number' % (name, type(e).__name__, e))
    if clean_pos is not None:
        log('evaluate %s: clean pass on %d of %d positive and %d of %d '
            'adversarial-negative clips (first N, alphabetical)'
            % (name, len(clean_pos), n_pos_all,
               0 if clean_neg is None else len(clean_neg), n_neg_all))
        for th in thresholds:
            fired = ('%.3f' % (clean_neg >= th).mean()) if clean_neg is not None else 'n/a'
            log('evaluate %s: CLEAN streaming, threshold %.2f -> recall %.3f, '
                'adversarial negatives fire %s' % (name, th, float((clean_pos >= th).mean()), fired))
    return rows


def main():
    if '--evaluate' in sys.argv:
        rest = sys.argv[sys.argv.index('--evaluate') + 1:]
        override = None
        if '--model' in rest:                 # score an older .onnx on this
            i = rest.index('--model')         # model's own held-out clips
            override = rest[i + 1]
            rest = rest[:i] + rest[i + 2:]
        for cfg in rest or ['hej_deda.yaml', 'cao_deda.yaml']:
            evaluate(cfg, onnx_override=override)
        return 0
    if '--smoke' in sys.argv:
        log('=== smoke-only run ===')
        clear_dir_contents(os.path.join(BASE, 'output_smoke_deda'))
        clear_dir_contents(os.path.join(BASE, 'output_smoke_deda_en'))
        ok = train('smoke_deda.yaml')
        log('=== smoke %s ===' % ('PASSED' if ok else 'FAILED'))
        return 0 if ok else 1
    if '--fresh' in sys.argv:
        # A retrain on purpose (new recipe): forget the finished models and
        # their clips so train() cannot skip on the old .onnx.
        for d in ('output_hej_deda', 'output_hej_deda_en',
                  'output_cao_deda', 'output_cao_deda_en'):
            log('fresh: clearing %s' % d)
            clear_dir_contents(os.path.join(BASE, d))
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
    clear_dir_contents(os.path.join(BASE, 'output_smoke_deda'))
    clear_dir_contents(os.path.join(BASE, 'output_smoke_deda_en'))
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
