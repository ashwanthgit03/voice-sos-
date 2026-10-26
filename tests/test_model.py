from src.model.load_model import load_whisper_model
def test_model_loading():
    processor, model, device = load_whisper_model()
    assert processor is not None
    assert model is not None
    assert device is not None
    assert model.num_parameters() > 0