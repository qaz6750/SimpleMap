package com.simplemap.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
        planState = RoutePlanState.Idle
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

    Surface(
        modifier = modifier
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(start = 14.dp, top = 10.dp, end = 14.dp, bottom = 94.dp)
            .fillMaxWidth(),
        color = Color(0xFAFFFFFF),
        shape = RoundedCornerShape(8.dp),
        shadowElevation = 12.dp,
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(
                text = "路线规划",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF17211F),
            )
            Spacer(Modifier.height(12.dp))
            EndpointField(
                label = "起点",
                query = originQuery,
                selectedPlace = origin,
                onQueryChange = {
                    originQuery = it
                    origin = null
                },
                onSearch = { searchEndpoint(RouteEndpoint.Origin) },
            )
            Spacer(Modifier.height(8.dp))
            EndpointField(
                label = "终点",
                query = destinationQuery,
                selectedPlace = destination,
                onQueryChange = {
                    destinationQuery = it
                    destination = null
                },
                onSearch = { searchEndpoint(RouteEndpoint.Destination) },
            )
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(
                    onClick = {
                        val previousOrigin = origin
                        val previousOriginQuery = originQuery
                        origin = destination
                        originQuery = destinationQuery
                        destination = previousOrigin
                        destinationQuery = previousOriginQuery
                        planState = RoutePlanState.Idle
                    },
                    enabled = origin != null || destination != null,
                ) {
                    Text("交换起终点")
                }
            }
            if (activeEndpoint != null) {
                SuggestionList(
                    places = suggestions,
                    message = suggestionMessage,
                    onSelected = ::selectEndpoint,
                )
            } else {
                RouteModeSelector(
                    selectedMode = selectedMode,
                    onSelected = {
                        selectedMode = it
                        planState = RoutePlanState.Idle
                    },
                )
                Spacer(Modifier.height(12.dp))
                Button(
                    onClick = ::planRoutes,
                    enabled = origin != null && destination != null && planState !is RoutePlanState.Loading,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF126B56)),
                ) {
                    Text("规划${selectedMode.label}路线")
                }
                RouteResults(
                    state = planState,
                    selectedPlan = selectedPlan,
                    onSelected = {
                        selectedPlan = it
                        onRouteSelected(it)
                    },
                    onStartNavigation = {
                        val routeOrigin = origin
                        val routeDestination = destination
                        val routePlan = selectedPlan
                        if (routeOrigin != null && routeDestination != null && routePlan != null) {
                            onStartNavigation(routeOrigin, routeDestination, routePlan)
                        }
                    },
                )
            }
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
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        modifier = Modifier
            .fillMaxWidth()
            .semantics { contentDescription = "$label 地点" },
        label = { Text(label) },
        placeholder = { Text("搜索$label") },
        supportingText = selectedPlace?.let {
            { Text(it.address.ifBlank { it.district }, maxLines = 1) }
        },
        trailingIcon = {
            TextButton(onClick = onSearch) { Text("搜索") }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
        shape = RoundedCornerShape(8.dp),
    )
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
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        RouteMode.entries.forEach { mode ->
            val selected = mode == selectedMode
            if (selected) {
                Button(
                    onClick = { onSelected(mode) },
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            role = Role.Tab
                            this.selected = true
                            contentDescription = mode.label
                        },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF126B56)),
                ) { Text(mode.label) }
            } else {
                OutlinedButton(
                    onClick = { onSelected(mode) },
                    modifier = Modifier
                        .weight(1f)
                        .semantics {
                            role = Role.Tab
                            this.selected = false
                            contentDescription = mode.label
                        },
                    shape = RoundedCornerShape(8.dp),
                    contentPadding = ButtonDefaults.TextButtonContentPadding,
                ) { Text(mode.label) }
            }
        }
    }
}

@Composable
private fun RouteResults(
    state: RoutePlanState,
    selectedPlan: RoutePlan?,
    onSelected: (RoutePlan) -> Unit,
    onStartNavigation: () -> Unit,
) {
    when (state) {
        RoutePlanState.Idle -> Unit
        RoutePlanState.Loading -> Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(84.dp),
            contentAlignment = Alignment.Center,
        ) {
            CircularProgressIndicator(color = Color(0xFF126B56))
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
                Spacer(Modifier.height(12.dp))
                LazyColumn(modifier = Modifier.heightIn(max = 260.dp)) {
                    items(state.plans, key = RoutePlan::id) { plan ->
                        RoutePlanItem(
                            plan = plan,
                            selected = plan.id == selectedPlan?.id,
                            onClick = { onSelected(plan) },
                        )
                    }
                }
                selectedPlan?.let { plan ->
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = plan.steps.take(3).joinToString("\n"),
                        color = Color(0xFF596561),
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                        maxLines = 3,
                    )
                    Spacer(Modifier.height(10.dp))
                    Button(
                        onClick = onStartNavigation,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF17211F)),
                    ) {
                        Text("开始导航")
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
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(role = Role.RadioButton, onClick = onClick)
            .semantics {
                this.selected = selected
                contentDescription = "路线方案 ${formatRouteDuration(plan.durationSeconds)}"
            }
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatRouteDuration(plan.durationSeconds),
                fontWeight = FontWeight.Bold,
                color = if (selected) Color(0xFF126B56) else Color(0xFF17211F),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(plan.summary, color = Color(0xFF65716E), fontSize = 13.sp, maxLines = 1)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(formatRouteDistance(plan.distanceMeters), color = Color(0xFF35413E))
            plan.costYuan?.let { Text("约 ¥%.1f".format(it), color = Color(0xFF7A8582), fontSize = 12.sp) }
        }
    }
    HorizontalDivider(color = Color(0xFFF0F3F1))
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