package com.simplemap.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.core.content.ContextCompat
import com.simplemap.amap.AmapMapView
import com.simplemap.amap.AmapMapController
import com.simplemap.route.AmapRoutePlanRepository
import com.simplemap.route.RoutePlan
import com.simplemap.route.RoutePlanRepository
import com.simplemap.search.AmapPlaceRepository
import com.simplemap.search.FavoritePlaceStore
import com.simplemap.search.Place
import com.simplemap.search.PlaceRepository
import com.simplemap.search.SharedPreferencesFavoritePlaceStore
import com.simplemap.startup.MapAccessController
import com.simplemap.startup.MapAccessState
import com.simplemap.ui.theme.SimpleMapTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class HomeDestination(val label: String) {
    Map("地图"),
    Routes("路线"),
    Trips("行程"),
    Profile("我的"),
}

private sealed interface PlaceSearchState {
    data object Idle : PlaceSearchState
    data object Loading : PlaceSearchState
    data class Results(val places: List<Place>) : PlaceSearchState
    data class Failed(val message: String) : PlaceSearchState
}

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

private val CompassIcon = ImageVector.Builder(
    name = "Compass",
    defaultWidth = 24.dp,
    defaultHeight = 24.dp,
    viewportWidth = 24f,
    viewportHeight = 24f,
).apply {
    path(fill = SolidColor(Color(0xFF187A63))) {
        moveTo(18.8f, 5.2f)
        lineTo(14.8f, 14.8f)
        lineTo(5.2f, 18.8f)
        lineTo(9.2f, 9.2f)
        close()
    }
}.build()

@Composable
fun SimpleMapRoot(
    controller: MapAccessController,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var state: MapAccessState by remember { mutableStateOf(MapAccessState.Loading) }
    val coroutineScope = rememberCoroutineScope()

    LaunchedEffect(controller) {
        state = withContext(Dispatchers.IO) { controller.load() }
    }

    when (val currentState = state) {
        MapAccessState.Loading -> LoadingScreen(modifier)
        MapAccessState.ConsentRequired -> PrivacyConsentScreen(
            onAccept = {
                state = MapAccessState.Loading
                coroutineScope.launch {
                    state = withContext(Dispatchers.IO) { controller.accept() }
                }
            },
            onDecline = onDecline,
            modifier = modifier,
        )
        MapAccessState.MissingApiKey -> MissingApiKeyScreen(modifier)
        MapAccessState.Ready -> SimpleMapApp(modifier = modifier)
        is MapAccessState.Failed -> FailureScreen(
            message = currentState.message,
            onRetry = {
                state = MapAccessState.Loading
                coroutineScope.launch {
                    state = withContext(Dispatchers.IO) { controller.load() }
                }
            },
            modifier = modifier,
        )
    }
}

