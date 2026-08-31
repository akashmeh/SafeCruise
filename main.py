"""Safe Cruise API -- temporal risk scoring with SHAP explainability.

Run:  .venv.nosync/bin/uvicorn main:app --reload --port 8000

The model is no longer point-in-time: every prediction needs rolling-window
statistics, so the client posts a *sequence* of telemetry samples and the server
derives the temporal features itself. Both endpoints therefore take the same
window payload:

  POST /predict        -> one verdict for the newest sample in the window
                          (the live in-car call: send your rolling buffer)
  POST /predict/batch  -> a verdict for every sample that has a full window
                          (offline / replay scoring)

BREAKING CHANGE from v2: /predict used to accept a single flat telemetry object.
It now requires {"readings": [...]} with at least MIN_WINDOW_SECONDS of history.
GET /schema returns the exact contract so the Leaflet frontend can read it at
runtime instead of hard-coding field names.

NOTE: no authentication. Anyone who can reach the port can post telemetry and
read predictions. Fine for localhost; put it behind an API key or gateway before
exposing it further.
"""

from typing import Dict, List, Optional

import numpy as np
import pandas as pd
from fastapi import FastAPI, HTTPException
from pydantic import BaseModel, Field

import features as F

app = FastAPI(
    title="Safe Cruise API",
    version="3.0.0",
    description="Temporal driver-risk engine (jerk + rolling-window features, SHAP)",
)

model, _ = F.load_model()
FEATURE_ORDER = F.model_feature_order(model)


# --------------------------------------------------------------------------- #
# Schemas
# --------------------------------------------------------------------------- #
class TelemetryPoint(BaseModel):
    """One raw sample. Timestamp is seconds since the start of the trip and must
    increase across the window -- it is what the rolling windows are measured on."""

    Timestamp: float = Field(..., ge=0, description="Seconds since trip start")
    Speed_kmh: float = Field(..., ge=0, description="GPS speed (km/h)")
    Accel_X_Filtered: float = Field(..., description="Lateral accel, Kalman-filtered (g)")
    Accel_Y_Filtered: float = Field(..., description="Longitudinal accel, KF (g)")
    Accel_Z_Filtered: float = Field(..., description="Vertical accel, KF (g)")
    Pitch: float = Field(..., description="Device pitch (degrees)")
    Yaw: float = Field(..., description="Heading (radians) -- used only via Yaw_std")


class TelemetryWindow(BaseModel):
    """A contiguous, time-ordered buffer of samples from one trip."""

    readings: List[TelemetryPoint] = Field(..., min_length=2)
    trip_id: Optional[str] = Field(None, description="Optional label, echoed back")

    model_config = {
        "json_schema_extra": {
            "example": {
                "trip_id": "live-session-1",
                "readings": [
                    {"Timestamp": 0.0, "Speed_kmh": 96.4, "Accel_X_Filtered": 0.012,
                     "Accel_Y_Filtered": -0.031, "Accel_Z_Filtered": 0.004,
                     "Pitch": -0.015, "Yaw": 1.204},
                    {"Timestamp": 0.1, "Speed_kmh": 96.6, "Accel_X_Filtered": 0.019,
                     "Accel_Y_Filtered": -0.044, "Accel_Z_Filtered": 0.002,
                     "Pitch": -0.013, "Yaw": 1.207},
                ],
            }
        }
    }


def window_to_frame(window: TelemetryWindow) -> pd.DataFrame:
    frame = pd.DataFrame([r.model_dump() for r in window.readings])
    missing = [c for c in F.RAW_INPUT_COLS if c not in frame.columns]
    if missing:
        raise HTTPException(422, f"Missing required columns: {missing}")
    return frame


def window_span(frame: pd.DataFrame) -> float:
    return float(frame["Timestamp"].max() - frame["Timestamp"].min())


def coverage_warning(span: float) -> Optional[str]:
    """The model was trained on statistics over a full MIN_WINDOW_SECONDS of
    history. A shorter buffer still produces numbers, but they are out of
    distribution and the confidence should not be trusted -- say so explicitly
    rather than returning a silently degraded score."""
    if span < F.MIN_WINDOW_SECONDS:
        return (
            f"Window spans {span:.1f}s but the model's longest rolling window is "
            f"{F.MIN_WINDOW_SECONDS:.0f}s. Rolling features are computed from a "
            f"partial window and predictions are out of distribution. Send at "
            f"least {F.MIN_WINDOW_SECONDS:.0f}s of history for a trustworthy score."
        )
    return None


