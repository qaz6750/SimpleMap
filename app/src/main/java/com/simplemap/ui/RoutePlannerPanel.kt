package com.simplemap.ui

import android.location.Location
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import com.simplemap.route.DriveRouteOptions
import com.simplemap.route.DriveRoutePreset
import com.simplemap.route.matchingPreset
import com.simplemap.route.toOptions
import com.simplemap.route.RouteMode
import com.simplemap.route.RoutePlan
import com.simplemap.route.RoutePlanRepository
import com.simplemap.route.RouteRequest
import com.simplemap.search.Place
import com.simplemap.search.PlaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.dropWhile
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

private sealed interface RouteEndpoint {
    data object Origin : RouteEndpoint
    data object Destination : RouteEndpoint
    data class Waypoint(val index: Int) : RouteEndpoint
}

private data class WaypointDraft(val query: String = "", val place: Place? = null)

private sealed interface RoutePlanState {
    data object Idle : RoutePlanState
    data object Loading : RoutePlanState
    data class Ready(val plans: List<RoutePlan>) : RoutePlanState
    data class Failed(val message: String) : RoutePlanState
}

internal data class RoutePlannerObstructions(
    val topInsetPx: Int = 0,
    val bottomInsetPx: Int = 0,
    val leftInsetPx: Int = 0,
)

