@echo off
REM ─────────────────────────────────────────────────────────────────────────
REM  Kavalan Voice SOS – Backend Launcher
REM  Run this file from the project root (the folder with kavalan_synthetic_model.pkl)
REM  The Android app must connect to this PC's local IP on port 8000.
REM
REM  Steps:
REM   1. Find your PC's WiFi IP:  ipconfig  → look for IPv4 (e.g. 192.168.1.5)
REM   2. In the app's Home screen, set Backend URL to: http://192.168.1.5:8000
REM   3. Make sure your phone and PC are on the same WiFi network.
REM ─────────────────────────────────────────────────────────────────────────

echo [Kavalan] Installing / checking backend dependencies...
pip install -r backend\requirements_backend.txt

echo.
echo [Kavalan] Starting server on 0.0.0.0:8000 ...
echo [Kavalan] Find your PC IP with:  ipconfig
echo.

uvicorn backend.server:app --host 0.0.0.0 --port 8000 --reload

pause
