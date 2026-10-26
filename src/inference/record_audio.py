import sounddevice as sd
import soundfile as sf
import os
SAMPLE_RATE = 16000
DURATION = 60
OUTPUT_PATH = "recordings/test.wav"
def record_audio():
    os.makedirs("recordings", exist_ok=True)
    print("================================")
    print("Voice Recording")
    print("================================")
    print()
    print("Get ready...")
    print("Recording will start now.")
    print()
    print("Speak clearly for 60 seconds.")
    print()
    recording = sd.rec(
        int(DURATION * SAMPLE_RATE),
        samplerate=SAMPLE_RATE,
        channels=1,
        dtype="float32"
    )
    sd.wait()
    sf.write(
        OUTPUT_PATH,
        recording,
        SAMPLE_RATE
    )
    print()
    print("Recording completed!")
    print("Saved to:", OUTPUT_PATH)
if __name__ == "__main__":
    record_audio()