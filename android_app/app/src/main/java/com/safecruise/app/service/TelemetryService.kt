package com.safecruise.app.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.IBinder
import android.os.Looper
import androidx.core.app.NotificationCompat
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.safecruise.app.MainActivity
import com.safecruise.app.data.api.RetrofitClient
import com.safecruise.app.data.model.PredictRequest
import com.safecruise.app.data.model.TelemetryPoint
import com.safecruise.app.data.repository.TelemetryRepository
import retrofit2.Response
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.util.ArrayDeque

class TelemetryService : Service(), SensorEventListener {

    private lateinit var sensorManager: SensorManager
    private var accelerometer: Sensor? = null
    private var gyroscope: Sensor? = null
    private lateinit var fusedLocationClient: FusedLocationProviderClient

    private val serviceScope = CoroutineScope(Dispatchers.IO + Job())

    // Latest raw sensor values
    @Volatile private var currentAccelX = 0f
    @Volatile private var currentAccelY = 0f
    @Volatile private var currentAccelZ = 0f
    @Volatile private var currentPitch = 0f
    @Volatile private var currentYaw = 0f
    @Volatile private var currentSpeedKmh = 0f
    @Volatile private var currentLatitude = 0.0
    @Volatile private var currentLongitude = 0.0
    @Volatile private var currentHeading = 0f

    // 60-second rolling buffer at 10Hz (max 600 items)
    private val bufferCapacity = 600
    private val rollingBuffer = ArrayDeque<TelemetryPoint>(bufferCapacity)

    private var samplingJob: Job? = null
    private var apiJob: Job? = null

    private val locationCallback = object : LocationCallback() {
        override fun onLocationResult(result: LocationResult) {
            val location = result.lastLocation ?: return
            currentSpeedKmh = if (location.hasSpeed()) location.speed * 3.6f else 0f
            currentLatitude = location.latitude
            currentLongitude = location.longitude
            if (location.hasBearing()) {
                currentHeading = location.bearing
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
        gyroscope = sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE)
        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)

        createNotificationChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopTracking()
            stopSelf()
            return START_NOT_STICKY
        }

        startForeground(NOTIFICATION_ID, buildNotification())
        startTracking()
        return START_STICKY
    }

    private fun startTracking() {
        TelemetryRepository.setTracking(true)

        accelerometer?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }
        gyroscope?.let {
            sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME)
        }

        startLocationUpdates()

        // 10Hz Telemetry Sampling Loop (100ms)
        samplingJob = serviceScope.launch {
            while (isActive) {
                val point = TelemetryPoint(
                    timestamp = System.currentTimeMillis(),
                    speedKmh = currentSpeedKmh,
                    accelX = currentAccelX,
                    accelY = currentAccelY,
                    accelZ = currentAccelZ,
                    pitch = currentPitch,
                    yaw = currentYaw,
                    latitude = currentLatitude,
                    longitude = currentLongitude,
                    heading = currentHeading
                )

                var currentSize: Int
                synchronized(rollingBuffer) {
                    if (rollingBuffer.size >= bufferCapacity) {
                        rollingBuffer.removeFirst()
                    }
                    rollingBuffer.addLast(point)
                    currentSize = rollingBuffer.size
                }

                TelemetryRepository.updateCurrentTelemetry(point, currentSize)
                delay(100) // 10Hz
            }
        }

        // 1-second Backend API Loop
        apiJob = serviceScope.launch {
            while (isActive) {
                delay(1000)
                val snapshot: List<TelemetryPoint>
                synchronized(rollingBuffer) {
                    snapshot = rollingBuffer.toList()
                }

                if (snapshot.isNotEmpty()) {
                    try {
                        val response = RetrofitClient.apiService.predictRisk(PredictRequest(snapshot))
                        if (response.isSuccessful && response.body() != null) {
                            TelemetryRepository.updateRiskResponse(response.body()!!)
                        } else {
                            TelemetryRepository.setError("API Error: ${response.code()}")
                        }
                    } catch (e: Exception) {
                        TelemetryRepository.setError(e.localizedMessage ?: "Network Error")
                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    private fun startLocationUpdates() {
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 500)
            .setMinUpdateIntervalMillis(100)
            .build()

        try {
            fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback, Looper.getMainLooper())
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun stopTracking() {
        TelemetryRepository.setTracking(false)
        sensorManager.unregisterListener(this)
        fusedLocationClient.removeLocationUpdates(locationCallback)
        samplingJob?.cancel()
        apiJob?.cancel()
    }

    override fun onDestroy() {
        stopTracking()
        super.onDestroy()
    }

    override fun onSensorChanged(event: SensorEvent?) {
        if (event == null) return
        when (event.sensor.type) {
            Sensor.TYPE_ACCELEROMETER -> {
                currentAccelX = event.values[0]
                currentAccelY = event.values[1]
                currentAccelZ = event.values[2]
            }
            Sensor.TYPE_GYROSCOPE -> {
                currentPitch = event.values[0]
                currentYaw = event.values[1]
            }
        }
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) {}

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "SafeCruise Telemetry Service",
                NotificationManager.IMPORTANCE_LOW
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): Notification {
        val pendingIntent = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("SafeCruise Active")
            .setContentText("Collecting telemetry & evaluating driver risk...")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setContentIntent(pendingIntent)
            .setOngoing(true)
            .build()
    }

    companion object {
        const val CHANNEL_ID = "safecruise_telemetry"
        const val NOTIFICATION_ID = 1001
        const val ACTION_STOP = "com.safecruise.app.STOP_TELEMETRY"
    }
}
