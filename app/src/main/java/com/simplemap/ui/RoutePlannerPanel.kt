package com.simplemap.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.draw.clip
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
import com.simplemap.route.RouteMode
import com.simplemap.route.RoutePlan
import com.simplemap.route.RoutePlanRepository
import com.simplemap.search.Place
import com.simplemap.search.PlaceRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private enum class RouteEndpoint {
    Origin,
    Destination,
}

private sealed interface RoutePlanState {
    data object Idle : RoutePlanState
    data object Loading : RoutePlanState
    data class Ready(val plans: List<RoutePlan>) : RoutePlanState
    data class Failed(val message: String) : RoutePlanState
}

@Composable
internal fun RoutePlannerPanel(
    placeRepository: PlaceRepository,
    routePlanRepository: RoutePlanRepository,
    initialDestination: Place?,
    onRouteSelected: (RoutePlan) -> Unit,
    onRouteCleared: () -> Unit,
    onStartNavigation: (Place, Place, RoutePlan) -> Unit,
    modifier: Modifier = Modifier,
) {
    val coroutineScope = rememberCoroutineScope()
    var origin by remember { mutableStateOf<Place?>(null) }
    var destination by remember(initialDestination) { mutableStateOf(initialDestination) }
    var originQuery by remember { mutableStateOf("") }
    var destinationQuery by remember(initialDestination) {
        mutableStateOf(initialDestination?.name.orEmpty())
    }
    var activeEndpoint by remember { mutableStateOf<RouteEndpoint?>(null) }
    var suggestions by remember { mutableStateOf<List<Place>>(emptyList()) }
    var suggestionMessage by remember { mutableStateOf<String?>(null) }
    var selectedMode by remember { mutableStateOf(RouteMode.Drive) }
    var planState by remember { mutableStateOf<RoutePlanState>(RoutePlanState.Idle) }
    var selectedPlan by remember { mutableStateOf<RoutePlan?>(null) }
    var workJob by remember { mutableStateOf<Job?>(null) }

    fun invalidateRoute() {
        selectedPlan = null
        planState = RoutePlanState.Idle
        onRouteCleared()
    }

    fun searchEndpoint(endpoint: RouteEndpoint) {
        val query = when (endpoint) {
            RouteEndpoint.Origin -> originQuery
            RouteEndpoint.Destination -> destinationQuery
        }.trim()
        if (query.isEmpty()) return
        workJob?.cancel()
        activeEndpoint = endpoint
        suggestions = emptyList()
        suggestionMessage = "正在搜索地点"
        workJob = coroutineScope.launch {
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
        when (activeEndpoint) {
            RouteEndpoint.Origin -> {
                origin = place
                originQuery = place.name
            }
            RouteEndpoint.Destination -> {
                destination = place
                destinationQuery = place.name
            }
            null -> Unit
        }
        activeEndpoint = null
        suggestions = emptyList()
        suggestionMessage = null
        invalidateRoute()
    }

    fun planRoutes() {
        val routeOrigin = origin ?: return
        val routeDestination = destination ?: return
        workJob?.cancel()
        selectedPlan = null
        planState = RoutePlanState.Loading
        workJob = coroutineScope.launch {
            val city = routeDestination.district.substringBefore(" · ")
            val result = withContext(Dispatchers.IO) {
                routePlanRepository.plan(routeOrigin, routeDestination, selectedMode, city)
            }
            planState = result.fold(
                onSuccess = { plans ->
                    plans.firstOrNull()?.let {
                        selectedPlan = it
                        onRouteSelected(it)
                    }
                    RoutePlanState.Ready(plans)
                },
                onFailure = {
                    RoutePlanState.Failed(it.localizedMessage ?: "路线规划暂不可用")
                },
            )
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        Surface(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(horizontal = 12.dp, vertical = 8.dp)
                .fillMaxWidth()
                .widthIn(max = 680.dp),
            color = Color(0xFCFFFFFF),
            shape = RoundedCornerShape(8.dp),
            shadowElevation = 10.dp,
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                EndpointEditor(
                    originQuery = originQuery,
                    destinationQuery = destinationQuery,
                    origin = origin,
                    destination = destination,
                    onOriginChange = {
                        originQuery = it
                        origin = null
                        invalidateRoute()
                    },
                    onDestinationChange = {
                        destinationQuery = it
                        destination = null
                        invalidateRoute()
                    },
                    onOriginSearch = { searchEndpoint(RouteEndpoint.Origin) },
                    onDestinationSearch = { searchEndpoint(RouteEndpoint.Destination) },
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
                Spacer(Modifier.height(8.dp))
                RouteModeSelector(
                    selectedMode = selectedMode,
                    onSelected = {
                        selectedMode = it
                        invalidateRoute()
                    },
                )
            }
        }
        if (activeEndpoint != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(start = 12.dp, top = 174.dp, end = 12.dp)
                    .fillMaxWidth()
                    .widthIn(max = 680.dp),
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 8.dp,
                color = Color.White,
            ) {
                SuggestionList(
                    places = suggestions,
                    message = suggestionMessage,
                    onSelected = ::selectEndpoint,
                )
            }
        } else {
            Surface(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .padding(horizontal = 12.dp, vertical = 10.dp)
                    .fillMaxWidth()
                    .widthIn(max = 680.dp),
                color = Color(0xFAFFFFFF),
                shape = RoundedCornerShape(8.dp),
                shadowElevation = 14.dp,
            ) {
                Column(modifier = Modifier.padding(top = if (planState is RoutePlanState.Ready) 10.dp else 0.dp)) {
                RouteResults(
                    state = planState,
                    selectedPlan = selectedPlan,
                    onSelected = {
                        selectedPlan = it
                        onRouteSelected(it)
                    },
                    modifier = Modifier.fillMaxWidth(),
                )
                Button(
                    onClick = if (selectedPlan == null) ::planRoutes else {
                        {
                            val routeOrigin = origin
                            val routeDestination = destination
                            val routePlan = selectedPlan
                            if (routeOrigin != null && routeDestination != null && routePlan != null) {
                                onStartNavigation(routeOrigin, routeDestination, routePlan)
                            }
                        }
                    },
                    enabled = origin != null && destination != null && planState !is RoutePlanState.Loading,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp).fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (selectedPlan == null) Color(0xFF1769E0) else Color(0xFF172033),
                    ),
                ) {
                    Text(if (selectedPlan == null) "规划${selectedMode.label}路线" else "开始导航")
                }
                }
            }
        }
    }
}

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
    onSwap: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(Modifier.size(9.dp).background(Color(0xFF1769E0), CircleShape))
            Box(Modifier.size(width = 2.dp, height = 36.dp).background(Color(0xFFD6DFDC)))
            Box(
                Modifier
                    .size(9.dp)
                    .border(2.dp, Color(0xFFE95D45), CircleShape),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            EndpointField("起点", originQuery, origin, onOriginChange, onOriginSearch)
            EndpointField("终点", destinationQuery, destination, onDestinationChange, onDestinationSearch)
        }
        TextButton(onClick = onSwap, enabled = origin != null || destination != null) {
            Text("交换", fontWeight = FontWeight.SemiBold)
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
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .semantics { contentDescription = "$label 地点" },
        color = Color(0xFFF5F7FA),
        shape = RoundedCornerShape(8.dp),
    ) {
        Row(
            modifier = Modifier.padding(start = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BasicTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = Color(0xFF172033)),
                cursorBrush = SolidColor(Color(0xFF1769E0)),
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                decorationBox = { innerTextField ->
                    if (query.isBlank()) {
                        Text(
                            if (label == "起点") "我的位置或输入起点" else "输入目的地",
                            color = Color(0xFF7A8798),
                            fontSize = 14.sp,
                        )
                    }
                    innerTextField()
                },
            )
            TextButton(onClick = onSearch) {
                Text("搜索", fontSize = 12.sp)
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
    HorizontalDivider(color = Color(0xFFE5EAE8))
    if (message != null) {
        Text(
            text = message,
            modifier = Modifier.padding(vertical = 22.dp),
            color = Color(0xFF66726F),
        )
    } else {
        LazyColumn(modifier = Modifier.heightIn(max = 280.dp)) {
            items(places, key = { it.id }) { place ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(role = Role.Button) { onSelected(place) }
                        .semantics { contentDescription = "选择地点 ${place.name}" }
                        .padding(vertical = 12.dp),
                ) {
                    Text(place.name, fontWeight = FontWeight.SemiBold, color = Color(0xFF17211F))
                    Text(
                        text = listOf(place.district, place.address)
                            .filter(String::isNotBlank)
                            .joinToString(" · "),
                        color = Color(0xFF6A7572),
                        fontSize = 13.sp,
                        maxLines = 1,
                    )
                }
                HorizontalDivider(color = Color(0xFFF0F3F1))
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
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFFF0F4F2))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
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
                    shape = RoundedCornerShape(6.dp),
                    color = Color.White,
                    shadowElevation = 2.dp,
                ) {
                    Text(
                        mode.label,
                        modifier = Modifier.padding(vertical = 9.dp),
                        color = Color(0xFF1769E0),
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
                    shape = RoundedCornerShape(6.dp),
                    color = Color.Transparent,
                ) {
                    Text(
                        mode.label,
                        modifier = Modifier.padding(vertical = 9.dp),
                        color = Color(0xFF65716E),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    )
                }
            }
        }
    }
}

