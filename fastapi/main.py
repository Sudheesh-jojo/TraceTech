from fastapi import FastAPI
from pydantic import BaseModel
from typing import List
import joblib
import numpy as np
import pandas as pd
import os
import logging

try:
    from prophet import Prophet
    PROPHET_AVAILABLE = True
    print("Prophet loaded")
except ImportError:
    PROPHET_AVAILABLE = False
    print("Prophet not available")

app = FastAPI(title="TraceTech ML Service", version="2.0.0")

BASE = os.path.dirname(os.path.abspath(__file__))

xgb_model = joblib.load(os.path.join(BASE, "xgb_model.pkl"))
encoders  = joblib.load(os.path.join(BASE, "encoders.pkl"))

try:
    import keras
    lstm_model = keras.saving.load_model(os.path.join(BASE, "lstm_model.keras.zip"))
    LSTM_AVAILABLE = True
    print("LSTM loaded")
except Exception as e:
    lstm_model = None
    LSTM_AVAILABLE = False
    print(f"LSTM not loaded: {e}")

print("XGBoost loaded")


class PredictRequest(BaseModel):
    date: str
    item_id: int
    item_name: str
    day_of_week: int
    is_exam_week: int
    is_holiday: int
    weather_condition: int
    temperature: float
    week_of_semester: int
    prev_1day_qty: int
    prev_7day_qty: int
    rolling_7day_avg: float
    stall_id: int = 1
    cluster: str = "samosa"
    meal_period: str = "allday"
    selling_price: float = 12.0
    ingredient_cost_per_unit: float = 5.0
    prev_2day_qty: int = 0
    prev_3day_qty: int = 0
    rolling_14day_avg: float = 0.0
    month: int = 3
    is_weekend: int = 0
    is_first_week: int = 0
    is_last_week: int = 0
    days_until_exam: int = 30
    is_college_open: int = 1


class PredictResponse(BaseModel):
    item_id: int
    predicted_qty: int
    range_low: int
    range_high: int
    top_reasons: List[str]
    anomaly_flag: bool
    confidence: str


def encode_feature(name, value):
    try:
        return int(encoders[name].transform([value])[0])
    except:
        return 0


def predict_xgb(req: PredictRequest) -> float:
    try:
        features = np.array([[
            req.item_id,
            req.stall_id,
            encode_feature("cluster", req.cluster),
            encode_feature("meal_period", req.meal_period),
            req.selling_price,
            req.ingredient_cost_per_unit,
            req.day_of_week,
            req.is_weekend,
            req.week_of_semester,
            req.month,
            req.is_exam_week,
            req.is_holiday,
            req.is_first_week,
            req.is_last_week,
            req.days_until_exam,
            req.is_college_open,
            encode_feature("weather_condition", req.weather_condition),
            req.temperature,
            req.prev_1day_qty,
            req.prev_2day_qty,
            req.prev_3day_qty,
            req.prev_7day_qty,
            req.rolling_7day_avg,
            req.rolling_14day_avg,
        ]])
        pred = xgb_model.predict(features)[0]
        return float(max(1, pred))
    except Exception as e:
        print(f"XGBoost error: {e}")
        return float(req.rolling_7day_avg) if req.rolling_7day_avg > 0 else float(req.prev_7day_qty)


def predict_lstm(req: PredictRequest) -> float:
    if not LSTM_AVAILABLE:
        return predict_xgb(req)
    try:
        seq = [
            req.prev_7day_qty,
            req.prev_7day_qty,
            req.prev_7day_qty,
            req.prev_3day_qty,
            req.prev_2day_qty,
            req.prev_1day_qty,
            int(req.rolling_7day_avg),
        ]
        x = np.array(seq, dtype=np.float32).reshape(1, 7, 1)
        pred = lstm_model.predict(x, verbose=0)[0][0]
        return float(max(1, pred))
    except Exception as e:
        print(f"LSTM error: {e}")
        return predict_xgb(req)


def predict_prophet(req: PredictRequest) -> float:
    """Fit Prophet on the fly using historical data reconstructed from request fields."""
    if not PROPHET_AVAILABLE:
        raise RuntimeError("Prophet not installed")

    today = pd.Timestamp(req.date)
    dates = [today - pd.Timedelta(days=i) for i in range(14, 0, -1)]

    # Use rolling averages to reconstruct approximate history
    qtys = []
    for i, d in enumerate(dates):
        if i < 7:
            qtys.append(req.rolling_14day_avg if req.rolling_14day_avg > 0 else req.rolling_7day_avg)
        else:
            qtys.append(req.rolling_7day_avg if req.rolling_7day_avg > 0 else float(req.prev_7day_qty))

    # Override with actual known values
    qtys[-1] = float(req.prev_1day_qty)
    qtys[-2] = float(req.prev_2day_qty) if req.prev_2day_qty > 0 else qtys[-2]
    qtys[-3] = float(req.prev_3day_qty) if req.prev_3day_qty > 0 else qtys[-3]
    qtys[-7] = float(req.prev_7day_qty)

    df = pd.DataFrame({'ds': dates, 'y': qtys})

    # Add regressors
    df['is_holiday'] = req.is_holiday
    df['is_exam_week'] = req.is_exam_week

    # Suppress Prophet's verbose stdout logging
    logging.getLogger('cmdstanpy').setLevel(logging.WARNING)
    logging.getLogger('prophet').setLevel(logging.WARNING)

    m = Prophet(
        daily_seasonality=False,
        weekly_seasonality=True,
        yearly_seasonality=False,
        changepoint_prior_scale=0.05,
    )
    m.add_regressor('is_holiday')
    m.add_regressor('is_exam_week')
    m.fit(df)

    # Predict for today
    future = pd.DataFrame({'ds': [today]})
    future['is_holiday'] = req.is_holiday
    future['is_exam_week'] = req.is_exam_week

    forecast = m.predict(future)
    return float(max(1, forecast['yhat'].values[0]))


