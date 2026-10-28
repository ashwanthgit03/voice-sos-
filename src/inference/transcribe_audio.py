import os
import sys
from pathlib import Path

# Ensure project root is in sys.path for direct script execution
PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

import torch
import librosa
import numpy as np
from transformers import (
    WhisperProcessor,
    WhisperForConditionalGeneration
)
from src.utils.audio_utils import normalize_audio, split_audio_by_silence
try:
    from src.config import ACTIVE_MODEL, BEAM_SIZE, LANGUAGE, TASK
except ImportError:
    ACTIVE_MODEL = "openai/whisper-small"
    BEAM_SIZE = 4
    LANGUAGE = "English"
    TASK = "transcribe"

DEFAULT_MODEL_PATH = "models/trained/whisper_asr"
FALLBACK_MODEL_PATH = "openai/whisper-small"
SAMPLE_RATE = 16000

_CACHED_MODEL = None
_CACHED_PROCESSOR = None
_CACHED_MODEL_PATH = None


def get_model_and_processor(model_path=None):
    """Load and cache Whisper model and processor on the optimal available device."""
    global _CACHED_MODEL, _CACHED_PROCESSOR, _CACHED_MODEL_PATH

    if model_path is None:
        model_path = ACTIVE_MODEL

    # If specified path doesn't exist locally and isn't a huggingface repo id, fallback
    if not os.path.exists(model_path) and "/" not in model_path:
        if os.path.exists(DEFAULT_MODEL_PATH):
            model_path = DEFAULT_MODEL_PATH
        else:
            model_path = FALLBACK_MODEL_PATH

    if _CACHED_MODEL is not None and _CACHED_MODEL_PATH == model_path:
        device = next(_CACHED_MODEL.parameters()).device
        return _CACHED_MODEL, _CACHED_PROCESSOR, device

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Loading Whisper model from '{model_path}' on {device}...")

    processor = WhisperProcessor.from_pretrained(
        model_path,
        language="English",
        task="transcribe"
    )
    model = WhisperForConditionalGeneration.from_pretrained(model_path)
    model.to(device)
    model.eval()

    _CACHED_MODEL = model
    _CACHED_PROCESSOR = processor
    _CACHED_MODEL_PATH = model_path
    return model, processor, device


def transcribe(
    audio_input,
    model_path=None,
    num_beams=None,
    language="english",
    task="transcribe",
    sample_rate=SAMPLE_RATE
):
    """
    High-accuracy speech-to-text transcription.
    
    Features:
    - Audio level normalization
    - Silence-based VAD chunking to prevent mid-word cutting and duplicated text
    - Beam search decoding with language and task enforcement
    - Anti-repetition penalties
    """
    if num_beams is None:
        num_beams = BEAM_SIZE

    # 1. Load audio if string path is provided
    if isinstance(audio_input, (str, os.PathLike)):
        if not os.path.exists(audio_input):
            raise FileNotFoundError(f"Audio file not found: {audio_input}")
        audio, sr = librosa.load(audio_input, sr=sample_rate, mono=True)
    else:
        audio = np.asarray(audio_input, dtype=np.float32)

    if len(audio) == 0:
        return ""

    # Check for near-total silence
    max_amp = np.max(np.abs(audio))
    if max_amp < 0.005:
        print("Audio is silent or amplitude is too low.")
        return ""

    # 2. Peak normalization
    audio = normalize_audio(audio, target_peak=0.95)

    # 3. Load model and processor
    model, processor, device = get_model_and_processor(model_path)

    # 4. Prepare decoding parameters
    forced_ids = processor.get_decoder_prompt_ids(language=language, task=task)
    gen_kwargs = {
        "forced_decoder_ids": forced_ids,
        "num_beams": num_beams,
        "no_repeat_ngram_size": 3,
        "temperature": 0.0,
        "condition_on_prev_tokens": False,
    }

    # 5. Smart chunking along silence boundaries
    chunks = split_audio_by_silence(
        audio,
        sample_rate=sample_rate,
        top_db=30,
        max_chunk_seconds=29,
        min_chunk_seconds=0.3
    )

    transcriptions = []
    for start, end in chunks:
        chunk = audio[start:end]
        if len(chunk) < int(0.3 * sample_rate):
            continue

        inputs = processor(
            chunk,
            sampling_rate=sample_rate,
            return_tensors="pt"
        )
        input_features = inputs.input_features.to(device)

        with torch.no_grad():
            predicted_ids = model.generate(input_features, **gen_kwargs)

        text = processor.batch_decode(
            predicted_ids,
            skip_special_tokens=True
        )[0].strip()

        if text:
            transcriptions.append(text)

    # 6. Join segments cleanly
    result = " ".join(transcriptions).strip()
    return result


if __name__ == "__main__":
    audio_path = "recordings/live.wav"
    if not os.path.exists(audio_path):
        audio_path = "recordings/test.wav"

    print(f"Transcribing {audio_path}...")
    result = transcribe(audio_path)
    print("\n================================")
    print("Transcription Result:")
    print("================================")
    print(result)
