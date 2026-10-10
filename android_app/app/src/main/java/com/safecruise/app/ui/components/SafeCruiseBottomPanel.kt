package com.safecruise.app.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.safecruise.app.data.model.PredictResponse
import com.safecruise.app.data.model.TelemetryPoint
import com.safecruise.app.ui.theme.BrightNeonGreen
import com.safecruise.app.ui.theme.DarkOliveBorder
import com.safecruise.app.ui.theme.DarkOliveBox
import com.safecruise.app.ui.theme.DarkOlivePanel
import com.safecruise.app.ui.theme.HighRiskRed
import com.safecruise.app.ui.theme.NeonLimePill
import com.safecruise.app.ui.theme.StatusPillBorder
import com.safecruise.app.ui.theme.TextBlack
import com.safecruise.app.ui.theme.TextMutedGrey
import com.safecruise.app.ui.theme.TextWhite
import com.safecruise.app.ui.theme.WarningOrange
import java.util.Locale

@Composable
fun SafeCruiseBottomPanel(
    isTracking: Boolean,
    telemetry: TelemetryPoint?,
    response: PredictResponse?,
    bufferSize: Int,
    apiError: String?,
    onToggleTracking: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp)
            .border(1.5.dp, DarkOliveBorder, RoundedCornerShape(32.dp)),
        shape = RoundedCornerShape(32.dp),
        colors = CardDefaults.cardColors(containerColor = DarkOlivePanel),
        elevation = CardDefaults.cardElevation(defaultElevation = 12.dp)
    ) {
        Column(
            modifier = Modifier.padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // TOP ROW: Status Pill (Left) & Speed Pill (Right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                val riskLevel = response?.riskLevel ?: 0
                val riskLabel = response?.riskLabel ?: if (isTracking) "Evaluating..." else "Ready"

                val (statusBorderColor, statusBgColor, statusTextColor) = when (riskLevel) {
                    2 -> Triple(HighRiskRed, HighRiskRed.copy(alpha = 0.15f), HighRiskRed)
                    1 -> Triple(WarningOrange, WarningOrange.copy(alpha = 0.15f), WarningOrange)
                    else -> Triple(StatusPillBorder, DarkOliveBox, BrightNeonGreen)
                }

                // Left Status Pill Box
                Box(
                    modifier = Modifier
                        .border(1.5.dp, statusBorderColor, RoundedCornerShape(12.dp))
                        .background(statusBgColor, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    Text(
                        text = riskLabel,
                        color = statusTextColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Right Speed Pill Box
                val speed = telemetry?.speedKmh ?: 0f
                Box(
                    modifier = Modifier
                        .background(NeonLimePill, RoundedCornerShape(20.dp))
                        .padding(horizontal = 16.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.Bottom
                    ) {
                        Text(
                            text = String.format(Locale.US, "%.1f", speed),
                            color = TextBlack,
                            fontSize = 30.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                        Spacer(modifier = Modifier.width(3.dp))
                        Text(
                            text = "km/h",
                            color = TextBlack,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                    }
                }
            }

            // MIDDLE ROW: Risk Score Circle (Left) & Notams / SHAP Box (Right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Top
            ) {
                val riskLevel = response?.riskLevel ?: 0
                val scoreColor = when (riskLevel) {
                    2 -> HighRiskRed
                    1 -> WarningOrange
                    else -> BrightNeonGreen
                }

                // Left: Risk Score Circle
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .border(2.dp, scoreColor, CircleShape)
                            .background(DarkOliveBox, CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "$riskLevel",
                            color = TextWhite,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.ExtraBold
                        )
                    }
                    Text(
                        text = "Risk Score",
                        color = TextWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }

                Spacer(modifier = Modifier.width(14.dp))

                // Right: Notams / SHAP Box
                Column(
                    modifier = Modifier.weight(1f)
                ) {
                    val topFeature = response?.topContributingFeature
                    val shapText = when {
                        apiError != null -> apiError
                        topFeature != null -> "${topFeature.first}: ${(topFeature.second * 100).toInt()}%"
                        isTracking -> "Collecting data ($bufferSize/600)..."
                        else -> "System ready"
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .border(1.5.dp, StatusPillBorder, RoundedCornerShape(10.dp))
                            .background(DarkOliveBox, RoundedCornerShape(10.dp))
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        contentAlignment = Alignment.CenterStart
                    ) {
                        Text(
                            text = shapText,
                            color = if (apiError != null) WarningOrange else BrightNeonGreen,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Text(
                        text = "Notams",
                        color = TextWhite,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .padding(top = 4.dp)
                            .align(Alignment.CenterHorizontally)
                    )
                }
            }

            // TELEMETRY SENSOR DETAILS
            val accelX = telemetry?.accelX ?: 0f
            val accelY = telemetry?.accelY ?: 0f
            val accelZ = telemetry?.accelZ ?: 0f
            Text(
                text = String.format(Locale.US, "X: %.2f  |  Y: %.2f  |  Z: %.2f m/s²", accelX, accelY, accelZ),
                color = TextMutedGrey,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.align(Alignment.CenterHorizontally)
            )

            // BOTTOM ROW: Full-Width Start/Stop Button
            Button(
                onClick = onToggleTracking,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = BrightNeonGreen,
                    contentColor = TextBlack
                )
            ) {
                Text(
                    text = if (isTracking) "stop tracking" else "start tracking",
                    color = TextBlack,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }
        }
    }
}