def build_reasons(req, predicted, base, prophet_pred=None, xgb_pred=None):
    reasons = []
    if base > 0:
        reasons.append(f"7-day average: {int(base)} units")
    if req.day_of_week == 4:
        reasons.append("Friday demand spike (+15%)")
    elif req.day_of_week == 0:
        reasons.append("Monday typically slower (-10%)")
    if req.is_exam_week == 1:
        reasons.append("Exam week — higher footfall (+20%)")
    if req.is_holiday == 1:
        reasons.append("Holiday — canteen mostly closed (-70%)")
    if req.weather_condition == 2:
        reasons.append("Rainy weather — comfort food demand up (+10%)")
    elif req.weather_condition == 3:
        reasons.append("Stormy — low footfall expected (-30%)")
    if req.temperature > 38:
        reasons.append("Very hot day — cold drinks in demand")
    if req.is_first_week == 1:
        reasons.append("First week of semester — high footfall")
    if req.is_last_week == 1:
        reasons.append("Last week of semester — reduced footfall")
    # Prophet trend insight
    if prophet_pred is not None and xgb_pred is not None and xgb_pred > 0:
        if abs(prophet_pred - xgb_pred) / xgb_pred > 0.15:
            reasons.append("Prophet trend model detected unusual pattern")
    if not reasons:
        reasons.append("Based on 7-day rolling average")
    return reasons[:3]


def do_predict(req: PredictRequest) -> PredictResponse:
    xgb_pred  = predict_xgb(req)
    lstm_pred = predict_lstm(req)

    # Try Prophet — fall back to XGBoost+LSTM if it fails
    prophet_pred = None
    try:
        prophet_pred = predict_prophet(req)
        # 3-model ensemble: 50% XGBoost + 30% LSTM + 20% Prophet
        raw_combined = (0.50 * xgb_pred) + (0.30 * lstm_pred) + (0.20 * prophet_pred)
    except Exception as e:
        print(f"Prophet fallback: {e}")
        # Fallback to original 2-model ensemble
        raw_combined = (0.60 * xgb_pred) + (0.40 * lstm_pred)

    # Apply business logic heuristics to correct underfitted model
    multiplier = 1.0
    if req.day_of_week == 4:
        multiplier *= 1.15
    elif req.day_of_week == 0:
        multiplier *= 0.90
    if req.is_exam_week == 1:
        multiplier *= 1.20
    if req.is_holiday == 1 or req.is_college_open == 0:
        multiplier *= 0.30
    if req.weather_condition == 2:
        multiplier *= 1.10
    elif req.weather_condition == 3:
        multiplier *= 0.70

    predicted = max(1, int(round(raw_combined * multiplier)))

    base     = req.rolling_7day_avg if req.rolling_7day_avg > 0 else req.prev_7day_qty
    reasons  = build_reasons(req, predicted, base, prophet_pred=prophet_pred, xgb_pred=xgb_pred)

    anomaly  = (req.prev_1day_qty > 0 and
                abs(predicted - req.prev_1day_qty) / req.prev_1day_qty > 0.40)

    if predicted > 20 and req.is_holiday == 0:
        conf = "High"
    elif predicted > 10:
        conf = "Medium"
    else:
        conf = "Low"

    return PredictResponse(
        item_id=req.item_id,
        predicted_qty=predicted,
        range_low=max(1, int(predicted * 0.88)),
        range_high=int(predicted * 1.12),
        top_reasons=reasons,
        anomaly_flag=anomaly,
        confidence=conf
    )


@app.get("/")
def root():
    return {
        "service": "TraceTech ML Service",
        "version": "3.0.0",
        "xgboost": "loaded",
        "lstm": "loaded" if LSTM_AVAILABLE else "unavailable",
        "prophet": "active" if PROPHET_AVAILABLE else "unavailable"
    }


@app.get("/health")
def health():
    return {
        "status": "ok",
        "models": {
            "xgboost": "loaded",
            "lstm": "loaded" if LSTM_AVAILABLE else "unavailable",
            "prophet": "active" if PROPHET_AVAILABLE else "unavailable"
        },
        "ensemble": "50% XGBoost + 30% LSTM + 20% Prophet" if PROPHET_AVAILABLE else "60% XGBoost + 40% LSTM"
    }


@app.post("/predict", response_model=PredictResponse)
def predict(req: PredictRequest):
    return do_predict(req)


@app.post("/predict/batch", response_model=List[PredictResponse])
def predict_batch(requests: List[PredictRequest]):
    return [do_predict(req) for req in requests]
