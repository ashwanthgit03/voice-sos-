
import pandas as pd
import joblib

from sklearn.model_selection import train_test_split
from sklearn.ensemble import RandomForestClassifier
from sklearn.metrics import (
    accuracy_score,
    classification_report,
    confusion_matrix
)

# 1. Load dataset
DATASET_PATH = "voice_sos_expanded_synthetic_audio_features.csv"

data = pd.read_csv(DATASET_PATH)

print("Dataset shape:", data.shape)
print("Dataset columns:", data.columns.tolist())

# 2. Check required label column
if "label" not in data.columns:
    raise ValueError("Dataset must contain a 'label' column")

# 3. Remove rows with missing values
data = data.dropna().copy()

# 4. Prepare features and labels
X = data.drop(columns=["label"])
y = data["label"].astype(str).str.strip()

# Ensure features are numeric
X = X.apply(pd.to_numeric, errors="coerce")

# Remove rows with invalid feature values
valid_rows = X.notna().all(axis=1)
X = X.loc[valid_rows]
y = y.loc[valid_rows]

if X.empty:
    raise ValueError("No valid feature rows found in dataset")

# Save the exact feature column names
feature_columns = X.columns.tolist()

print("\nNumber of samples:", len(X))
print("Number of features:", len(feature_columns))
print("\nDataset labels:")
print(y.value_counts().sort_index())

# 5. Split data for evaluation
X_train, X_test, y_train, y_test = train_test_split(
    X,
    y,
    test_size=0.2,
    random_state=42,
    stratify=y
)

# 6. Create and train model
model = RandomForestClassifier(
    n_estimators=300,
    random_state=42,
    class_weight="balanced",
    min_samples_leaf=2,
    n_jobs=-1
)

model.fit(X_train, y_train)

# 7. Evaluate model on unseen test split
predictions = model.predict(X_test)

print("\nModel Evaluation")
print("Accuracy:", accuracy_score(y_test, predictions))

print("\nClassification Report:")
print(classification_report(
    y_test,
    predictions,
    zero_division=0
))

print("\nConfusion Matrix:")
print(confusion_matrix(
    y_test,
    predictions,
    labels=model.classes_
))

print("\nLabels used during evaluation:")
print(sorted(model.classes_))

# 8. Train final model using all valid rows
model.fit(X, y)

# 9. Save model and feature names together
joblib.dump(
    {
        "model": model,
        "columns": feature_columns,
        "labels": sorted(y.unique())
    },
    "scream_model.pkl"
)

print("\nFinal model labels:")
print(sorted(model.classes_))

print("\nModel saved as scream_model.pkl")
