package com.simplemap.ui

import android.location.Location
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.simplemap.search.Place
import com.simplemap.search.PlaceRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Immutable
internal data class SearchCoordinate(
    val latitude: Double,
    val longitude: Double,
)

internal data class PlaceSearchUiState(
    val active: Boolean = false,
    val query: String = "",
    val result: PlaceSearchResult = PlaceSearchResult.Idle,
    val nearbyCenter: SearchCoordinate? = null,
)

internal sealed interface PlaceSearchResult {
    data object Idle : PlaceSearchResult
    data object Loading : PlaceSearchResult
    data class Results(val places: List<Place>) : PlaceSearchResult
    data class Failed(val message: String) : PlaceSearchResult
}

internal class PlaceSearchStateHolder(
    private val repository: PlaceRepository,
    private val coroutineScope: CoroutineScope,
) {
    var uiState by mutableStateOf(PlaceSearchUiState())
        private set

    private var searchJob: Job? = null

    fun open() {
        uiState = uiState.copy(active = true, nearbyCenter = null)
    }

    fun openNearby(query: String, center: SearchCoordinate) {
        uiState = uiState.copy(active = true, query = query, nearbyCenter = center)
    }

    fun hide() {
        uiState = uiState.copy(active = false, nearbyCenter = null)
    }

    fun close() {
        searchJob?.cancel()
        searchJob = null
        uiState = PlaceSearchUiState()
    }

    fun updateQuery(query: String) {
        uiState = uiState.copy(query = query)
    }

    fun cancelPendingSearch() {
        searchJob?.cancel()
        searchJob = null
    }

    fun showIdle() {
        uiState = uiState.copy(result = PlaceSearchResult.Idle)
    }

    fun submit(reference: SearchCoordinate?, city: String) {
        val query = uiState.query.trim()
        if (query.isEmpty()) return

        val nearbyCenter = uiState.nearbyCenter
        cancelPendingSearch()
        uiState = uiState.copy(result = PlaceSearchResult.Loading)
        searchJob = coroutineScope.launch {
            val result = withContext(Dispatchers.IO) {
                val placeResult = if (nearbyCenter != null) {
                    repository.searchNearby(
                        query = query,
                        latitude = nearbyCenter.latitude,
                        longitude = nearbyCenter.longitude,
                        radiusMeters = 3_000,
                    )
                } else {
                    repository.search(query, city)
                }
                placeResult.map { places -> places.withDistancesFrom(reference) }
            }
            uiState = uiState.copy(
                result = result.fold(
                    onSuccess = { places -> PlaceSearchResult.Results(places) },
                    onFailure = {
                        PlaceSearchResult.Failed(it.localizedMessage ?: "搜索服务暂不可用")
                    },
                ),
            )
        }
    }
}

private fun List<Place>.withDistancesFrom(reference: SearchCoordinate?): List<Place> {
    reference ?: return this
    val distance = FloatArray(1)
    return map { place ->
        Location.distanceBetween(
            reference.latitude,
            reference.longitude,
            place.latitude,
            place.longitude,
            distance,
        )
        place.copy(distanceMeters = distance.first().toInt())
    }.sortedBy(Place::distanceMeters)
}