@Composable
fun SimpleMapApp(
    modifier: Modifier = Modifier,
    showLiveMap: Boolean = true,
    placeRepository: PlaceRepository? = null,
    favoritePlaceStore: FavoritePlaceStore? = null,
    routePlanRepository: RoutePlanRepository? = null,
) {
    val context = LocalContext.current
    val repository = remember(context, placeRepository) {
        placeRepository ?: AmapPlaceRepository(context)
    }
    val favoriteStore = remember(context, favoritePlaceStore) {
        favoritePlaceStore ?: SharedPreferencesFavoritePlaceStore(context)
    }
    val routeRepository = remember(context, routePlanRepository) {
        routePlanRepository ?: AmapRoutePlanRepository(context)
    }
    val coroutineScope = rememberCoroutineScope()
    var mapController by remember { mutableStateOf<AmapMapController?>(null) }
    var selectedDestination by remember { mutableStateOf(HomeDestination.Map) }
    var searchActive by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var searchState by remember { mutableStateOf<PlaceSearchState>(PlaceSearchState.Idle) }
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var selectedPlace by remember { mutableStateOf<Place?>(null) }
    var routeDestination by remember { mutableStateOf<Place?>(null) }
    var selectedRoutePlan by remember { mutableStateOf<RoutePlan?>(null) }
    var pendingNavigation by remember { mutableStateOf<Triple<Place, Place, RoutePlan>?>(null) }
    var favoritePlaceIds by remember {
        mutableStateOf(favoriteStore.load().mapTo(mutableSetOf()) { it.id })
    }
    var trafficEnabled by remember { mutableStateOf(false) }
    var satelliteEnabled by remember { mutableStateOf(false) }
    var locationEnabled by remember { mutableStateOf(false) }
    val locationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { permissions ->
        val granted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
            permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true
        locationEnabled = granted
        mapController?.setMyLocationEnabled(granted)
    }

    fun requestLocation() {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED || ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_COARSE_LOCATION,
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            locationEnabled = true
            mapController?.setMyLocationEnabled(true)
        } else {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION,
                ),
            )
        }
    }

    fun submitSearch() {
        val query = searchQuery.trim()
        if (query.isEmpty()) return
        searchJob?.cancel()
        searchState = PlaceSearchState.Loading
        searchJob = coroutineScope.launch {
            val result = withContext(Dispatchers.IO) { repository.search(query) }
            searchState = result.fold(
                onSuccess = { PlaceSearchState.Results(it) },
                onFailure = {
                    PlaceSearchState.Failed(it.localizedMessage ?: "搜索服务暂不可用")
                },
            )
        }
    }

    fun selectPlace(place: Place) {
        selectedPlace = place
        searchActive = false
        mapController?.showPlace(
            latitude = place.latitude,
            longitude = place.longitude,
            title = place.name,
            snippet = place.address.ifBlank { place.district },
        )
    }

    fun toggleFavorite(place: Place) {
        coroutineScope.launch {
            val isFavorite = place.id in favoritePlaceIds
            val persisted = withContext(Dispatchers.IO) {
                if (isFavorite) favoriteStore.remove(place.id) else favoriteStore.save(place)
            }
            if (persisted) {
                favoritePlaceIds = favoritePlaceIds.toMutableSet().apply {
                    if (isFavorite) remove(place.id) else add(place.id)
                }
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        if (showLiveMap) {
            AmapMapView(
                modifier = Modifier.fillMaxSize(),
                onControllerReady = { controller ->
                    mapController = controller
                    controller.setTrafficEnabled(trafficEnabled)
                    controller.setSatelliteEnabled(satelliteEnabled)
                    selectedPlace?.let(::selectPlace)
                    selectedRoutePlan?.let { controller.showRoute(it.polyline) }
                },
            )
        } else {
            MapBackdrop()
        }
        if (selectedDestination == HomeDestination.Map) {
            if (searchActive) {
                SearchPanel(
                    query = searchQuery,
                    onQueryChange = { searchQuery = it },
                    state = searchState,
                    onSearch = ::submitSearch,
                    onPlaceSelected = ::selectPlace,
                    onClose = {
                        searchJob?.cancel()
                        searchActive = false
                        searchQuery = ""
                        searchState = PlaceSearchState.Idle
                    },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            } else {
                SearchBar(
                    onClick = { searchActive = true },
                    modifier = Modifier.align(Alignment.TopCenter),
                )
            }
            MapControls(
                trafficEnabled = trafficEnabled,
                satelliteEnabled = satelliteEnabled,
                locationEnabled = locationEnabled,
                onTrafficClick = {
                    trafficEnabled = !trafficEnabled
                    mapController?.setTrafficEnabled(trafficEnabled)
                },
                onSatelliteClick = {
                    satelliteEnabled = !satelliteEnabled
                    mapController?.setSatelliteEnabled(satelliteEnabled)
                },
                onLocationClick = ::requestLocation,
                onZoomIn = { mapController?.zoomIn() },
                onZoomOut = { mapController?.zoomOut() },
                modifier = Modifier.align(Alignment.BottomEnd),
            )
            selectedPlace?.let { place ->
                PlaceDetailPanel(
                    place = place,
                    isFavorite = place.id in favoritePlaceIds,
                    onFavoriteClick = { toggleFavorite(place) },
                    onDirectionsClick = {
                        routeDestination = place
                        selectedDestination = HomeDestination.Routes
                    },
                    onClose = {
                        selectedPlace = null
                        mapController?.clearSelectedPlace()
                    },
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        } else if (selectedDestination == HomeDestination.Routes) {
            RoutePlannerPanel(
                placeRepository = repository,
                routePlanRepository = routeRepository,
                initialDestination = routeDestination,
                onRouteSelected = {
                    selectedRoutePlan = it
                    mapController?.showRoute(it.polyline)
                },
                onStartNavigation = { origin, destination, plan ->
                    pendingNavigation = Triple(origin, destination, plan)
                },
                modifier = Modifier.align(Alignment.TopCenter),
            )
        } else {
            DestinationPanel(
                destination = selectedDestination,
                modifier = Modifier.align(Alignment.TopCenter),
            )
        }
        FloatingNavigation(
            selected = selectedDestination,
            onSelected = { destination ->
                searchActive = false
                selectedDestination = destination
            },
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }
}

@Composable
private fun LoadingScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF4F7F4)),
        contentAlignment = Alignment.Center,
    ) {
        Text("正在准备地图", color = Color(0xFF43504D))
    }
}

@Composable
private fun PrivacyConsentScreen(
    onAccept: () -> Unit,
    onDecline: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(modifier = modifier.fillMaxSize(), color = Color(0xFFF4F7F4)) {
        Column(
            modifier = Modifier
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 28.dp, vertical = 32.dp)
                .widthIn(max = 560.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            Text(
                text = "欢迎使用 SimpleMap",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF17211F),
            )
            Spacer(Modifier.height(18.dp))
            Text(
                text = "为提供地图展示、地点搜索、路线规划和实时导航，应用会在你同意后使用高德地图服务，并在获得系统授权后处理位置信息。不同意时不会初始化地图服务，也不会访问位置。",
                style = MaterialTheme.typography.bodyLarge,
                color = Color(0xFF4F5B58),
                lineHeight = 25.sp,
            )
            Spacer(Modifier.height(12.dp))
            Text(
                text = "你可以稍后在设置中管理定位权限和数据选项。继续即表示你已阅读并同意隐私说明。",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF6E7976),
                lineHeight = 22.sp,
            )
            Spacer(Modifier.height(30.dp))
            Button(
                onClick = onAccept,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF126B56)),
            ) {
                Text("同意并继续", modifier = Modifier.padding(vertical = 5.dp))
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(
                onClick = onDecline,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(8.dp),
            ) {
                Text("暂不同意", modifier = Modifier.padding(vertical = 5.dp))
            }
        }
    }
}

