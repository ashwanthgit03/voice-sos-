
# Speech-to-Text Model Using Whisper Tiny

## 1. Project Overview

This project focuses on developing a Speech-to-Text (STT) model that converts spoken language into written text.

The model uses OpenAI Whisper Tiny, a pretrained automatic speech recognition model, and is fine-tuned using a subset of the Mozilla Common Voice English dataset.

The system accepts audio input, processes the speech, and generates a text transcription. It supports microphone-based recording and audio file transcription.

The project is designed as a standalone AI/ML model that can be tested independently and integrated into a larger application or project.

## 2. Project Objectives

- Develop a functional Speech-to-Text model.
- Use a pretrained Whisper Tiny model for speech recognition.
- Prepare and process English speech data for model training.
- Fine-tune the model using a selected subset of the Common Voice dataset.
- Convert recorded speech into written text.
- Evaluate the model using speech recognition metrics.
- Prepare the model for future integration with other AI/ML components.

## 3. Model Information

**Model Name:** Whisper Tiny

**Model Type:** Automatic Speech Recognition (ASR)

**Task:** Speech-to-Text conversion

**Dataset:** Mozilla Common Voice English

**Training Samples:** 500

**Validation Samples:** 50

**Training Duration:** 1 epoch

**Audio Sample Rate:** 16,000 Hz

**Audio Format:** Mono audio

**Execution Environment:** Python with CPU-based inference

The project uses a fine-tuned Whisper Tiny model saved locally for speech recognition. The current working model is stored in the trained model directory.

## 4. Project Structure

The project is organized into separate directories for data, model training, inference, evaluation, recordings, and supporting files.

### Root Directory

**main.py**

The main entry point of the project. It starts the terminal-based speech recognition workflow, records audio through the microphone, sends the recording for transcription, and displays the recognized text.

**requirements.txt**

Contains the Python dependencies required to install and run the project, including libraries for audio processing, dataset handling, model training, and speech recognition.

**README.md**

Provides documentation about the project, its structure, model details, workflow, dependencies, and usage.

**.gitignore**

Specifies files and folders that should not be tracked by Git, such as virtual environments, temporary files, logs, and other generated or sensitive files.

### data/

Stores the datasets used by the project.

