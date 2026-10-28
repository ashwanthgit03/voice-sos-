from pathlib import Path
PROJECT_ROOT = Path(__file__).resolve().parent.parent
DATA_DIR = PROJECT_ROOT / "data"
RAW_DATA_DIR = DATA_DIR / "raw"
PROCESSED_DATA_DIR = DATA_DIR / "processed"
SPLITS_DIR = DATA_DIR / "splits"
MODELS_DIR = PROJECT_ROOT / "models"
BASE_MODEL_DIR = MODELS_DIR / "base"
TRAINED_MODEL_DIR = MODELS_DIR / "trained"
OUTPUT_DIR = PROJECT_ROOT / "outputs"
CHECKPOINT_DIR = OUTPUT_DIR / "checkpoints"
LOG_DIR = OUTPUT_DIR / "logs"
PREDICTION_DIR = OUTPUT_DIR / "predictions"
RECORDINGS_DIR = PROJECT_ROOT / "recordings"
MODEL_NAME = "openai/whisper-small"
LANGUAGE = "English"
TASK = "transcribe"
SAMPLING_RATE = 16000
ACTIVE_MODEL = "openai/whisper-small"
BEAM_SIZE = 4
USE_NOISE_REDUCTION = False
SILENCE_TOP_DB = 30
MAX_CHUNK_SECONDS = 29
TRAIN_SAMPLES = 10000
VALIDATION_SAMPLES = 1000
TEST_SAMPLES = 1000
BATCH_SIZE = 8
EVAL_BATCH_SIZE = 8
GRADIENT_ACCUMULATION_STEPS = 2
LEARNING_RATE = 1e-5
NUM_EPOCHS = 3
WARMUP_STEPS = 500
FP16 = True
SAVE_STEPS = 500
EVAL_STEPS = 500
LOGGING_STEPS = 25
MAX_AUDIO_LENGTH_SECONDS = 30
MIN_AUDIO_LENGTH_SECONDS = 0.5
DIRECTORIES = [
    DATA_DIR,
    RAW_DATA_DIR,
    PROCESSED_DATA_DIR,
    SPLITS_DIR,
    MODELS_DIR,
    BASE_MODEL_DIR,
    TRAINED_MODEL_DIR,
    OUTPUT_DIR,
    CHECKPOINT_DIR,
    LOG_DIR,
    PREDICTION_DIR,
    RECORDINGS_DIR,
]
def create_directories():
    for directory in DIRECTORIES:
        directory.mkdir(parents=True, exist_ok=True)
if __name__ == "__main__":
    create_directories()
    print("Project directories created successfully.")