from datasets import DatasetDict
from transformers import WhisperProcessor
from src.data.load_dataset import load_common_voice
from src.data.preprocess import load_audio, normalize_audio
MODEL_NAME = "openai/whisper-small"
def prepare_dataset(num_train=100, num_validation=20):
    print("Loading Whisper processor...")
    processor = WhisperProcessor.from_pretrained(
        MODEL_NAME,
        language="English",
        task="transcribe"
    )
    print("Loading Common Voice datasets...")
    train_dataset = load_common_voice("train")
    validation_dataset = load_common_voice("validation")
    train_dataset = train_dataset.select(
        range(min(num_train, len(train_dataset)))
    )
    validation_dataset = validation_dataset.select(
        range(min(num_validation, len(validation_dataset)))
    )
    def prepare_sample(sample):
        audio, sample_rate = load_audio(sample["audio"])
        audio = normalize_audio(audio)
        input_features = processor.feature_extractor(
            audio,
            sampling_rate=sample_rate
        ).input_features[0]
        labels = processor.tokenizer(
            sample["text"]
        ).input_ids
        return {
            "input_features": input_features,
            "labels": labels
        }
    print("Processing training samples...")
    train_dataset = train_dataset.map(
        prepare_sample,
        remove_columns=train_dataset.column_names
    )
    print("Processing validation samples...")
    validation_dataset = validation_dataset.map(
        prepare_sample,
        remove_columns=validation_dataset.column_names
    )
    dataset = DatasetDict({
        "train": train_dataset,
        "validation": validation_dataset
    })
    return dataset
if __name__ == "__main__":
    dataset = prepare_dataset(
        num_train=100,
        num_validation=20
    )
    print("\nPrepared dataset:")
    print(dataset)
    print("\nTrain sample:")
    print(dataset["train"][0].keys())
    print("\nInput features shape:")
    print(
        len(dataset["train"][0]["input_features"]),
        len(dataset["train"][0]["input_features"][0])
    )
    print("\nNumber of labels:")
    print(len(dataset["train"][0]["labels"]))