package com.safecruise.app.ui.dashboard

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MyLocation
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.FloatingActionButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalInspectionMode
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.android.gms.location.LocationServices
import com.mapbox.geojson.Point
import com.mapbox.maps.MapInitOptions
import com.mapbox.maps.dsl.cameraOptions
import com.mapbox.maps.extension.compose.MapboxMap
import com.mapbox.maps.extension.compose.animation.viewport.rememberMapViewportState
import com.mapbox.maps.extension.compose.annotation.generated.PointAnnotation
import com.mapbox.maps.plugin.animation.MapAnimationOptions
import com.safecruise.app.R
import com.safecruise.app.data.model.PredictResponse
import com.safecruise.app.data.model.TelemetryPoint
import com.safecruise.app.ui.components.SafeCruiseBottomPanel
import com.safecruise.app.ui.theme.BrightNeonGreen
import com.safecruise.app.ui.theme.DarkBackground
import com.safecruise.app.ui.theme.DarkOlivePanel
import com.safecruise.app.ui.theme.LimeGreen
import com.safecruise.app.ui.theme.NeonYellow
import com.safecruise.app.ui.theme.SafeCruiseTheme
import com.safecruise.app.ui.theme.StatusPillBorder
import com.safecruise.app.ui.theme.TextMutedGrey
import com.safecruise.app.utils.MapUtils

@Composable
fun DashboardScreen(
    viewModel: DashboardViewModel = viewModel()
) {
    val context = LocalContext.current
    val isInspection = LocalInspectionMode.current

    val isTracking by viewModel.isTracking.collectAsStateWithLifecycle()
    val telemetry by viewModel.currentTelemetry.collectAsStateWithLifecycle()
    val riskResponse by viewModel.latestRiskResponse.collectAsStateWithLifecycle()
    val bufferSize by viewModel.bufferSize.collectAsStateWithLifecycle()
    val apiError by viewModel.apiError.collectAsStateWithLifecycle()

    if (!isInspection) {
        val permissionLauncher = rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestMultiplePermissions()
        ) { _ -> }

        LaunchedEffect(Unit) {
            val permissionsToRequest = mutableListOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
            val missingPermissions = permissionsToRequest.filter {
                ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
            }
            if (missingPermissions.isNotEmpty()) {
                permissionLauncher.launch(missingPermissions.toTypedArray())
            }
        }
    }

    DashboardContent(
        isInspection = isInspection,
        isTracking = isTracking,
        telemetry = telemetry,
        riskResponse = riskResponse,
        bufferSize = bufferSize,
        apiError = apiError,
        onToggleTracking = {
            if (!isInspection) {
                viewModel.toggleTracking(context)
            }
        }
    )
}