**data/raw/**

Contains the original dataset files before preprocessing.

**data/raw/commonvoice/**

Contains the downloaded Mozilla Common Voice dataset, including audio recordings, transcript metadata, and dataset documentation.

**cv-valid-train/**

Contains audio recordings selected for training the model.

**cv-valid-dev/**

Contains audio recordings used for validation during model development.

**cv-valid-test/**

Contains audio recordings reserved for testing and evaluating model performance.

**CSV metadata files**

The Common Voice CSV files contain information about audio recordings and their corresponding text transcripts. The data-loading module uses the relevant metadata files to locate audio recordings and retrieve their transcripts.

**cv-invalid/ and cv-other-* directories**

Contain additional dataset categories that are not part of the initial training workflow. They can be used for future experiments if required.

**data/processed/**

Intended to store audio or dataset files after preprocessing, if processed data is saved separately.

**data/splits/**

Intended to store dataset split information when separate split files are needed.

### models/

Stores pretrained and trained model files.

**models/base/**

Intended for storing original pretrained model files when local copies are required.

**models/trained/**

Contains models that have been fine-tuned or trained for the project.

**models/trained/whisper_asr/**

Contains the current working Whisper Tiny model trained using 500 training samples and 50 validation samples. It includes the saved model weights and processor configuration needed for inference.

**models/trained/whisper_tiny_1000/**

Contains an experimental model trained using a larger subset of the dataset, if retained. It is not the primary model used by the current application workflow.

### src/

Contains the source modules responsible for dataset preparation, model training, evaluation, and inference.

**src/__init__.py**

Marks the source directory as a Python package and supports package-based imports.

### src/data/

Contains modules responsible for loading and preparing the dataset.

**src/data/__init__.py**

Marks the data directory as a Python package.

**src/data/load_dataset.py**

Loads the Common Voice metadata and identifies the audio files and corresponding transcripts for the selected dataset split.

**src/data/preprocess.py**

Loads audio recordings, converts them to mono audio at 16 kHz, and provides audio normalization functionality.

**src/data/prepare_dataset.py**

Combines dataset loading and audio preprocessing. It prepares audio features and text labels in the format required by Whisper during training.

### src/model/

Contains the model training module.

**src/model/__init__.py**

Marks the model directory as a Python package.

**src/model/load_model.py**

Intended for loading the trained model for reuse, if implemented in the project.

**src/model/train.py**

Loads the pretrained Whisper Tiny model, prepares the training and validation datasets, configures the training process, fine-tunes the model, and saves the trained model and processor.

### src/evaluation/

Contains modules for measuring model performance.

**src/evaluation/__init__.py**

Marks the evaluation directory as a Python package.

**src/evaluation/evaluate.py**

Loads the trained model, generates transcriptions for test audio, compares the predicted text with reference transcripts, and calculates the Word Error Rate.

**src/evaluation/metrics.py**

Intended for storing reusable evaluation metrics and related calculations, if implemented separately.

### src/inference/

Contains modules used to run speech recognition with the trained model.

**src/inference/__init__.py**

Marks the inference directory as a Python package.

**src/inference/microphone.py**

Records audio from the microphone and passes it to the trained model for transcription. It is used by the main terminal-based workflow.

**src/inference/record_audio.py**

Records microphone input and saves it as an audio file for later use. It is useful for creating recordings for testing, if retained.

**src/inference/transcribe_audio.py**

Loads the trained model and converts a supplied audio file into written text.

**src/inference/compare_models.py**

An optional experimental script for comparing the outputs of different trained models. It is not required for the main speech recognition workflow.

### src/utils/

Contains general-purpose helper functions.

**src/utils/__init__.py**

Marks the utilities directory as a Python package.

**src/utils/audio_utils.py**

Intended for reusable audio-processing helper functions, if implemented separately.

### notebooks/

Contains Jupyter notebooks for interactive experimentation and analysis, if retained.

**01_data_exploration.ipynb**

Intended for exploring the dataset, inspecting transcript metadata, and understanding the available audio samples.

**02_training.ipynb**

Intended for experimenting with the model training workflow.

**03_evaluation.ipynb**

Intended for analyzing transcription outputs and evaluation results.

### outputs/

Stores generated outputs from model development and evaluation.

**outputs/checkpoints/**

Intended for storing intermediate training checkpoints.

**outputs/logs/**

Intended for storing training logs and experiment information.

**outputs/predictions/**

Intended for storing predicted transcriptions and evaluation outputs.

### recordings/

Stores microphone recordings generated during testing.

**test.wav**

An example recording used to test audio transcription, if retained.

**live.wav**

An example recording produced by the live microphone workflow, if retained.

### tests/

Intended to contain tests that verify the behavior of dataset loading, preprocessing, model inference, and other project components.

## 5. How the System Works

The Speech-to-Text workflow consists of the following stages:

1. Audio Input: Speech is captured through the microphone or supplied as an audio file.
2. Audio Preprocessing: The recording is converted to mono audio at a sample rate of 16 kHz.
3. Feature Extraction: The Whisper processor converts the audio into input features suitable for the model.
4. Speech Recognition: The fine-tuned Whisper Tiny model processes the audio features and generates text tokens.
5. Text Decoding: The generated tokens are converted into readable text.
6. Output: The recognized transcription is displayed in the terminal or returned by the transcription module for use by other components.

## 6. Dataset Description

The model uses the Mozilla Common Voice English dataset obtained from Kaggle.

The dataset contains speech recordings and their associated text transcripts. These pairs are used to teach the model to associate spoken audio with written language.

A subset of 500 training samples and 50 validation samples was used for the current working model.

The training set is used to fine-tune the model, while the validation set is used to monitor its performance during training. A separate test set can be used to evaluate transcription quality.

## 7. Model Training

The project uses transfer learning rather than training a speech recognition model from scratch.

A pretrained Whisper Tiny model is loaded and fine-tuned using the selected Common Voice samples. The trained model and its associated processor are then saved locally for future inference.

The current working configuration uses one training epoch and a CPU-based environment.

The model stored in the trained model directory is used for inference, so retraining is not required every time the system runs.

## 8. Model Evaluation

The project evaluates transcription quality by comparing predicted text with the correct reference transcripts.

Word Error Rate (WER) is used as an evaluation metric. It measures the number of word-level substitutions, deletions, and insertions relative to the reference text.

A lower WER generally indicates better transcription accuracy. Results depend on the test samples, the speaker, pronunciation, recording quality, and background noise.

The current model previously achieved a WER of approximately 0.1684 on a small evaluation of 10 test samples. This result is preliminary and should not be treated as a comprehensive measure of real-world accuracy.

## 9. How the Model Is Used

The model can be used through the terminal-based microphone workflow or through the audio transcription module.

The microphone workflow records speech and sends the recording to the locally saved model. The transcription module can also process an existing audio file.

The trained model can later be integrated into other AI/ML systems that require speech recognition, such as voice assistants, voice-controlled applications, meeting transcription tools, or audio-based information systems.

The model is a speech recognition component and does not independently provide speech generation or text-to-speech functionality.

## 10. Technologies Used

- Python
- PyTorch
- Hugging Face Transformers
- Hugging Face Datasets
- Whisper Tiny
- Mozilla Common Voice
- Librosa
- NumPy
- Pandas
- SoundDevice
- SoundFile
- Jiwer

## 11. Current Project Status

The project has a working Whisper Tiny model fine-tuned on a small subset of Mozilla Common Voice.

Microphone recording and speech transcription have been tested successfully through the terminal-based workflow. Audio file transcription and model evaluation are also supported by the corresponding modules.

The current model is intended as a functional prototype. Its accuracy and robustness can be improved through broader evaluation, additional suitable training data, and testing across different speakers and recording conditions.

## 12. Limitations and Future Improvements

The current model was trained on a small subset of the available dataset, and its accuracy may vary with accents, speaking speed, background noise, and microphone quality.

Possible future improvements include training with a larger and more diverse dataset, evaluating on more test samples, testing with multiple speakers, improving audio preprocessing, and optimizing inference performance.

The model can also be integrated into a larger AI/ML application as an independent speech recognition module.

## 13. Conclusion

This project demonstrates the development and testing of a Speech-to-Text model using Whisper Tiny and Mozilla Common Voice.

It covers dataset loading, audio preprocessing, model fine-tuning, evaluation, and microphone-based inference.

The resulting model provides a foundation for further speech recognition development and future integration into larger AI/ML projects.