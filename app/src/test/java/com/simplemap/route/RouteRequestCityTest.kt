package com.simplemap.route

import com.simplemap.search.Place
import org.junit.Assert.assertEquals
import org.junit.Test

class RouteRequestCityTest {
    @Test
    fun resolvesSeparateCitiesForCrossCityTransit() {
        val request = RouteRequest(
            origin = place("杭州"),
            destination = place("上海"),
            mode = RouteMode.Transit,
            city = "上海市",
            originCity = "杭州市",
            destinationCity = "上海市",
        )

        assertEquals("杭州市", request.resolvedOriginCity)
        assertEquals("上海市", request.resolvedDestinationCity)
    }

    @Test
    fun fallsBackToLegacyCityForRestoredRequests() {
        val request = RouteRequest(
            origin = place("杭州东站"),
            destination = place("西湖"),
            mode = RouteMode.Transit,
            city = "杭州市",
        )

        assertEquals("杭州市", request.resolvedOriginCity)
        assertEquals("杭州市", request.resolvedDestinationCity)
    }

    private fun place(name: String) = Place(
        id = name,
        name = name,
        address = "",
        district = "",
        category = "",
        phone = "",
        latitude = 0.0,
        longitude = 0.0,
        distanceMeters = null,
    )
}