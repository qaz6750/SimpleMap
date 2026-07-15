package com.simplemap.navigation

internal fun trafficChangeMessage(
    previous: NavigationTrafficAlert?,
    current: NavigationTrafficAlert?,
): String? = when {
    current != null && current.level.severity >= NavigationTrafficLevel.Congested.severity &&
        (previous == null || current.level.severity > previous.level.severity) -> {
        val label = if (current.level == NavigationTrafficLevel.SeverelyCongested) "严重拥堵" else "拥堵"
        "前方路况变为$label"
    }
    previous != null && previous.level.severity >= NavigationTrafficLevel.Congested.severity &&
        (current == null || current.level.severity < NavigationTrafficLevel.Congested.severity) -> "前方拥堵已缓解"
    else -> null
}

private val NavigationTrafficLevel.severity: Int
    get() = when (this) {
        NavigationTrafficLevel.SeverelyCongested -> 3
        NavigationTrafficLevel.Congested -> 2
        NavigationTrafficLevel.Slow -> 1
        NavigationTrafficLevel.Smooth, NavigationTrafficLevel.Unknown -> 0
    }