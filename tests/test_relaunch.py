"""Regression test for the crash that shipped in 0.1.2 through 0.1.5.

The bug: the app opened once after install and closed instantly on every
launch after that. R8's shrinker deleted 61 native methods that the DAT
SDK's .so files register by name at load time, so `RegisterNatives` failed
and ART aborted the process.

The reason it shipped four times is the shape of the old smoke test, not
the difficulty of the bug: every release check did `adb uninstall` and then
ONE `am start`, and launch 1 is precisely the launch this bug spares.

This test drives `release/smoke_relaunch.py`, which installs clean, launches,
RELAUNCHES, grants the runtime permissions, and launches three more times.
It is red on any build with the defect and green only when all five launches
survive.

It needs a device and an APK, so it is deliberately NOT one of the three
guards in `run_guards.py` (those run on every edit and must stay instant).
It skips — rather than fails — when either is missing, so a checkout on a
machine with no Android SDK still runs the suite clean.

    python tests/run_guards.py            # the 3 fast guards, without this
    DEDA_APK=<path> python -m pytest tests/test_relaunch.py   # this one
"""
import os
import subprocess
import sys

import pytest

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.dirname(HERE)
SMOKE = os.path.join(ROOT, 'release', 'smoke_relaunch.py')
RELEASE_APK = os.path.join(
    ROOT, 'android', 'app', 'build', 'outputs', 'apk', 'release',
    'app-release.apk')


def _apk():
    """The APK under test: DEDA_APK wins, else this repo's release output."""
    override = os.environ.get('DEDA_APK')
    if override:
        return override if os.path.exists(override) else None
    return RELEASE_APK if os.path.exists(RELEASE_APK) else None


def _has_device():
    sys.path.insert(0, os.path.join(ROOT, 'release'))
    try:
        from smoke_relaunch import Device, adb_path
    except SystemExit:
        return False  # adb_path() exits when adb is nowhere to be found
    try:
        return bool(Device(adb_path()).shell('echo ok'))
    except SystemExit:
        return False


def test_app_survives_being_reopened():
    apk = _apk()
    if apk is None:
        pytest.skip('no APK to test (build one, or set DEDA_APK)')
    if not _has_device():
        pytest.skip('no device/emulator reachable via adb')

    run = subprocess.run([sys.executable, SMOKE, apk],
                         capture_output=True, text=True, timeout=600)
    launches = [line.strip() for line in run.stdout.splitlines()
                if line.strip().startswith('launch ')]
    assert run.returncode == 0, (
        'the app does not survive being reopened — this is the 0.1.5 crash:\n'
        + '\n'.join(launches) + '\n' + run.stdout[-2000:])
