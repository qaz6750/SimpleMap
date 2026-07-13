package com.simplemap.route

import android.content.Context
import com.amap.api.services.core.LatLonPoint
import com.amap.api.services.route.BusPath
import com.amap.api.services.route.DrivePath
import com.amap.api.services.route.RidePath
import com.amap.api.services.route.RouteSearch
import com.amap.api.services.route.WalkPath
import com.simplemap.search.Place

class AmapRoutePlanRepository(context: Context) : RoutePlanRepository {
    private val applicationContext = context.applicationContext

    override fun plan(
        origin: Place,
        destination: Place,
        mode: RouteMode,
        city: String,
    ): Result<List<RoutePlan>> = runCatching {
        val routeSearch = RouteSearch(applicationContext)
        val fromAndTo = RouteSearch.FromAndTo(origin.toPoint(), destination.toPoint()).apply {
            startPoiID = origin.id
            destinationPoiID = destination.id
        }
        when (mode) {
            RouteMode.Drive -> routeSearch.calculateDriveRoute(
                RouteSearch.DriveRouteQuery(
                    fromAndTo,
                    RouteSearch.DRIVING_MULTI_STRATEGY_FASTEST_SHORTEST_AVOID_CONGESTION,
                    null,
                    null,
                    "",
                ).apply { extensions = RouteSearch.EXTENSIONS_ALL },
            ).paths.orEmpty().mapIndexed { index, path -> path.toPlan(index) }

            RouteMode.Transit -> routeSearch.calculateBusRoute(
                RouteSearch.BusRouteQuery(
                    fromAndTo,
                    RouteSearch.BUS_DEFAULT,
                    city,
                    0,
                ).apply {
                    cityd = city
                    extensions = RouteSearch.EXTENSIONS_ALL
                },
            ).paths.orEmpty().mapIndexed { index, path -> path.toPlan(index) }

            RouteMode.Ride -> routeSearch.calculateRideRoute(
                RouteSearch.RideRouteQuery(fromAndTo, RouteSearch.RIDING_RECOMMEND).apply {
                    extensions = RouteSearch.EXTENSIONS_ALL
                },
            ).paths.orEmpty().mapIndexed { index, path -> path.toPlan(index) }

            RouteMode.Walk -> routeSearch.calculateWalkRoute(
                RouteSearch.WalkRouteQuery(fromAndTo, RouteSearch.WALK_MULTI_PATH).apply {
                    extensions = RouteSearch.EXTENSIONS_ALL
                },
            ).paths.orEmpty().mapIndexed { index, path -> path.toPlan(index) }
        }
    }

    private fun DrivePath.toPlan(index: Int) = RoutePlan(
        id = "drive-$index",
        mode = RouteMode.Drive,
        durationSeconds = duration,
        distanceMeters = distance.toInt(),
        costYuan = tolls.takeIf { it > 0f },
        summary = listOfNotNull(
            strategy.takeIf { it.isNotBlank() },
            totalTrafficlights.takeIf { it > 0 }?.let { "$it 个红绿灯" },
        ).joinToString(" · ").ifBlank { "推荐驾车路线" },
        steps = steps.orEmpty().mapNotNull { it.instruction?.takeIf(String::isNotBlank) },
        polyline = polyline.toRoutePoints(),
    )

    private fun BusPath.toPlan(index: Int): RoutePlan {
        val lineNames = steps.orEmpty()
            .flatMap { it.busLines.orEmpty() }
            .mapNotNull { it.busLineName?.substringBefore('(')?.takeIf(String::isNotBlank) }
            .distinct()
        val instructions = buildList {
            steps.orEmpty().forEach { step ->
                step.walk?.steps.orEmpty().mapNotNullTo(this) {
                    it.instruction?.takeIf(String::isNotBlank)
                }
                step.busLines.orEmpty().forEach { line ->
                    val name = line.busLineName?.substringBefore('(').orEmpty()
                    val departure = line.departureBusStation?.busStationName.orEmpty()
                    val arrival = line.arrivalBusStation?.busStationName.orEmpty()
                    if (name.isNotBlank()) add("乘坐 $name：$departure 至 $arrival")
                }
            }
        }
        return RoutePlan(
            id = "transit-$index",
            mode = RouteMode.Transit,
            durationSeconds = duration,
            distanceMeters = distance.toInt(),
            costYuan = cost.takeIf { it > 0f },
            summary = lineNames.joinToString(" → ").ifBlank { "公共交通方案" },
            steps = instructions,
            polyline = polyline.toRoutePoints(),
        )
    }

    private fun RidePath.toPlan(index: Int) = RoutePlan(
        id = "ride-$index",
        mode = RouteMode.Ride,
        durationSeconds = duration,
        distanceMeters = distance.toInt(),
        costYuan = null,
        summary = "推荐骑行路线",
        steps = steps.orEmpty().mapNotNull { it.instruction?.takeIf(String::isNotBlank) },
        polyline = polyline.toRoutePoints(),
    )

    private fun WalkPath.toPlan(index: Int) = RoutePlan(
        id = "walk-$index",
        mode = RouteMode.Walk,
        durationSeconds = duration,
        distanceMeters = distance.toInt(),
        costYuan = null,
        summary = "推荐步行路线",
        steps = steps.orEmpty().mapNotNull { it.instruction?.takeIf(String::isNotBlank) },
        polyline = polyline.toRoutePoints(),
    )

    private fun Place.toPoint() = LatLonPoint(latitude, longitude)

    private fun List<LatLonPoint>?.toRoutePoints() = orEmpty().map {
        RoutePoint(latitude = it.latitude, longitude = it.longitude)
    }
}