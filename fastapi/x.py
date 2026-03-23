import joblib
import numpy as np

model    = joblib.load('xgb_model.pkl')
encoders = joblib.load('encoders.pkl')

cluster_enc  = int(encoders['cluster'].transform(['samosa'])[0])
meal_enc     = int(encoders['meal_period'].transform(['allday'])[0])
weather_enc  = int(encoders['weather_condition'].transform([0])[0])

base = np.array([[
    9, 1, cluster_enc, meal_enc, 12.0, 5.0,
    3, 0, 11, 3, 0, 0, 0, 0, 30, 1,
    weather_enc, 34.0, 87, 89, 90, 89, 89.0, 88.0
]])

# Try 0-based day_of_week (0=Mon, 4=Fri, 6=Sun)
base[0][10] = 0  # reset exam week
base[0][11] = 0  # reset holiday

base[0][6] = 4   # Friday (0-based)
print(f"Friday (0-based):    {model.predict(base)[0]:.1f}")

base[0][6] = 0   # Monday (0-based)
print(f"Monday (0-based):    {model.predict(base)[0]:.1f}")

base[0][6] = 2   # Wednesday (0-based)
print(f"Wednesday (0-based): {model.predict(base)[0]:.1f}")

base[0][10] = 1  # exam week
print(f"Exam week (0-based): {model.predict(base)[0]:.1f}")

base[0][10] = 0
base[0][11] = 1  # holiday
print(f"Holiday (0-based):   {model.predict(base)[0]:.1f}")
