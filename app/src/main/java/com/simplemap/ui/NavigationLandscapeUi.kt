package com.simplemap.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplemap.navigation.NavigationLane
import com.simplemap.navigation.NavigationPhase
import com.simplemap.navigation.NavigationRouteNotice

@Composable
internal fun NavigationLandscapeInformation(
    guidanceState: NavigationGuidanceState,
    maneuverIconBitmap: android.graphics.Bitmap?,
    lanes: List<NavigationLane>,
    tripSummaryState: NavigationTripSummaryState,
    message: String?,
    routeNotice: NavigationRouteNotice?,
    compactGuidance: Boolean,
    destinationName: String,
    junctionViewBitmap: android.graphics.Bitmap?,
    junctionViewHeight: androidx.compose.ui.unit.Dp,
    mapInteracting: Boolean,
    actionsEnabled: Boolean,
    onRecoverFollowing: () -> Unit,
    onSettings: () -> Unit,
    onExit: () -> Unit,
    onFindParking: () -> Unit,
    onSaveParkingLocation: () -> Unit,
    parkingLocationAvailable: Boolean,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .padding(top = 6.dp)
            .semantics { contentDescription = "横屏导航信息卡" },
        color = Color.Transparent,
        shape = RoundedCornerShape(16.dp),
        shadowElevation = 16.dp,
    ) {
        Column {
            Column(modifier = Modifier.background(PortraitNavigationPanelColor)) {
                NavigationLandscapeInstructionContent(
                    guidanceState = guidanceState,
                    maneuverIconBitmap = maneuverIconBitmap,
                    destinationName = destinationName,
                    compact = compactGuidance || junctionViewBitmap != null,
                )
                NavigationRouteNoticeBanner(routeNotice)
            }
            if (junctionViewBitmap != null) {
                NavigationJunctionView(
                    bitmap = junctionViewBitmap,
                    lanes = lanes,
                    modifier = Modifier.fillMaxWidth().height(junctionViewHeight),
                )
            }
            if (junctionViewBitmap == null) {
                if (!mapInteracting) {
                    NavigationLandscapeTripSummary(tripSummaryState)
                }
                message?.let { statusMessage ->
                    Text(
                        text = statusMessage,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 7.dp),
                        color = DaySecondaryText,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }
            if (actionsEnabled) {
                when (guidanceState.phase) {
                    NavigationPhase.Arrived -> NavigationArrivalActions(
                        onFindParking = onFindParking,
                        onSaveParkingLocation = onSaveParkingLocation,
                        parkingLocationAvailable = parkingLocationAvailable,
                        onExit = onExit,
                    )
                    NavigationPhase.Failed -> Box(Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) {
                        Button(
                            onClick = onExit,
                            modifier = Modifier.fillMaxWidth(),
                            shape = PanelShapeSmall,
                            colors = ButtonDefaults.buttonColors(containerColor = NightErrorContainer),
                        ) {
                            Text("返回路线规划")
                        }
                    }
                    else -> if (mapInteracting) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(PortraitNavigationPanelColor)
                                .padding(horizontal = 10.dp, vertical = 9.dp),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                        ) {
                            NavigationAction("继续导航", NightActionContainer, Color.White, onRecoverFollowing, Modifier.weight(1f))
                            NavigationAction("设置", NightActionContainer, Color.White, onSettings, Modifier.weight(1f))
                            NavigationAction("结束", NightErrorContainer, NightOnErrorContainer, onExit, Modifier.weight(1f))
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun NavigationLandscapeInstructionContent(
    guidanceState: NavigationGuidanceState,
    maneuverIconBitmap: android.graphics.Bitmap?,
    destinationName: String,
    compact: Boolean,
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        val iconSize = if (compact) 52.dp else 70.dp
        maneuverIconBitmap?.let { bitmap ->
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = "导航转向指示 ${guidanceState.maneuverIconType}",
                modifier = Modifier.size(iconSize),
            )
        } ?: ManeuverIcon(
            iconType = guidanceState.maneuverIconType,
            modifier = Modifier.size(iconSize),
            backgroundColor = Color.Transparent,
            arrowColor = Color.White,
        )
        Column(
            modifier = Modifier.padding(start = 10.dp).weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            if (guidanceState.maneuverDistanceMeters > 0) {
                Row(verticalAlignment = Alignment.Bottom) {
                    Text(
                        text = formatNavigationDistance(guidanceState.maneuverDistanceMeters),
                        color = Color.White,
                        fontSize = if (compact) 25.sp else 32.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        text = " 后",
                        modifier = Modifier.padding(bottom = 3.dp),
                        color = NavigationSecondaryText,
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
                }
            }
            Text(
                text = guidanceState.nextRoad.ifBlank {
                    if (guidanceState.phase == NavigationPhase.Arrived) {
                        "已到达目的地附近"
                    } else {
                        destinationName
                    }
                },
                color = Color.White,
                fontSize = if (compact) 17.sp else 20.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
internal fun NavigationLandscapeTripSummary(summaryState: NavigationTripSummaryState) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(48.dp)
            .background(PortraitNavigationPanelColor)
            .padding(horizontal = 12.dp)
            .semantics { contentDescription = "横屏行程信息条" },
        horizontalArrangement = Arrangement.SpaceEvenly,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        NavigationTripMetric(
            "剩余",
            formatNavigationTime(summaryState.remainingTimeSeconds),
            true,
            true,
            Modifier.weight(1f),
        )
        NavigationTripMetric(
            "距离",
            formatNavigationDistance(summaryState.remainingDistanceMeters),
            true,
            true,
            Modifier.weight(1f),
        )
        NavigationTripMetric(
            "到达",
            formatNavigationArrivalTime(summaryState.remainingTimeSeconds),
            true,
            true,
            Modifier.weight(1f),
        )
        if (summaryState.remainingTrafficLights > 0) {
            NavigationTripMetric(
                "红绿灯",
                "${summaryState.remainingTrafficLights} 个",
                true,
                true,
                Modifier.weight(1f),
            )
        }
    }
}
