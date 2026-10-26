import os
import torch
from transformers import (
    WhisperProcessor,
    WhisperForConditionalGeneration,
    Seq2SeqTrainingArguments,
    Seq2SeqTrainer,
)
from src.data.prepare_dataset import prepare_dataset
MODEL_NAME = "openai/whisper-small"
OUTPUT_DIR = "models/trained/whisper_asr"
def main():
    print("Loading Whisper processor...")
    processor = WhisperProcessor.from_pretrained(
        MODEL_NAME,
        language="English",
        task="transcribe"
    )
    print("Loading Whisper model...")
    model = WhisperForConditionalGeneration.from_pretrained(
        MODEL_NAME
    )
    model.generation_config.language = "en"
    model.generation_config.task = "transcribe"
    model.generation_config.forced_decoder_ids = None
    print("Preparing dataset...")
    dataset = prepare_dataset(
        num_train=3000,
        num_validation=300
    )
    print("\nDataset ready:")
    print(dataset)
    def data_collator(features):
        input_features = [
            {"input_features": feature["input_features"]}
            for feature in features
        ]
        batch = processor.feature_extractor.pad(
            input_features,
            return_tensors="pt"
        )
        label_features = [
            {"input_ids": feature["labels"]}
            for feature in features
        ]
        labels_batch = processor.tokenizer.pad(
            label_features,
            return_tensors="pt"
        )
        labels = labels_batch["input_ids"].masked_fill(
            labels_batch.attention_mask.ne(1),
            -100
        )
        if (labels[:, 0] == processor.tokenizer.bos_token_id).all():
            labels = labels[:, 1:]
        batch["labels"] = labels
        return batch
    use_fp16 = torch.cuda.is_available()
    print("\nCUDA available:", torch.cuda.is_available())
    if torch.cuda.is_available():
        print("GPU:", torch.cuda.get_device_name(0))
    training_args = Seq2SeqTrainingArguments(
        output_dir=OUTPUT_DIR,
        per_device_train_batch_size=4,
        per_device_eval_batch_size=4,
        gradient_accumulation_steps=2,
        learning_rate=1e-5,
        num_train_epochs=3,
        warmup_steps=100,
        fp16=use_fp16,
        optim="adamw_torch_fused",
        gradient_checkpointing=True,
        dataloader_num_workers=0,
        logging_steps=10,
        eval_strategy="steps",
        eval_steps=200,
        save_strategy="steps",
        save_steps=200,
        save_total_limit=2,
        predict_with_generate=True,
        report_to="none",
        remove_unused_columns=False,
    )
    trainer = Seq2SeqTrainer(
        args=training_args,
        model=model,
        train_dataset=dataset["train"],
        eval_dataset=dataset["validation"],
        data_collator=data_collator,
        processing_class=processor,
    )
    print("\nStarting test training...")
    trainer.train()
    print("\nSaving model...")
    trainer.save_model(OUTPUT_DIR)
    processor.save_pretrained(OUTPUT_DIR)
    print("\nTraining completed successfully.")
    print("Model saved to:", OUTPUT_DIR)
if __name__ == "__main__":
    main()