@Composable
private fun MissingApiKeyScreen(modifier: Modifier = Modifier) {
    StatusScreen(
        title = "地图服务尚未配置",
        message = "请在 local.properties 中添加与 com.simplemap 绑定的 AMAP_API_KEY，然后重新构建应用。",
        modifier = modifier,
    )
}

@Composable
private fun FailureScreen(
    message: String,
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    StatusScreen(
        title = "地图服务暂不可用",
        message = message,
        modifier = modifier,
        action = {
            Button(onClick = onRetry, shape = RoundedCornerShape(8.dp)) {
                Text("重试")
            }
        },
    )
}

@Composable
private fun StatusScreen(
    title: String,
    message: String,
    modifier: Modifier = Modifier,
    action: @Composable () -> Unit = {},
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(Color(0xFFF4F7F4))
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(title, style = MaterialTheme.typography.titleLarge, color = Color(0xFF17211F))
        Spacer(Modifier.height(12.dp))
        Text(message, color = Color(0xFF5F6B68), lineHeight = 22.sp)
        Spacer(Modifier.height(20.dp))
        action()
    }
}

@Composable
private fun MapBackdrop() {
    Canvas(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFFE8EEE9))
            .semantics { contentDescription = "地图区域" },
    ) {
        val road = Path().apply {
            moveTo(-40f, size.height * 0.78f)
            cubicTo(size.width * 0.2f, size.height * 0.6f, size.width * 0.45f, size.height * 0.84f, size.width + 40f, size.height * 0.46f)
        }
        drawPath(road, color = Color.White, style = Stroke(width = 42f, cap = StrokeCap.Round))
        drawPath(road, color = Color(0xFFC8D1CB), style = Stroke(width = 2f, cap = StrokeCap.Round))
        drawCircle(Color(0xFFA8D8C2), radius = 92f, center = Offset(size.width * 0.18f, size.height * 0.28f))
        drawCircle(Color(0xFFDAE6D9), radius = 135f, center = Offset(size.width * 0.82f, size.height * 0.22f))
    }
}

