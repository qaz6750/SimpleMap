package com.simplemap.navigation

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.simplemap.BuildConfig
import com.simplemap.MainActivity
import com.simplemap.R
import com.simplemap.amap.AndroidAmapRuntime
import com.simplemap.privacy.SharedPreferencesPrivacyConsentStore
import com.simplemap.route.DriveRouteOptions
import com.simplemap.route.RouteMode
import com.simplemap.route.RoutePlan
import com.simplemap.route.RoutePoint
import com.simplemap.route.RouteRequest
import com.simplemap.search.Place
import com.simplemap.settings.AppOrientationMode
import com.simplemap.settings.NavigationPerspectiveMode
import com.simplemap.settings.NavigationSettings
import com.simplemap.settings.NavigationThemeMode
import com.simplemap.settings.VoiceGuidanceLevel
import com.simplemap.startup.MapAccessController
import com.simplemap.startup.MapAccessState

class NavigationSessionService : Service() {
    override fun onCreate() {
        super.onCreate()
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "实时导航",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "在后台持续当前导航会话"
                setShowBadge(false)
            },
        )
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            if (NavigationSessionCoordinator.session.value == null) {
                stopSelf(startId)
            } else {
                NavigationSessionCoordinator.finish(this)
            }
            return START_NOT_STICKY
        }
        val restoredSpec = intent?.getBundleExtra(EXTRA_SESSION)?.toNavigationSessionSpec()
        if (restoredSpec == null &&
            NavigationSessionCoordinator.session.value == null &&
            !NavigationSessionCoordinator.hasPendingSession()
        ) {
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED
        ) {
            NavigationSessionCoordinator.reportActivationFailure("精确定位权限已关闭，无法恢复实时导航")
            stopSelf(startId)
            return START_NOT_STICKY
        }
        val destination = restoredSpec?.routeRequest?.destination?.name.orEmpty().ifBlank { "目的地" }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_navigation)
            .setContentTitle("正在导航至 $destination")
            .setContentText("返回 SimpleMap 查看实时路线")
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
            .addAction(
                0,
                "结束导航",
                PendingIntent.getService(
                    this,
                    1,
                    Intent(this, NavigationSessionService::class.java).setAction(ACTION_STOP),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        try {
            startForeground(NOTIFICATION_ID, notification)
        } catch (error: SecurityException) {
            NavigationSessionCoordinator.reportActivationFailure(
                error.localizedMessage ?: "系统不允许启动实时导航服务",
            )
            stopSelf(startId)
            return START_NOT_STICKY
        }
        if (NavigationSessionCoordinator.session.value == null) {
            val accessFailure = prepareMapAccessForNavigation()
            if (accessFailure != null) {
                NavigationSessionCoordinator.reportActivationFailure(accessFailure)
                stopSelf(startId)
                return START_NOT_STICKY
            }
            runCatching {
                if (!NavigationSessionCoordinator.hasPendingSession()) {
                    val spec = checkNotNull(restoredSpec) { "导航会话无法恢复" }
                    NavigationSessionCoordinator.prepare(spec)
                }
                NavigationSessionCoordinator.activate(this)
            }
                .onFailure {
                    NavigationSessionCoordinator.reportActivationFailure(
                        it.localizedMessage ?: "导航引擎初始化失败",
                    )
                    stopSelf()
                }
        }
        return START_REDELIVER_INTENT
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        NavigationSessionCoordinator.onServiceDestroyed(this)
        super.onDestroy()
    }

    private fun prepareMapAccessForNavigation(): String? {
        val accessState = MapAccessController(
            consentStore = SharedPreferencesPrivacyConsentStore(applicationContext),
            apiKeyPresent = BuildConfig.AMAP_API_KEY_PRESENT,
            runtime = AndroidAmapRuntime(applicationContext),
        ).load()
        return when (accessState) {
            MapAccessState.Ready -> null
            MapAccessState.ConsentRequired -> "隐私同意已失效，无法恢复导航"
            MapAccessState.MissingApiKey -> "高德地图 API Key 未配置"
            MapAccessState.Loading -> "地图服务尚未就绪"
            is MapAccessState.Failed -> accessState.message
        }
    }

    companion object {
        private const val CHANNEL_ID = "navigation_session"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_SESSION = "session"
        private const val ACTION_STOP = "com.simplemap.navigation.STOP"

        fun start(context: Context, spec: NavigationSessionSpec): Boolean {
            val intent = Intent(context, NavigationSessionService::class.java)
                .putExtra(EXTRA_SESSION, spec.toBundle())
            return runCatching { context.startForegroundService(intent) }.isSuccess
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NavigationSessionService::class.java))
        }
    }
}

