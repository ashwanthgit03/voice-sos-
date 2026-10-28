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


def normalize_audio(audio, target_peak=0.95):
    """Peak normalize audio to target peak amplitude."""
    audio = np.asarray(audio, dtype=np.float32)
    maximum = np.max(np.abs(audio))
    if maximum > 0:
        audio = (audio / maximum) * target_peak
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


def get_audio_duration(audio_path):
    duration = librosa.get_duration(path=audio_path)
    return duration


def split_audio_by_silence(
    audio,
    sample_rate=TARGET_SAMPLE_RATE,
    top_db=30,
    max_chunk_seconds=29,
    min_chunk_seconds=0.3
):
    """
    Split audio into natural speech chunks along silence boundaries,
    ensuring words are never cut mid-utterance and avoiding duplicate text.
    """
    total_samples = len(audio)
    max_chunk_samples = int(max_chunk_seconds * sample_rate)
    min_chunk_samples = int(min_chunk_seconds * sample_rate)

    if total_samples <= max_chunk_samples:
        return [(0, total_samples)]

    try:
        intervals = librosa.effects.split(
            audio,
            top_db=top_db,
            frame_length=2048,
            hop_length=512
        )
    except Exception:
        intervals = []

    if len(intervals) == 0:
        chunks = []
        step = max_chunk_samples
        for start in range(0, total_samples, step):
            end = min(start + step, total_samples)
            chunks.append((start, end))
        return chunks

    chunks = []
    current_start = None
    current_end = None

    for start, end in intervals:
        if current_start is None:
            current_start = start
            current_end = end
        elif (end - current_start) <= max_chunk_samples:
            current_end = end
        else:
            chunks.append((current_start, current_end))
            current_start = start
            current_end = end

    if current_start is not None and (current_end - current_start) >= min_chunk_samples:
        chunks.append((current_start, current_end))

    return chunks if chunks else [(0, total_samples)]
