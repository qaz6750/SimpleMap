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

    override fun plan(request: RouteRequest): Result<List<RoutePlan>> = runCatching {
        val routeSearch = RouteSearch(applicationContext)
        val fromAndTo = RouteSearch.FromAndTo(request.origin.toPoint(), request.destination.toPoint()).apply {
            startPoiID = request.origin.id
            destinationPoiID = request.destination.id
        }
        when (request.mode) {
            RouteMode.Drive -> routeSearch.calculateDriveRoute(
                RouteSearch.DriveRouteQuery(
                    fromAndTo,
                    request.driveOptions.toAmapStrategy(),
                    request.waypoints.map { it.toPoint() }.ifEmpty { null },
                    null,
                    "",
                ).apply { extensions = RouteSearch.EXTENSIONS_ALL },
            ).paths.orEmpty().mapIndexed { index, path -> path.toPlan(index) }

            RouteMode.Transit -> routeSearch.calculateBusRoute(
                RouteSearch.BusRouteQuery(
                    fromAndTo,
                    RouteSearch.BUS_DEFAULT,
                    request.city,
                    0,
                ).apply {
                    cityd = request.city
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
        trafficSegments = steps.orEmpty()
            .flatMap { it.tmCs.orEmpty() }
            .mapNotNull { traffic ->
                traffic.polyline.toRoutePoints()
                    .takeIf { it.size >= 2 }
                    ?.let { RouteTrafficSegment(RouteTrafficStatus.fromAmap(traffic.status.orEmpty()), it) }
            },
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

    private fun DriveRouteOptions.toAmapStrategy() = when {
        prioritizeHighway && avoidCongestion -> RouteSearch.DRIVING_MULTI_CHOICE_HIGHWAY_AVOID_CONGESTION
        prioritizeHighway -> RouteSearch.DRIVING_MULTI_CHOICE_HIGHWAY
        avoidCongestion && avoidHighway && saveMoney ->
            RouteSearch.DRIVING_MULTI_CHOICE_AVOID_CONGESTION_NO_HIGHWAY_SAVE_MONEY
        avoidCongestion && avoidHighway -> RouteSearch.DRIVING_MULTI_CHOICE_AVOID_CONGESTION_NO_HIGHWAY
        avoidCongestion && saveMoney -> RouteSearch.DRIVING_MULTI_CHOICE_AVOID_CONGESTION_SAVE_MONEY
        avoidHighway && saveMoney -> RouteSearch.DRIVING_MULTI_CHOICE_SAVE_MONEY_NO_HIGHWAY
        avoidCongestion -> RouteSearch.DRIVING_MULTI_CHOICE_AVOID_CONGESTION
        avoidHighway -> RouteSearch.DRIVING_MULTI_CHOICE_NO_HIGHWAY
        saveMoney -> RouteSearch.DRIVING_MULTI_CHOICE_SAVE_MONEY
        else -> RouteSearch.DRIVING_MULTI_STRATEGY_FASTEST_SHORTEST_AVOID_CONGESTION
    }

    private fun List<LatLonPoint>?.toRoutePoints() = orEmpty().map {
        RoutePoint(latitude = it.latitude, longitude = it.longitude)
    }
}