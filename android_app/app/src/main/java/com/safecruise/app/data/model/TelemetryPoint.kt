package com.safecruise.app.data.model

import com.google.gson.annotations.SerializedName

data class TelemetryPoint(
    @SerializedName("timestamp") val timestamp: Long,
    @SerializedName("speed_kmh") val speedKmh: Float,
    @SerializedName("accel_x") val accelX: Float,
    @SerializedName("accel_y") val accelY: Float,
    @SerializedName("accel_z") val accelZ: Float,
    @SerializedName("pitch") val pitch: Float,
    @SerializedName("yaw") val yaw: Float,
    @SerializedName("latitude") val latitude: Double = 0.0,
    @SerializedName("longitude") val longitude: Double = 0.0,
    @SerializedName("heading") val heading: Float = 0f
)
