package com.simplemap.search

interface SearchHistoryStore {
    fun load(): List<Place>

    fun record(place: Place): Boolean

    fun remove(placeId: String): Boolean

    fun clear(): Boolean
}

internal const val SEARCH_HISTORY_LIMIT = 10

internal fun appendSearchHistory(history: List<Place>, place: Place): List<Place> =
    (listOf(place) + history.filterNot { it.id == place.id }).take(SEARCH_HISTORY_LIMIT)
