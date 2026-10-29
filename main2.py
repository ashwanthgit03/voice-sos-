
from pathlib import Path
import sys
import joblib
import librosa
import numpy as np
import pandas as pd

from sklearn.model_selection import train_test_split
from sklearn.metrics import (
    accuracy_score,
    precision_score,
    recall_score,
    f1_score
)

MODEL_PATH = "kavalan_synthetic_model.pkl"
DATASET_PATH = "kavalan_synthetic_voice_features_200000.csv"
DEFAULT_AUDIO = "freesound_community-cat-wants-food-107901.mp3"


def extract_features(audio_path):
    y, sr = librosa.load(audio_path, sr=22050, mono=True)

    if len(y) == 0:
        raise ValueError("Empty audio file")

    mfcc = librosa.feature.mfcc(y=y, sr=sr, n_mfcc=13)
    spectrum = np.abs(librosa.stft(y))
    frequencies = librosa.fft_frequencies(sr=sr)

    features = {
        "pitch_hz": 0.0,
        "dominant_frequency_hz": float(
            frequencies[np.argmax(spectrum.mean(axis=1))]
        ),
        "zcr": float(np.mean(librosa.feature.zero_crossing_rate(y))),
        "spectral_centroid_hz": float(
            np.mean(librosa.feature.spectral_centroid(y=y, sr=sr))
        ),
        "spectral_bandwidth_hz": float(
            np.mean(librosa.feature.spectral_bandwidth(y=y, sr=sr))
        ),
        "spectral_rolloff_hz": float(
            np.mean(librosa.feature.spectral_rolloff(y=y, sr=sr))
        ),
        "duration_sec": float(len(y) / sr),
        "rms_energy": float(np.mean(librosa.feature.rms(y=y))),
    }

    try:
        pitches = librosa.yin(y, fmin=70, fmax=1500, sr=sr)
        valid = pitches[np.isfinite(pitches)]
        if len(valid):
            features["pitch_hz"] = float(np.mean(valid))
    except Exception:
        pass

    for i in range(13):
        features[f"mfcc_{i + 1}"] = float(np.mean(mfcc[i]))

    return features


def evaluate_model(model, columns):
    df = pd.read_csv(DATASET_PATH)
    df = df.dropna(subset=columns + ["label"])

    X = df[columns]
    y = df["label"]

    X_train, X_test, y_train, y_test = train_test_split(
        X,
        y,
        test_size=0.30,
        random_state=42,
        stratify=y
    )

    # Evaluate the saved model on the same held-out split
    predictions = model.predict(X_test)

    accuracy = accuracy_score(y_test, predictions)
    precision = precision_score(
        y_test, predictions, pos_label="distress", zero_division=0
    )
    recall = recall_score(
        y_test, predictions, pos_label="distress", zero_division=0
    )
    f1 = f1_score(
        y_test, predictions, pos_label="distress", zero_division=0
    )

    return accuracy, precision, recall, f1, len(X_test)


def main():
    audio_path = (
        Path(sys.argv[1])
        if len(sys.argv) > 1
        else Path(DEFAULT_AUDIO)
    )

    if not Path(MODEL_PATH).exists():
        print("Model file not found:", MODEL_PATH)
        return

    if not Path(DATASET_PATH).exists():
        print("Dataset file not found:", DATASET_PATH)
        return

    if not audio_path.exists():
        print("Audio file not found:", audio_path)
        return

    try:
        saved = joblib.load(MODEL_PATH)
        model = saved["model"]
        columns = saved["features"]

        features = extract_features(audio_path)
        sample = pd.DataFrame(
            [[features[c] for c in columns]],
            columns=columns
        )

        prediction = model.predict(sample)[0]

        print("\n========== KAVALAN VOICE SOS ==========")
        print("Audio file:", audio_path.name)
        print("Prediction:", prediction)
        print(f"Audio duration: {features['duration_sec']:.2f} seconds")

        if hasattr(model, "predict_proba"):
            probabilities = model.predict_proba(sample)[0]
            distress_index = list(model.classes_).index("distress")
            confidence = probabilities[distress_index] * 100
            print(f"Distress model score: {confidence:.2f}%")

        accuracy, precision, recall, f1, test_count = evaluate_model(
            model, columns
        )

        print("\n======= MODEL EVALUATION =======")
        print("Test samples:", f"{test_count:,}")
        print(f"Accuracy:  {accuracy * 100:.2f}%")
        print(f"Precision: {precision * 100:.2f}%")
        print(f"Recall:    {recall * 100:.2f}%")
        print(f"F1-score:  {f1 * 100:.2f}%")

    except Exception as error:
        print("Error:", error)


if __name__ == "__main__":
    main()
