package com.simplemap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplemap.route.DriveRouteOptions
import com.simplemap.route.matchingPreset
import com.simplemap.route.RoutePlan
import kotlin.math.roundToInt

@Composable
internal fun LandscapeRouteSelectionPanel(
    plans: List<RoutePlan>,
    selectedPlan: RoutePlan?,
    detailsExpanded: Boolean,
    onEditRoute: () -> Unit,
    onAddWaypoint: () -> Unit,
    onRouteSelected: (RoutePlan) -> Unit,
    onDetailsExpandedChange: (Boolean) -> Unit,
    onStartSimulatedNavigation: () -> Unit,
    onStartNavigation: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.semantics { contentDescription = "横屏路线选择面板" },
        color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.97f),
        shape = PanelShapeLarge,
        shadowElevation = 18.dp,
    ) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth().height(46.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Surface(
                    onClick = onEditRoute,
                    modifier = Modifier.size(44.dp).semantics { contentDescription = "编辑起终点" },
                    color = Color.Transparent,
                    shape = CircleShape,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("‹", color = MaterialTheme.colorScheme.onSurface, fontSize = 38.sp, fontWeight = FontWeight.Medium)
                    }
                }
                LandscapeRouteHeaderAction(
                    label = "途经点",
                    symbol = "○",
                    onClick = onAddWaypoint,
                    modifier = Modifier.weight(1f),
                )
            }
            if (detailsExpanded && selectedPlan != null) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 6.dp),
                ) {
                    RoutePlanDetails(
                        plan = selectedPlan,
                        onCollapse = { onDetailsExpandedChange(false) },
                    )
                }
            } else {
                Column(
                    modifier = Modifier.weight(1f).padding(top = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    plans.take(3).forEach { plan ->
                        LandscapeRoutePlanRow(
                            plan = plan,
                            selected = plan.id == selectedPlan?.id,
                            onClick = { onRouteSelected(plan) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp).height(52.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    onClick = { onDetailsExpandedChange(!detailsExpanded) },
                    modifier = Modifier.width(48.dp).fillMaxHeight().semantics { contentDescription = "更多路线操作" },
                    color = MaterialTheme.colorScheme.surface,
                    shape = PanelShapeMedium,
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Text("•••", color = MaterialTheme.colorScheme.onSurface, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    }
                }
                OutlinedButton(
                    onClick = onStartSimulatedNavigation,
                    enabled = selectedPlan != null,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = PanelShapeMedium,
                    contentPadding = PaddingValues(horizontal = 6.dp),
                ) {
                    Text("模拟导航", maxLines = 1, fontSize = 13.sp)
                }
                Button(
                    onClick = onStartNavigation,
                    enabled = selectedPlan != null,
                    modifier = Modifier.weight(1f).fillMaxHeight(),
                    shape = PanelShapeMedium,
                ) {
                    Text("开始导航", maxLines = 1, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

@Composable
internal fun LandscapeDrivePreferences(
    expanded: Boolean,
    options: DriveRouteOptions,
    onExpandedChange: (Boolean) -> Unit,
    onChanged: (DriveRouteOptions) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier, horizontalAlignment = Alignment.Start) {
        Surface(
            onClick = { onExpandedChange(!expanded) },
            modifier = Modifier.semantics {
                contentDescription = if (expanded) "收起规划偏好" else "展开规划偏好"
            },
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            shape = PanelShapeMedium,
            shadowElevation = 10.dp,
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("◆", color = MaterialTheme.colorScheme.primary, fontSize = 12.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    text = options.matchingPreset()?.label?.let { "高德$it" } ?: "路线偏好",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
            }
        }
        androidx.compose.animation.AnimatedVisibility(visible = expanded) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                shape = RoundedCornerShape(14.dp),
                shadowElevation = 14.dp,
            ) {
                DrivePreferenceSelector(
                    options = options,
                    onChanged = onChanged,
                    modifier = Modifier.padding(10.dp),
                )
            }
        }
    }
}

@Composable
internal fun LandscapeRouteHeaderAction(
    label: String,
    symbol: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        onClick = onClick,
        modifier = modifier.fillMaxHeight(),
        color = MaterialTheme.colorScheme.surface,
        shape = PanelShapeMedium,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 9.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(symbol, color = MaterialTheme.colorScheme.onSurface, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.width(5.dp))
            Text(label, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
        }
    }
}

@Composable
internal fun LandscapeRoutePlanRow(
    plan: RoutePlan,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 58.dp, max = 78.dp)
            .selectable(selected = selected, role = Role.RadioButton, onClick = onClick)
            .semantics { contentDescription = "路线方案 ${formatRouteDuration(plan.durationSeconds)}" },
        color = if (selected) MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.surfaceVariant,
        shape = PanelShapeMedium,
        border = if (selected) androidx.compose.foundation.BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 7.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = formatLandscapeRouteDuration(plan.durationSeconds),
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                fontSize = 19.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = plan.summary,
                    modifier = Modifier
                        .background(
                            if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh,
                            RoundedCornerShape(6.dp),
                        )
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                )
                Spacer(Modifier.width(7.dp))
                Text(formatRouteDistance(plan.distanceMeters), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                plan.costYuan?.let { cost ->
                    Spacer(Modifier.width(7.dp))
                    Text("¥ ${cost.roundToInt()}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
            }
        }
    }
}

internal fun formatLandscapeRouteDuration(durationSeconds: Long): String {
    val minutes = (durationSeconds + 59) / 60
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return when {
        hours == 0L -> "${minutes}分钟"
        remainingMinutes == 0L -> "${hours}小时"
        else -> "${hours}小时${remainingMinutes}分钟"
    }
}
