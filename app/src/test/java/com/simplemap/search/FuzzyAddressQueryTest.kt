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
}