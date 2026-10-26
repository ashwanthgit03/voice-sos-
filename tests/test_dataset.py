from src.data.load_dataset import load_common_voice
def test_dataset_loading():
    dataset = load_common_voice(
        split="train"
    )
    assert dataset is not None
    assert len(dataset) > 0
    assert "audio" in dataset.column_names
    assert "text" in dataset.column_names