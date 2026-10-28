import re
import string
import evaluate
import jiwer

wer_metric = evaluate.load("wer")


def normalize_text(text):
    """Normalize text by converting to lowercase and stripping punctuation/extra spaces."""
    if text is None:
        return ""
    text = text.lower()
    text = text.translate(str.maketrans("", "", string.punctuation))
    text = re.sub(r"\s+", " ", text).strip()
    return text


def calculate_wer(predictions, references):
    """Calculate Word Error Rate (WER) after normalization."""
    predictions = [normalize_text(text) for text in predictions]
    references = [normalize_text(text) for text in references]
    score = wer_metric.compute(
        predictions=predictions,
        references=references
    )
    return float(score)


def calculate_cer(predictions, references):
    """Calculate Character Error Rate (CER) after normalization."""
    predictions = [normalize_text(text) for text in predictions]
    references = [normalize_text(text) for text in references]
    return float(jiwer.cer(references, predictions))


if __name__ == "__main__":
    predictions = [
        "Hello, World!",
        "This is a test."
    ]
    references = [
        "hello world",
        "this is test"
    ]
    wer = calculate_wer(predictions, references)
    cer = calculate_cer(predictions, references)
    print(f"Normalized WER: {wer:.4f}")
    print(f"Normalized CER: {cer:.4f}")