@Composable
private fun SearchBar(
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
            .semantics { contentDescription = "搜索地点、公交或路线" },
        color = Color.White,
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 10.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 13.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(SearchIcon, contentDescription = null, tint = Color.Unspecified)
            Text(
                text = "搜索地点、公交或路线",
                modifier = Modifier.padding(start = 12.dp),
                color = Color(0xFF6E7976),
                fontSize = 16.sp,
            )
            Spacer(Modifier.weight(1f))
            Surface(
                color = Color(0xFFE8F5EF),
                shape = CircleShape,
                modifier = Modifier.semantics { contentDescription = "账户" },
            ) {
                Text(
                    text = "A",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    color = Color(0xFF126B56),
                    fontWeight = FontWeight.Bold,
                )
            }
        }
    }
}

@Composable
private fun SearchPanel(
    query: String,
    onQueryChange: (String) -> Unit,
    state: PlaceSearchState,
    onSearch: () -> Unit,
    onPlaceSelected: (Place) -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .statusBarsPadding()
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .fillMaxWidth()
            .widthIn(max = 680.dp),
        color = Color.White,
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 10.dp,
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
                    placeholder = { Text("输入地点、公交或路线") },
                    singleLine = true,
                    leadingIcon = { Icon(SearchIcon, contentDescription = null, tint = Color.Unspecified) },
                    trailingIcon = {
                        IconButton(onClick = onSearch) {
                            Icon(SearchIcon, contentDescription = "搜索")
                        }
                    },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                    shape = RoundedCornerShape(8.dp),
                )
                TextButton(onClick = onClose) {
                    Text("取消", color = Color(0xFF126B56))
                }
            }
            when (state) {
                PlaceSearchState.Idle -> Unit
                PlaceSearchState.Loading -> Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(92.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(28.dp),
                        color = Color(0xFF126B56),
                        strokeWidth = 3.dp,
                    )
                }
                is PlaceSearchState.Failed -> SearchMessage(state.message)
                is PlaceSearchState.Results -> {
                    if (state.places.isEmpty()) {
                        SearchMessage("没有找到相关地点，试试更具体的名称")
                    } else {
                        HorizontalDivider(color = Color(0xFFE4EAE7))
                        LazyColumn(modifier = Modifier.heightIn(max = 430.dp)) {
                            items(state.places, key = { it.id }) { place ->
                                SearchResultItem(place = place, onClick = { onPlaceSelected(place) })
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
        color = Color(0xFF66726F),
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
                color = Color(0xFF17211F),
                fontWeight = FontWeight.SemiBold,
                style = MaterialTheme.typography.titleMedium,
            )
            place.distanceMeters?.let {
                Text(formatDistance(it), color = Color(0xFF126B56), fontSize = 12.sp)
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = listOf(place.district, place.address).filter { it.isNotBlank() }.joinToString(" · "),
            color = Color(0xFF697572),
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 2,
        )
    }
    HorizontalDivider(color = Color(0xFFF0F3F1))
}

@Composable
private fun PlaceDetailPanel(
    place: Place,
    isFavorite: Boolean,
    onFavoriteClick: () -> Unit,
    onDirectionsClick: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 86.dp)
            .fillMaxWidth()
            .widthIn(max = 680.dp),
        color = Color(0xFAFFFFFF),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 14.dp,
    ) {
        Column(modifier = Modifier.padding(18.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = place.name,
                        color = Color(0xFF17211F),
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                    )
                    if (place.address.isNotBlank()) {
                        Spacer(Modifier.height(5.dp))
                        Text(place.address, color = Color(0xFF5F6B68), maxLines = 2)
                    }
                }
                TextButton(onClick = onClose) { Text("关闭") }
            }
            Spacer(Modifier.height(12.dp))
            PlaceMetadata(place)
            Spacer(Modifier.height(16.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(
                    onClick = onFavoriteClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                ) {
                    Text(if (isFavorite) "取消收藏" else "收藏")
                }
                Button(
                    onClick = onDirectionsClick,
                    modifier = Modifier.weight(1f),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF126B56)),
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
        place.distanceMeters?.let { add("距离" to formatDistance(it)) }
        add("坐标" to "%.5f, %.5f".format(place.latitude, place.longitude))
    }
    details.forEach { (label, value) ->
        Row(modifier = Modifier.padding(vertical = 3.dp)) {
            Text(label, modifier = Modifier.widthIn(min = 52.dp), color = Color(0xFF7A8582), fontSize = 13.sp)
            Text(value, color = Color(0xFF35413E), fontSize = 13.sp)
        }
    }
}

