import os
TRANSCRIPTIONS_DIR = "transcriptions"
def main():
    if not os.path.isdir(TRANSCRIPTIONS_DIR):
        print("No transcriptions folder found.")
        return
    files = sorted(
        [
            name
            for name in os.listdir(TRANSCRIPTIONS_DIR)
            if name.endswith(".txt")
        ],
        reverse=True
    )
    if not files:
        print("No saved transcriptions found.")
        return
    print("\n=== Saved Transcriptions ===")
    for index, filename in enumerate(files, start=1):
        print(f"{index}. {filename}")
    choice = input("\nEnter a file number (or 0 to exit): ")
    if not choice.isdigit():
        print("Please enter a valid number.")
        return
    number = int(choice)
    if number == 0:
        return
    if number < 1 or number > len(files):
        print("Invalid selection.")
        return
    path = os.path.join(
        TRANSCRIPTIONS_DIR,
        files[number - 1]
    )
    with open(path, "r", encoding="utf-8") as file:
        text = file.read()
    print("\n=== Transcription ===")
    print(text)
if __name__ == "__main__":
    main()