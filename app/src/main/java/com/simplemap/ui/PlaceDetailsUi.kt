package com.simplemap.ui

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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplemap.search.Place

@Composable
internal fun PlaceDetailPanel(
    place: Place,
    isFavorite: Boolean,
    interactionEnabled: Boolean,
    onFavoriteClick: () -> Unit,
    onDirectionsClick: () -> Unit,
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
