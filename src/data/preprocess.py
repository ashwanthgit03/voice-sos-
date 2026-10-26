import librosa
import numpy as np
TARGET_SAMPLE_RATE = 16000
def load_audio(audio_path):
    audio, sample_rate = librosa.load(
        audio_path,
        sr=TARGET_SAMPLE_RATE,
        mono=True
    )
    audio = audio.astype(np.float32)
    return audio, TARGET_SAMPLE_RATE
def normalize_audio(audio):
    max_value = np.max(np.abs(audio))
    if max_value > 0:
        audio = audio / max_value
    return audio