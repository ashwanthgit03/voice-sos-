# Kavalan — Women Safety Voice SOS

AI-powered women safety app that listens for distress in the background,
classifies audio using a RandomForest model, and triggers Whisper STT
to detect keywords like "help" or "save me" — then fires a loud alarm beep.

---

## How it works

```
Android mic (10s window)
        ↓
  POST /predict  →  FastAPI backend (your PC)
        ↓
  RandomForest classifier  (kavalan_synthetic_model.pkl)
        ↓  if distress confidence >= 80%
  Whisper small  (transcribes audio)
        ↓  if keyword found ("help", "save me", "sos" …)
  BEEP alarm + Emergency notification
```

---

## Project structure

```
voice-sos-/
├── mainapp.py                          ← Run this to start the backend
├── kavalan_synthetic_model.pkl         ← Trained RandomForest model (Git LFS)
│
├── backend/
│   ├── server.py                       ← FastAPI /predict endpoint
│   ├── requirements_backend.txt        ← Python dependencies
│   └── start_server.bat                ← Windows one-click launcher
│
└── app/                                ← Android app (Jetpack Compose)
    └── src/main/java/com/example/womensos/
        ├── MainActivity.kt             ← UI: login, home, SOS, demo upload
        ├── AudioMonitoringService.kt   ← Always-on background mic service
        └── ApiClient.kt                ← Shared OkHttp client
```

---

## Requirements

### Backend (PC / laptop)
- Python 3.9 or higher
- Git LFS (to download the model file)
- Internet connection on first run (Whisper downloads ~244 MB)

### Android app
- Android Studio (latest stable)
- Android phone with Android 8.0+ (API 26+)
- USB cable + USB Debugging enabled on phone
- Phone and PC on the **same WiFi network**

---

## Setup — Step by step

### Step 1 — Clone the repository

You MUST have Git LFS installed before cloning, otherwise the model file
will not download correctly.

Install Git LFS:  https://git-lfs.com/

Then clone:

```bash
git clone https://github.com/ashwanthgit03/voice-sos-.git
cd voice-sos-
git lfs pull
```

The last command downloads `kavalan_synthetic_model.pkl` (134 MB).
You should see the file is ~134 MB after this step.
If the file is only a few hundred bytes, run `git lfs pull` again.

---

### Step 2 — Install Python backend dependencies

```bash
pip install -r backend/requirements_backend.txt
```

This installs: fastapi, uvicorn, librosa, scikit-learn, openai-whisper, etc.

---

### Step 3 — Start the backend server

```bash
python mainapp.py
```

The terminal will print your PC's local IP address, for example:

```
PC WiFi IP    :  192.168.1.5
Backend URL   :  http://192.168.1.5:8000
```

Keep this terminal window open the entire time you use the app.

On first distress detection, Whisper will download (~244 MB). This is
a one-time download and will be cached for future runs.

---

### Step 4 — Build and install the Android app

1. Open Android Studio
2. Click Open → select the cloned folder (voice-sos-)
3. Wait for Gradle sync to complete (2-5 minutes first time)
4. Enable USB Debugging on your phone:
   - Settings → About Phone → tap Build Number 7 times
   - Settings → Developer Options → USB Debugging ON
5. Plug phone into PC via USB → Allow USB Debugging when prompted
6. In Android Studio → click the green Run ▶ button
7. The app installs and opens on your phone automatically

---

### Step 5 — Connect app to backend

1. Log in to the app
2. On the Home screen, find the "Backend Server URL" field
3. Enter the URL shown by mainapp.py, for example:
   ```
   http://192.168.1.5:8000
   ```
4. Tap "Save & Reconnect"
5. The status card turns green: "Live Monitoring Active"

---

### Step 6 — Test with the demo upload

1. On the Home screen, tap "Choose Audio File"
2. Select any .wav or .mp3 file from your phone
3. The app sends it to the AI model
4. Results appear: prediction, confidence, Whisper transcript
5. If distress + keyword detected → loud BEEP alarm plays

---

## Firebase setup (if you use your own Firebase project)

The `google-services.json` included in this repo is linked to the original
Firebase project. If you want to use your own:

1. Go to https://console.firebase.google.com
2. Create a new project
3. Add an Android app with package name: `com.example.womensos`
4. Download `google-services.json`
5. Replace `app/google-services.json` with your file
6. Enable Authentication (Email/Password) and Firestore in Firebase console

---

## Troubleshooting

| Problem | Fix |
|---------|-----|
| Model file is tiny (a few hundred bytes) | Run `git lfs pull` |
| `ERR_CONNECTION_REFUSED` in browser | Make sure `python mainapp.py` is running |
| App cannot connect to backend | Phone and PC must be on same WiFi |
| `sklearn InconsistentVersionWarning` | Safe to ignore — model still works |
| Whisper slow on first distress hit | First-time download of 244 MB — wait once |
| Android build fails | Check that `google-services.json` is in the `app/` folder |

---

## Tech stack

| Layer | Technology |
|-------|-----------|
| Android app | Kotlin, Jetpack Compose, OkHttp, Firebase Auth + Firestore |
| Background mic | Android `AudioRecord` foreground service |
| Backend API | Python, FastAPI, uvicorn |
| Distress classifier | scikit-learn RandomForestClassifier (21 audio features via librosa) |
| STT / keyword detection | OpenAI Whisper (small model) |
| Model storage | Git LFS |

---

## Important notes

- The backend must run on a PC on the **same WiFi** as the Android phone
- The backend IP changes if you change WiFi networks — update it in the app
- SMS alerts are not implemented — emergency triggers a beep + notification
- The sklearn version warning on startup is harmless