private fun formatDistance(distanceMeters: Int): String = if (distanceMeters < 1_000) {
    "${distanceMeters} 米"
} else {
    "%.1f 公里".format(distanceMeters / 1_000f)
}

@Composable
private fun MapControls(
    trafficEnabled: Boolean,
    satelliteEnabled: Boolean,
    locationEnabled: Boolean,
    onTrafficClick: () -> Unit,
    onSatelliteClick: () -> Unit,
    onLocationClick: () -> Unit,
    onZoomIn: () -> Unit,
    onZoomOut: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .navigationBarsPadding()
            .padding(end = 16.dp, bottom = 104.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        horizontalAlignment = Alignment.End,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MapToolButton("路况", trafficEnabled, onTrafficClick)
            MapToolButton("卫星", satelliteEnabled, onSatelliteClick)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MapToolButton("−", false, onZoomOut, "缩小地图")
            MapToolButton("+", false, onZoomIn, "放大地图")
        }
        Surface(
            shape = CircleShape,
            color = if (locationEnabled) Color(0xFF126B56) else Color.White,
            shadowElevation = 6.dp,
        ) {
            IconButton(onClick = onLocationClick, modifier = Modifier.size(52.dp)) {
                Icon(
                    CompassIcon,
                    contentDescription = if (locationEnabled) "正在跟随当前位置" else "回到当前位置",
                    tint = if (locationEnabled) Color.White else Color.Unspecified,
                )
            }
        }
    }
}

@Composable
private fun MapToolButton(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    description: String = label,
) {
    Surface(
        modifier = Modifier
            .size(52.dp)
            .clickable(role = Role.Button, onClick = onClick)
            .semantics { contentDescription = description },
        color = if (selected) Color(0xFF126B56) else Color.White,
        shape = CircleShape,
        shadowElevation = 6.dp,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Text(
                text = label,
                color = if (selected) Color.White else Color(0xFF263330),
                fontSize = if (label.length == 1) 24.sp else 12.sp,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

@Composable
private fun DestinationPanel(
    destination: HomeDestination,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 12.dp)
            .fillMaxWidth()
            .widthIn(max = 680.dp),
        color = Color(0xF7FFFFFF),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 10.dp,
    ) {
        Column(modifier = Modifier.padding(22.dp)) {
            Text(destination.label, style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(8.dp))
            Text(
                text = when (destination) {
                    HomeDestination.Routes -> "选择起点和终点，比较驾车、公交、骑行与步行方案"
                    HomeDestination.Trips -> "查看最近行程、常用路线与通勤统计"
                    HomeDestination.Profile -> "管理收藏地点、离线地图、导航偏好与隐私设置"
                    HomeDestination.Map -> ""
                },
                color = Color(0xFF596561),
                lineHeight = 22.sp,
            )
        }
    }
}

@Composable
private fun FloatingNavigation(
    selected: HomeDestination,
    onSelected: (HomeDestination) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier
            .navigationBarsPadding()
            .padding(horizontal = 18.dp, vertical = 14.dp)
            .fillMaxWidth()
            .widthIn(max = 680.dp),
        color = Color(0xF7FFFFFF),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 14.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceAround,
        ) {
            HomeDestination.entries.forEach { destination ->
                NavigationItem(
                    label = destination.label,
                    selected = selected == destination,
                    onClick = { onSelected(destination) },
                )
            }
        }
    }
}

@Composable
private fun NavigationItem(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Column(
        modifier = Modifier
            .size(width = 68.dp, height = 52.dp)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics {
                role = Role.Tab
                this.selected = selected
                contentDescription = label
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            modifier = Modifier
                .size(7.dp)
                .background(if (selected) Color(0xFF13745C) else Color.Transparent, CircleShape),
        )
        Spacer(Modifier.height(5.dp))
        Text(
            text = label,
            color = if (selected) Color(0xFF126B56) else Color(0xFF5F6B68),
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Preview(showBackground = true, widthDp = 393, heightDp = 852)
@Composable
private fun SimpleMapPreview() {
    SimpleMapTheme {
        SimpleMapApp(showLiveMap = false)
    }
}