def row_payload(row: pd.Series, index: int) -> Dict:
    """Serialise one scored row: verdict, confidence, SHAP, and the temporal
    features that produced it (so the frontend can plot cause alongside effect)."""
    impacts = {name: float(row[f"SHAP_{name}"]) for name in FEATURE_ORDER}
    return {
        "index": index,
        "timestamp": float(row["Timestamp"]),
        "risk_level": int(row["Predicted_Risk"]),
        "risk_label": f"{F.CLASS_NAMES[int(row['Predicted_Risk'])]} Risk",
        "behaviour": F.CLASS_BEHAVIOURS[int(row["Predicted_Risk"])],
        "confidence_scores": {
            f"{name.lower()}_risk": float(row[f"Proba_{name}"])
            for name in F.CLASS_NAMES
        },
        "temporal_features": {name: float(row[name]) for name in FEATURE_ORDER},
        "explainability_shap_impacts": impacts,
        "top_contributing_feature": max(impacts, key=lambda k: abs(impacts[k])),
    }


# --------------------------------------------------------------------------- #
# Routes
# --------------------------------------------------------------------------- #
@app.get("/")
def home():
    return {
        "status": "Safe Cruise Backend is Online!",
        "version": app.version,
        "n_features": len(FEATURE_ORDER),
        "min_window_seconds": F.MIN_WINDOW_SECONDS,
    }


@app.get("/schema")
def schema():
    """Runtime feature contract -- the frontend should read this, not hard-code it."""
    return {
        "required_input_columns": F.RAW_INPUT_COLS,
        "rolling_windows": list(F.ROLL_WINDOWS),
        "min_window_seconds": F.MIN_WINDOW_SECONDS,
        "nominal_hz": F.NOMINAL_HZ,
        "recommended_buffer_samples": int(F.MIN_WINDOW_SECONDS * F.NOMINAL_HZ),
        "model_features": FEATURE_ORDER,
        "engineered_features": {
            "instantaneous": F.INSTANT_FEATURES,
            "jerk": F.JERK_FEATURES,
            "rolling": F.ROLLING_FEATURES,
        },
        "excluded_by_design": {
            "columns": F.DROPPED_STATIC + ([] if F.KEEP_RAW_YAW else ["Yaw (raw)"]),
            "reason": "route / phone-mount fingerprints that leaked trip identity",
        },
        "classes": {i: f"{n} Risk" for i, n in enumerate(F.CLASS_NAMES)},
    }


@app.post("/predict")
def predict_risk(window: TelemetryWindow):
    """Score the newest sample in the buffer, using the rest as its history."""
    frame = window_to_frame(window)
    span = window_span(frame)

    scored, skipped = F.score_window(frame)
    if scored.empty:
        raise HTTPException(
            422,
            f"No sample in this window has enough history to score. Window spans "
            f"{span:.1f}s; at least {F.MIN_WINDOW_SECONDS * F.MIN_COVERAGE:.0f}s "
            f"is required (ideally {F.MIN_WINDOW_SECONDS:.0f}s).",
        )

    latest = scored.iloc[-1]
    return {
        "trip_id": window.trip_id,
        "window": {
            "samples_received": len(frame),
            "samples_scored": int(len(scored)),
            "warmup_samples_skipped": skipped,
            "span_seconds": round(span, 3),
        },
        "warning": coverage_warning(span),
        **row_payload(latest, index=int(len(frame) - 1)),
    }


@app.post("/predict/batch")
def predict_batch(window: TelemetryWindow):
    """Score every sample in the sequence, deriving rolling features dynamically.

    Rows in the leading warm-up period cannot have a full window, so they are
    reported in `skipped_indices` rather than scored with invented values.
    """
    frame = window_to_frame(window)
    span = window_span(frame)

    scored, skipped = F.score_window(frame)
    if scored.empty:
        raise HTTPException(
            422,
            f"Window spans {span:.1f}s -- too short to derive rolling features. "
            f"Send at least {F.MIN_WINDOW_SECONDS:.0f}s of history.",
        )

    # score_window() drops rows, so map results back to the caller's positions.
    position = {float(t): i for i, t in enumerate(frame["Timestamp"].to_numpy())}
    results = [
        row_payload(row, index=position.get(float(row["Timestamp"]), -1))
        for _, row in scored.iterrows()
    ]
    scored_positions = {r["index"] for r in results}

    predictions = scored["Predicted_Risk"].to_numpy()
    counts = {
        f"{name.lower()}_risk_count": int((predictions == i).sum())
        for i, name in enumerate(F.CLASS_NAMES)
    }
    peak_idx = int(np.argmax(scored["Proba_High"].to_numpy()))

    return {
        "trip_id": window.trip_id,
        "summary": {
            "samples_received": len(frame),
            "samples_scored": int(len(scored)),
            "warmup_samples_skipped": skipped,
            "span_seconds": round(span, 3),
            "rolling_windows": list(F.ROLL_WINDOWS),
            **counts,
            "dominant_risk_label": f"{F.CLASS_NAMES[int(pd.Series(predictions).mode()[0])]} Risk",
            "peak_high_risk_probability": float(scored["Proba_High"].max()),
            "peak_high_risk_timestamp": float(scored["Timestamp"].iloc[peak_idx]),
        },
   
