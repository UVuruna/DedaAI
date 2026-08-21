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
import json
import os
import re
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

    # deda-version.json rides next to the APK on the same stable URL — the
    # app's in-app updater (UpdateChecker) compares its versionCode with its
    # own and offers the update. Read from the gradle file so the numbers
    # can never drift from what was just built.
    gradle_file = os.path.join(HERE, '..', 'android', 'app', 'build.gradle.kts')
    with open(gradle_file, encoding='utf-8') as f:
        gradle = f.read()
    version_code = int(re.search(r'versionCode\s*=\s*(\d+)', gradle).group(1))
    version_name = re.search(r'versionName\s*=\s*"([^"]+)"', gradle).group(1)
    vjson = os.path.join(HERE, 'deda-version.json')
    with open(vjson, 'w', encoding='utf-8') as f:
        json.dump({'versionCode': version_code,
                   'versionName': version_name,
                   'notes': notes}, f, ensure_ascii=False)

    rc = subprocess.call(['gh', 'release', 'create', tag, staged, vjson,
                          '--repo', REPO, '--title', title, '--notes', notes])
    if rc != 0:
        # Same tag already exists (rebuild within one minute) — replace assets.
        rc = subprocess.call(['gh', 'release', 'upload', tag, staged, vjson,
                              '--repo', REPO, '--clobber'])
    os.remove(staged)
    os.remove(vjson)
    if rc != 0:
        log('publish FAILED: gh exited with %d' % rc)
        return rc
    log('published %s (%.0f MB) -> %s' % (title, os.path.getsize(APK_SRC) / 1e6, STABLE_URL))
    return 0


if __name__ == '__main__':
    sys.exit(main())
