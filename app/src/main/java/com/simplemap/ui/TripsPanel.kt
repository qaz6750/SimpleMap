package com.simplemap.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplemap.trips.TripHistoryStore
import com.simplemap.trips.TripRecord
import com.simplemap.trips.TripStatus
import com.simplemap.trips.toTripReview
import com.simplemap.search.Place
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
        Column(modifier = Modifier.padding(18.dp)) {
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
            Spacer(Modifier.height(12.dp))
            parkingLocation?.let { parking ->
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onReturnToParking(parking) }
                        .semantics { contentDescription = "返回停车位置" },
                    color = MaterialTheme.colorScheme.primaryContainer,
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("停车位置", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                        Text("规划步行路线返回上次保存的位置", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (trips.isEmpty()) {
                Text(
                    text = "开始一次导航后，路线会出现在这里",
                    modifier = Modifier.padding(vertical = 30.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                LazyColumn(modifier = Modifier.weight(1f)) {
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
private fun TripItem(trip: TripRecord, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "查看行程 ${trip.destination.name}" }
            .padding(vertical = 13.dp),
    ) {
        Row {
            Text(
                text = trip.destination.name,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
            )
            Text(
                listOfNotNull(
                    if (trip.simulated) "模拟" else null,
                    when (trip.status) {
                        TripStatus.Arrived -> "已到达"
                        TripStatus.Cancelled -> "已取消"
                        TripStatus.Failed -> "失败"
                    },
                ).joinToString(" · "),
                color = when (trip.status) {
                    TripStatus.Arrived -> Color(0xFF1769E0)
                    TripStatus.Cancelled -> MaterialTheme.colorScheme.onSurfaceVariant
                    TripStatus.Failed -> Color(0xFFB43E36)
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = "${trip.origin.name} → ${trip.destination.name}",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 13.sp,
            maxLines = 1,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(formatTripTime(trip.startedAtMillis), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            Text(formatRouteDuration(trip.durationSeconds), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Text(formatRouteDistance(trip.distanceMeters), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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
            Column {
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
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
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