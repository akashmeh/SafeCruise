package com.safecruise.app.data.model

import com.google.gson.annotations.SerializedName

data class PredictResponse(
    @SerializedName("risk_level") val riskLevel: Int, // 0: Low, 1: Medium, 2: High
    @SerializedName("risk_label") val riskLabel: String, // "Low", "Medium", "High"
    @SerializedName("explainability_shap_impacts") val explainabilityShapImpacts: Map<String, Float>? = null
) {
    val topContributingFeature: Pair<String, Float>?
        get() = explainabilityShapImpacts?.maxByOrNull { it.value }?.toPair()
}
