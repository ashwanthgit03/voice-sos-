"""
Kavalan Voice SOS – Backend Server
===================================
POST /predict  →  accepts any audio file (WAV / MP3 / M4A …)
                 1. Extracts 21 audio features (librosa)
                 2. Classifies with kavalan_synthetic_model.pkl (RandomForest)
                 3. If distress confidence >= 80 %:
                    runs Whisper-small to transcribe
                    checks transcript for distress keywords
                 4. Returns full JSON result

Run:
    uvicorn backend.server:app --host 0.0.0.0 --port 8000 --reload
(from the project root, i.e. the folder that contains kavalan_synthetic_model.pkl)
"""

import io
import logging
import os
import tempfile
from datetime import datetime
from pathlib import Path

import joblib
import pandas as pd
import librosa
import numpy as np
from fastapi import FastAPI, File, HTTPException, UploadFile
from fastapi.middleware.cors import CORSMiddleware

# ── Paths ──────────────────────────────────────────────────────────────────
PROJECT_ROOT = Path(__file__).resolve().parent.parent
MODEL_PATH = PROJECT_ROOT / "kavalan_synthetic_model.pkl"

# ── Configuration ──────────────────────────────────────────────────────────
DISTRESS_THRESHOLD = 0.80   # confidence required to trigger Whisper
AUDIO_SAMPLE_RATE = 22050   # must match training sample rate

DISTRESS_KEYWORDS = {
    "help", "help me", "save me", "sos", "please help",
    "somebody help", "someone help", "emergency",
    "let me go", "leave me alone", "get away",
    "stop", "call police", "call 911", "call 100",
    "danger", "attack",
}

# ── Logging ────────────────────────────────────────────────────────────────
logging.basicConfig(
    level=logging.INFO,
    format="%(asctime)s  %(levelname)-8s  %(message)s",
    datefmt="%H:%M:%S",
)
log = logging.getLogger("kavalan")

# ── FastAPI app ────────────────────────────────────────────────────────────
app = FastAPI(title="Kavalan Voice SOS API", version="1.0.0")
app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_methods=["*"],
    allow_headers=["*"],
)

# ── Load RandomForest model once at startup ────────────────────────────────
if not MODEL_PATH.exists():
    raise RuntimeError(
        f"\n[KAVALAN] Model not found at: {MODEL_PATH}\n"
        "Run train_model2.py first to generate kavalan_synthetic_model.pkl"
    )

log.info(f"Loading RandomForest model from {MODEL_PATH} …")
_saved = joblib.load(MODEL_PATH)
_model = _saved["model"]
_features: list[str] = list(_saved.get("features", _saved.get("feature_names", [])))
_classes: list[str] = list(_model.classes_)
_distress_idx: int = _classes.index("distress") if "distress" in _classes else 0
log.info(f"Model ready — {len(_features)} features  |  classes: {_classes}")

# ── Whisper loaded lazily on first distress hit ────────────────────────────
_whisper_model = None


def _get_whisper():
    global _whisper_model
    if _whisper_model is None:
        log.info("Loading Whisper small (first run may download ~244 MB) …")
        import whisper  # pip install openai-whisper
        _whisper_model = whisper.load_model("small")
        log.info("Whisper ready.")
    return _whisper_model


# ── Feature extraction — 21 features identical to train_model2.py ──────────
def _extract_features(y: np.ndarray, sr: int) -> dict:
    y = y.astype(np.float32)
    y = y - y.mean()  # remove DC offset

    # Pitch (YIN algorithm)
    try:
        pitches = librosa.yin(y, fmin=65, fmax=1500, sr=sr)
        valid = pitches[np.isfinite(pitches) & (pitches > 0)]
        pitch_hz = float(np.mean(valid)) if len(valid) else 0.0
    except Exception:
        pitch_hz = 0.0

    # Dominant frequency via windowed FFT
    spectrum = np.abs(np.fft.rfft(y * np.hanning(len(y))))
    freqs = np.fft.rfftfreq(len(y), d=1.0 / sr)
    dominant_hz = float(freqs[1 + np.argmax(spectrum[1:])]) if len(spectrum) > 1 else 0.0

    feat: dict = {
        "pitch_hz":             pitch_hz,
        "dominant_frequency_hz": dominant_hz,
        "zcr":                  float(np.mean(librosa.feature.zero_crossing_rate(y))),
        "spectral_centroid_hz": float(np.mean(librosa.feature.spectral_centroid(y=y, sr=sr))),
        "spectral_bandwidth_hz": float(np.mean(librosa.feature.spectral_bandwidth(y=y, sr=sr))),
        "spectral_rolloff_hz":  float(np.mean(librosa.feature.spectral_rolloff(y=y, sr=sr))),
        "duration_sec":         float(len(y) / sr),
        "rms_energy":           float(np.mean(librosa.feature.rms(y=y))),
    }

    mfcc = librosa.feature.mfcc(y=y, sr=sr, n_mfcc=13)
    for i in range(13):
        feat[f"mfcc_{i + 1}"] = float(np.mean(mfcc[i]))

    return feat


