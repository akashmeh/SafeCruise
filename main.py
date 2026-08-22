from typing import List
from fastapi import FastAPI
from pydantic import BaseModel
import joblib
import shap
import pandas as pd

# FAST API
app = FastAPI(title="Safe Cruise API", description="Real-time Driver Risk & Explainability Engine")


model = joblib.load('safe_cruise_xgboost_model.pkl')
explainer = shap.Explainer(model)


class TelemetryInput(BaseModel):
    Speed_kmh: float
    Accel_X_Filtered: float
    Accel_Y_Filtered: float


class BatchTelemetryInput(BaseModel):
    readings: List[TelemetryInput]

@app.get("/")
def home():
    return {"status": "Safe Cruise Backend is Online!"}

@app.post("/predict")
def predict_risk(data: TelemetryInput):
    
    input_df = pd.DataFrame([{
        'Speed_kmh': data.Speed_kmh,
        'Accel_X_Filtered': data.Accel_X_Filtered,
        'Accel_Y_Filtered': data.Accel_Y_Filtered
    }])
    
    
    prediction = model.predict(input_df)[0]
    probabilities = model.predict_proba(input_df)[0].tolist()
    

    shap_values = explainer(input_df, check_additivity=False)
    
    
    feature_impacts = dict(zip(input_df.columns, shap_values.values[0, :, int(prediction)].tolist()))
    
    
    risk_mapping = {0: "Low Risk", 1: "Medium Risk", 2: "High Risk"}
    
    return {
        "predicted_risk_level": int(prediction),
        "risk_label": risk_mapping.get(int(prediction), "Unknown"),
        "confidence_scores": {
            "low_risk": probabilities[0],
            "medium_risk": probabilities[1] if len(probabilities) > 1 else 0.0,
            "high_risk": probabilities[-1]
        },
        "explainability_shap_impacts": feature_impacts
    }

@app.post("/predict/batch")
def predict_batch(batch: BatchTelemetryInput):
    
    data_list = [
        {
            'Speed_kmh': r.Speed_kmh,
            'Accel_X_Filtered': r.Accel_X_Filtered,
            'Accel_Y_Filtered': r.Accel_Y_Filtered
        }
        for r in batch.readings
    ]
    input_df = pd.DataFrame(data_list)
    
  
    predictions = model.predict(input_df)
    risk_mapping = {0: "Low Risk", 1: "Medium Risk", 2: "High Risk"}
    
    results = []
    for i, pred in enumerate(predictions):
        results.append({
            "index": i,
            "risk_level": int(pred),
            "risk_label": risk_mapping.get(int(pred), "Unknown")
        })
        
  
    summary = {
        "total_readings": len(predictions),
        "low_risk_count": int((predictions == 0).sum()),
        "medium_risk_count": int((predictions == 1).sum()),
        "high_risk_count": int((predictions == 2).sum())
    }
    
    return {
        "summary": summary,
        "detailed_predictions": results
    }
