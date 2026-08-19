@echo off
cd /d "%~dp0"
set PYTHONIOENCODING=utf-8
set PYTHONUTF8=1
python publish.py %* >> publish.log 2>&1
if errorlevel 1 (
    echo PUBLISH FAILED - see publish.log
    exit /b 1
)
echo Published - stable link: https://github.com/UVuruna/DedaAI/releases/latest/download/deda.apk
