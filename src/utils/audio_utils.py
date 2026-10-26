import numpy as np
import librosa
import soundfile as sf
TARGET_SAMPLE_RATE = 16000
def load_audio(
    audio_path,
    sample_rate=TARGET_SAMPLE_RATE
):
    audio, sr = librosa.load(
        audio_path,
        sr=sample_rate,
        mono=True
    )
    return audio, sr
def normalize_audio(audio):
    audio = np.asarray(
        audio,
        dtype=np.float32
    )
    maximum = np.max(
        np.abs(audio)
    )
    if maximum > 0:
        audio = audio / maximum
    return audio
def save_audio(
    audio,
    output_path,
    sample_rate=TARGET_SAMPLE_RATE
):
    sf.write(
        output_path,
        audio,
        sample_rate
    )
def get_audio_duration(
    audio_path
):
    duration = librosa.get_duration(
        path=audio_path
    )
    return duration