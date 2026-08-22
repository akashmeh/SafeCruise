from typing import List
from fastapi import FastAPI
from pydantic import BaseModel
import joblib
import shap
import pandas as pd

# 1. Initialize FastAPI app
app = FastAPI(title="Safe Cruise API", description="Real-time Driver Risk & Explainability Engine")

# 2. Load our pre-trained model and initialize SHAP explainer
model = joblib.load('safe_cruise_xgboost_model.pkl')
explainer = shap.Explainer(model)

# 3. Define the data structure incoming from the car/client
class TelemetryInput(BaseModel):
    Speed_kmh: float
    Accel_X_Filtered: float
    Accel_Y_Filtered: float

# Define a structure for a batch of telemetry points
class BatchTelemetryInput(BaseModel):
    readings: List[TelemetryInput]

@app.get("/")
def home():
    return {"status": "Safe Cruise Backend is Online!"}

@app.post("/predict")
def predict_risk(data: TelemetryInput):
    # Convert input JSON into a Pandas DataFrame row matching our model features
    input_df = pd.DataFrame([{
        'Speed_kmh': data.Speed_kmh,
        'Accel_X_Filtered': data.Accel_X_Filtered,
        'Accel_Y_Filtered': data.Accel_Y_Filtered
    }])
    
    # Run the model prediction
    prediction = model.predict(input_df)[0]
    probabilities = model.predict_proba(input_df)[0].tolist()
    
    # Calculate SHAP values for this specific telemetry point
    shap_values = explainer(input_df, check_additivity=False)
    
    # Extract the SHAP values for the predicted class
    feature_impacts = dict(zip(input_df.columns, shap_values.values[0, :, int(prediction)].tolist()))
    
    # Map numerical prediction back to readable risk levels
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
    # Convert incoming list of readings to a DataFrame
    data_list = [
        {
            'Speed_kmh': r.Speed_kmh,
            'Accel_X_Filtered': r.Accel_X_Filtered,
            'Accel_Y_Filtered': r.Accel_Y_Filtered
        }
        for r in batch.readings
    ]
    input_df = pd.DataFrame(data_list)
    
    # Run predictions for all rows at once
    predictions = model.predict(input_df)
    risk_mapping = {0: "Low Risk", 1: "Medium Risk", 2: "High Risk"}
    
    results = []
    for i, pred in enumerate(predictions):
        results.append({
            "index": i,
            "risk_level": int(pred),
            "risk_label": risk_mapping.get(int(pred), "Unknown")
        })
        
    # Count how many of each risk type occurred in this batch
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