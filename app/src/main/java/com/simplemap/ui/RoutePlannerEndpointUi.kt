package com.simplemap.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
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
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplemap.search.Place

internal const val CURRENT_LOCATION_ID = "current-location"

@Composable
internal fun EndpointEditor(
    originQuery: String,
    destinationQuery: String,
    origin: Place?,
    destination: Place?,
    onOriginChange: (String) -> Unit,
    onDestinationChange: (String) -> Unit,
    onOriginSearch: () -> Unit,
    onDestinationSearch: () -> Unit,
    waypointContent: @Composable () -> Unit,
    onSwap: () -> Unit,
    showAddWaypoint: Boolean,
    canAddWaypoint: Boolean,
    onAddWaypoint: () -> Unit,
    compact: Boolean = false,
) {
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            Box(Modifier.width(2.dp).weight(1f).background(MaterialTheme.colorScheme.outlineVariant))
            Box(
                Modifier
                    .size(8.dp)
                    .border(1.5.dp, MaterialTheme.colorScheme.tertiary, CircleShape),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            EndpointField("起点", originQuery, origin, onOriginChange, onOriginSearch, compact = compact)
            waypointContent()
            EndpointField("终点", destinationQuery, destination, onDestinationChange, onDestinationSearch, compact = compact)
        }
        Column(
            modifier = Modifier.fillMaxHeight(),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            TextButton(
                onClick = onSwap,
                enabled = origin != null || destination != null,
                modifier = Modifier.heightIn(min = 40.dp),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
            ) {
                Text("交换", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
            }
            if (showAddWaypoint) {
                Spacer(Modifier.height(2.dp))
                Surface(
                    onClick = onAddWaypoint,
                    enabled = canAddWaypoint,
                    shape = CircleShape,
                    color = if (canAddWaypoint) {
                        MaterialTheme.colorScheme.primaryContainer
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                    },
                    modifier = Modifier
                        .size(28.dp)
                        .semantics { contentDescription = "添加途经点" },
                ) {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            "＋",
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (canAddWaypoint) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.onSurfaceVariant
                            },
                        )
                    }
                }
            }
        }
    }
}

@Composable
internal fun EndpointField(
    label: String,
    query: String,
    selectedPlace: Place?,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 46.dp)
            .semantics { contentDescription = "$label 地点" },
        color = if (selectedPlace != null) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        } else {
            MaterialTheme.colorScheme.surface
        },
        shape = MaterialTheme.shapes.small,
        border = androidx.compose.foundation.BorderStroke(
            if (selectedPlace != null) 1.25.dp else 1.dp,
            if (selectedPlace != null) {
                MaterialTheme.colorScheme.primary.copy(alpha = 0.75f)
            } else if (label == "终点") {
                MaterialTheme.colorScheme.secondary.copy(alpha = 0.35f)
            } else {
                MaterialTheme.colorScheme.outlineVariant
            },
        ),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 0.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        color = when {
                            label == "终点" -> AlertRed
                            label == "起点" -> MaterialTheme.colorScheme.primary
                            else -> MaterialTheme.colorScheme.outline
                        },
                        shape = CircleShape,
                    ),
            )
            Spacer(Modifier.width(8.dp))
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 14.sp,
                ),
                cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                decorationBox = { innerTextField ->
                    if (query.isBlank()) {
                        Text(
                            if (label == "起点") "我的位置或输入起点" else "输入目的地",
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            fontSize = 13.sp,
                        )
                    }
                    innerTextField()
                },
            )
            TextButton(
                onClick = onSearch,
                modifier = Modifier
                    .then(if (compact) Modifier.width(44.dp) else Modifier)
                    .heightIn(min = 44.dp),
                contentPadding = PaddingValues(horizontal = if (compact) 4.dp else 12.dp),
            ) {
                Text(
                    if (selectedPlace != null && compact) "改" else if (selectedPlace != null) "更改" else "搜索",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}


@Composable
internal fun SuggestionList(
    places: List<Place>,
    message: String?,
    onSelected: (Place) -> Unit,
    onDismiss: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "搜索结果",
            color = MaterialTheme.colorScheme.onSurface,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
        )
        TextButton(
            onClick = onDismiss,
            modifier = Modifier.semantics { contentDescription = "取消地点搜索" },
        ) {
            Text("取消")
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    if (message != null) {
        Text(
            text = message,
            modifier = Modifier.padding(vertical = 16.dp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    } else {
        Column {
            places.forEach { place ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onSelected(place) }
                        .semantics { contentDescription = "选择地点 ${place.name}" }
                        .heightIn(min = 52.dp)
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                ) {
                    Text(place.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                    Text(
                        text = listOf(place.district, place.address)
                            .filter(String::isNotBlank)
                            .joinToString(" · "),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                        maxLines = 1,
                    )
                }
                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            }
        }
    }
}
