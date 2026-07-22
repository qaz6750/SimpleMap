package com.simplemap.search

import org.junit.Assert.assertEquals
import org.junit.Test

class FuzzyAddressQueryTest {
    @Test
    fun longAddressAlsoSearchesWithoutAdministrativePrefix() {
        assertEquals(
            listOf("浙江省杭州市上城区平海路 1 号", "浙江省杭州市上城区平海路1号", "平海路1号"),
            fuzzyAddressQueries(" 浙江省杭州市上城区平海路 1 号 "),
        )
    }

    @Test
    fun placeNameUsesSingleQuery() {
        assertEquals(listOf("未来科技城"), fuzzyAddressQueries("未来科技城"))
    }

    @Test
    fun fuzzyPlacesPreferNameMatchAndExcludeUnrelatedResults() {
        val ranked = rankFuzzyPlaces(
            "西湖景区",
            listOf(
                place("address", "湖滨公园", "西湖景区入口"),
                place("ordered", "西子湖畔景区", "北山街"),
                place("name", "西湖景区", "龙井路"),
                place("unrelated", "杭州东站", "天城路"),
            ),
        )

        assertEquals(listOf("name", "address", "ordered"), ranked.map(Place::id))
    }

    @Test
    fun identifiesBusStationPois() {
        assertEquals(true, isBusStationPlace(place("bus", "武林广场公交站", "", "交通设施服务;公交车站")))
        assertEquals(false, isBusStationPlace(place("mall", "武林广场", "体育场路", "商务住宅")))
    }

    private fun place(
        id: String,
        name: String,
        address: String,
        category: String = "地点",
    ) = Place(
        id = id,
        name = name,
        address = address,
        district = "杭州市",
        category = category,
        phone = "",
        latitude = 30.0,
        longitude = 120.0,
        distanceMeters = null,
    )
}