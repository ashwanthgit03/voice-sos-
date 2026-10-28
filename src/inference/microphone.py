import os
import sys
from pathlib import Path

# Ensure project root is in sys.path for direct script execution
PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

import sounddevice as sd
import soundfile as sf
from src.inference.transcribe_audio import transcribe, DEFAULT_MODEL_PATH

SAMPLE_RATE = 16000
RECORD_SECONDS = 60
RECORDING_PATH = "recordings/live.wav"


def record_audio(
    duration=RECORD_SECONDS,
    output_path=RECORDING_PATH,
    sample_rate=SAMPLE_RATE
):
    """
    Record audio from the microphone and save it.
    Supports early stopping with Ctrl+C (retains all recorded speech).
    """
    os.makedirs(os.path.dirname(output_path), exist_ok=True)
    print("\n================================")
    print("       MICROPHONE RECORDING")
    print("================================")
    print(f"Recording for up to {duration} seconds...")
    print("Speak clearly into your microphone.")
    print("(Press Ctrl+C at any time to stop recording early and transcribe immediately)\n")

    audio_frames = []

    def callback(indata, frames, time_info, status):
        audio_frames.append(indata.copy())

    try:
        with sd.InputStream(
            samplerate=sample_rate,
            channels=1,
            dtype="float32",
            callback=callback
        ):
            sd.sleep(int(duration * 1000))
    except KeyboardInterrupt:
        print("\n[Stopped recording early by user]")

    if not audio_frames:
        print("No audio captured.")
        return

    import numpy as np
    audio = np.concatenate(audio_frames, axis=0).flatten()
    sf.write(output_path, audio, sample_rate)

    actual_duration = len(audio) / sample_rate
    print(f"Recording finished ({actual_duration:.1f}s captured).")
    print(f"Audio saved to: {output_path}")


def transcribe_audio(
    audio_path=RECORDING_PATH,
    model_path=None
):
    """
    Transcribe the recorded audio using the optimized Whisper inference engine.
    """
    if not os.path.exists(audio_path):
        raise FileNotFoundError(
            f"Recording not found: {audio_path}. Run record_audio() first."
        )

    return transcribe(
        audio_input=audio_path,
        model_path=model_path,
        num_beams=4,
        language="english",
        task="transcribe"
    )


if __name__ == "__main__":
    record_audio()
    print("\nTranscribing your recording...")
    result = transcribe_audio()
    print("\n================================")
    print("TRANSCRIPTION")
    print("================================")
    print(result)
