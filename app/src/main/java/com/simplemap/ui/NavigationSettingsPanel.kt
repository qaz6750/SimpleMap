package com.simplemap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplemap.settings.AppOrientationMode
import com.simplemap.settings.NavigationPerspectiveMode
import com.simplemap.settings.NavigationThemeMode
import com.simplemap.settings.VoiceGuidanceLevel

@Composable
internal fun NavigationSettingsPanel(
    state: NavigationSettingsPanelState,
    onEvent: (NavigationSettingsEvent) -> Unit,
    modifier: Modifier = Modifier,
) {
    val voiceGuidanceEnabled = state.voiceGuidanceLevel != VoiceGuidanceLevel.Muted
    val voiceGuidanceLevel = state.voiceGuidanceLevel
    val quietHoursEnabled = state.quietHoursEnabled
    val quietHoursStartMinutes = state.quietHoursStartMinutes
    val quietHoursEndMinutes = state.quietHoursEndMinutes
    val trafficLayerEnabled = state.trafficLayerEnabled
    val routeAlertsEnabled = state.routeAlertsEnabled
    val trafficBarEnabled = state.trafficBarEnabled
    val eagleMapEnabled = state.eagleMapEnabled
    val autoZoomEnabled = state.autoZoomEnabled
    val perspectiveMode = state.perspectiveMode
    val themeMode = state.themeMode
    val orientationMode = state.orientationMode
    val alternativeRoutes = state.alternativeRoutes
    val onVoiceGuidanceChange: (Boolean) -> Unit = {
        onEvent(NavigationSettingsEvent.VoiceGuidanceChanged(it))
    }
    val onVoiceGuidanceLevelChange: (VoiceGuidanceLevel) -> Unit = {
        onEvent(NavigationSettingsEvent.VoiceGuidanceLevelChanged(it))
    }
    val onQuietHoursChange: (Boolean) -> Unit = {
        onEvent(NavigationSettingsEvent.QuietHoursChanged(it))
    }
    val onTrafficLayerChange: (Boolean) -> Unit = {
        onEvent(NavigationSettingsEvent.TrafficLayerChanged(it))
    }
    val onRouteAlertsChange: (Boolean) -> Unit = {
        onEvent(NavigationSettingsEvent.RouteAlertsChanged(it))
    }
    val onTrafficBarChange: (Boolean) -> Unit = {
        onEvent(NavigationSettingsEvent.TrafficBarChanged(it))
    }
    val onEagleMapChange: (Boolean) -> Unit = {
        onEvent(NavigationSettingsEvent.EagleMapChanged(it))
    }
    val onAutoZoomChange: (Boolean) -> Unit = {
        onEvent(NavigationSettingsEvent.AutoZoomChanged(it))
    }
    val onPerspectiveModeChange: (NavigationPerspectiveMode) -> Unit = {
        onEvent(NavigationSettingsEvent.PerspectiveModeChanged(it))
    }
    val onThemeModeChange: (NavigationThemeMode) -> Unit = {
        onEvent(NavigationSettingsEvent.ThemeModeChanged(it))
    }
    val onOrientationModeChange: (AppOrientationMode) -> Unit = {
        onEvent(NavigationSettingsEvent.OrientationModeChanged(it))
    }
    val onAlternativeRouteSelected: (Long) -> Unit = {
        onEvent(NavigationSettingsEvent.AlternativeRouteSelected(it))
    }
    val onOverview = { onEvent(NavigationSettingsEvent.OverviewRequested) }
    val onDismiss = { onEvent(NavigationSettingsEvent.Dismissed) }
    val nightMode = state.nightMode
    val isLandscape = state.isLandscape

    Surface(
        modifier = modifier.semantics {
            contentDescription = if (isLandscape) "横屏导航设置面板" else "竖屏导航设置面板"
        },
        color = if (nightMode) NavigationPanelColor else MaterialTheme.colorScheme.surface.copy(alpha = 0.99f),
        shape = if (isLandscape) {
            MaterialTheme.shapes.extraLarge
        } else {
            RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        },
        shadowElevation = 20.dp,
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            if (!isLandscape) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 10.dp, bottom = 2.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        modifier = Modifier
                            .size(width = 40.dp, height = 4.dp)
                            .background(
                                if (nightMode) NavigationPanelDivider else MaterialTheme.colorScheme.outlineVariant,
                                RoundedCornerShape(50),
                            ),
                    )
                }
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(
                        start = 18.dp,
                        top = if (isLandscape) 16.dp else 8.dp,
                        end = 18.dp,
                        bottom = 12.dp,
                    ),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        "导航设置",
                        color = if (nightMode) Color.White else MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        fontSize = 19.sp,
                    )
                    Text(
                        if (isLandscape) "车机侧边面板" else "设置会应用到后续行程",
                        color = if (nightMode) NavigationSecondaryText else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                    )
                }
                Button(onClick = onDismiss, shape = RoundedCornerShape(10.dp)) { Text("完成") }
            }
            HorizontalDivider(
                color = if (nightMode) NavigationPanelDivider else MaterialTheme.colorScheme.outlineVariant,
            )
            Column(
                modifier = Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                NavigationSettingsSection("常用控制", "高频操作优先显示", nightMode) {
                    NavigationSettingToggle(
                        "语音播报",
                        voiceGuidanceEnabled,
                        nightMode,
                        { onVoiceGuidanceChange(!voiceGuidanceEnabled) },
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        VoiceGuidanceLevel.entries.forEach { level ->
                            NavigationChoiceChip(
                                label = level.label,
                                visualLabel = level.label.removeSuffix("播报"),
                                selected = voiceGuidanceLevel == level,
                                nightMode = nightMode,
                                onClick = { onVoiceGuidanceLevelChange(level) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    NavigationSettingToggle(
                        "实时路况",
                        trafficLayerEnabled,
                        nightMode,
                        { onTrafficLayerChange(!trafficLayerEnabled) },
                    )
                    NavigationSettingToggle(
                        "自动缩放",
                        autoZoomEnabled,
                        nightMode,
                        { onAutoZoomChange(!autoZoomEnabled) },
                    )
                    NavigationSettingToggle(
                        "路线更新提示",
                        routeAlertsEnabled,
                        nightMode,
                        { onRouteAlertsChange(!routeAlertsEnabled) },
                    )
                }
                NavigationSettingsSection("语音与提醒", "保留所有播报偏好", nightMode) {
                    NavigationSettingToggle(
                        "静音时段 ${formatMinutesOfDay(quietHoursStartMinutes)}-${formatMinutesOfDay(quietHoursEndMinutes)}",
                        quietHoursEnabled,
                        nightMode,
                        { onQuietHoursChange(!quietHoursEnabled) },
                    )
                }
                NavigationSettingsSection("地图显示", "主题与辅助图层", nightMode) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NavigationPerspectiveMode.entries.forEach { mode ->
                            NavigationChoiceChip(
                                label = "导航视角 ${mode.label}",
                                visualLabel = mode.label,
                                selected = perspectiveMode == mode,
                                nightMode = nightMode,
                                onClick = { onPerspectiveModeChange(mode) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                    NavigationSettingToggle(
                        "路况柱",
                        trafficBarEnabled,
                        nightMode,
                        { onTrafficBarChange(!trafficBarEnabled) },
                    )
                    NavigationSettingToggle(
                        "鹰眼总览",
                        eagleMapEnabled,
                        nightMode,
                        { onEagleMapChange(!eagleMapEnabled) },
                    )
                    Text(
                        "地图主题",
                        color = if (nightMode) NavigationSecondaryText else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NavigationThemeMode.entries.forEach { mode ->
                            NavigationChoiceChip(
                                label = mode.label,
                                visualLabel = mode.label.removePrefix("始终"),
                                selected = themeMode == mode,
                                nightMode = nightMode,
                                onClick = { onThemeModeChange(mode) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
                NavigationSettingsSection("路线与布局", null, nightMode) {
                    NavigationSettingCommand("路线总览", "查看完整路线与剩余路段", nightMode) {
                        onOverview()
                    }
                    if (alternativeRoutes.size > 1) {
                        alternativeRoutes.forEach { route ->
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(min = 48.dp)
                                    .clickable(
                                        enabled = !route.selected,
                                        role = Role.Button,
                                        onClick = { onAlternativeRouteSelected(route.pathId) },
                                    )
                                    .semantics { contentDescription = "选择备选路线 ${route.label}" },
                                color = if (route.selected) {
                                    Color(0xFF244E78)
                                } else if (nightMode) {
                                    Color(0xFF25364D)
                                } else {
                                    MaterialTheme.colorScheme.surfaceVariant
                                },
                                shape = RoundedCornerShape(10.dp),
                            ) {
                                Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                                    Text(
                                        if (route.selected) "${route.label} · 当前路线" else route.label,
                                        color = if (nightMode) Color.White else MaterialTheme.colorScheme.onSurface,
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 12.sp,
                                    )
                                    Text(
                                        "${formatNavigationTime(route.durationSeconds)} · " +
                                            "${formatNavigationDistance(route.distanceMeters)} · " +
                                            "过路费 ${route.tollCostYuan} 元",
                                        color = if (nightMode) {
                                            NavigationSecondaryText
                                        } else {
                                            MaterialTheme.colorScheme.onSurfaceVariant
                                        },
                                        fontSize = 10.sp,
                                    )
                                }
                            }
                        }
                    }
                    Text(
                        "应用显示方向",
                        color = if (nightMode) NavigationSecondaryText else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppOrientationMode.entries.forEach { mode ->
                            NavigationChoiceChip(
                                label = mode.label,
                                visualLabel = mode.label,
                                selected = orientationMode == mode,
                                nightMode = nightMode,
                                onClick = { onOrientationModeChange(mode) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                    }
                }
            }
        }
    }
}
