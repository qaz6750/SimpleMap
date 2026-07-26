package com.simplemap.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHistoryTest {
    @Test
    fun newestEntryComesFirst() {
        val history = appendSearchHistory(listOf(place("a"), place("b")), place("c"))

        assertEquals(listOf("c", "a", "b"), history.map(Place::id))
    }

    @Test
    fun repeatedEntryMovesToFront() {
        val history = appendSearchHistory(listOf(place("a"), place("b")), place("b"))

        assertEquals(listOf("b", "a"), history.map(Place::id))
    }

    @Test
    fun historyKeepsAtMostTenEntries() {
        val existing = (0 until SEARCH_HISTORY_LIMIT).map { place("p$it") }

        val history = appendSearchHistory(existing, place("new"))

        assertEquals(SEARCH_HISTORY_LIMIT, history.size)
        assertEquals("new", history.first().id)
        assertEquals("p8", history.last().id)
    }

    private fun place(id: String) = Place(
        id = id,
        name = id,
        address = "",
        district = "",
        category = "",
        phone = "",
        latitude = 0.0,
        longitude = 0.0,
        distanceMeters = null,
    )
}
