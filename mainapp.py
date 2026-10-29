"""
Kavalan - Women Safety Voice SOS
=================================
Run this file to start the backend server:
    python mainapp.py

Keep this terminal window OPEN while using the Android app.
"""

import os
import socket
import subprocess
import sys


def get_wifi_ip() -> str:
    try:
        with socket.socket(socket.AF_INET, socket.SOCK_DGRAM) as s:
            s.connect(("8.8.8.8", 80))
            return s.getsockname()[0]
    except Exception:
        return "127.0.0.1"


def main():
    ip   = get_wifi_ip()
    port = 8000
    url  = f"http://{ip}:{port}"
    sep  = "=" * 58

    print(f"\n{sep}")
    print("  KAVALAN - Women Safety Voice SOS")
    print("  AI Backend: RandomForest + Whisper Pipeline")
    print(sep)
    print(f"\n  Local access  :  http://localhost:{port}")
    print(f"  PC WiFi IP    :  {ip}")
    print(f"  Backend URL   :  {url}")
    print(f"  Health check  :  http://localhost:{port}/health")
    print(f"  API docs      :  http://localhost:{port}/docs")
    print(f"\n{sep}")
    print("  ANDROID APP SETUP:")
    print(f"    1. Open the app -> Home screen")
    print(f"    2. Set 'Backend URL' to:  {url}")
    print(f"    3. Tap 'Save & Reconnect'")
    print(f"    4. Phone & PC must be on SAME WiFi/Hotspot")
    print(f"\n  Keep this terminal OPEN while using the app.")
    print(f"{sep}\n")

    # Try adding Windows Firewall rule (needs admin rights — ok if it fails)
    try:
        subprocess.run(
            ["netsh", "advfirewall", "firewall", "add", "rule",
             "name=Kavalan_SOS_Backend", "protocol=TCP",
             "dir=in", f"localport={port}", "action=allow"],
            capture_output=True, timeout=8
        )
        print(f"  [OK] Firewall rule set for port {port}.")
    except Exception:
        pass

    print(f"  Starting server on port {port} ... (Ctrl+C to stop)\n")

    # Use subprocess.run — works reliably on Windows
    subprocess.run(
        [sys.executable, "-m", "uvicorn", "backend.server:app",
         "--host", "0.0.0.0", "--port", str(port), "--reload"],
        cwd=os.path.dirname(os.path.abspath(__file__))
    )


if __name__ == "__main__":
    main()
