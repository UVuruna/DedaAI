"""The smoke test that would have caught the 0.1.5 crash — RELAUNCH, not launch.

Every release check this project ever ran did `adb uninstall` and then a
SINGLE `am start`. That is exactly the one launch the bug spared. The app
opened once after install and died on every launch after that, so a
one-launch smoke reported green four releases in a row while the owner's
phone was unusable.

What it does, against a real APK on a real device/emulator:

  1. uninstall, install the APK  -> a genuinely fresh install
  2. launch, wait, check the process is alive        (launch 1)
  3. launch again without granting anything          (launch 2)
  4. grant every runtime permission the app asks for
  5. launch three more times                         (launches 3-5)

A launch counts as PASS only if the process is still alive after the
settle delay. Any dead launch dumps the crash buffer and fails the run,
because that is the user-visible symptom: "I open it and it closes".

Usage:
    python release/smoke_relaunch.py [path/to/app-release.apk] [--serial S]

With no path it uses the release output of this repo's Android build.
Exit code 0 = every launch survived.
"""
import os
import re
import subprocess
import sys
import time

HERE = os.path.dirname(os.path.abspath(__file__))
DEFAULT_APK = os.path.join(
    HERE, '..', 'android', 'app', 'build', 'outputs', 'apk', 'release',
    'app-release.apk')
PKG = 'com.uvuruna.dedaai'
ACTIVITY = 'com.meta.wearable.dat.externalsampleapps.cameraaccess.MainActivity'
MANIFEST = os.path.join(
    HERE, '..', 'android', 'app', 'src', 'main', 'AndroidManifest.xml')

#: How long to let a launch settle before asking whether it is still there.
#: The 0.1.5 abort landed ~2 s in; 4 s leaves margin on a cold emulator.
SETTLE_SEC = 4

#: Permissions that are install-time (or not user-grantable) — `pm grant`
#: errors on these, so they are skipped rather than reported as failures.
NOT_RUNTIME = ('INTERNET', 'BLUETOOTH', 'BLUETOOTH_ADMIN', 'WAKE_LOCK',
               'ACCESS_NETWORK_STATE', 'MODIFY_AUDIO_SETTINGS',
               'FOREGROUND_SERVICE', 'REQUEST_INSTALL_PACKAGES', 'SET_ALARM')


def adb_path():
    """adb is rarely on PATH on this machine; fall back to the SDK location."""
    for candidate in ('adb', os.path.join(
            os.environ.get('LOCALAPPDATA', ''),
            'Android', 'Sdk', 'platform-tools', 'adb.exe')):
        try:
            subprocess.run([candidate, 'version'], capture_output=True,
                           check=True)
            return candidate
        except (OSError, subprocess.CalledProcessError):
            continue
    sys.exit('smoke FAILED: adb not found (PATH or Android SDK platform-tools)')


class Device:
    def __init__(self, adb, serial=None):
        self.base = [adb] + (['-s', serial] if serial else [])

    def run(self, *args, **kw):
        return subprocess.run(self.base + list(args), capture_output=True,
                              text=True, **kw)

    def shell(self, cmd):
        return self.run('shell', cmd).stdout.strip()

    def alive(self):
        return bool(self.shell('pidof %s' % PKG).strip())


def runtime_permissions():
    """The runtime permissions this app declares, read from its manifest.

    Read rather than hard-coded: a permission added to the manifest without
    being added here would silently stop being covered by the smoke.
    """
    with open(MANIFEST, encoding='utf-8') as f:
        names = re.findall(r'uses-permission android:name="([^"]+)"', f.read())
    out = []
    for name in names:
        short = name.rsplit('.', 1)[-1]
        if name.startswith('android.permission.') and short not in NOT_RUNTIME:
            out.append(name)
    return out


def launch(dev, label):
    dev.shell('am force-stop %s' % PKG)
    dev.run('shell', 'am start -W -n %s/%s' % (PKG, ACTIVITY))
    time.sleep(SETTLE_SEC)
    ok = dev.alive()
    print('  launch %-28s %s' % (label, 'ALIVE' if ok else 'DEAD'), flush=True)
    return ok


def main():
    args = [a for a in sys.argv[1:]]
    serial = None
    if '--serial' in args:
        i = args.index('--serial')
        serial = args[i + 1]
        del args[i:i + 2]
    apk = os.path.abspath(args[0]) if args else os.path.abspath(DEFAULT_APK)

    if not os.path.exists(apk):
        sys.exit('smoke FAILED: no APK at %s' % apk)

    dev = Device(adb_path(), serial)
    if not dev.shell('echo ok'):
        sys.exit('smoke FAILED: no device reachable (adb devices)')

    print('APK: %s (%.1f MB)' % (apk, os.path.getsize(apk) / 1e6))
    dev.run('uninstall', PKG)
    install = dev.run('install', '-r', apk)
    if 'Success' not in install.stdout:
        sys.exit('smoke FAILED: install did not succeed\n%s%s'
                 % (install.stdout, install.stderr))
    print('installed clean')
    dev.run('logcat', '-c')

    results = []
    results.append(('1 (fresh, no permissions)', launch(dev, '1 fresh')))
    results.append(('2 (relaunch, no permissions)', launch(dev, '2 relaunch')))

    granted = 0
    for perm in runtime_permissions():
        result = dev.run('shell', 'pm grant %s %s' % (PKG, perm))
        # `pm grant` prints nothing on success and an Exception on refusal
        # (install-time permissions, or one the manifest does not declare).
        if not (result.stdout.strip() or result.stderr.strip()):
            granted += 1
    print('granted %d runtime permissions' % granted)

    for n in (3, 4, 5):
        results.append(('%d (permissions granted)' % n,
                        launch(dev, '%d granted' % n)))

    dead = [name for name, ok in results if not ok]
    print()
    if dead:
        print('CRASH BUFFER (last 40 lines)')
        print(dev.run('logcat', '-b', 'crash', '-d', '-t', '40').stdout)
        print('smoke FAILED: %d of %d launches died -- %s'
              % (len(dead), len(results), ', '.join(dead)))
        return 1
    print('smoke PASSED: %d/%d launches survived' % (len(results), len(results)))
    return 0


if __name__ == '__main__':
    sys.exit(main())