@Composable
private fun RouteResults(
    state: RoutePlanState,
    selectedPlan: RoutePlan?,
    onSelected: (RoutePlan) -> Unit,
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
            CircularProgressIndicator(color = Color(0xFF1769E0))
        }
        is RoutePlanState.Failed -> Text(
            text = state.message,
            modifier = Modifier.padding(vertical = 18.dp),
            color = MaterialTheme.colorScheme.error,
        )
        is RoutePlanState.Ready -> {
            if (state.plans.isEmpty()) {
                Text(
                    text = "没有找到可用路线",
                    modifier = Modifier.padding(vertical = 18.dp),
                    color = Color(0xFF66726F),
                )
            } else {
                Text(
                    text = "为你推荐 ${state.plans.size} 条路线",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    color = Color(0xFF53615D),
                    fontSize = 13.sp,
                )
                Spacer(Modifier.height(8.dp))
                LazyRow(
                    modifier = modifier,
                    contentPadding = PaddingValues(horizontal = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
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
                    Text(
                        text = plan.steps.take(3).joinToString("  ·  "),
                        modifier = Modifier.padding(start = 16.dp, top = 10.dp, end = 16.dp),
                        color = Color(0xFF596561),
                        fontSize = 12.sp,
                        maxLines = 1,
                    )
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
            .size(width = 210.dp, height = 112.dp)
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = "路线方案 ${formatRouteDuration(plan.durationSeconds)}"
            }
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) Color(0xFF1769E0) else Color(0xFFE0E7E4),
                shape = RoundedCornerShape(8.dp),
            ),
        shape = RoundedCornerShape(8.dp),
        color = if (selected) Color(0xFFF1F6FF) else Color.White,
        shadowElevation = if (selected) 5.dp else 1.dp,
    ) {
        Column(modifier = Modifier.padding(12.dp).fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = formatRouteDuration(plan.durationSeconds),
                    fontWeight = FontWeight.Bold,
                    color = if (selected) Color(0xFF1769E0) else Color(0xFF172033),
                    fontSize = 20.sp,
                )
                Spacer(Modifier.weight(1f))
                if (selected) {
                    Text("推荐", color = Color.White, fontSize = 11.sp, modifier = Modifier.background(Color(0xFF1769E0), RoundedCornerShape(4.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            Text(plan.summary, color = Color(0xFF53615D), fontSize = 12.sp, maxLines = 1)
            Spacer(Modifier.weight(1f))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(formatRouteDistance(plan.distanceMeters), color = Color(0xFF35413E), fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.weight(1f))
                plan.costYuan?.let { Text("约 ¥%.1f".format(it), color = Color(0xFF7A8582), fontSize = 12.sp) }
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