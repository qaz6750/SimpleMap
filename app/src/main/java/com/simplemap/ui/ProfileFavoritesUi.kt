package com.simplemap.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplemap.search.FavoriteGroup
import com.simplemap.search.FavoritePlace
import com.simplemap.search.Place
import com.simplemap.ui.theme.sectionSurfaceEmphasis

@Composable
internal fun FavoritesSection(
    favorites: List<FavoritePlace>,
    onNavigateTo: (Place) -> Unit,
    onRemove: (Place) -> Unit,
    onGroupChanged: (FavoritePlace, FavoriteGroup) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (favorites.isEmpty()) {
        Column(modifier = modifier.fillMaxWidth()) {
            SectionCard {
                Text("暂无收藏地点", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(bottom = 12.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        FavoriteGroup.entries.forEach { group ->
            val groupedFavorites = favorites.filter { it.group == group }
            if (groupedFavorites.isNotEmpty()) {
                item(key = "header-${group.name}") {
                    Text(
                        text = group.label,
                        modifier = Modifier.padding(top = 2.dp, start = 4.dp),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                item(key = "group-${group.name}") {
                    FavoriteListGroup(
                        favorites = groupedFavorites,
                        onNavigateTo = onNavigateTo,
                        onRemove = onRemove,
                        onGroupChanged = onGroupChanged,
                    )
                }
            }
        }
    }
}

@Composable
internal fun FavoriteRow(
    favorite: FavoritePlace,
    onNavigateTo: (Place) -> Unit,
    onRemove: (Place) -> Unit,
    onGroupChanged: (FavoritePlace, FavoriteGroup) -> Unit,
) {
    val place = favorite.place
    Column(
        modifier = Modifier
            .clickable(role = Role.Button) { onNavigateTo(place) }
            .semantics { contentDescription = "规划到 ${place.name}" }
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(modifier = Modifier.weight(1f)) {
                Text(place.name, fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                val detail = place.address.ifBlank { place.district }
                if (detail.isNotBlank()) {
                    Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
                }
            }
            TextButton(
                modifier = Modifier.heightIn(min = 48.dp),
                onClick = { onRemove(place) },
            ) { Text("移除") }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            FavoriteGroup.entries.forEach { targetGroup ->
                CompactChoiceChip(
                    text = targetGroup.label,
                    selected = favorite.group == targetGroup,
                    onClick = { onGroupChanged(favorite, targetGroup) },
                    enabled = favorite.group != targetGroup,
                    selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                    selectedContentColor = MaterialTheme.colorScheme.secondary,
                    unselectedContainerColor = MaterialTheme.colorScheme.sectionSurfaceEmphasis,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

