package com.simplemap.ui

import android.app.TimePickerDialog
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import android.widget.Toast
import com.simplemap.BuildConfig
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplemap.settings.NavigationSettings
import com.simplemap.settings.AppOrientationMode
import com.simplemap.settings.NavigationPerspectiveMode
import com.simplemap.settings.NavigationThemeMode
import com.simplemap.settings.VoiceGuidanceLevel
import com.simplemap.ui.theme.panelBorder
import com.simplemap.ui.theme.sectionSurfaceEmphasis
import com.simplemap.update.AppUpdateInfo
import com.simplemap.update.AppUpdateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
internal fun SettingsSection(
    settings: NavigationSettings,
    updateRepository: AppUpdateRepository,
    onChanged: (NavigationSettings) -> Unit,
    onClearLocalData: () -> Unit,
    onRevokePrivacyConsent: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    var pendingCommand by remember { mutableStateOf<SettingsCommand?>(null) }
    var updateState by remember { mutableStateOf<AppUpdateState>(AppUpdateState.Idle) }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SectionCard {
            Text("显示", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text("主题模式", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            SettingChoiceRow(
                options = NavigationThemeMode.entries,
                selected = settings.themeMode,
                label = NavigationThemeMode::label,
                onSelect = { mode -> onChanged(settings.copy(themeMode = mode, nightMode = mode == NavigationThemeMode.Night)) },
            )
            Text(
                "按时间自动在 19:00 至次日 06:00 使用夜间主题；导航进入隧道时会临时切换。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            Text("屏幕方向", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
            SettingChoiceRow(
                options = AppOrientationMode.entries,
                selected = settings.orientationMode,
                label = AppOrientationMode::label,
                onSelect = { mode -> onChanged(settings.copy(orientationMode = mode)) },
            )
            Text(
                "方向偏好应用于手机窗口；Android 16 及以上的大屏设备会按当前窗口尺寸自动布局。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
                lineHeight = 16.sp,
            )
        }

        SectionCard {
            Text("导航语音与提醒", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            SettingToggle("语音导航", "播报转向、路况与到达提醒", settings.resolvedVoiceGuidanceLevel != VoiceGuidanceLevel.Muted) {
                val level = if (it) VoiceGuidanceLevel.Detailed else VoiceGuidanceLevel.Muted
                onChanged(settings.copy(voiceGuidance = it, voiceGuidanceLevel = level))
            }
            SettingChoiceRow(
                options = VoiceGuidanceLevel.entries,
                selected = settings.resolvedVoiceGuidanceLevel,
                label = VoiceGuidanceLevel::label,
                onSelect = { level ->
                    onChanged(settings.copy(voiceGuidance = level != VoiceGuidanceLevel.Muted, voiceGuidanceLevel = level))
                },
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            SettingToggle("静音时段", "在设定时间内暂停常规导航播报", settings.quietHoursEnabled) {
                onChanged(settings.copy(quietHoursEnabled = it))
            }
            if (settings.quietHoursEnabled) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SettingsCommandButton(
                        title = "开始 ${formatMinutesOfDay(settings.quietHoursStartMinutes)}",
                        description = "选择开始时间",
                        modifier = Modifier.weight(1f),
                    ) {
                        showTimePicker(context, settings.quietHoursStartMinutes) { minutes ->
                            onChanged(settings.copy(quietHoursStartMinutes = minutes))
                        }
                    }
                    SettingsCommandButton(
                        title = "结束 ${formatMinutesOfDay(settings.quietHoursEndMinutes)}",
                        description = "选择结束时间",
                        modifier = Modifier.weight(1f),
                    ) {
                        showTimePicker(context, settings.quietHoursEndMinutes) { minutes ->
                            onChanged(settings.copy(quietHoursEndMinutes = minutes))
                        }
                    }
                }
            }
            SettingToggle("实时路况", "在地图和导航路线中显示拥堵", settings.trafficLayer) {
                onChanged(settings.copy(trafficLayer = it))
            }
            Text("导航视角", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                NavigationPerspectiveMode.entries.forEach { mode ->
                    CompactChoiceChip(
                        text = mode.label,
                        selected = settings.perspectiveMode == mode,
                        onClick = { onChanged(settings.copy(perspectiveMode = mode)) },
                        modifier = Modifier.weight(1f),
                        role = Role.RadioButton,
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedContentColor = MaterialTheme.colorScheme.primary,
                        unselectedContainerColor = MaterialTheme.colorScheme.surface,
                        unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            SettingToggle("路线状态提醒", "偏航或路线更新时显示提示", settings.routeAlerts) {
                onChanged(settings.copy(routeAlerts = it))
            }
        }

        SectionCard {
            Text("关于", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics {
                        contentDescription = "当前版本 ${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})"
                    },
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text("SimpleMap", color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                    Text("当前版本", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("v${BuildConfig.VERSION_NAME}", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                    Text("构建 ${BuildConfig.VERSION_CODE}", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp)
                }
            }
            val currentUpdateState = updateState
            SettingsCommandButton(
                title = when (currentUpdateState) {
                    AppUpdateState.Idle -> "检查更新"
                    AppUpdateState.Checking -> "正在检查更新"
                    is AppUpdateState.UpToDate -> "已是最新版本"
                    is AppUpdateState.Available -> "发现新版本 v${currentUpdateState.info.latestVersionName}"
                    is AppUpdateState.Failed -> "检查失败，点击重试"
                },
                description = when (currentUpdateState) {
                    AppUpdateState.Idle -> "从 GitHub Release 获取最新正式版本"
                    AppUpdateState.Checking -> "正在连接 GitHub Release"
                    is AppUpdateState.UpToDate -> "最新正式版本为 v${currentUpdateState.versionName}"
                    is AppUpdateState.Available -> "点击前往 GitHub Release 下载"
                    is AppUpdateState.Failed -> currentUpdateState.message
                },
                enabled = currentUpdateState != AppUpdateState.Checking,
            ) {
                if (currentUpdateState is AppUpdateState.Available) {
                    runCatching {
                        context.startActivity(
                            Intent(Intent.ACTION_VIEW, Uri.parse(currentUpdateState.info.releasePageUrl)),
                        )
                    }.onFailure {
                        Toast.makeText(context, "无法打开系统页面", Toast.LENGTH_SHORT).show()
                    }
                } else {
                    updateState = AppUpdateState.Checking
                    coroutineScope.launch {
                        val result = withContext(Dispatchers.IO) {
                            updateRepository.checkForUpdate(BuildConfig.VERSION_NAME)
                        }
                        updateState = result.fold(
                            onSuccess = { info ->
                                if (info.updateAvailable) {
                                    AppUpdateState.Available(info)
                                } else {
                                    AppUpdateState.UpToDate(info.latestVersionName)
                                }
                            },
                            onFailure = { error ->
                                AppUpdateState.Failed(error.localizedMessage ?: "无法获取最新版本")
                            },
                        )
                    }
                }
            }
        }

        SectionCard {
            Text("隐私与权限", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
            Text(
                "地图服务仅在你同意隐私说明后初始化；定位权限由系统设置管理。",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
                lineHeight = 20.sp,
            )
            SettingsCommandButton("系统应用权限", "管理定位、通知等系统权限") {
                runCatching {
                    context.startActivity(
                        Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        ),
                    )
                }.onFailure {
                    Toast.makeText(context, "无法打开系统页面", Toast.LENGTH_SHORT).show()
                }
            }
            SettingsCommandButton("清除本地数据", "删除收藏、行程、停车位置与导航设置") {
                pendingCommand = SettingsCommand.ClearData
            }
            SettingsCommandButton(
                title = "撤回隐私同意",
                description = "下次启动时重新显示隐私说明",
                destructive = true,
            ) {
                pendingCommand = SettingsCommand.RevokeConsent
            }
        }
    }

    pendingCommand?.let { command ->
        AlertDialog(
            onDismissRequest = { pendingCommand = null },
            title = { Text(if (command == SettingsCommand.ClearData) "清除本地数据？" else "撤回隐私同意？") },
            text = {
                Text(
                    if (command == SettingsCommand.ClearData) {
                        "收藏、行程、停车位置和导航设置将被删除，此操作无法撤销。"
                    } else {
                        "应用将关闭。下次启动前不会再次初始化地图服务。"
                    },
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingCommand = null
                        if (command == SettingsCommand.ClearData) onClearLocalData() else onRevokePrivacyConsent()
                    },
                ) { Text(if (command == SettingsCommand.ClearData) "确认清除" else "确认撤回") }
            },
            dismissButton = {
                TextButton(onClick = { pendingCommand = null }) { Text("取消") }
            },
        )
    }
}

internal enum class SettingsCommand { ClearData, RevokeConsent }

private sealed interface AppUpdateState {
    data object Idle : AppUpdateState
    data object Checking : AppUpdateState
    data class UpToDate(val versionName: String) : AppUpdateState
    data class Available(val info: AppUpdateInfo) : AppUpdateState
    data class Failed(val message: String) : AppUpdateState
}

@Composable
internal fun SettingsCommandButton(
    title: String,
    description: String,
    modifier: Modifier = Modifier,
    destructive: Boolean = false,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = title },
        color = if (destructive) MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.sectionSurfaceEmphasis,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.panelBorder),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                title,
                color = if (destructive) MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
            )
            Text(
                description,
                color = if (destructive) MaterialTheme.colorScheme.onErrorContainer.copy(alpha = 0.82f) else MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 11.sp,
            )
        }
    }
}

@Composable
internal fun <T> SettingChoiceRow(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    LazyRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        items(options.size) { index ->
            val option = options[index]
            CompactChoiceChip(
                text = label(option),
                selected = option == selected,
                onClick = { onSelect(option) },
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedContentColor = MaterialTheme.colorScheme.primary,
                unselectedContainerColor = MaterialTheme.colorScheme.surface,
                unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
internal fun CompactChoiceChip(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    role: Role = Role.RadioButton,
    selectedContainerColor: Color,
    selectedContentColor: Color,
    unselectedContainerColor: Color,
    unselectedContentColor: Color,
) {
    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .selectable(
                selected = selected,
                enabled = enabled,
                role = role,
                onClick = onClick,
            )
            .semantics {
                contentDescription = text
                this.role = role
                this.selected = selected
            },
        color = if (selected) selectedContainerColor else unselectedContainerColor,
        shape = MaterialTheme.shapes.small,
        border = BorderStroke(1.dp, if (selected) selectedContentColor.copy(alpha = 0.18f) else MaterialTheme.colorScheme.panelBorder),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(
                text,
                color = if (selected) selectedContentColor else unselectedContentColor,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                fontSize = 12.sp,
            )
        }
    }
}

internal fun showTimePicker(context: android.content.Context, initialMinutes: Int, onSelected: (Int) -> Unit) {
    val safeMinutes = initialMinutes.coerceIn(0, 24 * 60 - 1)
    TimePickerDialog(
        context,
        { _, hour, minute -> onSelected(hour * 60 + minute) },
        safeMinutes / 60,
        safeMinutes % 60,
        true,
    ).show()
}

internal fun formatMinutesOfDay(minutes: Int): String {
    val safeMinutes = minutes.coerceIn(0, 24 * 60 - 1)
    return "%02d:%02d".format(safeMinutes / 60, safeMinutes % 60)
}

@Composable
internal fun SettingToggle(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.Medium)
            Text(description, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            modifier = Modifier.semantics { contentDescription = title },
        )
    }
}
