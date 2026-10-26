import re
import string
import evaluate
wer_metric = evaluate.load("wer")
def normalize_text(text):
    # Ignore uppercase vs lowercase
    text = text.lower()
    # Remove punctuation
    text = text.translate(
        str.maketrans("", "", string.punctuation)
    )
    # Normalize extra spaces
    text = re.sub(r"\s+", " ", text).strip()
    return text
def calculate_wer(predictions, references):
    predictions = [
        normalize_text(text)
        for text in predictions
    ]
    references = [
        normalize_text(text)
        for text in references
    ]
    score = wer_metric.compute(
        predictions=predictions,
        references=references
    )
    return score
if __name__ == "__main__":
    predictions = [
        "Hello, World!",
        "This is a test."
    ]
    references = [
        "hello world",
        "this is test"
    ]
    score = calculate_wer(
        predictions,
        references
    )
    print(f"Normalized WER: {score:.4f}")