@SuppressLint("MissingPermission")
@Composable
fun DashboardContent(
    isInspection: Boolean,
    isTracking: Boolean,
    telemetry: TelemetryPoint?,
    riskResponse: PredictResponse?,
    bufferSize: Int,
    apiError: String?,
    onToggleTracking: () -> Unit
) {
    val context = LocalContext.current

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        if (isInspection) {
            DarkMapPreviewBackground()
        } else {
            val mapViewportState = rememberMapViewportState {
                setCameraOptions {
                    center(Point.fromLngLat(-122.4194, 37.7749))
                    zoom(14.0)
                    pitch(0.0)
                }
            }

            val fusedLocationClient = remember { LocationServices.getFusedLocationProviderClient(context) }

            val recenterLocation: () -> Unit = {
                val lat = telemetry?.latitude ?: 0.0
                val lng = telemetry?.longitude ?: 0.0
                if (lat != 0.0 && lng != 0.0) {
                    mapViewportState.flyTo(
                        cameraOptions = cameraOptions {
                            center(Point.fromLngLat(lng, lat))
                            zoom(15.5)
                        },
                        animationOptions = MapAnimationOptions.mapAnimationOptions {
                            duration(1000)
                        }
                    )
                } else {
                    try {
                        fusedLocationClient.lastLocation.addOnSuccessListener { loc ->
                            if (loc != null) {
                                mapViewportState.flyTo(
                                    cameraOptions = cameraOptions {
                                        center(Point.fromLngLat(loc.longitude, loc.latitude))
                                        zoom(15.5)
                                    },
                                    animationOptions = MapAnimationOptions.mapAnimationOptions {
                                        duration(1000)
                                    }
                                )
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }
            }

            val pointLng = if (telemetry != null && telemetry.longitude != 0.0) telemetry.longitude else -122.4194
            val pointLat = if (telemetry != null && telemetry.latitude != 0.0) telemetry.latitude else 37.7749
            val heading = telemetry?.heading ?: 0f

            val carBitmap = remember(context) {
                getBitmapFromVectorDrawable(context, R.drawable.ic_navigation_car)
            }

            MapboxMap(
                modifier = Modifier.fillMaxSize(),
                mapViewportState = mapViewportState,
                mapInitOptionsFactory = { ctx ->
                    MapInitOptions(ctx, styleUri = MapUtils.MAPBOX_DARK_STYLE)
                }
            ) {
                if (carBitmap != null) {
                    PointAnnotation(
                        point = Point.fromLngLat(pointLng, pointLat),
                        iconImageBitmap = carBitmap,
                        iconRotate = heading.toDouble(),
                        iconSize = 1.2
                    )
                } else {
                    PointAnnotation(
                        point = Point.fromLngLat(pointLng, pointLat)
                    )
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 16.dp, end = 20.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                FloatingActionButton(
                    onClick = recenterLocation,
                    modifier = Modifier
                        .size(52.dp)
                        .border(1.5.dp, StatusPillBorder, CircleShape),
                    shape = CircleShape,
                    containerColor = DarkOlivePanel,
                    contentColor = BrightNeonGreen,
                    elevation = FloatingActionButtonDefaults.elevation(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "My Location",
                        tint = BrightNeonGreen,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        if (isInspection) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .padding(top = 16.dp, end = 20.dp),
                contentAlignment = Alignment.TopEnd
            ) {
                FloatingActionButton(
                    onClick = {},
                    modifier = Modifier
                        .size(52.dp)
                        .border(1.5.dp, StatusPillBorder, CircleShape),
                    shape = CircleShape,
                    containerColor = DarkOlivePanel,
                    contentColor = BrightNeonGreen,
                    elevation = FloatingActionButtonDefaults.elevation(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.MyLocation,
                        contentDescription = "My Location",
                        tint = BrightNeonGreen,
                        modifier = Modifier.size(26.dp)
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            SafeCruiseBottomPanel(
                isTracking = isTracking,
                telemetry = telemetry,
                response = riskResponse,
                bufferSize = bufferSize,
                apiError = apiError,
                onToggleTracking = onToggleTracking,
                modifier = Modifier.align(Alignment.BottomCenter)
            )
        }
    }
}

private fun getBitmapFromVectorDrawable(context: Context, drawableId: Int): Bitmap? {
    val drawable = ContextCompat.getDrawable(context, drawableId) ?: return null
    val bitmap = Bitmap.createBitmap(
        drawable.intrinsicWidth.coerceAtLeast(1),
        drawable.intrinsicHeight.coerceAtLeast(1),
        Bitmap.Config.ARGB_8888
    )
    val canvas = android.graphics.Canvas(bitmap)
    drawable.setBounds(0, 0, canvas.width, canvas.height)
    drawable.draw(canvas)
    return bitmap
}

@Composable
private fun DarkMapPreviewBackground() {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val width = size.width
        val height = size.height

        val gridStep = 80f
        var x = 0f
        while (x < width) {
            drawLine(
                color = Color(0xFF1E2838),
                start = Offset(x, 0f),
                end = Offset(x, height),
                strokeWidth = 1f
            )
            x += gridStep
        }
        var y = 0f
        while (y < height) {
            drawLine(
                color = Color(0xFF1E2838),
                start = Offset(0f, y),
                end = Offset(width, y),
                strokeWidth = 1f
            )
            y += gridStep
        }

        val roadPath = Path().apply {
            moveTo(0f, height * 0.4f)
            cubicTo(width * 0.3f, height * 0.3f, width * 0.6f, height * 0.6f, width, height * 0.5f)
        }
        drawPath(
            path = roadPath,
            color = Color(0xFF2C3E55),
            style = Stroke(width = 16f)
        )

        val roadPath2 = Path().apply {
            moveTo(width * 0.5f, 0f)
            cubicTo(width * 0.4f, height * 0.4f, width * 0.7f, height * 0.7f, width * 0.5f, height)
        }
        drawPath(
            path = roadPath2,
            color = Color(0xFF34495E),
            style = Stroke(width = 12f)
        )

        drawCircle(
            color = NeonYellow,
            radius = 16f,
            center = Offset(width * 0.5f, height * 0.5f)
        )
        drawCircle(
            color = LimeGreen,
            radius = 8f,
            center = Offset(width * 0.5f, height * 0.5f)
        )
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 40.dp),
        contentAlignment = Alignment.TopCenter
    ) {
        Text(
            text = "MAPBOX MAP VIEW • SAFECRUISE",
            color = TextMutedGrey,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            letterSpacing = 1.5.sp
        )
    }
}

@Preview(showBackground = true, widthDp = 412, heightDp = 915)
@Composable
fun DashboardScreenPreview() {
    SafeCruiseTheme {
        DashboardContent(
            isInspection = true,
            isTracking = false,
            telemetry = TelemetryPoint(
                timestamp = System.currentTimeMillis(),
                speedKmh = 0.0f,
                accelX = 0.0f,
                accelY = 0.0f,
                accelZ = 0.0f,
                pitch = 0.0f,
                yaw = 0.0f
            ),
            riskResponse = null,
            bufferSize = 0,
            apiError = null,
            onToggleTracking = {}
        )
    }
}
