package com.simplemap.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplemap.search.Place
import com.simplemap.trips.TripHistoryStore
import com.simplemap.trips.TripRecord
import com.simplemap.trips.TripStatus
import com.simplemap.trips.toTripReview
import com.simplemap.ui.theme.navigationAccent
import com.simplemap.ui.theme.panelBorder
import com.simplemap.ui.theme.sectionSurface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun TripsPanel(
    tripHistoryStore: TripHistoryStore,
    parkingLocation: Place? = null,
    onReturnToParking: (Place) -> Unit = {},
    onPlanAgain: (TripRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    var trips by remember(tripHistoryStore) { mutableStateOf<List<TripRecord>>(emptyList()) }
    var selectedTrip by remember { mutableStateOf<TripRecord?>(null) }
    var clearConfirmationVisible by remember { mutableStateOf(false) }
    val totalDistance = trips.sumOf { it.distanceMeters.toLong() }

    LaunchedEffect(tripHistoryStore) {
        trips = withContext(Dispatchers.IO) { tripHistoryStore.load() }
    }

    Surface(
        modifier = modifier
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(bottom = FloatingNavigationClearance)
            .fillMaxWidth()
            .fillMaxHeight(),
        color = MaterialTheme.colorScheme.background,
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("行程", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${trips.size} 条行程记录 · 累计 ${formatNavigationDistance(totalDistance.toInt())}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                }
                if (trips.isNotEmpty()) {
                    TextButton(onClick = { clearConfirmationVisible = true }) { Text("清空") }
                }
            }
            Spacer(Modifier.height(10.dp))
            parkingLocation?.let { parking ->
                ParkingShortcutCard(parking = parking, onReturnToParking = onReturnToParking)
                Spacer(Modifier.height(8.dp))
            }
            if (trips.isEmpty()) {
                EmptyTripsCard()
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentPadding = PaddingValues(bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(trips, key = TripRecord::id) { trip ->
                        TripItem(trip = trip, onClick = { selectedTrip = trip })
                    }
                }
            }
        }
    }
    if (clearConfirmationVisible) {
        AlertDialog(
            onDismissRequest = { clearConfirmationVisible = false },
            title = { Text("清空全部行程？") },
            text = { Text("所有本地行程记录将被删除，此操作无法撤销。") },
            confirmButton = {
                TextButton(onClick = {
                    clearConfirmationVisible = false
                    coroutineScope.launch {
                        if (withContext(Dispatchers.IO) { tripHistoryStore.clear() }) trips = emptyList()
                    }
                }) { Text("确认清空") }
            },
            dismissButton = {
                TextButton(onClick = { clearConfirmationVisible = false }) { Text("取消") }
            },
        )
    }
    selectedTrip?.let { trip ->
        TripReviewDialog(
            trip = trip,
            onDismiss = { selectedTrip = null },
            onPlanAgain = {
                selectedTrip = null
                onPlanAgain(trip)
            },
        )
    }
}

@Composable
private fun ParkingShortcutCard(parking: Place, onReturnToParking: (Place) -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button) { onReturnToParking(parking) }
            .semantics { contentDescription = "返回停车位置" },
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.24f)),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text("停车位置", color = MaterialTheme.colorScheme.secondary, fontWeight = FontWeight.SemiBold)
            Text(
                "规划步行路线返回上次保存的位置",
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                fontSize = 12.sp,
            )
            val detail = parking.address.ifBlank { parking.district }
            if (detail.isNotBlank()) {
                Text(detail, color = MaterialTheme.colorScheme.onSecondaryContainer, fontSize = 11.sp)
            }
        }
    }
}

@Composable
private fun EmptyTripsCard() {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.sectionSurface,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.panelBorder),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Text(
            text = "开始一次导航后，路线会出现在这里",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 18.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
        )
    }
}

@Composable
private fun TripItem(trip: TripRecord, onClick: () -> Unit) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "查看行程 ${trip.destination.name}" },
        color = MaterialTheme.colorScheme.surface,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.panelBorder),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = trip.destination.name,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = "${trip.origin.name} → ${trip.destination.name}",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        maxLines = 1,
                    )
                }
                Text(
                    text = tripStatusLabel(trip),
                    color = tripStatusColor(trip),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                TripMetaText(formatTripTime(trip.startedAtMillis))
                TripMetaText(formatRouteDuration(trip.durationSeconds))
                TripMetaText(formatRouteDistance(trip.distanceMeters))
            }
        }
    }
}

@Composable
private fun TripMetaText(value: String) {
    Text(value, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
}

private fun tripStatusLabel(trip: TripRecord): String =
    listOfNotNull(
        if (trip.simulated) "模拟" else null,
        when (trip.status) {
            TripStatus.Arrived -> "已到达"
            TripStatus.Cancelled -> "已取消"
            TripStatus.Failed -> "失败"
        },
    ).joinToString(" · ")

@Composable
private fun tripStatusColor(trip: TripRecord) = when (trip.status) {
    TripStatus.Arrived -> MaterialTheme.colorScheme.navigationAccent
    TripStatus.Cancelled -> MaterialTheme.colorScheme.onSurfaceVariant
    TripStatus.Failed -> MaterialTheme.colorScheme.error
}

@Composable
private fun TripReviewDialog(
    trip: TripRecord,
    onDismiss: () -> Unit,
    onPlanAgain: () -> Unit,
) {
    val review = trip.toTripReview()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("行程复盘")
                Text(
                    "${trip.origin.name} → ${trip.destination.name}",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 13.sp,
                    maxLines = 2,
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    color = MaterialTheme.colorScheme.sectionSurface,
                    shape = MaterialTheme.shapes.small,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.panelBorder),
                    tonalElevation = 0.dp,
                    shadowElevation = 0.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        TripReviewMetric("出发时间", formatTripTime(trip.startedAtMillis))
                        TripReviewMetric("出行方式", listOfNotNull(trip.mode.label, if (trip.simulated) "模拟导航" else null).joinToString(" · "))
                        TripReviewMetric("行驶时长", formatRouteDuration(review.elapsedSeconds))
                        TripReviewMetric("行驶距离", formatRouteDistance(review.distanceMeters))
                        TripReviewMetric("平均速度", "${review.averageSpeedKmh} km/h")
                        TripReviewMetric(
                            "结束状态",
                            when (trip.status) {
                                TripStatus.Arrived -> "已到达"
                                TripStatus.Cancelled -> "已取消"
                                TripStatus.Failed -> "导航失败"
                            },
                        )
                    }
                }
                Text(
                    "仅保存在本机，记录行程摘要，不保存轨迹点。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                )
            }
        },
        confirmButton = {
            TextButton(
                modifier = Modifier.semantics {
                    contentDescription = "再次规划到 ${trip.destination.name}"
                },
                onClick = onPlanAgain,
            ) { Text("再次规划") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("关闭") } },
    )
}

@Composable
private fun TripReviewMetric(label: String, value: String) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
    }
}

private fun formatTripTime(timeMillis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(timeMillis))
