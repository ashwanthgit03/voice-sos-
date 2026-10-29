
import time
import queue
import joblib
import numpy as np
import pandas as pd
import sounddevice as sd
import librosa

from datetime import datetime

MODEL_PATH = "kavalan_synthetic_model.pkl"
SAMPLE_RATE = 22050
WINDOW_SECONDS = 10
CHANNELS = 1

FEATURE_NAMES = [
    "pitch_hz",
    "dominant_frequency_hz",
    "zcr",
    "spectral_centroid_hz",
    "spectral_bandwidth_hz",
    "spectral_rolloff_hz",
    "duration_sec",
    "rms_energy",
] + [f"mfcc_{i}" for i in range(1, 14)]

audio_queue = queue.Queue()


def load_model():
    saved = joblib.load(MODEL_PATH)

    if isinstance(saved, dict):
        model = saved.get("model", saved.get("classifier"))
        features = saved.get("features", FEATURE_NAMES)
        labels = saved.get("labels", None)
    else:
        model = saved
        features = getattr(model, "feature_names_in_", FEATURE_NAMES)
        labels = None

    if model is None:
        raise ValueError("Model not found inside saved model file.")

    features = list(features)

    if len(features) != 21:
        raise ValueError(
            f"Expected 21 features, but model has {len(features)}."
        )

    return model, features, labels


def extract_features(audio):
    audio = np.asarray(audio, dtype=np.float32).flatten()

    if len(audio) == 0:
        raise ValueError("No audio received.")

    # Remove DC offset and normalize only if necessary
    audio = audio - np.mean(audio)

    duration = len(audio) / SAMPLE_RATE

    # Pitch
    pitch = librosa.yin(
        audio,
        fmin=65,
        fmax=1500,
        sr=SAMPLE_RATE
    )
    pitch_hz = float(np.mean(pitch)) if len(pitch) else 0.0

    # Dominant frequency
    spectrum = np.abs(
        np.fft.rfft(audio * np.hanning(len(audio)))
    )
    frequencies = np.fft.rfftfreq(
        len(audio), d=1 / SAMPLE_RATE
    )

    if len(spectrum) > 1:
        dominant_frequency_hz = float(
            frequencies[1 + np.argmax(spectrum[1:])]
        )
    else:
        dominant_frequency_hz = 0.0

    # Other audio features
    zcr = float(np.mean(librosa.feature.zero_crossing_rate(audio)))

    centroid = librosa.feature.spectral_centroid(
        y=audio, sr=SAMPLE_RATE
    )
    bandwidth = librosa.feature.spectral_bandwidth(
        y=audio, sr=SAMPLE_RATE
    )
    rolloff = librosa.feature.spectral_rolloff(
        y=audio, sr=SAMPLE_RATE
    )
    rms = librosa.feature.rms(y=audio)
    mfcc = librosa.feature.mfcc(
        y=audio, sr=SAMPLE_RATE, n_mfcc=13
    )

    features = {
        "pitch_hz": pitch_hz,
        "dominant_frequency_hz": dominant_frequency_hz,
        "zcr": zcr,
        "spectral_centroid_hz": float(np.mean(centroid)),
        "spectral_bandwidth_hz": float(np.mean(bandwidth)),
        "spectral_rolloff_hz": float(np.mean(rolloff)),
        "duration_sec": float(duration),
        "rms_energy": float(np.mean(rms)),
    }

    for i in range(13):
        features[f"mfcc_{i + 1}"] = float(np.mean(mfcc[i]))

    return features


def predict_audio(audio, model, feature_names, labels):
    features = extract_features(audio)

    # Use the exact feature order expected by the saved model
    row = pd.DataFrame(
        [[features[name] for name in feature_names]],
        columns=feature_names
    )

    prediction = model.predict(row)[0]

    if labels is not None:
        try:
            prediction = labels[int(prediction)]
        except (IndexError, TypeError, ValueError):
            pass

    print("\n" + "=" * 55)
    print("KAVALAN - LIVE AUDIO ANALYSIS")
    print("=" * 55)
    print(f"Time: {datetime.now().strftime('%H:%M:%S')}")
    print(f"Audio duration: {features['duration_sec']:.2f} sec")

    print("\nExtracted 21 Features:")
    for name in feature_names:
        print(f"{name:<30}: {features[name]:.4f}")

    print("\nModel Prediction:", prediction)

    # Display model probability when the model supports it
    if hasattr(model, "predict_proba"):
        probabilities = model.predict_proba(row)[0]
        classes = list(model.classes_)
        best_index = int(np.argmax(probabilities))

        print(
            f"Model probability for predicted class: "
            f"{probabilities[best_index] * 100:.2f}%"
        )

        if len(classes) > 1:
            print("\nClass probabilities:")
            for class_name, probability in zip(classes, probabilities):
                print(f"{class_name}: {probability * 100:.2f}%")
    else:
        print("Probability: Not available for this model.")

    print("\nNote: Model output is experimental, not a confirmed")
    print("real-world emergency detection.")
    print("=" * 55)


def audio_callback(indata, frames, time_info, status):
    if status:
        print("Audio status:", status)

    audio_queue.put(indata[:, 0].copy())


def main():
    model, feature_names, labels = load_model()

    print("\nKAVALAN - LIVE MICROPHONE MONITORING")
    print("Sample rate:", SAMPLE_RATE)
    print("Analysis interval:", WINDOW_SECONDS, "seconds")
    print("Press Ctrl+C to stop recording.\n")

    audio_buffer = []
    samples_per_window = SAMPLE_RATE * WINDOW_SECONDS
    total_samples = 0

    try:
        with sd.InputStream(
            samplerate=SAMPLE_RATE,
            channels=CHANNELS,
            dtype="float32",
            callback=audio_callback,
            blocksize=2048
        ):
            print("Microphone recording started...")

            while True:
                block = audio_queue.get()
                audio_buffer.append(block)
                total_samples += len(block)

                if total_samples >= samples_per_window:
                    audio = np.concatenate(audio_buffer)

                    # Keep any extra samples for the next window
                    audio_buffer = []
                    total_samples = 0

                    try:
                        predict_audio(
                            audio, model, feature_names, labels
                        )
                    except Exception as error:
                        print("Analysis error:", error)

                    print("\nListening for the next 10 seconds...")

    except KeyboardInterrupt:
        print("\nStopping microphone recording...")

    # Process audio that remains in the buffer/queue
    while not audio_queue.empty():
        block = audio_queue.get_nowait()
        audio_buffer.append(block)
        total_samples += len(block)

    if total_samples > 0:
        print("\nAnalyzing final remaining audio...")
        try:
            audio = np.concatenate(audio_buffer)
            predict_audio(audio, model, feature_names, labels)
        except Exception as error:
            print("Final analysis error:", error)

    print("\nKavalan recording stopped.")


if __name__ == "__main__":
    main()
