import torch
import librosa
from transformers import (
    WhisperProcessor,
    WhisperForConditionalGeneration
)
from src.evaluation.metrics import calculate_wer
from src.data.load_dataset import load_common_voice
MODEL_PATH = "models/trained/whisper_asr"
def main():
    device = "cuda" if torch.cuda.is_available() else "cpu"
    print("Device:", device)
    print("Loading trained Whisper model...")
    processor = WhisperProcessor.from_pretrained(
        MODEL_PATH
    )
    model = WhisperForConditionalGeneration.from_pretrained(
        MODEL_PATH
    )
    model.to(device)
    model.eval()
    print("Loading test dataset...")
    test_dataset = load_common_voice("test")
    test_dataset = test_dataset.select(
        range(min(100, len(test_dataset)))
    )
    predictions = []
    references = []
    for i, sample in enumerate(test_dataset):
        print(f"\nProcessing sample {i + 1}/{len(test_dataset)}")
        audio_path = sample["audio"]
        reference = sample["text"]
        audio, sample_rate = librosa.load(
            audio_path,
            sr=16000,
            mono=True
        )
        input_features = processor(
            audio,
            sampling_rate=sample_rate,
            return_tensors="pt"
        ).input_features
        input_features = input_features.to(device)
        with torch.no_grad():
            predicted_ids = model.generate(
                input_features
            )
        prediction = processor.batch_decode(
            predicted_ids,
            skip_special_tokens=True
        )[0]
        predictions.append(prediction)
        references.append(reference)
        print("Reference :", reference)
        print("Prediction:", prediction)
    score = calculate_wer(
        predictions,
        references
    )
    print("\n==============================")
    print("Evaluation Results")
    print("==============================")
    print("WER:", score)
    print("Samples evaluated:", len(test_dataset))
    print("Normalized evaluation: lowercase + punctuation removed")
if __name__ == "__main__":
    main()