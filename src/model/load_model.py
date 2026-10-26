import torch
from transformers import (
    WhisperProcessor,
    WhisperForConditionalGeneration
)
MODEL_NAME = "openai/whisper-small"
def get_device():
    if torch.cuda.is_available():
        return torch.device("cuda")
    return torch.device("cpu")
def load_whisper_model():
    print("Loading Whisper model...")
    processor = WhisperProcessor.from_pretrained(
        MODEL_NAME,
        language="English",
        task="transcribe"
    )
    model = WhisperForConditionalGeneration.from_pretrained(
        MODEL_NAME
    )
    device = get_device()
    model = model.to(device)
    print(f"Model loaded successfully.")
    print(f"Device: {device}")
    return processor, model, device
if __name__ == "__main__":
    processor, model, device = load_whisper_model()
    print("\nModel information:")
    print(f"Device: {device}")
    print(f"Model parameters: {model.num_parameters():,}")