private fun NavigationSessionSpec.toBundle() = Bundle().apply {
    putBundle("origin", routeRequest.origin.toBundle())
    putBundle("destination", routeRequest.destination.toBundle())
    putParcelableArrayList("waypoints", ArrayList(routeRequest.waypoints.map(Place::toBundle)))
    putString("mode", routeRequest.mode.name)
    putBundle("driveOptions", routeRequest.driveOptions.toBundle())
    putString("city", routeRequest.city)
    putString("originCity", routeRequest.originCity)
    putString("destinationCity", routeRequest.destinationCity)
    putString("planId", plan.id)
    putLong("planDuration", plan.durationSeconds)
    putInt("planDistance", plan.distanceMeters)
    putString("planSummary", plan.summary)
    putParcelableArrayList(
        "planPolyline",
        ArrayList(plan.polyline.evenlySampled(16).map(RoutePoint::toBundle)),
    )
    putBundle("settings", settings.toBundle())
}

private fun Bundle.toNavigationSessionSpec(): NavigationSessionSpec? = runCatching {
    val origin = requireNotNull(getBundle("origin")?.toPlace())
    val destination = requireNotNull(getBundle("destination")?.toPlace())
    val mode = enumValue<RouteMode>("mode", RouteMode.Drive)
    val driveOptions = getBundle("driveOptions")?.toDriveRouteOptions() ?: DriveRouteOptions()
    val request = RouteRequest(
        origin = origin,
        destination = destination,
        waypoints = getParcelableArrayList<Bundle>("waypoints").orEmpty().mapNotNull(Bundle::toPlace),
        mode = mode,
        driveOptions = driveOptions,
        city = getString("city").orEmpty(),
        originCity = getString("originCity").orEmpty(),
        destinationCity = getString("destinationCity").orEmpty(),
    )
    NavigationSessionSpec(
        routeRequest = request,
        plan = RoutePlan(
            id = getString("planId").orEmpty().ifBlank { "restored" },
            mode = mode,
            durationSeconds = getLong("planDuration").coerceAtLeast(0L),
            distanceMeters = getInt("planDistance").coerceAtLeast(0),
            costYuan = null,
            summary = getString("planSummary").orEmpty(),
            steps = emptyList(),
            polyline = getParcelableArrayList<Bundle>("planPolyline")
                .orEmpty()
                .mapNotNull(Bundle::toRoutePoint),
        ),
        settings = getBundle("settings")?.toNavigationSettings() ?: NavigationSettings(),
    )
}.getOrNull()

private fun RoutePoint.toBundle() = Bundle().apply {
    putDouble("latitude", latitude)
    putDouble("longitude", longitude)
}

private fun Bundle.toRoutePoint(): RoutePoint? {
    if (!containsKey("latitude") || !containsKey("longitude")) return null
    return RoutePoint(
        latitude = getDouble("latitude"),
        longitude = getDouble("longitude"),
    )
}

private fun Place.toBundle() = Bundle().apply {
    putString("id", id)
    putString("name", name)
    putString("address", address)
    putString("district", district)
    putString("category", category)
    putString("phone", phone)
    putDouble("latitude", latitude)
    putDouble("longitude", longitude)
}

