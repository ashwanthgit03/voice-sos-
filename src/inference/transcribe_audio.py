import torch
import librosa
import numpy as np
import noisereduce as nr
from transformers import (
    WhisperProcessor,
    WhisperForConditionalGeneration
)
MODEL_PATH = "models/trained/whisper_asr"
SAMPLE_RATE = 16000
CHUNK_SECONDS = 25
OVERLAP_SECONDS = 2
def transcribe(audio_path):
    device = "cuda" if torch.cuda.is_available() else "cpu"
    processor = WhisperProcessor.from_pretrained(MODEL_PATH)
    model = WhisperForConditionalGeneration.from_pretrained(
        MODEL_PATH
    )
    model.to(device)
    model.eval()
    audio, _ = librosa.load(
        audio_path,
        sr=SAMPLE_RATE,
        mono=True
    )
    audio = nr.reduce_noise(
        y=audio,
        sr=SAMPLE_RATE,
        stationary=True,
        prop_decrease=0.7
    )
    chunk_samples = CHUNK_SECONDS * SAMPLE_RATE
    overlap_samples = OVERLAP_SECONDS * SAMPLE_RATE
    step_samples = chunk_samples - overlap_samples
    transcriptions = []
    for start in range(0, len(audio), step_samples):
        end = min(start + chunk_samples, len(audio))
        chunk = audio[start:end]
        if len(chunk) == 0:
            continue
        input_features = processor(
            chunk.astype(np.float32),
            sampling_rate=SAMPLE_RATE,
            return_tensors="pt"
        ).input_features.to(device)
        with torch.no_grad():
            predicted_ids = model.generate(input_features)
        text = processor.batch_decode(
            predicted_ids,
            skip_special_tokens=True
        )[0].strip()
        if text:
            transcriptions.append(text)
        if end == len(audio):
            break
    return " ".join(transcriptions)
if __name__ == "__main__":
    audio_path = "recordings/test.wav"
    result = transcribe(audio_path)
    print("\nTranscription:")
    print(result)