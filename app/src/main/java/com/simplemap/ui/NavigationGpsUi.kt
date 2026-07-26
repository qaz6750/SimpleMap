package com.simplemap.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplemap.navigation.NavigationGpsMode
import com.simplemap.navigation.NavigationLocationIssue
import com.simplemap.navigation.NavigationUiState
import com.simplemap.navigation.determineNavigationGpsMode

@Composable
internal fun NavigationGpsStatus(
    state: NavigationUiState,
    isLandscape: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val diagnostic = state.locationDiagnostic
    val gpsMode = determineNavigationGpsMode(
        gpsEnabled = state.gpsEnabled,
        gpsSignalWeak = state.gpsSignalWeak,
        satelliteStatus = state.satelliteStatus,
        locationDiagnostic = diagnostic,
    )
    val isNormal = gpsMode == NavigationGpsMode.Normal && diagnostic == null
    val backgroundColor = if (isLandscape) Color(0xF7FFFFFF) else Color(0xD9141C2B)
    val iconColor = when {
        !isNormal -> Color(0xFFE53935)
        isLandscape -> Color(0xFF182033)
        else -> Color.White
    }
    val statusLabel = when {
        gpsMode == NavigationGpsMode.Unavailable -> "GPS 未开启"
        gpsMode == NavigationGpsMode.Weak -> "GPS 信号弱"
        diagnostic?.issue == NavigationLocationIssue.LowAccuracy -> "GPS 漂移"
        diagnostic?.issue == NavigationLocationIssue.OffRoute -> "待校准"
        else -> "GPS ${state.satelliteStatus.usedInFixCount}"
    }
    Surface(
        modifier = modifier
            .size(width = if (isLandscape) 48.dp else 38.dp, height = 38.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics {
                contentDescription = "GPS 卫星状态"
                stateDescription = statusLabel
            },
        color = backgroundColor,
        shape = if (isLandscape) RoundedCornerShape(12.dp) else CircleShape,
        shadowElevation = 6.dp,
    ) {
        Canvas(Modifier.padding(horizontal = if (isLandscape) 13.dp else 8.dp, vertical = 8.dp)) {
            val signalCenter = Offset(size.width * 0.28f, size.height * 0.72f)
            drawCircle(iconColor, radius = size.minDimension * 0.09f, center = signalCenter)
            listOf(0.25f, 0.43f).forEach { radiusFraction ->
                val radius = size.minDimension * radiusFraction
                drawArc(
                    color = iconColor,
                    startAngle = -90f,
                    sweepAngle = 90f,
                    useCenter = false,
                    topLeft = Offset(signalCenter.x - radius, signalCenter.y - radius),
                    size = Size(radius * 2f, radius * 2f),
                    style = Stroke(width = size.minDimension * 0.1f, cap = StrokeCap.Round),
                )
            }
        }
    }
}

@Composable
internal fun NavigationSatellitePanel(
    state: NavigationUiState,
    dismissSeconds: Int,
    nightMode: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val satellite = state.satelliteStatus
    val gpsMode = determineNavigationGpsMode(
        gpsEnabled = state.gpsEnabled,
        gpsSignalWeak = state.gpsSignalWeak,
        satelliteStatus = satellite,
        locationDiagnostic = state.locationDiagnostic,
    )
    val panelColor = if (nightMode) NavigationPanelColor else GpsPanelBackground
    val surfaceColor = if (nightMode) Color(0xFF263B55) else GpsPanelSurface
    val dividerColor = if (nightMode) NavigationPanelDivider else GpsPanelDivider
    val primaryTextColor = if (nightMode) Color.White else GpsPanelText
    val secondaryTextColor = if (nightMode) NavigationSecondaryText else GpsPanelSecondaryText
    val accentColor = if (nightMode) NavigationAccentText else GpsPanelAccent
    val warningSurfaceColor = if (nightMode) Color(0xFF4D2630) else Color(0xFFFFEBEE)
    val warningTextColor = if (nightMode) Color(0xFFFFB4AB) else Color(0xFFB71C1C)
    Surface(
        modifier = modifier.semantics { contentDescription = "GPS 定位详情面板" },
        color = panelColor,
        shape = MaterialTheme.shapes.extraLarge,
        shadowElevation = 18.dp,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(
                        "GPS 定位详情",
                        color = primaryTextColor,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        "${dismissSeconds.coerceAtLeast(0)} 秒后自动关闭",
                        color = accentColor,
                        fontSize = 11.sp,
                    )
                }
                TextButton(onClick = onDismiss) { Text("关闭", color = accentColor) }
            }
            HorizontalDivider(color = dividerColor)
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()).padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (gpsMode != NavigationGpsMode.Normal) {
                    Surface(color = warningSurfaceColor, shape = RoundedCornerShape(8.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                            Text(
                                if (gpsMode == NavigationGpsMode.Unavailable) "GPS 定位未开启" else "弱 GPS 模式",
                                color = warningTextColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(
                                if (gpsMode == NavigationGpsMode.Unavailable) {
                                    "请开启系统定位后继续导航"
                                } else {
                                    "定位可能延迟，请沿当前道路行驶并等待信号恢复"
                                },
                                color = warningTextColor,
                                fontSize = 10.sp,
                            )
                        }
                    }
                }
                state.locationDiagnostic?.let { diagnostic ->
                    val title = if (diagnostic.issue == NavigationLocationIssue.LowAccuracy) {
                        "GPS 信号漂移"
                    } else {
                        "可能偏离导航路线"
                    }
                    val detail = if (diagnostic.issue == NavigationLocationIssue.LowAccuracy) {
                        "定位精度较低，暂不判断为真实偏航"
                    } else {
                        "连续定位未匹配路线，等待导航重新校准"
                    }
                    Surface(color = warningSurfaceColor, shape = RoundedCornerShape(8.dp)) {
                        Column(modifier = Modifier.fillMaxWidth().padding(10.dp)) {
                            Text(
                                title,
                                color = warningTextColor,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            Text(detail, color = warningTextColor, fontSize = 10.sp)
                        }
                    }
                    NavigationSatelliteMetric(
                        label = "当前定位精度",
                        value = "约 ${diagnostic.accuracyMeters} 米",
                        primaryTextColor = primaryTextColor,
                        secondaryTextColor = secondaryTextColor,
                    )
                }
                Surface(color = surfaceColor, shape = RoundedCornerShape(8.dp)) {
                    Column(
                        modifier = Modifier.fillMaxWidth().padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        NavigationSatelliteMetric(
                            "可见卫星",
                            "${satellite.visibleCount} 颗",
                            primaryTextColor,
                            secondaryTextColor,
                        )
                        NavigationSatelliteMetric(
                            "参与定位",
                            "${satellite.usedInFixCount} 颗",
                            primaryTextColor,
                            secondaryTextColor,
                        )
                        NavigationSatelliteMetric(
                            "平均信号",
                            "%.1f dB-Hz".format(satellite.averageCn0DbHz),
                            primaryTextColor,
                            secondaryTextColor,
                        )
                        if (satellite.systems.isEmpty()) {
                            Text("正在等待卫星数据", color = secondaryTextColor, fontSize = 12.sp)
                        } else {
                            satellite.systems.forEach { (system, count) ->
                                NavigationSatelliteMetric(
                                    system,
                                    "$count 颗",
                                    primaryTextColor,
                                    secondaryTextColor,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun NavigationSatelliteMetric(
    label: String,
    value: String,
    primaryTextColor: Color,
    secondaryTextColor: Color,
) {
    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = secondaryTextColor, fontSize = 12.sp)
        Text(value, color = primaryTextColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}
