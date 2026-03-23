from tensorflow import keras
import os

# Load the Keras 3 model from the folder
old_path = r"C:\Users\Admin\OneDrive\Documents\backend\fastapi\lstm_model_folder"
model = keras.models.load_model(old_path)

print("✅ Original model loaded successfully!")
print("Model summary:")
model.summary()

# Save as single .keras file (optional, for cleaner FastAPI)
new_path = r"C:\Users\Admin\OneDrive\Documents\backend\fastapi\lstm_model.keras"
model.save(new_path)
print("✅ Saved single .keras file to:", new_path)
 