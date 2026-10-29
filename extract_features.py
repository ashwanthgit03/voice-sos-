
import librosa
import numpy as np
import pandas as pd
import parselmouth


def extract_features(file_path):
    y, sr = librosa.load(file_path, sr=22050, mono=True, res_type="kaiser_fast")

    if len(y) == 0:
        raise ValueError("Audio file is empty")

    duration = len(y) / sr

    # Pitch using Praat
    sound = parselmouth.Sound(y, sampling_frequency=sr)
    pitch_obj = sound.to_pitch(
        time_step=0.01,
        pitch_floor=70,
        pitch_ceiling=1500
    )

    pitch_values = pitch_obj.selected_array["frequency"]
    valid_pitch = pitch_values[pitch_values > 0]
    pitch_hz = np.mean(valid_pitch) if len(valid_pitch) else np.nan

    # Spectral features
    zcr = librosa.feature.zero_crossing_rate(y=y)[0]
    centroid = librosa.feature.spectral_centroid(y=y, sr=sr)[0]
    bandwidth = librosa.feature.spectral_bandwidth(y=y, sr=sr)[0]
    rolloff = librosa.feature.spectral_rolloff(y=y, sr=sr)[0]
    rms = librosa.feature.rms(y=y)[0]

    # Dominant frequency
    stft = np.abs(librosa.stft(y=y))
    frequencies = librosa.fft_frequencies(sr=sr)
    stft[frequencies < 50, :] = 0

    dominant_indices = np.argmax(stft, axis=0)
    dominant_frequency_hz = np.mean(frequencies[dominant_indices])

    # MFCC
    mfcc = librosa.feature.mfcc(y=y, sr=sr, n_mfcc=13)
    

    features = {
        "pitch_hz": pitch_hz,
        "dominant_frequency_hz": dominant_frequency_hz,
        "zcr": np.mean(zcr),
        "spectral_centroid_hz": np.mean(centroid),
        "spectral_bandwidth_hz": np.mean(bandwidth),
        "spectral_rolloff_hz": np.mean(rolloff),
        "duration_sec": duration,
        "rms_energy": np.mean(rms)
    }

    for i in range(13):
        features[f"mfcc_{i+1}"] = np.mean(mfcc[i])

    # Label must be assigned after checking the audio
    features["label"] = "unknown"

    df = pd.DataFrame([features])
    df.to_csv("voice_sos_synthetic_audio_features_5000", index=False)

    return df
