"""Publishes the Deda APK as a GitHub release (internet, not LAN).

Owner's order 2026-08-19 (second iteration): users — current and future —
must be able to download the APK from the INTERNET, not from a PC on the
home network. GitHub releases give a permanent, stable address:

    https://github.com/UVuruna/DedaAI/releases/latest/download/deda.apk

Every publish creates a new dated release; the /latest/ URL always follows
the newest one, so the QR code in the repo README never changes. The public
repo (UVuruna/DedaAI) holds only the install guide and QR — no source.

Run via publish.cmd after every build. Requires `gh` authenticated as the
owner (it already is on this PC).
"""
import os
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
# The RELEASE build (signed, shrunk) from the app that lives in this repo —
# the old fork-checkout path died with the 2026-08-19 monorepo merge.
APK_SRC = os.path.join(
    HERE, '..', 'android',
    'app', 'build', 'outputs', 'apk', 'release', 'app-release.apk')
REPO = 'UVuruna/DedaAI'
STABLE_URL = 'https://github.com/%s/releases/latest/download/deda.apk' % REPO


def log(msg):
    print('[%s] %s' % (time.strftime('%Y-%m-%d %H:%M:%S'), msg), flush=True)


def main():
    if not os.path.exists(APK_SRC):
        log('publish FAILED: no APK at %s — build first' % APK_SRC)
        return 1
    stamp = time.localtime(os.path.getmtime(APK_SRC))
    tag = time.strftime('v%Y.%m.%d-%H%M', stamp)
    title = time.strftime('Deda %Y-%m-%d %H:%M', stamp)
    notes = ' '.join(sys.argv[1:]) or 'Nova verzija.'  # lang-ok: user-facing release note

    # The asset must be named deda.apk for the stable /latest/ URL.
    staged = os.path.join(HERE, 'deda.apk')
    import shutil
    shutil.copy2(APK_SRC, staged)

    rc = subprocess.call(['gh', 'release', 'create', tag, staged,
                          '--repo', REPO, '--title', title, '--notes', notes])
    if rc != 0:
        # Same tag already exists (rebuild within one minute) — replace asset.
        rc = subprocess.call(['gh', 'release', 'upload', tag, staged,
                              '--repo', REPO, '--clobber'])
    os.remove(staged)
    if rc != 0:
        log('publish FAILED: gh exited with %d' % rc)
        return rc
    log('published %s (%.0f MB) -> %s' % (title, os.path.getsize(APK_SRC) / 1e6, STABLE_URL))
    return 0


if __name__ == '__main__':
    sys.exit(main())
