
import joblib
import numpy as np
import pandas as pd
import librosa
from tkinter import Tk, filedialog

MODEL_PATH = "kavalan_synthetic_model.pkl"
SAMPLE_RATE = 22050
CHUNK_SECONDS = 5

FEATURES = [
    "pitch_hz",
    "dominant_frequency_hz",
    "zcr",
    "spectral_centroid_hz",
    "spectral_bandwidth_hz",
    "spectral_rolloff_hz",
    "duration_sec",
    "rms_energy"
] + [f"mfcc_{i}" for i in range(1, 14)]


def extract_features(audio, sr):
    pitch = librosa.yin(
        audio, fmin=50, fmax=2000, sr=sr
    )

    fft = np.abs(np.fft.rfft(audio))
    frequencies = np.fft.rfftfreq(len(audio), d=1 / sr)

    dominant_frequency = (
        frequencies[1:][np.argmax(fft[1:])]
        if len(fft) > 1 else 0
    )

    zcr = np.mean(
        librosa.feature.zero_crossing_rate(y=audio)
    )
    centroid = np.mean(
        librosa.feature.spectral_centroid(y=audio, sr=sr)
    )
    bandwidth = np.mean(
        librosa.feature.spectral_bandwidth(y=audio, sr=sr)
    )
    rolloff = np.mean(
        librosa.feature.spectral_rolloff(y=audio, sr=sr)
    )
    rms = np.mean(
        librosa.feature.rms(y=audio)
    )

    mfcc = librosa.feature.mfcc(
        y=audio, sr=sr, n_mfcc=13
    )
    mfcc_values = np.mean(mfcc, axis=1)

    values = [
        np.mean(pitch),
        dominant_frequency,
        zcr,
        centroid,
        bandwidth,
        rolloff,
        len(audio) / sr,
        rms
    ] + mfcc_values.tolist()

    return pd.DataFrame([values], columns=FEATURES)


def main():
    try:
        saved = joblib.load(MODEL_PATH)
        model = saved["model"]
        model_features = saved.get("features", FEATURES)

        if list(model_features) != FEATURES:
            raise ValueError(
                "Model feature names/order mismatch. "
                "Check the training code."
            )

        root = Tk()
        root.withdraw()

        file_path = filedialog.askopenfilename(
            title="Select Scream Audio",
            filetypes=[
                ("Audio files", "*.wav *.mp3 *.m4a *.mpeg *.flac"),
                ("All files", "*.*")
            ]
        )
        root.destroy()

        if not file_path:
            print("No audio file selected.")
            return

        audio, sr = librosa.load(
            file_path, sr=SAMPLE_RATE, mono=True
        )

        if len(audio) == 0:
            print("Audio file is empty.")
            return

        chunk_size = CHUNK_SECONDS * sr
        total_chunks = int(np.ceil(len(audio) / chunk_size))
        predictions = []
        distress_chunks = 0

        print(f"\nAudio duration: {len(audio) / sr:.2f} seconds")
        print(f"Chunk size: {CHUNK_SECONDS} seconds")
        print(f"Total chunks: {total_chunks}")

        for i, start in enumerate(
            range(0, len(audio), chunk_size), start=1
        ):
            chunk = audio[start:start + chunk_size]
            start_time = start / sr
            end_time = (start + len(chunk)) / sr

            data = extract_features(chunk, sr)
            prediction = model.predict(data[FEATURES])[0]
            predictions.append(prediction)

            print(f"\n--- Chunk {i} ---")
            print(f"Time: {start_time:.1f} - {end_time:.1f} sec")
            print("Predicted class:", prediction)

            if hasattr(model, "predict_proba"):
                probabilities = model.predict_proba(data[FEATURES])[0]

                for label, probability in zip(
                    model.classes_, probabilities
                ):
                    print(f"{label}: {probability * 100:.2f}%")

            if str(prediction).lower() == "distress":
                distress_chunks += 1

        print("\n--- FINAL SUMMARY ---")
        for i, prediction in enumerate(predictions, start=1):
            print(f"Chunk {i}: {prediction}")

        print(f"\nDistress chunks: {distress_chunks}/{total_chunks}")

        if distress_chunks:
            print("Result: Distress predicted in one or more chunks.")
        else:
            print("Result: No chunk was classified as distress.")

    except Exception as error:
        print("Error:", error)


if __name__ == "__main__":
    main()
