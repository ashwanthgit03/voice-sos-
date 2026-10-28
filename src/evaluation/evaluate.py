import os
import sys
from pathlib import Path

PROJECT_ROOT = Path(__file__).resolve().parent.parent.parent
if str(PROJECT_ROOT) not in sys.path:
    sys.path.insert(0, str(PROJECT_ROOT))

import torch
import librosa
from transformers import (
    WhisperProcessor,
    WhisperForConditionalGeneration
)
from src.evaluation.metrics import calculate_wer, calculate_cer, normalize_text
from src.data.load_dataset import load_common_voice
from src.utils.audio_utils import normalize_audio

DEFAULT_MODEL_PATH = "models/trained/whisper_asr"
FALLBACK_MODEL_PATH = "openai/whisper-small"


def evaluate_model(
    model_path=None,
    split="test",
    num_samples=50,
    num_beams=4
):
    if model_path is None:
        model_path = DEFAULT_MODEL_PATH if os.path.exists(DEFAULT_MODEL_PATH) else FALLBACK_MODEL_PATH

    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    print(f"Evaluating model: {model_path} on {device}")

    processor = WhisperProcessor.from_pretrained(
        model_path,
        language="English",
        task="transcribe"
    )
    model = WhisperForConditionalGeneration.from_pretrained(model_path).to(device)
    model.eval()

    print(f"Loading {split} dataset...")
    dataset = load_common_voice(split)
    eval_dataset = dataset.select(range(min(num_samples, len(dataset))))

    forced_ids = processor.get_decoder_prompt_ids(language="english", task="transcribe")
    gen_kwargs = {
        "forced_decoder_ids": forced_ids,
        "num_beams": num_beams,
        "temperature": 0.0,
    }

    predictions = []
    references = []

    print(f"\nEvaluating {len(eval_dataset)} samples...")
    for i, sample in enumerate(eval_dataset):
        audio_path = sample["audio"]
        reference = sample["text"]

        audio, sample_rate = librosa.load(audio_path, sr=16000, mono=True)
        audio = normalize_audio(audio)

        input_features = processor(
            audio,
            sampling_rate=16000,
            return_tensors="pt"
        ).input_features.to(device)

        with torch.no_grad():
            predicted_ids = model.generate(input_features, **gen_kwargs)

        prediction = processor.batch_decode(
            predicted_ids,
            skip_special_tokens=True
        )[0].strip()

        predictions.append(prediction)
        references.append(reference)

        if i < 5 or (i + 1) % 10 == 0:
            print(f"\n[Sample {i + 1}/{len(eval_dataset)}]")
            print(f"  Ref : {reference}")
            print(f"  Pred: {prediction}")

    wer_score = calculate_wer(predictions, references)
    cer_score = calculate_cer(predictions, references)

    print("\n==============================")
    print("      EVALUATION RESULTS      ")
    print("==============================")
    print(f"Model: {model_path}")
    print(f"Samples evaluated: {len(eval_dataset)}")
    print(f"Word Error Rate (WER)     : {wer_score:.4f} ({wer_score * 100:.2f}%)")
    print(f"Character Error Rate (CER): {cer_score:.4f} ({cer_score * 100:.2f}%)")
    print(f"Accuracy (1 - WER)        : {(1.0 - wer_score) * 100:.2f}%")

    return {
        "model": model_path,
        "wer": wer_score,
        "cer": cer_score,
        "accuracy": 1.0 - wer_score,
        "samples": len(eval_dataset)
    }


def main():
    evaluate_model(num_samples=20)


if __name__ == "__main__":
    main()
