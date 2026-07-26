package com.simplemap.ui

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplemap.search.Place

private val SearchIcon = ImageVector.Builder(
    name = "Search",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = null, stroke = SolidColor(Color(0xFF1E2927)), strokeLineWidth = 2f) {
        moveTo(10.5f, 4f)
        curveTo(6.9f, 4f, 4f, 6.9f, 4f, 10.5f)
        curveTo(4f, 14.1f, 6.9f, 17f, 10.5f, 17f)
        curveTo(14.1f, 17f, 17f, 14.1f, 17f, 10.5f)
        curveTo(17f, 6.9f, 14.1f, 4f, 10.5f, 4f)
        close()
        moveTo(15.2f, 15.2f)
        lineTo(21f, 21f)
    }
}.build()

@Composable
internal fun SearchBar(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .fillMaxWidth()
            .widthIn(max = 680.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "搜索地点或路线" },
        color = MaterialTheme.colorScheme.surface,
        shape = PanelShapeLarge,
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = SearchIcon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "搜索地点或路线",
                modifier = Modifier.padding(start = 12.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 16.sp,
            )
            Spacer(Modifier.weight(1f))
        }
    }
}

@Composable
internal fun SearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    state: PlaceSearchResult,
    onSearch: () -> Unit,
    onPlaceSelected: (Place) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val resultMaxHeight = (
        LocalConfiguration.current.screenHeightDp.dp - 136.dp
    ).coerceIn(96.dp, 360.dp)
    Surface(
        modifier = modifier
            .statusBarsPadding()
            .imePadding()
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .fillMaxWidth()
            .wrapContentHeight()
            .widthIn(max = 680.dp),
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f),
        shape = PanelShapeLarge,
        shadowElevation = 12.dp,
    ) {
        Column {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text("输入地点或路线") },
                    singleLine = true,
                    leadingIcon = {
                        Icon(
                            imageVector = SearchIcon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    },
                    trailingIcon = {
                        IconButton(onClick = onSearch) {
                            Icon(SearchIcon, contentDescription = "搜索")
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    shape = PanelShapeSmall,
                )
                TextButton(onClick = onClose) {
                    Text("取消", color = MaterialTheme.colorScheme.primary)
                }
            }
            AnimatedContent(
                targetState = state,
                modifier = Modifier.heightIn(max = resultMaxHeight),
                transitionSpec = { fadeIn() togetherWith fadeOut() },
                contentKey = { it::class },
                label = "搜索结果",
            ) { animatedState ->
                when (animatedState) {
                    PlaceSearchResult.Idle -> Unit
                    PlaceSearchResult.Loading -> Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(92.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(28.dp),
                            color = MaterialTheme.colorScheme.primary,
                            strokeWidth = 3.dp,
                        )
                    }
                    is PlaceSearchResult.Failed -> SearchMessage(animatedState.message)
                    is PlaceSearchResult.Results -> {
                        if (animatedState.places.isEmpty()) {
                            SearchMessage("没有找到相关地点，试试名称中的关键词")
                        } else {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            LazyColumn(modifier = Modifier.fillMaxWidth()) {
                                items(animatedState.places, key = Place::id) { place ->
                                    SearchResultItem(place = place, onClick = { onPlaceSelected(place) })
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchMessage(message: String) {
    Text(
        text = message,
        modifier = Modifier.padding(horizontal = 18.dp, vertical = 24.dp),
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        lineHeight = 21.sp,
    )
}

@Composable
private fun SearchResultItem(
    place: Place,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = "查看地点 ${place.name}" }
            .padding(horizontal = 18.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = place.name,
                modifier = Modifier.weight(1f),
                color = MaterialTheme.colorScheme.onSurface,
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
            )
            place.distanceMeters?.let { distanceMeters ->
                Text(
                    text = formatPlaceDistance(distanceMeters),
                    color = MaterialTheme.colorScheme.primary,
                    fontSize = 12.sp,
                )
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = listOf(place.district, place.address).filter(String::isNotBlank).joinToString(" · "),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
        )
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
}
