package com.simplemap.search

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class FavoritePlaceGroupTest {
    private val home = place("home")
    private val work = place("work")
    private val saved = place("saved")

    @Test
    fun homeAndWorkKeepOnePlaceEach() {
        val favorites = listOf(
            FavoritePlace(home, FavoriteGroup.Home),
            FavoritePlace(work, FavoriteGroup.Work),
            FavoritePlace(saved, FavoriteGroup.Saved),
        )

        val updated = updateFavoriteGroup(favorites, saved, FavoriteGroup.Home)

        assertEquals(listOf("work", "saved"), updated.map { it.place.id })
        assertEquals(FavoriteGroup.Home, updated.last().group)
    }

    @Test
    fun savedGroupKeepsMultiplePlaces() {
        val updated = updateFavoriteGroup(
            favorites = listOf(FavoritePlace(saved, FavoriteGroup.Saved)),
            place = home,
            group = FavoriteGroup.Saved,
        )

        assertEquals(setOf("saved", "home"), updated.map { it.place.id }.toSet())
        assertTrue(updated.all { it.group == FavoriteGroup.Saved })
    }

    private fun place(id: String) = Place(
        id = id,
        name = id,
        address = "",
        district = "",
        category = "",
        phone = "",
        latitude = 30.0,
        longitude = 120.0,
        distanceMeters = null,
    )
}
