package com.simplemap.ui

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplemap.search.FavoriteGroup
import com.simplemap.search.Place
import java.util.Locale

@Composable
internal fun PlaceDetailPanel(
    place: Place,
    isFavorite: Boolean,
    isHome: Boolean,
    isWork: Boolean,
    interactionEnabled: Boolean,
    onFavoriteClick: () -> Unit,
    onSetFavoriteGroup: (FavoriteGroup) -> Unit,
    onDirectionsClick: () -> Unit,
    onNearbySearch: (String) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(start = 18.dp, end = 18.dp, bottom = 14.dp)
            .fillMaxWidth()
            .widthIn(max = 680.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
        shape = PanelShapeLarge,
        shadowElevation = 14.dp,
    ) {
        Column(
            modifier = Modifier
                .heightIn(max = 520.dp)
                .verticalScroll(rememberScrollState())
                .padding(18.dp),
        ) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = place.name,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (place.address.isNotBlank()) {
                        Spacer(Modifier.height(5.dp))
                        Text(place.address, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2)
                    }
                }
                TextButton(onClick = onClose, enabled = interactionEnabled) { Text("关闭") }
            }
            Spacer(Modifier.height(12.dp))
            PlaceMetadata(place)
            Spacer(Modifier.height(12.dp))
            NearbyQuickSearchRow(enabled = interactionEnabled, onNearbySearch = onNearbySearch)
            Spacer(Modifier.height(12.dp))
            FavoriteGroupRow(
                isHome = isHome,
                isWork = isWork,
                enabled = interactionEnabled,
                onSetFavoriteGroup = onSetFavoriteGroup,
            )
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onFavoriteClick,
                    enabled = interactionEnabled,
                    modifier = Modifier.weight(1f),
                    shape = PanelShapeSmall,
                ) {
                    Text(if (isFavorite) "取消收藏" else "收藏")
                }
                val context = LocalContext.current
                OutlinedButton(
                    onClick = { sharePlace(context, place) },
                    enabled = interactionEnabled,
                    shape = PanelShapeSmall,
                ) {
                    Text("分享")
                }
                Button(
                    onClick = onDirectionsClick,
                    enabled = interactionEnabled,
                    modifier = Modifier.weight(1f),
                    shape = PanelShapeSmall,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) {
                    Text("去这里")
                }
            }
        }
    }
}

private fun sharePlace(context: Context, place: Place) {
    val detail = listOf(place.address, place.district).firstOrNull(String::isNotBlank).orEmpty()
    val text = buildString {
        append(place.name)
        if (detail.isNotBlank()) {
            append("\n")
            append(detail)
        }
        append("\n位置：")
        append(String.format(Locale.US, "%.6f, %.6f", place.latitude, place.longitude))
    }
    val intent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_TEXT, text)
    }
    runCatching { context.startActivity(Intent.createChooser(intent, "分享地点")) }
}

@Composable
private fun PlaceMetadata(place: Place) {
    val details = buildList {
        if (place.district.isNotBlank()) add("区域" to place.district)
        if (place.category.isNotBlank()) add("分类" to place.category)
        if (place.phone.isNotBlank()) add("电话" to place.phone)
        place.distanceMeters?.let { add("距离" to formatPlaceDistance(it)) }
    }
    details.forEach { (label, value) ->
        Row(modifier = Modifier.padding(vertical = 3.dp)) {
            Text(
                text = label,
                modifier = Modifier.widthIn(min = 52.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 13.sp,
            )
            Text(value, color = MaterialTheme.colorScheme.onSurface, fontSize = 13.sp)
        }
    }
}

private val NearbyQuickSearches = listOf("停车场", "加油站", "充电站", "美食")

@Composable
private fun FavoriteGroupRow(
    isHome: Boolean,
    isWork: Boolean,
    enabled: Boolean,
    onSetFavoriteGroup: (FavoriteGroup) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FavoriteGroupChip(
            label = if (isHome) "已设为家" else "设为家",
            selected = isHome,
            enabled = enabled,
            onClick = { onSetFavoriteGroup(FavoriteGroup.Home) },
        )
        FavoriteGroupChip(
            label = if (isWork) "已设为公司" else "设为公司",
            selected = isWork,
            enabled = enabled,
            onClick = { onSetFavoriteGroup(FavoriteGroup.Work) },
        )
    }
}

@Composable
private fun FavoriteGroupChip(
    label: String,
    selected: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .clickable(enabled = enabled && !selected, role = Role.Button, onClick = onClick)
            .semantics { contentDescription = label },
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerHigh
        },
        shape = PanelShapeSmall,
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
            color = if (selected) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            fontSize = 13.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

@Composable
private fun NearbyQuickSearchRow(
    enabled: Boolean,
    onNearbySearch: (String) -> Unit,
) {
    Column {
        Text(
            text = "周边",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(Modifier.height(7.dp))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NearbyQuickSearches.forEach { keyword ->
                Surface(
                    modifier = Modifier
                        .clickable(enabled = enabled, role = Role.Button) { onNearbySearch(keyword) }
                        .semantics { contentDescription = "搜索附近的$keyword" },
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.55f),
                    shape = PanelShapeSmall,
                ) {
                    Text(
                        text = keyword,
                        modifier = Modifier.padding(horizontal = 13.dp, vertical = 7.dp),
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        fontSize = 13.sp,
                    )
                }
            }
        }
    }
}
