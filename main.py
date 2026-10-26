from src.inference.microphone import record_audio, transcribe_audio
from datetime import datetime
import os
def main():
    print("\n================================")
    print("       SPEECH TO TEXT")
    print("================================")
    record_audio()
    print("\nTranscribing...")
    result = transcribe_audio()
    print("\n================================")
    print("Transcription:")
    print("================================")
    print(result)
    os.makedirs("transcriptions", exist_ok=True)
    filename = datetime.now().strftime(
        "transcriptions/transcription_%Y%m%d_%H%M%S.txt"
    )
    with open(filename, "w", encoding="utf-8") as file:
        file.write("SPEECH-TO-TEXT TRANSCRIPTION\n")
        file.write("============================\n")
        file.write(
            f"Date and time: "
            f"{datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n\n"
        )
        file.write(result)
        file.write("\n")
    print(f"\nTranscription saved to: {filename}")
if __name__ == "__main__":
    main()