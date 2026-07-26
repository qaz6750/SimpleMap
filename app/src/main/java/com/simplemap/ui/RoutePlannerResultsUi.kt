package com.simplemap.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplemap.route.RoutePlan

@Composable
internal fun RouteResults(
    state: RoutePlanState,
    selectedPlan: RoutePlan?,
    onSelected: (RoutePlan) -> Unit,
    detailsExpanded: Boolean,
    onDetailsExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        RoutePlanState.Idle -> Unit
        RoutePlanState.Loading -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        is RoutePlanState.Failed -> Text(
            text = state.message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            color = MaterialTheme.colorScheme.error,
        )
        is RoutePlanState.Ready -> {
            if (state.plans.isEmpty()) {
                Text(
                    text = "没有找到可用路线",
                    modifier = Modifier.padding(vertical = 14.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                if (detailsExpanded && selectedPlan != null) {
                    RoutePlanDetails(
                        plan = selectedPlan,
                        onCollapse = { onDetailsExpandedChange(false) },
                        modifier = modifier,
                    )
                } else {
                    Text(
                        text = "为你推荐 ${state.plans.size} 条路线",
                        modifier = Modifier.padding(horizontal = 14.dp),
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Spacer(Modifier.height(6.dp))
                    LazyRow(
                        modifier = modifier,
                        contentPadding = PaddingValues(horizontal = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(state.plans, key = RoutePlan::id) { plan ->
                            RoutePlanItem(
                                plan = plan,
                                selected = plan.id == selectedPlan?.id,
                                onClick = { onSelected(plan) },
                            )
                        }
                    }
                    selectedPlan?.let { plan ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 14.dp, top = 6.dp, end = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "${formatRouteDuration(plan.durationSeconds)} · " +
                                        formatRouteDistance(plan.distanceMeters),
                                    color = MaterialTheme.colorScheme.onSurface,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.SemiBold,
                                )
                                Text(
                                    text = plan.summary,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 11.sp,
                                    maxLines = 1,
                                )
                            }
                            TextButton(
                                onClick = { onDetailsExpandedChange(true) },
                                modifier = Modifier
                                    .heightIn(min = 42.dp)
                                    .semantics { contentDescription = "查看路线详情" },
                            ) {
                                Text("查看详情", fontSize = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
internal fun RoutePlanDetails(
    plan: RoutePlan,
    onCollapse: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .semantics { contentDescription = "路线详情列表" },
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "路线详情",
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = plan.summary,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontSize = 11.sp,
                    maxLines = 1,
                )
            }
            TextButton(
                onClick = onCollapse,
                modifier = Modifier.semantics { contentDescription = "收起路线详情" },
            ) {
                Text("收起")
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.primaryContainer, PanelShapeSmall)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            RouteDetailMetric("用时", formatRouteDuration(plan.durationSeconds))
            RouteDetailMetric("里程", formatRouteDistance(plan.distanceMeters))
            RouteDetailMetric("费用", plan.costYuan?.let { "%.1f 元".format(it) } ?: "--")
        }
        Spacer(Modifier.height(10.dp))
        if (plan.steps.isEmpty()) {
            Text(
                text = "暂无分段导航信息",
                modifier = Modifier.padding(vertical = 10.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
            )
        } else {
            plan.steps.forEachIndexed { index, step ->
                RouteDetailStep(
                    number = index + 1,
                    text = step,
                    isLast = index == plan.steps.lastIndex,
                )
            }
        }
    }
}

@Composable
internal fun RouteDetailMetric(label: String, value: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, color = MaterialTheme.colorScheme.primary, fontSize = 13.sp, fontWeight = FontWeight.Bold)
        Text(label, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
    }
}

@Composable
internal fun RouteDetailStep(number: Int, text: String, isLast: Boolean) {
    Row(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.width(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .background(MaterialTheme.colorScheme.primary, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text("$number", color = MaterialTheme.colorScheme.onPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
            if (!isLast) {
                Box(
                    Modifier
                        .width(2.dp)
                        .height(30.dp)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.25f)),
                )
            }
        }
        Text(
            text = text,
            modifier = Modifier.weight(1f).padding(start = 8.dp, top = 3.dp, bottom = if (isLast) 12.dp else 8.dp),
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            lineHeight = 18.sp,
        )
    }
}

@Composable
internal fun RoutePlanItem(
    plan: RoutePlan,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .widthIn(min = 150.dp, max = 180.dp)
            .heightIn(min = 76.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = "路线方案 ${formatRouteDuration(plan.durationSeconds)}"
            }
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.medium,
            ),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shadowElevation = if (selected) 5.dp else 1.dp,
    ) {
        Column(modifier = Modifier.padding(8.dp).fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatRouteDuration(plan.durationSeconds),
                    fontWeight = FontWeight.Bold,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontSize = 16.sp,
                )
                Spacer(Modifier.weight(1f))
                if (selected) {
                    Text(
                        "当前",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(plan.summary, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatRouteDistance(plan.distanceMeters),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 11.sp,
                )
                Spacer(Modifier.weight(1f))
                plan.costYuan?.let { Text("约 ¥%.1f".format(it), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp) }
            }
        }
    }
}

internal fun formatRouteDuration(durationSeconds: Long): String {
    val minutes = (durationSeconds + 59) / 60
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return when {
        hours == 0L -> "$minutes 分钟"
        remainingMinutes == 0L -> "$hours 小时"
        else -> "$hours 小时 $remainingMinutes 分钟"
    }
}

internal fun formatRouteDistance(distanceMeters: Int): String = if (distanceMeters < 1_000) {
    "$distanceMeters 米"
} else {
    "%.1f 公里".format(distanceMeters / 1_000f)
}
