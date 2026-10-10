package com.safecruise.app.data.repository

import com.safecruise.app.data.model.PredictResponse
import com.safecruise.app.data.model.TelemetryPoint
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

object TelemetryRepository {
    private val _isTracking = MutableStateFlow(false)
    val isTracking: StateFlow<Boolean> = _isTracking.asStateFlow()

    private val _currentTelemetry = MutableStateFlow<TelemetryPoint?>(null)
    val currentTelemetry: StateFlow<TelemetryPoint?> = _currentTelemetry.asStateFlow()

    private val _latestRiskResponse = MutableStateFlow<PredictResponse?>(null)
    val latestRiskResponse: StateFlow<PredictResponse?> = _latestRiskResponse.asStateFlow()

    private val _bufferSize = MutableStateFlow(0)
    val bufferSize: StateFlow<Int> = _bufferSize.asStateFlow()

    private val _apiError = MutableStateFlow<String?>(null)
    val apiError: StateFlow<String?> = _apiError.asStateFlow()

    fun setTracking(tracking: Boolean) {
        _isTracking.value = tracking
    }

    fun updateCurrentTelemetry(point: TelemetryPoint, currentBufferSize: Int) {
        _currentTelemetry.value = point
        _bufferSize.value = currentBufferSize
    }

    fun updateRiskResponse(response: PredictResponse) {
        _latestRiskResponse.value = response
        _apiError.value = null
    }

    fun setError(message: String?) {
        _apiError.value = message
    }
}
