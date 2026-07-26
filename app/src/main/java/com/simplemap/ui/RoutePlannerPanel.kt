package com.simplemap.ui

import android.location.Location
import androidx.compose.foundation.background
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import com.simplemap.route.DriveRouteOptions
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
import java.util.concurrent.atomic.AtomicInteger
import kotlin.math.roundToInt

private sealed interface RouteEndpoint {
    data object Origin : RouteEndpoint
    data object Destination : RouteEndpoint
    data class Waypoint(val index: Int) : RouteEndpoint
}

internal data class WaypointDraft(val query: String = "", val place: Place? = null)

internal sealed interface RoutePlanState {
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
    onBack: () -> Unit = {},
) {
    val coroutineScope = rememberCoroutineScope()
    var origin by remember { mutableStateOf(initialOrigin) }
    var destination by remember(initialDestination) { mutableStateOf(initialDestination) }
    var originQuery by remember {
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
    val landscapeSetupScrollState = rememberScrollState()
    var searchJob by remember { mutableStateOf<Job?>(null) }
    var planJob by remember { mutableStateOf<Job?>(null) }
    var previousInitialOrigin by remember { mutableStateOf(initialOrigin) }
    var previousInitialDestination by remember { mutableStateOf(initialDestination) }
    val planVersion = remember { AtomicInteger() }
    var viewportHeightPx by remember { mutableIntStateOf(0) }
    var topPanelBottomPx by remember { mutableIntStateOf(0) }
    var topPanelRightPx by remember { mutableIntStateOf(0) }
    var bottomStackTopPx by remember { mutableIntStateOf(0) }
    var bottomStackRightPx by remember { mutableIntStateOf(0) }
    var driveOptionsInitialized by remember { mutableStateOf(false) }
    val canPlanRoute by remember { derivedStateOf { origin != null && destination != null } }
    val latestOrigin by rememberUpdatedState(origin)
    val latestDestination by rememberUpdatedState(destination)

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
        if (origin == null || destination == null) return
        if (hasUnconfirmedWaypoint()) return
        planJob?.cancel()
        selectedPlan = null
        plannedRequest = null
        detailsExpanded = false
        planState = RoutePlanState.Loading
        onRoutesChanged(emptyList(), null)
        onRouteCleared()
        val requestVersion = planVersion.incrementAndGet()
        planJob = coroutineScope.launch {
            // Debounce rapid successive changes (typing, preference toggles) to avoid
            // re-planning and re-drawing the route on every keystroke/tap.
            delay(350L)
            if (requestVersion != planVersion.get()) return@launch
            val routeOrigin = latestOrigin ?: return@launch
            val routeDestination = latestDestination ?: return@launch
            val request = RouteRequest(
                origin = routeOrigin,
                destination = routeDestination,
                waypoints = if (selectedMode == RouteMode.Drive) waypoints.mapNotNull(WaypointDraft::place) else emptyList(),
                mode = selectedMode,
                driveOptions = driveOptions,
                city = routeDestination.district.substringBefore(" · "),
                originCity = routeOrigin.district.substringBefore(" · "),
                destinationCity = routeDestination.district.substringBefore(" · "),
            )
            val result = withContext(Dispatchers.IO) {
                routePlanRepository.plan(request)
            }
            if (requestVersion != planVersion.get()) {
                return@launch
            }
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
        val shouldSyncOrigin = originChanged &&
            initialOrigin != null &&
            (origin == null || origin?.id == CURRENT_LOCATION_ID)

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

    // Automatically re-plan when the drive preference changes, but skip the very first
    // composition (which reflects the initial/default options rather than a user edit).
    LaunchedEffect(driveOptions) {
        if (!driveOptionsInitialized) {
            driveOptionsInitialized = true
            return@LaunchedEffect
        }
        if (canPlanRoute) {
            planRoutes()
        }
    }

    LaunchedEffect(selectedPlan?.id) {
        detailsExpanded = false
        resultsScrollState.scrollTo(0)
    }

    val detailsSwipeConnection = remember(selectedPlan?.id, detailsExpanded) {
        object : NestedScrollConnection {
            override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                if (!detailsExpanded && selectedPlan != null && available.y < -12f) {
                    detailsExpanded = true
                }
                return Offset.Zero
            }
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
            .imePadding()
            .onSizeChanged { viewportHeightPx = it.height },
    ) {
        val isLandscape = maxWidth > maxHeight
        val landscapePlans = (planState as? RoutePlanState.Ready)?.plans.orEmpty()
        val showLandscapeRouteSelector = isLandscape &&
            selectedMode == RouteMode.Drive &&
            activeEndpoint == null &&
            selectedPlan != null &&
            landscapePlans.isNotEmpty()
        val extraCompact = maxWidth < 360.dp
        val panelMaxWidth = if (isLandscape) {
            minOf(maxOf(maxWidth * 0.38f, 280.dp), minOf(400.dp, maxWidth * 0.5f - 12.dp))
        } else {
            640.dp
        }
        val landscapePanelContentWidth = (panelMaxWidth - 12.dp).coerceAtLeast(0.dp)
        val compactHeight = maxHeight < 520.dp
        val panelHorizontalPadding = if (extraCompact) 6.dp else if (isLandscape) 12.dp else if (maxWidth < 400.dp) 8.dp else 10.dp
        val editorCollapsedMaxHeight = if (isLandscape) {
            minOf(
                (maxHeight * if (compactHeight) 0.52f else 0.44f) + 48.dp * waypoints.size,
                maxHeight - 96.dp,
            )
        } else {
            minOf(
                if (extraCompact) 188.dp else 220.dp,
                maxHeight * if (compactHeight) 0.36f else 0.32f,
            )
        }
        val editorExpandedMaxHeight = if (isLandscape) {
            maxHeight - 16.dp
        } else {
            maxHeight - 12.dp
        }
        val desiredBottomStackMaxHeight = when {
            isLandscape && detailsExpanded -> maxHeight * if (compactHeight) 0.62f else 0.58f
            isLandscape -> maxHeight * if (compactHeight) 0.56f else 0.52f
            detailsExpanded -> maxHeight * if (compactHeight) 0.66f else 0.62f
            planState is RoutePlanState.Ready -> minOf(260.dp, maxHeight * 0.34f)
            else -> minOf(196.dp, maxHeight * 0.28f)
        }
        val bottomStackMaxHeight = minOf(
            desiredBottomStackMaxHeight,
            maxOf(
                96.dp,
                maxHeight - editorCollapsedMaxHeight - if (isLandscape) 28.dp else 56.dp,
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
                    topInsetPx = if (isLandscape) 0 else topPanelBottomPx,
                    bottomInsetPx = if (isLandscape) {
                        0
                    } else if (viewportHeightPx > 0 && bottomStackTopPx > 0) {
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
        if (isLandscape && !showLandscapeRouteSelector) {
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
                    .width(panelMaxWidth)
                    .fillMaxHeight()
                    .semantics { contentDescription = "横屏路线规划面板" },
                color = MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.97f),
                shape = PanelShapeLarge,
                shadowElevation = 18.dp,
            ) {}
        }
        if (!showLandscapeRouteSelector) {
            Surface(
                modifier = Modifier
                    .align(if (isLandscape) Alignment.TopStart else Alignment.TopCenter)
                    .then(
                        if (isLandscape) {
                            Modifier
                                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
                                .width(landscapePanelContentWidth)
                        } else {
                            Modifier
                                .padding(horizontal = panelHorizontalPadding, vertical = 6.dp)
                                .widthIn(max = panelMaxWidth)
                                .fillMaxWidth()
                        },
                    )
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
                color = if (isLandscape) Color.Transparent else MaterialTheme.colorScheme.surface.copy(alpha = 0.99f),
                shape = if (isLandscape) PanelShapeLarge else MaterialTheme.shapes.large,
                shadowElevation = if (isLandscape) 0.dp else 12.dp,
            ) {
                Column(
                    modifier = Modifier
                        .verticalScroll(rememberScrollState())
                        .padding(
                            horizontal = if (extraCompact) 9.dp else 12.dp,
                            vertical = if (extraCompact) 8.dp else 10.dp,
                        ),
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
                        if (selectedMode == RouteMode.Drive && waypoints.isNotEmpty()) {
                            SimpleWaypointFields(
                                waypoints = waypoints,
                                compact = isLandscape,
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
                    showAddWaypoint = selectedMode == RouteMode.Drive,
                    canAddWaypoint = selectedMode == RouteMode.Drive && waypoints.size < 3,
                    compact = isLandscape,
                    onAddWaypoint = {
                        if (waypoints.size < 3) {
                            waypoints = waypoints + WaypointDraft()
                            invalidateRoute()
                        }
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
        }
        if (activeEndpoint == null && !showLandscapeRouteSelector) {
            Column(
                modifier = Modifier
                    .align(if (isLandscape) Alignment.TopStart else Alignment.BottomCenter)
                    .then(
                        if (isLandscape) {
                            Modifier
                                .padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
                                .padding(top = editorCollapsedMaxHeight)
                                .width(landscapePanelContentWidth)
                                .fillMaxHeight()
                                .verticalScroll(landscapeSetupScrollState)
                        } else {
                            Modifier
                                .padding(horizontal = panelHorizontalPadding, vertical = 8.dp)
                                .widthIn(max = panelMaxWidth)
                                .fillMaxWidth()
                        },
                    )
                    .onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInRoot()
                        bottomStackTopPx = bounds.top.roundToInt()
                        bottomStackRightPx = bounds.right.roundToInt()
                    },
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                if (!isLandscape && selectedMode == RouteMode.Drive && !detailsExpanded) {
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
                    color = if (isLandscape) Color.Transparent else MaterialTheme.colorScheme.surface.copy(alpha = 0.98f),
                    shape = if (isLandscape) PanelShapeMedium else MaterialTheme.shapes.extraLarge,
                    shadowElevation = if (isLandscape) 0.dp else 16.dp,
                ) {
                    Column(
                        modifier = Modifier
                            .then(
                                if (isLandscape) Modifier else Modifier.heightIn(max = bottomStackMaxHeight),
                            )
                            .nestedScroll(detailsSwipeConnection)
                            .then(
                                if (isLandscape) Modifier else Modifier.verticalScroll(resultsScrollState),
                            )
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
        if (showLandscapeRouteSelector) {
            LandscapeRouteSelectionPanel(
                plans = landscapePlans,
                selectedPlan = selectedPlan,
                detailsExpanded = detailsExpanded,
                onBack = onBack,
                onAddWaypoint = {
                    if (waypoints.size < 3) {
                        val index = waypoints.size
                        waypoints = waypoints + WaypointDraft()
                        invalidateRoute()
                        activeEndpoint = RouteEndpoint.Waypoint(index)
                    }
                },
                onRouteSelected = {
                    selectedPlan = it
                    detailsExpanded = false
                    onRouteSelected(it)
                    onRoutesChanged(landscapePlans, it)
                },
                onDetailsExpandedChange = { detailsExpanded = it },
                onStartSimulatedNavigation = {
                    val request = plannedRequest
                    val routePlan = selectedPlan
                    if (request != null && routePlan != null) {
                        onStartNavigation(request, routePlan, true)
                    }
                },
                onStartNavigation = {
                    val request = plannedRequest
                    val routePlan = selectedPlan
                    if (request != null && routePlan != null) {
                        onStartNavigation(request, routePlan, false)
                    }
                },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
                    .width(panelMaxWidth)
                    .fillMaxHeight()
                    .onGloballyPositioned { coordinates ->
                        val bounds = coordinates.boundsInRoot()
                        topPanelBottomPx = 0
                        bottomStackTopPx = 0
                        topPanelRightPx = bounds.right.roundToInt()
                        bottomStackRightPx = bounds.right.roundToInt()
                    },
            )
        }
        if (isLandscape && selectedMode == RouteMode.Drive && activeEndpoint == null) {
            val preferenceWidth = (maxWidth - panelMaxWidth - 36.dp).coerceIn(180.dp, 320.dp)
            LandscapeDrivePreferences(
                expanded = drivePreferencesExpanded,
                options = driveOptions,
                onExpandedChange = { drivePreferencesExpanded = it },
                onChanged = {
                    driveOptions = it
                    onDriveOptionsChanged(it)
                    invalidateRoute()
                },
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(start = panelMaxWidth + 18.dp, top = 8.dp)
                    .width(preferenceWidth),
            )
        }
    }
}

