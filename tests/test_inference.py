from pathlib import Path
def test_recording_directory():
    recording_directory = Path(
        "recordings"
    )
    assert recording_directory.exists()