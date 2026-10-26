from src.data.load_dataset import load_common_voice
from src.data.preprocess import load_audio, normalize_audio
dataset = load_common_voice("train")
sample = dataset[0]
audio_path = sample["audio"]
text = sample["text"]
print("Audio path:")
print(audio_path)
print("\nTranscript:")
print(text)
audio, sample_rate = load_audio(audio_path)
audio = normalize_audio(audio)
print("\nAudio information:")
print("Sample rate:", sample_rate)
print("Number of samples:", len(audio))
print("Duration:", len(audio) / sample_rate, "seconds")
print("Data type:", audio.dtype)
print("Minimum:", audio.min())
print("Maximum:", audio.max())