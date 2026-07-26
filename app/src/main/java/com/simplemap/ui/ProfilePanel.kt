package com.simplemap.ui

import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.simplemap.offline.OfflineMapRepository
import com.simplemap.search.FavoriteGroup
import com.simplemap.search.FavoritePlace
import com.simplemap.search.FavoritePlaceStore
import com.simplemap.search.Place
import com.simplemap.settings.NavigationSettings
import com.simplemap.ui.theme.panelBorder
import com.simplemap.ui.theme.sectionSurface
import com.simplemap.update.AppUpdateRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal enum class ProfileSection(val label: String, val summary: String) {
    Favorites("收藏", "快捷管理常用地点与分组。"),
    Offline("离线地图", "查看城市包、下载条件与可用容量。"),
    Settings("设置", "管理主题、导航提醒、隐私与本地数据。"),
}

@Composable
internal fun ProfilePanel(
    favoriteStore: FavoritePlaceStore,
    settings: NavigationSettings,
    updateRepository: AppUpdateRepository,
    offlineRepository: OfflineMapRepository?,
    offlineUnavailableMessage: String?,
    destroyOfflineRepositoryOnDispose: Boolean,
    onNavigateTo: (Place) -> Unit,
    onFavoritesChanged: (List<FavoritePlace>) -> Unit,
    onClearLocalData: suspend () -> Boolean,
    onRevokePrivacyConsent: suspend () -> Boolean,
    onPrivacyRevoked: () -> Unit,
    onSettingsChanged: (NavigationSettings) -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    var section by remember { mutableStateOf<ProfileSection?>(null) }
    var favorites by remember(favoriteStore) { mutableStateOf<List<FavoritePlace>>(emptyList()) }
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(favoriteStore) {
        favorites = withContext(Dispatchers.IO) { favoriteStore.loadFavorites() }
        onFavoritesChanged(favorites)
    }

    DisposableEffect(offlineRepository) {
        onDispose {
            if (destroyOfflineRepositoryOnDispose) offlineRepository?.destroy()
        }
    }

    BackHandler(enabled = section != null) { section = null }

    Box(modifier = modifier.fillMaxSize()) {
        val activeSection = section
        Surface(
            modifier = Modifier
                .fillMaxSize(),
            color = MaterialTheme.colorScheme.background,
            shape = RectangleShape,
        ) {
            Column(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(start = 16.dp, top = 14.dp, end = 16.dp, bottom = FloatingNavigationClearance),
            ) {
                if (activeSection == null) {
                    Text("我的", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "收藏地点、离线地图与应用设置",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 13.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    ProfileSectionList(
                        favoriteCount = favorites.size,
                        onSectionSelected = { section = it },
                        modifier = Modifier.weight(1f),
                    )
                } else {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TextButton(
                            onClick = { section = null },
                            modifier = Modifier.semantics { contentDescription = "返回我的列表" },
                        ) {
                            Text("‹ 返回")
                        }
                        Column(modifier = Modifier.padding(start = 4.dp)) {
                            Text(activeSection.label, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(
                                activeSection.summary,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 13.sp,
                            )
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                }
                when (activeSection) {
                    ProfileSection.Favorites -> FavoritesSection(
                        favorites = favorites,
                        onNavigateTo = onNavigateTo,
                        onRemove = { place ->
                            coroutineScope.launch {
                                if (withContext(Dispatchers.IO) { favoriteStore.remove(place.id) }) {
                                    val removed = favorites.firstOrNull { it.place.id == place.id }
                                    favorites = favorites.filterNot { it.place.id == place.id }
                                    onFavoritesChanged(favorites)
                                    val result = snackbarHostState.showSnackbar(
                                        message = "已移除 ${place.name}",
                                        actionLabel = "撤销",
                                    )
                                    if (result == SnackbarResult.ActionPerformed &&
                                        removed != null && withContext(Dispatchers.IO) {
                                            favoriteStore.save(removed.place, removed.group)
                                        }
                                    ) {
                                        favorites = favorites + removed
                                        onFavoritesChanged(favorites)
                                    }
                                }
                            }
                        },
                        onGroupChanged = { favorite, group ->
                            coroutineScope.launch {
                                if (withContext(Dispatchers.IO) { favoriteStore.setGroup(favorite.place.id, group) }) {
                                    favorites = withContext(Dispatchers.IO) { favoriteStore.loadFavorites() }
                                    onFavoritesChanged(favorites)
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )

                    ProfileSection.Offline -> if (offlineRepository != null) {
                        OfflineMapsSection(
                            repository = offlineRepository,
                            wifiOnly = settings.wifiOnlyOfflineDownloads,
                            onWifiOnlyChanged = { wifiOnly ->
                                val updated = settings.copy(wifiOnlyOfflineDownloads = wifiOnly)
                                onSettingsChanged(updated)
                            },
                            modifier = Modifier.weight(1f),
                        )
                    } else {
                        Column(modifier = Modifier.weight(1f)) {
                            SectionCard(containerColor = MaterialTheme.colorScheme.errorContainer) {
                                Text(
                                    text = offlineUnavailableMessage ?: "离线地图服务暂不可用",
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                        }
                    }

                    ProfileSection.Settings -> SettingsSection(
                        settings = settings,
                        updateRepository = updateRepository,
                        onChanged = { updated ->
                            onSettingsChanged(updated)
                        },
                        onClearLocalData = {
                            coroutineScope.launch {
                                val cleared = onClearLocalData()
                                favorites = withContext(Dispatchers.IO) { favoriteStore.loadFavorites() }
                                onFavoritesChanged(favorites)
                                if (!cleared) {
                                    snackbarHostState.showSnackbar("部分本地数据未能清除，请重试")
                                }
                            }
                        },
                        onRevokePrivacyConsent = {
                            coroutineScope.launch {
                                if (withContext(Dispatchers.IO) { onRevokePrivacyConsent() }) {
                                    onPrivacyRevoked()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                    null -> Unit
                }
            }
        }
        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = FloatingNavigationClearance - 8.dp),
        )
    }
}

@Composable
internal fun ProfileSectionList(
    favoriteCount: Int,
    onSectionSelected: (ProfileSection) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(1.dp),
        contentPadding = PaddingValues(bottom = 12.dp),
    ) {
        items(ProfileSection.entries, key = ProfileSection::name) { item ->
            val subtitle = when (item) {
                ProfileSection.Favorites -> "$favoriteCount 个已收藏地点"
                else -> item.summary.removeSuffix("。")
            }
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(role = Role.Button) { onSectionSelected(item) }
                    .semantics { contentDescription = "打开${item.label}" },
                color = MaterialTheme.colorScheme.surface,
                shape = PanelShapeSmall,
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.panelBorder),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Box(
                        Modifier
                            .size(width = 4.dp, height = 34.dp)
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50)),
                    )
                    Column(modifier = Modifier.padding(start = 12.dp).weight(1f)) {
                        Text(item.label, color = MaterialTheme.colorScheme.onSurface, fontWeight = FontWeight.SemiBold)
                        Text(subtitle, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp)
                    }
                    Text("›", color = MaterialTheme.colorScheme.primary, fontSize = 24.sp)
                }
            }
        }
    }
}

@Composable
internal fun FavoriteListGroup(
    favorites: List<FavoritePlace>,
    onNavigateTo: (Place) -> Unit,
    onRemove: (Place) -> Unit,
    onGroupChanged: (FavoritePlace, FavoriteGroup) -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.surface,
        shape = PanelShapeSmall,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.panelBorder),
    ) {
        Column {
            favorites.forEachIndexed { index, favorite ->
                if (index > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                FavoriteRow(
                    favorite = favorite,
                    onNavigateTo = onNavigateTo,
                    onRemove = onRemove,
                    onGroupChanged = onGroupChanged,
                )
            }
        }
    }
}

@Composable
internal fun SectionCard(
    modifier: Modifier = Modifier,
    containerColor: Color = MaterialTheme.colorScheme.sectionSurface,
    content: @Composable ColumnScope.() -> Unit,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = containerColor,
        shape = MaterialTheme.shapes.medium,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.panelBorder),
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
            content = content,
        )
    }
}