def _keyword_found(text: str) -> bool:
    lower = text.lower()
    return any(kw in lower for kw in DISTRESS_KEYWORDS)


def _empty_response(note: str) -> dict:
    return {
        "prediction":          "empty",
        "confidence":          0.0,
        "distress_confidence": 0.0,
        "whisper_text":        "",
        "keyword_found":       False,
        "trigger_emergency":   False,
        "timestamp":           datetime.utcnow().isoformat(),
        "note":                note,
    }


# ── Endpoints ──────────────────────────────────────────────────────────────

@app.get("/health")
def health():
    """Quick liveness check — call from the app to verify the backend is reachable."""
    return {
        "status":  "ok",
        "model":   "kavalan_synthetic_model",
        "version": "1.0.0",
        "classes": _classes,
    }


@app.post("/predict")
async def predict(audio: UploadFile = File(...)):
    """
    Accepts any audio file upload.
    Returns distress prediction + optional Whisper transcript + emergency flag.
    """
    content = await audio.read()
    if not content:
        raise HTTPException(status_code=400, detail="Empty audio data received.")

    suffix = Path(audio.filename or "audio.wav").suffix.lower() or ".wav"
    tmp_path: str | None = None

    try:
        # Save to a temp file so librosa can load it (handles WAV/MP3/M4A/etc.)
        with tempfile.NamedTemporaryFile(suffix=suffix, delete=False) as tmp:
            tmp.write(content)
            tmp_path = tmp.name

        log.info(f"Received '{audio.filename}'  ({len(content) // 1024} KB)")

        # Load audio
        y, sr = librosa.load(tmp_path, sr=AUDIO_SAMPLE_RATE, mono=True)

        if len(y) == 0 or np.max(np.abs(y)) < 1e-5:
            log.info("Audio is empty / silent — skipping.")
            return _empty_response("Audio is empty or silent.")

        # ── Step 1: Feature extraction + RandomForest prediction ──────────
        feat = _extract_features(y, sr)
        # Use a DataFrame so the model receives named feature columns (avoids sklearn warning)
        row = pd.DataFrame([[feat.get(f, 0.0) for f in _features]], columns=_features)

        proba = _model.predict_proba(row)[0]
        prediction = str(_model.predict(row)[0])
        distress_conf = float(proba[_distress_idx])

        log.info(f"Prediction: {prediction}  |  distress_confidence={distress_conf:.3f}")

        # ── Step 2: Whisper (only if distress confidence >= threshold) ─────
        whisper_text = ""
        kw_found = False

        if distress_conf >= DISTRESS_THRESHOLD:
            log.info("Distress threshold exceeded → running Whisper transcription …")
            try:
                wm = _get_whisper()
                result = wm.transcribe(tmp_path, language="en", fp16=False)
                whisper_text = result.get("text", "").strip()
                kw_found = _keyword_found(whisper_text)
                log.info(f"Whisper: '{whisper_text}'  |  keyword_found={kw_found}")
            except Exception as ex:
                log.error(f"Whisper error: {ex}")

        trigger = distress_conf >= DISTRESS_THRESHOLD and kw_found

        if trigger:
            log.warning("🚨 EMERGENCY TRIGGER — distress confirmed with keyword!")

        return {
            "prediction":          prediction,
            "confidence":          distress_conf,
            "distress_confidence": distress_conf,
            "whisper_text":        whisper_text,
            "keyword_found":       kw_found,
            "trigger_emergency":   trigger,
            "timestamp":           datetime.utcnow().isoformat(),
        }

    except Exception as ex:
        log.error(f"Prediction error: {ex}", exc_info=True)
        raise HTTPException(status_code=500, detail=str(ex))

    finally:
        if tmp_path:
            try:
                os.unlink(tmp_path)
            except Exception:
                pass
