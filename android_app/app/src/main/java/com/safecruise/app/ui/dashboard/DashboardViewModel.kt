package com.safecruise.app.ui.dashboard

import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import com.safecruise.app.data.model.PredictResponse
import com.safecruise.app.data.model.TelemetryPoint
import com.safecruise.app.data.repository.TelemetryRepository
import com.safecruise.app.service.TelemetryService
import kotlinx.coroutines.flow.StateFlow

class DashboardViewModel : ViewModel() {

    val isTracking: StateFlow<Boolean> = TelemetryRepository.isTracking
    val currentTelemetry: StateFlow<TelemetryPoint?> = TelemetryRepository.currentTelemetry
    val latestRiskResponse: StateFlow<PredictResponse?> = TelemetryRepository.latestRiskResponse
    val bufferSize: StateFlow<Int> = TelemetryRepository.bufferSize
    val apiError: StateFlow<String?> = TelemetryRepository.apiError

    fun toggleTracking(context: Context) {
        val intent = Intent(context, TelemetryService::class.java)
        if (isTracking.value) {
            intent.action = TelemetryService.ACTION_STOP
            context.startService(intent)
        } else {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
