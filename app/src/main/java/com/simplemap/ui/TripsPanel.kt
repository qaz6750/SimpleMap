package com.simplemap.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
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
    var clearConfirmationVisible by remember { mutableStateOf(false) }
    val totalDistance = trips.sumOf { it.distanceMeters.toLong() }

    LaunchedEffect(tripHistoryStore) {
        trips = withContext(Dispatchers.IO) { tripHistoryStore.load() }
    }

    Surface(
        modifier = modifier
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 94.dp)
            .fillMaxWidth(),
        color = Color(0xFAFFFFFF),
        shape = RoundedCornerShape(18.dp),
        shadowElevation = 12.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("行程", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        text = "${trips.size} 条行程记录 · 累计 ${formatNavigationDistance(totalDistance.toInt())}",
                        color = Color(0xFF68736F),
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
                    color = Color(0xFFEDF3FC),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text("停车位置", color = Color(0xFF1769E0), fontWeight = FontWeight.Bold)
                        Text("规划步行路线返回上次保存的位置", color = Color(0xFF5F6B68), fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
            }
            if (trips.isEmpty()) {
                Text(
                    text = "开始一次导航后，路线会出现在这里",
                    modifier = Modifier.padding(vertical = 30.dp),
                    color = Color(0xFF66726F),
                )
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 610.dp)) {
                    items(trips, key = TripRecord::id) { trip ->
                        TripItem(trip = trip, onClick = { onPlanAgain(trip) })
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
}

@Composable
private fun TripItem(trip: TripRecord, onClick: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "再次规划到 ${trip.destination.name}" }
            .padding(vertical = 13.dp),
    ) {
        Row {
            Text(
                text = trip.destination.name,
                modifier = Modifier.weight(1f),
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF17211F),
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
                    TripStatus.Cancelled -> Color(0xFF68736F)
                    TripStatus.Failed -> Color(0xFFB43E36)
                },
                fontSize = 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
        Text(
            text = "${trip.origin.name} → ${trip.destination.name}",
            color = Color(0xFF5F6B68),
            fontSize = 13.sp,
            maxLines = 1,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(formatTripTime(trip.startedAtMillis), color = Color(0xFF7A8582), fontSize = 12.sp)
            Text(formatRouteDuration(trip.durationSeconds), color = Color(0xFF7A8582), fontSize = 12.sp)
            Text(formatRouteDistance(trip.distanceMeters), color = Color(0xFF7A8582), fontSize = 12.sp)
        }
    }
    HorizontalDivider(color = Color(0xFFF0F3F1))
}

private fun formatTripTime(timeMillis: Long): String =
    SimpleDateFormat("MM-dd HH:mm", Locale.CHINA).format(Date(timeMillis))