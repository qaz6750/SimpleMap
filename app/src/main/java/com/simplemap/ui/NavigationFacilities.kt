package com.simplemap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplemap.navigation.NavigationFacilityKind
import com.simplemap.navigation.NavigationRouteFacility
import com.simplemap.navigation.NavigationUiState

@Composable
internal fun NavigationFacilityBands(
    facilities: List<NavigationRouteFacility>,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    if (facilities.isEmpty()) return
    val visibleFacilities = facilities.sortedBy(NavigationRouteFacility::distanceMeters).take(2)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(end = 14.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "查看全部沿途设施" },
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        visibleFacilities.forEachIndexed { index, facility ->
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp)
                    .semantics {
                        contentDescription = "沿途信息条 ${facility.kind.label} ${facility.name}"
                    },
                color = when (facility.kind) {
                    NavigationFacilityKind.TollGate -> NavigationBlueAccent
                    NavigationFacilityKind.ServiceArea -> ServiceAreaGreen
                },
                shape = RoundedCornerShape(6.dp),
                shadowElevation = if (index == 0) 8.dp else 3.dp,
            ) {
                Row(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = facility.kind.shortLabel,
                        modifier = Modifier.width(38.dp),
                        color = Color.White.copy(alpha = 0.78f),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                    Text(
                        text = facility.name,
                        modifier = Modifier.weight(1f),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatNavigationDistance(facility.distanceMeters),
                        modifier = Modifier.padding(start = 10.dp),
                        color = Color.White,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
            }
        }
    }
}

@Composable
internal fun NavigationFacilitiesPanel(
    facilities: List<NavigationRouteFacility>,
    nightMode: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val panelColor = if (nightMode) NavigationPanelColor else DayPanelSoft
    val titleColor = if (nightMode) Color.White else DayInkText
    val secondaryColor = if (nightMode) NavigationSecondaryText else DayTertiaryText
    val dividerColor = if (nightMode) NavigationPanelDivider else DayPanelDivider
    Surface(
        modifier = modifier.semantics { contentDescription = "全路线沿途设施" },
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
                    Text("沿途设施", color = titleColor, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text("全路线 ${facilities.size} 处", color = GpsPanelAccent, fontSize = 11.sp)
                }
                TextButton(onClick = onDismiss) { Text("关闭", color = GpsPanelAccent) }
            }
            HorizontalDivider(color = dividerColor)
            LazyColumn(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .semantics { contentDescription = "沿途设施列表" },
            ) {
                items(facilities, key = { "${it.kind}-${it.name}" }) { facility ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 5.dp)
                            .background(
                                color = facility.kind.cardColor.copy(alpha = if (nightMode) 0.18f else 0.09f),
                                shape = RoundedCornerShape(9.dp),
                            )
                            .padding(horizontal = 10.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Surface(
                            color = facility.kind.cardColor,
                            shape = RoundedCornerShape(7.dp),
                        ) {
                            Text(
                                facility.kind.shortLabel,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                            )
                        }
                        Column(modifier = Modifier.padding(start = 11.dp).weight(1f)) {
                            Text(
                                facility.name,
                                color = titleColor,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(facility.distanceAndTimeLabel, color = secondaryColor, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }
}

private val NavigationFacilityKind.label: String
    get() = when (this) {
        NavigationFacilityKind.ServiceArea -> "服务区"
        NavigationFacilityKind.TollGate -> "收费站"
    }

private val NavigationFacilityKind.shortLabel: String
    get() = when (this) {
        NavigationFacilityKind.ServiceArea -> "服务"
        NavigationFacilityKind.TollGate -> "收费"
    }

private val NavigationFacilityKind.cardColor: Color
    get() = when (this) {
        NavigationFacilityKind.ServiceArea -> ServiceAreaGreen
        NavigationFacilityKind.TollGate -> NavigationBlueAccent
    }

internal fun visibleNavigationFacilities(
    facilities: List<NavigationRouteFacility>,
): List<NavigationRouteFacility> = NavigationFacilityKind.entries.mapNotNull { kind ->
    facilities.firstOrNull { facility -> facility.kind == kind }
}.sortedBy(NavigationRouteFacility::distanceMeters)

internal fun highwayNavigationFacilities(state: NavigationUiState): List<NavigationRouteFacility> {
    val onHighway = state.highwayExit.isNotBlank() ||
        state.currentRoad.contains("高速") ||
        state.nextRoad.contains("高速") ||
        state.routeFacilities.any { facility -> facility.kind == NavigationFacilityKind.ServiceArea }
    if (!onHighway) return emptyList()
    return state.routeFacilities.sortedBy(NavigationRouteFacility::distanceMeters)
}

private val NavigationRouteFacility.distanceAndTimeLabel: String
    get() = buildString {
        append(formatNavigationDistance(distanceMeters))
        if (remainingTimeSeconds > 0) {
            append(" · 约 ")
            append(formatNavigationTime(remainingTimeSeconds))
        }
    }
