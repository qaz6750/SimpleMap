package com.simplemap.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplemap.route.DriveRouteOptions
import com.simplemap.route.DriveRoutePreset
import com.simplemap.route.matchingPreset
import com.simplemap.route.toOptions
import com.simplemap.route.RouteMode

@Composable
internal fun RouteModeSelector(
    selectedMode: RouteMode,
    onSelected: (RouteMode) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surfaceVariant)
            .padding(2.dp),
        horizontalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        RouteMode.entries.forEach { mode ->
            val selected = mode == selectedMode
            if (selected) {
                Surface(
                    onClick = { onSelected(mode) },
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            role = Role.Tab
                            this.selected = true
                            contentDescription = mode.label
                        },
                    shape = PanelShapeSmall,
                    color = MaterialTheme.colorScheme.surface,
                    shadowElevation = 2.dp,
                ) {
                    Text(
                        mode.label,
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            } else {
                Surface(
                    onClick = { onSelected(mode) },
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            role = Role.Tab
                            this.selected = false
                            contentDescription = mode.label
                        },
                    shape = PanelShapeSmall,
                    color = Color.Transparent,
                ) {
                    Text(
                        mode.label,
                        modifier = Modifier.padding(vertical = 8.dp),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

internal enum class DrivePreference(val label: String) {
    Recommended("推荐"),
    AvoidCongestion("躲避拥堵"),
    AvoidHighway("不走高速"),
    SaveMoney("少收费"),
    PrioritizeHighway("高速优先"),
}

@Composable
internal fun DrivePreferencesSection(
    expanded: Boolean,
    onExpandedChange: (Boolean) -> Unit,
    options: DriveRouteOptions,
    onChanged: (DriveRouteOptions) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.widthIn(max = 520.dp), horizontalAlignment = Alignment.Start) {
        androidx.compose.animation.AnimatedVisibility(visible = expanded) {
            Surface(
                modifier = Modifier.padding(bottom = 4.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                shape = MaterialTheme.shapes.medium,
                shadowElevation = 10.dp,
            ) {
                DrivePreferenceSelector(
                    options = options,
                    onChanged = onChanged,
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                )
            }
        }
        Surface(
            onClick = { onExpandedChange(!expanded) },
            modifier = Modifier.semantics {
                contentDescription = if (expanded) "收起规划偏好" else "展开规划偏好"
            },
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
            shape = MaterialTheme.shapes.small,
            shadowElevation = 8.dp,
        ) {
            Text(
                text = if (expanded) "收起偏好" else "路线偏好",
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                color = MaterialTheme.colorScheme.primary,
                fontWeight = FontWeight.SemiBold,
                fontSize = 12.sp,
            )
        }
    }
}

@Composable
internal fun DrivePreferenceSelector(
    options: DriveRouteOptions,
    onChanged: (DriveRouteOptions) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .semantics { contentDescription = "驾车路线偏好" },
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text("偏好预设", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(DriveRoutePreset.entries, key = DriveRoutePreset::name) { preset ->
                val selected = options.matchingPreset() == preset
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .heightIn(min = 42.dp)
                        .selectable(
                            selected = selected,
                            role = Role.RadioButton,
                            onClick = {
                                if (!selected) onChanged(preset.toOptions())
                            },
                        )
                        .semantics { contentDescription = "路线预设 ${preset.label}" },
                ) {
                    Text(
                        text = preset.label,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
        Text("手动微调", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            items(DrivePreference.entries, key = DrivePreference::name) { preference ->
                val selected = when (preference) {
                    DrivePreference.Recommended -> options == DriveRouteOptions()
                    DrivePreference.AvoidCongestion -> options.avoidCongestion
                    DrivePreference.AvoidHighway -> options.avoidHighway
                    DrivePreference.SaveMoney -> options.saveMoney
                    DrivePreference.PrioritizeHighway -> options.prioritizeHighway
                }
                val updatedOptions = when (preference) {
                    DrivePreference.Recommended -> DriveRouteOptions()
                    DrivePreference.AvoidCongestion -> options.copy(
                        avoidCongestion = !options.avoidCongestion,
                    )
                    DrivePreference.AvoidHighway -> options.copy(
                        avoidHighway = !options.avoidHighway,
                        prioritizeHighway = false,
                    )
                    DrivePreference.SaveMoney -> options.copy(
                        saveMoney = !options.saveMoney,
                        prioritizeHighway = false,
                    )
                    DrivePreference.PrioritizeHighway -> options.copy(
                        avoidHighway = false,
                        saveMoney = false,
                        prioritizeHighway = !options.prioritizeHighway,
                    )
                }
                Surface(
                    shape = MaterialTheme.shapes.small,
                    color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier
                        .heightIn(min = 42.dp)
                        .toggleable(
                            value = selected,
                            role = Role.Checkbox,
                            onValueChange = { onChanged(updatedOptions) },
                        )
                        .semantics { contentDescription = "路线偏好 ${preference.label}" },
                ) {
                    Text(
                        text = preference.label,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                        color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 11.sp,
                        fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
internal fun SimpleWaypointFields(
    waypoints: List<WaypointDraft>,
    onQueryChange: (Int, String) -> Unit,
    onSearch: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    compact: Boolean = false,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        waypoints.forEachIndexed { index, waypoint ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                EndpointField(
                    label = "途经点 ${index + 1}",
                    query = waypoint.query,
                    selectedPlace = waypoint.place,
                    onQueryChange = { onQueryChange(index, it) },
                    onSearch = { onSearch(index) },
                    modifier = Modifier.weight(1f),
                    compact = compact,
                )
                TextButton(
                    onClick = { onRemove(index) },
                    modifier = Modifier
                        .then(if (compact) Modifier.width(36.dp) else Modifier)
                        .heightIn(min = 36.dp)
                        .semantics { contentDescription = "移除途经点 ${index + 1}" },
                    contentPadding = PaddingValues(horizontal = if (compact) 4.dp else 12.dp),
                ) {
                    Text("×", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                }
            }
            if (waypoint.query.isNotBlank() && waypoint.place == null) {
                Text(
                    text = "请从搜索结果中选择途经点",
                    modifier = Modifier.padding(start = 12.dp),
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

