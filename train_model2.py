"""Train a baseline model on Kavalan synthetic showcase data.
Scores describe synthetic data only, NOT real-world emergency detection accuracy.
"""
from pathlib import Path
import joblib
import pandas as pd
from sklearn.model_selection import train_test_split
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix
from sklearn.metrics import precision_score, recall_score, f1_score

DATA = Path("kavalan_synthetic_voice_features_200000.csv")
MODEL = Path("kavalan_synthetic_model.pkl")
FEATURES = ["pitch_hz","dominant_frequency_hz","zcr","spectral_centroid_hz",
            "spectral_bandwidth_hz","spectral_rolloff_hz","duration_sec","rms_energy"] + [
            f"mfcc_{i}" for i in range(1,14)]

def main():
    if not DATA.exists():
        raise FileNotFoundError("CSV not found. Run generate_synthetic_dataset.py first.")
    df = pd.read_csv(DATA)
    df = df.dropna(subset=FEATURES+["label"])
    X, y = df[FEATURES], df["label"]
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=.10, random_state=42, stratify=y)
    model = RandomForestClassifier(n_estimators=100, max_depth=18,
        min_samples_leaf=3, class_weight="balanced", n_jobs=-1, random_state=42)
    print(f"Training rows: {len(X_train):,}; test rows: {len(X_test):,}")
    model.fit(X_train, y_train)
    pred = model.predict(X_test)
    print("\\n=== SYNTHETIC SHOWCASE EVALUATION ===")
    print(f"Accuracy:  {accuracy_score(y_test,pred):.4f}")
    print(f"Distress precision: {precision_score(y_test,pred,pos_label='distress'):.4f}")
    print(f"Distress recall:    {recall_score(y_test,pred,pos_label='distress'):.4f}")
    print(f"Distress F1-score:  {f1_score(y_test,pred,pos_label='distress'):.4f}")
    print("\\nClassification report:")
    print(classification_report(y_test,pred,zero_division=0))
    print("Confusion matrix (true rows / predicted columns; distress, non_distress):")
    print(confusion_matrix(y_test,pred,labels=["distress","non_distress"]))
    joblib.dump({"model":model,"features":FEATURES,"labels":list(model.classes_)},MODEL)
    print(f"\\nSaved model: {MODEL}")
    

if __name__ == "__main__":
    main()
