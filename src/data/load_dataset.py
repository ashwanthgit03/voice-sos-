import os
import pandas as pd
from datasets import Dataset
DATA_PATH = "data/raw/commonvoice"
def load_common_voice(split="train"):
    if split == "train":
        csv_file = os.path.join(DATA_PATH, "cv-valid-train.csv")
        audio_folder = os.path.join(DATA_PATH, "cv-valid-train")
    elif split == "validation":
        csv_file = os.path.join(DATA_PATH, "cv-valid-dev.csv")
        audio_folder = os.path.join(DATA_PATH, "cv-valid-dev")
    elif split == "test":
        csv_file = os.path.join(DATA_PATH, "cv-valid-test.csv")
        audio_folder = os.path.join(DATA_PATH, "cv-valid-test")
    else:
        raise ValueError("split must be 'train', 'validation', or 'test'")
    print("Loading:", csv_file)
    df = pd.read_csv(csv_file)
    print("Number of samples:", len(df))
    print("Columns:", df.columns.tolist())
    df["audio"] = df["filename"].apply(
        lambda x: os.path.join(audio_folder, x)
    )
    df = df[["audio", "text"]]
    df = df.dropna()
    df = df[df["audio"].apply(os.path.exists)]
    print("Valid audio samples:", len(df))
    dataset = Dataset.from_pandas(
        df,
        preserve_index=False
    )
    return dataset
if __name__ == "__main__":
    dataset = load_common_voice("train")
    print("\nDataset:")
    print(dataset)
    print("\nFirst sample:")
    print(dataset[0])