@echo off
cd /d "%~dp0"
set PYTHONIOENCODING=utf-8
set PYTHONUTF8=1
set HF_HUB_DISABLE_TELEMETRY=1
venv\Scripts\python.exe -u run_training.py >> training.log 2>&1
