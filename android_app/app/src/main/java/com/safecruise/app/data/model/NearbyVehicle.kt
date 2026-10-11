package com.safecruise.app.data.model

data class NearbyVehicle(
    val id: String,
    val name: String,
    val latitude: Double,
    val longitude: Double,
    val speedKmh: Float,
    val distanceMeters: Float,
    val bearingDegrees: Float,
    val relativePosition: String, // "BEHIND", "AHEAD", "LEFT", "RIGHT"
    val isAggressive: Boolean = false,
    val aggressiveReason: String = ""
)