private fun Bundle.toPlace(): Place? {
    val id = getString("id") ?: return null
    return Place(
        id = id,
        name = getString("name").orEmpty(),
        address = getString("address").orEmpty(),
        district = getString("district").orEmpty(),
        category = getString("category").orEmpty(),
        phone = getString("phone").orEmpty(),
        latitude = getDouble("latitude"),
        longitude = getDouble("longitude"),
        distanceMeters = null,
    )
}

private fun DriveRouteOptions.toBundle() = Bundle().apply {
    putBoolean("avoidCongestion", avoidCongestion)
    putBoolean("avoidHighway", avoidHighway)
    putBoolean("saveMoney", saveMoney)
    putBoolean("prioritizeHighway", prioritizeHighway)
}

private fun Bundle.toDriveRouteOptions() = DriveRouteOptions(
    avoidCongestion = getBoolean("avoidCongestion"),
    avoidHighway = getBoolean("avoidHighway"),
    saveMoney = getBoolean("saveMoney"),
    prioritizeHighway = getBoolean("prioritizeHighway"),
)

private fun NavigationSettings.toBundle() = Bundle().apply {
    putBoolean("voiceGuidance", voiceGuidance)
    putString("voiceGuidanceLevel", voiceGuidanceLevel.name)
    putBoolean("quietHoursEnabled", quietHoursEnabled)
    putInt("quietHoursStartMinutes", quietHoursStartMinutes)
    putInt("quietHoursEndMinutes", quietHoursEndMinutes)
    putBoolean("importantAlertsEnabled", importantAlertsEnabled)
    putBoolean("trafficLayer", trafficLayer)
    putBoolean("routeAlerts", routeAlerts)
    putBoolean("trafficBar", trafficBar)
    putBoolean("eagleMap", eagleMap)
    putBoolean("autoZoom", autoZoom)
    putString("perspectiveMode", perspectiveMode.name)
    putBoolean("nightMode", nightMode)
    putString("themeMode", themeMode.name)
    putString("orientationMode", orientationMode.name)
    putBoolean("wifiOnlyOfflineDownloads", wifiOnlyOfflineDownloads)
    putBundle("driveRouteOptions", driveRouteOptions.toBundle())
}

private fun Bundle.toNavigationSettings() = NavigationSettings(
    voiceGuidance = getBoolean("voiceGuidance", true),
    voiceGuidanceLevel = enumValue("voiceGuidanceLevel", VoiceGuidanceLevel.Detailed),
    quietHoursEnabled = getBoolean("quietHoursEnabled"),
    quietHoursStartMinutes = getInt("quietHoursStartMinutes", 22 * 60),
    quietHoursEndMinutes = getInt("quietHoursEndMinutes", 7 * 60),
    importantAlertsEnabled = getBoolean("importantAlertsEnabled", true),
    trafficLayer = getBoolean("trafficLayer", true),
    routeAlerts = getBoolean("routeAlerts", true),
    trafficBar = getBoolean("trafficBar", true),
    eagleMap = getBoolean("eagleMap"),
    autoZoom = getBoolean("autoZoom", true),
    perspectiveMode = enumValue("perspectiveMode", NavigationPerspectiveMode.ThreeDimensional),
    nightMode = getBoolean("nightMode"),
    themeMode = enumValue("themeMode", NavigationThemeMode.FollowSystem),
    orientationMode = enumValue("orientationMode", AppOrientationMode.FollowSystem),
    wifiOnlyOfflineDownloads = getBoolean("wifiOnlyOfflineDownloads", true),
    driveRouteOptions = getBundle("driveRouteOptions")?.toDriveRouteOptions() ?: DriveRouteOptions(),
)

private inline fun <reified T : Enum<T>> Bundle.enumValue(key: String, default: T): T =
    getString(key)?.let { stored -> enumValues<T>().firstOrNull { it.name == stored } } ?: default
