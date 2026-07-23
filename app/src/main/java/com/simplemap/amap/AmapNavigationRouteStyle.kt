package com.simplemap.amap

import android.graphics.Color
import com.amap.api.navi.model.RouteOverlayOptions

internal fun amapNavigationRouteOverlayOptions() = RouteOverlayOptions().apply {
    arrowColor = Color.WHITE
    arrowSideColor = Color.argb(230, 20, 28, 40)
    lineWidth = 28f
    setTurnArrowIs3D(true)
}