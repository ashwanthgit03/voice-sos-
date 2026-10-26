import os
import numpy as np
import sounddevice as sd
import soundfile as sf
import librosa
import noisereduce as nr
import torch
from transformers import (
    WhisperProcessor,
    WhisperForConditionalGeneration
)
MODEL_PATH = "models/trained/whisper_asr"
SAMPLE_RATE = 16000
RECORD_SECONDS = 60
CHUNK_SECONDS = 25
OVERLAP_SECONDS = 2
RECORDING_PATH = "recordings/live.wav"
def record_audio():
    """Record audio from the microphone and save it."""
    os.makedirs("recordings", exist_ok=True)
    print("\n================================")
    print("       MICROPHONE RECORDING")
    print("================================")
    print(f"Recording for {RECORD_SECONDS} seconds...")
    print("Speak clearly and keep the microphone close.")
    print("Recording starts now!\n")
    audio = sd.rec(
        int(RECORD_SECONDS * SAMPLE_RATE),
        samplerate=SAMPLE_RATE,
        channels=1,
        dtype="float32"
    )
    sd.wait()
    audio = audio.flatten()
    sf.write(
        RECORDING_PATH,
        audio,
        SAMPLE_RATE
    )
    print("Recording completed.")
    print(f"Audio saved to: {RECORDING_PATH}")
def transcribe_audio():
    """Reduce background noise and transcribe the recording."""
    device = "cuda" if torch.cuda.is_available() else "cpu"
    print("\nUsing device:", device)
    if not os.path.exists(RECORDING_PATH):
        raise FileNotFoundError(
            f"Recording not found: {RECORDING_PATH}. "
            "Run record_audio() first."
        )
    print("Loading trained Whisper model...")
    processor = WhisperProcessor.from_pretrained(MODEL_PATH)
    model = WhisperForConditionalGeneration.from_pretrained(
        MODEL_PATH
    )
    model.to(device)
    model.eval()
    print("Loading recorded audio...")
    audio, _ = librosa.load(
        RECORDING_PATH,
        sr=SAMPLE_RATE,
        mono=True
    )
    if len(audio) == 0:
        print("The recording is empty.")
        return ""
    print("Reducing background noise...")
    audio = nr.reduce_noise(
        y=audio,
        sr=SAMPLE_RATE,
        stationary=False,
        prop_decrease=0.7
    )
    chunk_samples = CHUNK_SECONDS * SAMPLE_RATE
    overlap_samples = OVERLAP_SECONDS * SAMPLE_RATE
    step_samples = chunk_samples - overlap_samples
    transcriptions = []
    print("Transcribing audio...\n")
    for start in range(0, len(audio), step_samples):
        end = min(start + chunk_samples, len(audio))
        chunk = audio[start:end]
        if len(chunk) == 0:
            continue
        if np.max(np.abs(chunk)) < 0.005:
            print("Skipping a nearly silent audio segment.")
            continue
        print(
            f"Processing {start / SAMPLE_RATE:.1f} "
            f"to {end / SAMPLE_RATE:.1f} seconds..."
        )
        inputs = processor(
            chunk,
            sampling_rate=SAMPLE_RATE,
            return_tensors="pt"
        )
        input_features = inputs.input_features.to(device)
        with torch.no_grad():
            predicted_ids = model.generate(
                input_features
            )
        text = processor.batch_decode(
            predicted_ids,
            skip_special_tokens=True
        )[0].strip()
        if text:
            transcriptions.append(text)
    result = " ".join(transcriptions)
    return result
if __name__ == "__main__":
    record_audio()
    print("\nTranscribing your recording...")
    transcription = transcribe_audio()
    print("\n================================")
    print("TRANSCRIPTION")
    print("================================")
    print(transcription)