<<<<<<< HEAD

import os
import sys
from pathlib import Path

from dotenv import load_dotenv
from google import genai

load_dotenv()

API_KEY = os.getenv("GEMINI_API_KEY")

if not API_KEY:
    print("Error: GEMINI_API_KEY not found in .env")
    sys.exit(1)

client = genai.Client(api_key=API_KEY)

AUDIO_PATH = Path("distress.wav")

CATEGORIES = [
    "distress_scream",
    "fear_scream",
    "pain_scream",
    "excitement_scream",
    "playful_scream",
    "surprise_scream",
    "laughter",
    "giggle",
    "crying",
    "gasp",
    "normal_speech",
    "smiling_speech",
    "background_sound",
    "uncertain"
]


def predict_audio(audio_path):
    if not audio_path.exists():
        print(f"Error: Audio file not found: {audio_path}")
        return

    print("\nUploading audio and analyzing with Gemini...")

    try:
        uploaded_file = client.files.upload(file=str(audio_path))

        prompt = f"""
Analyze the provided audio recording for sound classification.

Choose the best matching label from this list:
{", ".join(CATEGORIES)}

Instructions:
- Listen to the actual audio, not just the filename.
- Distinguish distress or fear screams from happy,
  playful, or excited screams.
- Use "uncertain" if the audio is ambiguous or unclear.
- Do not invent details that cannot be heard.
- This is an audio classification estimate, not a
  guaranteed determination of a person's emotional state.

Return exactly this format:
Predicted type: <one label from the list>
Reason: <one short explanation>
Distress evidence: <present, absent, or uncertain>
"""

        response = client.models.generate_content(
        model="gemini-3.6-flash",
        contents=[uploaded_file, prompt]
    )

        print("\nKavalan - Voice SOS System")
        print("-------------------------")
        print(response.text or "No response received.")

    except Exception as error:
        print("\nPrediction failed.")
        print("Error:", error)


if __name__ == "__main__":
    print("Kavalan - Voice SOS System")
    predict_audio(AUDIO_PATH)
=======
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
>>>>>>> 41c78ff88aadf8692123af79b1c971a03bb34ba4