@Composable
internal fun RoutePlannerPanel(
    placeRepository: PlaceRepository,
    routePlanRepository: RoutePlanRepository,
    initialOrigin: Place?,
    initialDestination: Place?,
    modifier: Modifier = Modifier,
    initialMode: RouteMode = RouteMode.Drive,
    autoPlan: Boolean = false,
    initialDriveOptions: DriveRouteOptions = DriveRouteOptions(),
    onDriveOptionsChanged: (DriveRouteOptions) -> Unit = {},
    onRouteSelected: (RoutePlan) -> Unit,
    onRoutesChanged: (List<RoutePlan>, RoutePlan?) -> Unit = { _, _ -> },
    onRouteCleared: () -> Unit,
    onStartNavigation: (RouteRequest, RoutePlan, Boolean) -> Unit,
    onObstructionsChanged: (RoutePlannerObstructions) -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()
    var origin by remember(initialOrigin?.id) { mutableStateOf(initialOrigin) }
    var destination by remember(initialDestination) { mutableStateOf(initialDestination) }
    var originQuery by remember(initialOrigin?.id) {
        mutableStateOf(initialOrigin?.name.orEmpty())
    }
    var destinationQuery by remember(initialDestination) {
        mutableStateOf(initialDestination?.name.orEmpty())
    }
    var activeEndpoint by remember { mutableStateOf<RouteEndpoint?>(null) }
    var suggestions by remember { mutableStateOf<List<Place>>(emptyList()) }
    var suggestionMessage by remember { mutableStateOf<String?>(null) }
    var selectedMode by remember(initialMode) { mutableStateOf(initialMode) }
    var driveOptions by remember(initialDriveOptions) { mutableStateOf(initialDriveOptions) }
    var drivePreferencesExpanded by remember { mutableStateOf(false) }
    var waypoints by remember { mutableStateOf<List<WaypointDraft>>(emptyList()) }
    var planState by remember { mutableStateOf<RoutePlanState>(RoutePlanState.Idle) }
    var selectedPlan by remember { mutableStateOf<RoutePlan?>(null) }
    var plannedRequest by remember { mutableStateOf<RouteRequest?>(null) }
    var detailsExpanded by remember { mutableStateOf(false) }
    val resultsScrollState = rememberScrollState()
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var planJob by remember { mutableStateOf<Job?>(null) }
    var previousInitialOrigin by remember { mutableStateOf(initialOrigin) }
    var previousInitialDestination by remember { mutableStateOf(initialDestination) }
    val planVersion = remember { AtomicInteger() }
    var viewportHeightPx by remember { mutableStateOf(0) }
    var topPanelBottomPx by remember { mutableStateOf(0) }
    var topPanelRightPx by remember { mutableStateOf(0) }
    var bottomStackTopPx by remember { mutableStateOf(0) }
    var bottomStackRightPx by remember { mutableStateOf(0) }

    DisposableEffect(Unit) {
        onDispose {
            searchJob?.cancel()
            planJob?.cancel()
        }
    }

    fun hasUnconfirmedWaypoint() = selectedMode == RouteMode.Drive &&
        waypoints.any { it.query.isNotBlank() && it.place == null }

    fun invalidateRoute() {
        planVersion.incrementAndGet()
        planJob?.cancel()
        planJob = null
        selectedPlan = null
        plannedRequest = null
        detailsExpanded = false
        planState = RoutePlanState.Idle
        onRoutesChanged(emptyList(), null)
        onRouteCleared()
    }

    fun searchEndpoint(
        endpoint: RouteEndpoint,
        queryOverride: String? = null,
        debounceSearch: Boolean = false,
    ) {
        val query = (
            queryOverride ?: when (endpoint) {
                RouteEndpoint.Origin -> originQuery
                RouteEndpoint.Destination -> destinationQuery
                is RouteEndpoint.Waypoint -> waypoints.getOrNull(endpoint.index)?.query.orEmpty()
            }
        ).trim()
        searchJob?.cancel()
        if (query.isEmpty()) {
            searchJob = null
            activeEndpoint = null
            suggestions = emptyList()
            suggestionMessage = null
            return
        }
        activeEndpoint = endpoint
        suggestions = emptyList()
        suggestionMessage = "正在搜索地点"
        searchJob = coroutineScope.launch {
            if (debounceSearch) {
                delay(250L)
            }
            val result = withContext(Dispatchers.IO) { placeRepository.search(query) }
            result.fold(
                onSuccess = {
                    suggestions = it.take(8)
                    suggestionMessage = if (it.isEmpty()) "没有找到相关地点" else null
                },
                onFailure = {
                    suggestionMessage = it.localizedMessage ?: "地点搜索暂不可用"
                },
            )
        }
    }

    fun selectEndpoint(place: Place) {
        when (val endpoint = activeEndpoint) {
            RouteEndpoint.Origin -> {
                origin = place
                originQuery = place.name
            }
            RouteEndpoint.Destination -> {
                destination = place
                destinationQuery = place.name
            }
            is RouteEndpoint.Waypoint -> {
                waypoints = waypoints.toMutableList().apply {
                    if (endpoint.index in indices) {
                        this[endpoint.index] = WaypointDraft(place.name, place)
                    }
                }
            }
            null -> Unit
        }
        activeEndpoint = null
        suggestions = emptyList()
        suggestionMessage = null
        detailsExpanded = false
        invalidateRoute()
    }

    fun planRoutes() {
        val routeOrigin = origin ?: return
        val routeDestination = destination ?: return
        if (hasUnconfirmedWaypoint()) return
        planJob?.cancel()
        selectedPlan = null
        detailsExpanded = false
        planState = RoutePlanState.Loading
        val request = RouteRequest(
            origin = routeOrigin,
            destination = routeDestination,
            waypoints = if (selectedMode == RouteMode.Drive) waypoints.mapNotNull(WaypointDraft::place) else emptyList(),
            mode = selectedMode,
            driveOptions = driveOptions,
            city = routeDestination.district.substringBefore(" · "),
        )
        val requestVersion = planVersion.incrementAndGet()
        planJob = coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                routePlanRepository.plan(request)
            }
            if (requestVersion != planVersion.get()) return@launch
            planState = result.fold(
                onSuccess = { plans ->
                    val recommendedPlans = plans.take(3)
                    recommendedPlans.firstOrNull()?.let {
                        selectedPlan = it
                        plannedRequest = request
                        detailsExpanded = false
                        onRouteSelected(it)
                        onRoutesChanged(recommendedPlans, it)
                    }
                    RoutePlanState.Ready(recommendedPlans)
                },
                onFailure = {
                    RoutePlanState.Failed(it.localizedMessage ?: "路线规划暂不可用")
                },
            )
        }
    }

    LaunchedEffect(autoPlan, initialOrigin, initialDestination) {
        val originChanged = when {
            initialOrigin == null -> false
            previousInitialOrigin == null -> true
            previousInitialOrigin?.id != initialOrigin.id -> true
            else -> {
                val previous = previousInitialOrigin ?: initialOrigin
                val distance = FloatArray(1)
                Location.distanceBetween(
                    previous.latitude,
                    previous.longitude,
                    initialOrigin.latitude,
                    initialOrigin.longitude,
                    distance,
                )
                distance[0] >= 50f
            }
        }
        val destinationChanged = previousInitialDestination != initialDestination
        val shouldSyncOrigin = originChanged && initialOrigin != null && origin?.id == CURRENT_LOCATION_ID

        if (originChanged) previousInitialOrigin = initialOrigin
        if (shouldSyncOrigin) {
            origin = initialOrigin
            originQuery = initialOrigin.name
        }
        if (destinationChanged) {
            previousInitialDestination = initialDestination
            destination = initialDestination
            destinationQuery = initialDestination?.name.orEmpty()
        }
        if (shouldSyncOrigin || destinationChanged) {
            invalidateRoute()
        }
        if (
            autoPlan &&
            origin != null &&
            destination != null &&
            (shouldSyncOrigin || destinationChanged || (plannedRequest == null && planState is RoutePlanState.Idle))
        ) {
            planRoutes()
        }
    }

    LaunchedEffect(selectedPlan?.id) {
        detailsExpanded = false
        resultsScrollState.scrollTo(0)
        snapshotFlow { resultsScrollState.value }
            .dropWhile { it < 24 }
            .collectLatest {
                detailsExpanded = true
            }
    }

    LaunchedEffect(activeEndpoint) {
        if (activeEndpoint != null) {
            bottomStackTopPx = 0
            bottomStackRightPx = 0
        }
    }

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .onSizeChanged { viewportHeightPx = it.height },
    ) {
        val isLandscape = maxWidth > maxHeight
        val panelMaxWidth = if (isLandscape) minOf(maxWidth * 0.46f, 420.dp) else 640.dp
        val compactHeight = maxHeight < 520.dp
        val panelHorizontalPadding = if (isLandscape || maxWidth < 400.dp) 8.dp else 10.dp
        val editorCollapsedMaxHeight = if (isLandscape) {
            maxHeight * if (compactHeight) 0.42f else 0.38f
        } else {
            minOf(220.dp, maxHeight * if (compactHeight) 0.36f else 0.32f)
        }
        val editorExpandedMaxHeight = if (isLandscape) {
            maxHeight - 16.dp
        } else {
            maxHeight - 12.dp
        }
        val desiredBottomStackMaxHeight = when {
            isLandscape && detailsExpanded -> maxHeight * if (compactHeight) 0.58f else 0.52f
            isLandscape -> maxHeight * if (compactHeight) 0.46f else 0.4f
            detailsExpanded -> maxHeight * if (compactHeight) 0.58f else 0.5f
            planState is RoutePlanState.Ready -> minOf(260.dp, maxHeight * 0.34f)
            else -> minOf(196.dp, maxHeight * 0.28f)
        }
        val bottomStackMaxHeight = minOf(
            desiredBottomStackMaxHeight,
            maxOf(
                96.dp,
                maxHeight - editorCollapsedMaxHeight - if (isLandscape) 44.dp else 56.dp,
            ),
        )
        LaunchedEffect(
            isLandscape,
            viewportHeightPx,
            topPanelBottomPx,
            topPanelRightPx,
            bottomStackTopPx,
            bottomStackRightPx,
        ) {
            onObstructionsChanged(
                RoutePlannerObstructions(
                    topInsetPx = topPanelBottomPx,
                    bottomInsetPx = if (viewportHeightPx > 0 && bottomStackTopPx > 0) {
                        (viewportHeightPx - bottomStackTopPx).coerceAtLeast(0)
                    } else {
                        0
                    },
                    leftInsetPx = if (isLandscape) {
                        maxOf(topPanelRightPx, bottomStackRightPx)
                    } else {
                        0
                    },
                ),
            )
        }
        Surface(
            modifier = Modifier
                .align(if (isLandscape) Alignment.TopStart else Alignment.TopCenter)
                .padding(horizontal = panelHorizontalPadding, vertical = 6.dp)
                .widthIn(max = panelMaxWidth)
                .fillMaxWidth()
                .heightIn(
                    max = if (activeEndpoint == null) {
                        editorCollapsedMaxHeight
                    } else {
                        editorExpandedMaxHeight
                    },
                )
                .onGloballyPositioned { coordinates ->
                    val bounds = coordinates.boundsInRoot()
                    topPanelBottomPx = bounds.bottom.roundToInt()
                    topPanelRightPx = bounds.right.roundToInt()
                }
                .semantics { contentDescription = "路线端点编辑" },
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.99f),
            shape = MaterialTheme.shapes.large,
            shadowElevation = 12.dp,
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 12.dp, vertical = 10.dp),
            ) {
                EndpointEditor(
                    originQuery = originQuery,
                    destinationQuery = destinationQuery,
                    origin = origin,
                    destination = destination,
                    onOriginChange = {
                        originQuery = it
                        origin = null
                        invalidateRoute()
                        searchEndpoint(
                            endpoint = RouteEndpoint.Origin,
                            queryOverride = it,
                            debounceSearch = true,
                        )
                    },
                    onDestinationChange = {
                        destinationQuery = it
                        destination = null
                        invalidateRoute()
                        searchEndpoint(
                            endpoint = RouteEndpoint.Destination,
                            queryOverride = it,
                            debounceSearch = true,
                        )
                    },
                    onOriginSearch = { searchEndpoint(RouteEndpoint.Origin) },
                    onDestinationSearch = { searchEndpoint(RouteEndpoint.Destination) },
                    waypointContent = {
                        if (selectedMode == RouteMode.Drive) {
                            WaypointEditors(
                                waypoints = waypoints,
                                onQueryChange = { index, query ->
                                    waypoints = waypoints.toMutableList().apply {
                                        this[index] = WaypointDraft(query)
                                    }
                                    invalidateRoute()
                                    searchEndpoint(RouteEndpoint.Waypoint(index), query, debounceSearch = true)
                                },
                                onSearch = { index -> searchEndpoint(RouteEndpoint.Waypoint(index)) },
                                onRemove = { index ->
                                    searchJob?.cancel()
                                    searchJob = null
                                    waypoints = waypoints.toMutableList().apply { removeAt(index) }
                                    activeEndpoint = null
                                    suggestions = emptyList()
                                    suggestionMessage = null
                                    invalidateRoute()
                                },
                                onMove = { fromIndex, toIndex ->
                                    searchJob?.cancel()
                                    searchJob = null
                                    waypoints = waypoints.toMutableList().apply {
                                        add(toIndex, removeAt(fromIndex))
                                    }
                                    activeEndpoint = null
                                    suggestions = emptyList()
                                    suggestionMessage = null
                                    invalidateRoute()
                                },
                                onAdd = {
                                    if (waypoints.size < 3) {
                                        waypoints = waypoints + WaypointDraft()
                                        invalidateRoute()
                                    }
                                },
                            )
                        }
                    },
                    onSwap = {
                        val previousOrigin = origin
                        val previousOriginQuery = originQuery
                        origin = destination
                        originQuery = destinationQuery
                        destination = previousOrigin
                        destinationQuery = previousOriginQuery
                        invalidateRoute()
                    },
                )
                Spacer(Modifier.height(6.dp))
                RouteModeSelector(
                    selectedMode = selectedMode,
                    onSelected = {
                        searchJob?.cancel()
                        searchJob = null
                        activeEndpoint = null
                        suggestions = emptyList()
                        suggestionMessage = null
                        selectedMode = it
                        invalidateRoute()
                    },
                )
                if (activeEndpoint != null) {
                    Spacer(Modifier.height(6.dp))
                    SuggestionList(
                        places = suggestions,
                        message = suggestionMessage,
                        onSelected = ::selectEndpoint,
                    )
                }
            }
        }
        if (activeEndpoint == null) {
            Column(
                modifier = Modifier
                    .align(if (isLandscape) Alignment.BottomStart else Alignment.BottomCenter)
                    .padding(horizontal = panelHorizontalPadding, vertical = 8.dp)
                    .widthIn(max = panelMaxWidth)
                    .fillMaxWidth()
                    .onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInRoot()
                        bottomStackTopPx = bounds.top.roundToInt()
                        bottomStackRightPx = bounds.right.roundToInt()
                    },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (selectedMode == RouteMode.Drive) {
                    DrivePreferencesSection(
                        expanded = drivePreferencesExpanded,
                        onExpandedChange = { drivePreferencesExpanded = it },
                        options = driveOptions,
                        onChanged = {
                            driveOptions = it
                            onDriveOptionsChanged(it)
                            invalidateRoute()
                        },
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .semantics { contentDescription = "路线规划结果" },
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                    shape = MaterialTheme.shapes.extraLarge,
                    shadowElevation = 16.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .heightIn(max = bottomStackMaxHeight)
                            .pointerInput(selectedPlan?.id, detailsExpanded) {
                                if (!detailsExpanded) {
                                    var upwardDrag = 0f
                                    detectVerticalDragGestures(
                                        onVerticalDrag = { change, dragAmount ->
                                            if (dragAmount < 0f) {
                                                upwardDrag -= dragAmount
                                                change.consume()
                                            }
                                        },
                                        onDragEnd = {
                                            if (upwardDrag > 24f) detailsExpanded = true
                                            upwardDrag = 0f
                                        },
                                    )
                                }
                            }
                            .verticalScroll(resultsScrollState)
                            .padding(top = if (planState is RoutePlanState.Ready) 8.dp else 0.dp),
                    ) {
                        if (planState is RoutePlanState.Ready) {
                            Box(
                                Modifier
                                    .align(Alignment.CenterHorizontally)
                                    .padding(bottom = 6.dp)
                                    .size(width = 30.dp, height = 4.dp)
                                    .background(
                                        MaterialTheme.colorScheme.outlineVariant,
                                        RoundedCornerShape(50),
                                    ),
                            )
                        }
                        RouteResults(
                            state = planState,
                            selectedPlan = selectedPlan,
                            onSelected = {
                                selectedPlan = it
                                detailsExpanded = false
                                onRouteSelected(it)
                                val plans = (planState as? RoutePlanState.Ready)?.plans.orEmpty()
                                onRoutesChanged(plans, it)
                            },
                            detailsExpanded = detailsExpanded,
                            onDetailsExpandedChange = { detailsExpanded = it },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (selectedPlan == null) {
                            Button(
                                onClick = ::planRoutes,
                                enabled = origin != null &&
                                    destination != null &&
                                    !hasUnconfirmedWaypoint() &&
                                    planState !is RoutePlanState.Loading,
                                modifier = Modifier
                                    .padding(horizontal = 10.dp, vertical = 8.dp)
                                    .fillMaxWidth()
                                    .heightIn(min = 46.dp),
                                shape = MaterialTheme.shapes.medium,
                            ) {
                                Text("规划${selectedMode.label}路线")
                            }
                        } else {
                            if (selectedMode == RouteMode.Transit) {
                                OutlinedButton(
                                    onClick = { detailsExpanded = true },
                                    modifier = Modifier
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                        .fillMaxWidth()
                                        .heightIn(min = 46.dp),
                                    shape = MaterialTheme.shapes.medium,
                                ) {
                                    Text("查看公交详情")
                                }
                            } else {
                                Row(
                                    modifier = Modifier
                                        .padding(horizontal = 10.dp, vertical = 8.dp)
                                        .fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            val request = plannedRequest
                                            val routePlan = selectedPlan
                                            if (request != null && routePlan != null) {
                                                onStartNavigation(request, routePlan, true)
                                            }
                                        },
                                        modifier = Modifier.weight(1f).heightIn(min = 46.dp),
                                        shape = MaterialTheme.shapes.medium,
                                    ) {
                                        Text("模拟导航")
                                    }
                                    Button(
                                        onClick = {
                                            val request = plannedRequest
                                            val routePlan = selectedPlan
                                            if (request != null && routePlan != null) {
                                                onStartNavigation(request, routePlan, false)
                                            }
                                        },
                                        modifier = Modifier.weight(1f).heightIn(min = 46.dp),
                                        shape = MaterialTheme.shapes.medium,
                                    ) {
                                        Text("开始导航")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private const val CURRENT_LOCATION_ID = "current-location"

@Composable
private fun EndpointEditor(
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
) {
    Row(modifier = Modifier.height(IntrinsicSize.Min)) {
        Column(
            modifier = Modifier
                .fillMaxHeight()
                .padding(vertical = 14.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(Modifier.size(8.dp).background(MaterialTheme.colorScheme.primary, CircleShape))
            Box(Modifier.width(2.dp).weight(1f).background(Color(0xFFD6DFDC)))
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
            EndpointField("起点", originQuery, origin, onOriginChange, onOriginSearch)
            waypointContent()
            EndpointField("终点", destinationQuery, destination, onDestinationChange, onDestinationSearch)
        }
        TextButton(
            onClick = onSwap,
            enabled = origin != null || destination != null,
            modifier = Modifier.heightIn(min = 44.dp),
        ) {
            Text("交换", fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
        }
    }
}

@Composable
private fun EndpointField(
    label: String,
    query: String,
    selectedPlace: Place?,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    modifier: Modifier = Modifier,
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
            Surface(
                color = if (label == "终点") MaterialTheme.colorScheme.secondary else MaterialTheme.colorScheme.primary,
                shape = CircleShape,
            ) {
                Text(
                    text = if (label == "终点") "终" else if (label.startsWith("途经点")) "经" else "起",
                    modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                    color = Color.White,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                )
            }
            Spacer(Modifier.width(6.dp))
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
            TextButton(onClick = onSearch, modifier = Modifier.heightIn(min = 44.dp)) {
                Text(
                    if (selectedPlace != null) "更改" else "搜索",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

@Composable
private fun SuggestionList(
    places: List<Place>,
    message: String?,
    onSelected: (Place) -> Unit,
) {
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

@Composable
private fun RouteModeSelector(
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
                    shape = RoundedCornerShape(8.dp),
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
                    shape = RoundedCornerShape(8.dp),
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

private enum class DrivePreference(val label: String) {
    Recommended("推荐"),
    AvoidCongestion("躲避拥堵"),
    AvoidHighway("不走高速"),
    SaveMoney("少收费"),
    PrioritizeHighway("高速优先"),
}

@Composable
private fun DrivePreferencesSection(
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
private fun DrivePreferenceSelector(
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
                            onClick = { onChanged(preset.toOptions()) },
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
private fun WaypointEditors(
    waypoints: List<WaypointDraft>,
    onQueryChange: (Int, String) -> Unit,
    onSearch: (Int) -> Unit,
    onRemove: (Int) -> Unit,
    onMove: (Int, Int) -> Unit,
    onAdd: () -> Unit,
) {
    Column {
        waypoints.forEachIndexed { index, waypoint ->
            Spacer(Modifier.height(6.dp))
            Column {
                EndpointField(
                    label = "途经点 ${index + 1}",
                    query = waypoint.query,
                    selectedPlace = waypoint.place,
                    onQueryChange = { onQueryChange(index, it) },
                    onSearch = { onSearch(index) },
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = { onMove(index, index - 1) },
                        enabled = index > 0,
                        modifier = Modifier.semantics {
                            contentDescription = "上移途经点 ${index + 1}"
                        },
                    ) {
                        Text("上移", fontSize = 12.sp)
                    }
                    TextButton(
                        onClick = { onMove(index, index + 1) },
                        enabled = index < waypoints.lastIndex,
                        modifier = Modifier.semantics {
                            contentDescription = "下移途经点 ${index + 1}"
                        },
                    ) {
                        Text("下移", fontSize = 12.sp)
                    }
                    TextButton(
                        onClick = { onRemove(index) },
                        modifier = Modifier.semantics {
                            contentDescription = "移除途经点 ${index + 1}"
                        },
                    ) {
                        Text("移除", fontSize = 12.sp)
                    }
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
        TextButton(
            onClick = onAdd,
            enabled = waypoints.size < 3,
            modifier = Modifier
                .heightIn(min = 48.dp)
                .semantics { contentDescription = "添加途经点" },
        ) {
            Text(if (waypoints.isEmpty()) "+ 途经点" else "+ 继续添加", fontSize = 11.sp)
        }
    }
}

@Composable
private fun RouteResults(
    state: RoutePlanState,
    selectedPlan: RoutePlan?,
    onSelected: (RoutePlan) -> Unit,
    detailsExpanded: Boolean,
    onDetailsExpandedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    when (state) {
        RoutePlanState.Idle -> Unit
        RoutePlanState.Loading -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
        is RoutePlanState.Failed -> Text(
            text = state.message,
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 14.dp),
            color = MaterialTheme.colorScheme.error,
        )
        is RoutePlanState.Ready -> {
            if (state.plans.isEmpty()) {
                Text(
                    text = "没有找到可用路线",
                    modifier = Modifier.padding(vertical = 14.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            } else {
                Text(
                    text = "为你推荐 ${state.plans.size} 条路线",
                    modifier = Modifier.padding(horizontal = 14.dp),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Spacer(Modifier.height(6.dp))
                LazyRow(
                    modifier = modifier,
                    contentPadding = PaddingValues(horizontal = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(state.plans, key = RoutePlan::id) { plan ->
                        RoutePlanItem(
                            plan = plan,
                            selected = plan.id == selectedPlan?.id,
                            onClick = { onSelected(plan) },
                        )
                    }
                }
                selectedPlan?.let { plan ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(start = 14.dp, top = 6.dp, end = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "${formatRouteDuration(plan.durationSeconds)} · " +
                                    formatRouteDistance(plan.distanceMeters),
                                color = MaterialTheme.colorScheme.onSurface,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = plan.summary,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 11.sp,
                                maxLines = 1,
                            )
                        }
                        TextButton(
                            onClick = { onDetailsExpandedChange(!detailsExpanded) },
                            modifier = Modifier
                                .heightIn(min = 42.dp)
                                .semantics {
                                    contentDescription = if (detailsExpanded) "收起路线详情" else "查看路线详情"
                                },
                        ) {
                            Text(if (detailsExpanded) "收起详情" else "查看详情", fontSize = 12.sp)
                        }
                    }
                    AnimatedVisibility(visible = detailsExpanded) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 14.dp),
                        ) {
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                            Spacer(Modifier.height(6.dp))
                            if (plan.steps.isEmpty()) {
                                Text(
                                    text = "暂无分段导航信息",
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontSize = 12.sp,
                                )
                            } else {
                                Column {
                                    plan.steps.forEachIndexed { index, step ->
                                        Text(
                                            text = "${index + 1}. $step",
                                            modifier = Modifier.padding(vertical = 4.dp),
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontSize = 12.sp,
                                            lineHeight = 17.sp,
                                        )
                                    }
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
private fun RoutePlanItem(
    plan: RoutePlan,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .widthIn(min = 172.dp, max = 196.dp)
            .heightIn(min = 92.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = "路线方案 ${formatRouteDuration(plan.durationSeconds)}"
            }
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = MaterialTheme.shapes.medium,
            ),
        shape = MaterialTheme.shapes.medium,
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
        shadowElevation = if (selected) 5.dp else 1.dp,
    ) {
        Column(modifier = Modifier.padding(10.dp).fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatRouteDuration(plan.durationSeconds),
                    fontWeight = FontWeight.Bold,
                    color = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                    fontSize = 18.sp,
                )
                Spacer(Modifier.weight(1f))
                if (selected) {
                    Text(
                        "当前",
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier
                            .background(MaterialTheme.colorScheme.primary, RoundedCornerShape(50))
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(plan.summary, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    formatRouteDistance(plan.distanceMeters),
                    color = MaterialTheme.colorScheme.onSurface,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 12.sp,
                )
                Spacer(Modifier.weight(1f))
                plan.costYuan?.let { Text("约 ¥%.1f".format(it), color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp) }
            }
        }
    }
}

internal fun formatRouteDuration(durationSeconds: Long): String {
    val minutes = (durationSeconds + 59) / 60
    val hours = minutes / 60
    val remainingMinutes = minutes % 60
    return when {
        hours == 0L -> "$minutes 分钟"
        remainingMinutes == 0L -> "$hours 小时"
        else -> "$hours 小时 $remainingMinutes 分钟"
    }
}

internal fun formatRouteDistance(distanceMeters: Int): String = if (distanceMeters < 1_000) {
    "$distanceMeters 米"
} else {
    "%.1f 公里".format(distanceMeters / 1_000f)
}
