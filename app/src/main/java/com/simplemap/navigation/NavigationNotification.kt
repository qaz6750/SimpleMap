package com.simplemap.navigation

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.graphics.drawable.Icon
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.core.app.NotificationCompat
import com.simplemap.MainActivity
import com.simplemap.R
import com.simplemap.ui.formatNavigationArrivalTime
import com.simplemap.ui.formatNavigationDistance
import com.simplemap.ui.formatNavigationTime

internal object NavigationNotification {
    const val NOTIFICATION_ID = 1001
    private const val CHANNEL_ID = "navigation_session"
    private const val LIVE_CHANNEL_ID = "navigation_live"
    private const val LIVE_UPDATES_MIN_SDK = 36
    private const val PROMOTED_ONGOING_MIN_SDK = 37
    private const val ROUTE_SEGMENT_COLOR = 0xFF34C759.toInt()

    fun createChannels(context: Context) {
        val notificationManager = context.getSystemService(NotificationManager::class.java)
        notificationManager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "高德地图导航",
                NotificationManager.IMPORTANCE_LOW,
            ).apply {
                description = "高德地图导航期间持续显示路线状态"
                setShowBadge(false)
            },
        )
        if (Build.VERSION.SDK_INT >= LIVE_UPDATES_MIN_SDK) {
            notificationManager.createNotificationChannel(
                NotificationChannel(
                    LIVE_CHANNEL_ID,
                    "导航实时进度",
                    NotificationManager.IMPORTANCE_DEFAULT,
                ).apply {
                    description = "导航期间在状态栏与锁屏实时展示转向指令与路线进度"
                    setShowBadge(false)
                    setSound(null, null)
                    enableVibration(false)
                },
            )
        }
    }

    fun build(
        context: Context,
        destinationName: String,
        state: NavigationUiState?,
        totalDistanceMeters: Int,
    ): Notification {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val stopIntent = PendingIntent.getService(
            context,
            1,
            Intent(context, NavigationSessionService::class.java)
                .setAction(NavigationSessionService.ACTION_STOP),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val title = contentTitle(state)
        val text = contentText(destinationName, state)
        return if (Build.VERSION.SDK_INT >= LIVE_UPDATES_MIN_SDK) {
            buildLiveUpdate(context, title, text, state, totalDistanceMeters, contentIntent, stopIntent)
        } else {
            buildCompat(context, title, text, contentIntent, stopIntent)
        }
    }

    private fun contentTitle(state: NavigationUiState?): String = when {
        state == null -> "高德地图正在导航"
        state.phase == NavigationPhase.Arrived -> "已到达目的地"
        state.phase == NavigationPhase.Navigating && state.instruction.isNotBlank() -> state.instruction
        else -> "高德地图正在导航"
    }

    private fun contentText(destinationName: String, state: NavigationUiState?): String {
        if (state == null || state.phase != NavigationPhase.Navigating) {
            return "正在前往 $destinationName，点击查看实时路线"
        }
        val remainingDistance = formatNavigationDistance(state.remainingDistanceMeters)
        val remainingTime = formatNavigationTime(state.remainingTimeSeconds)
        val arrivalTime = formatNavigationArrivalTime(state.remainingTimeSeconds)
        return "剩余 $remainingDistance · $remainingTime · 预计 $arrivalTime 到达"
    }

    @RequiresApi(LIVE_UPDATES_MIN_SDK)
    private fun buildLiveUpdate(
        context: Context,
        title: String,
        text: String,
        state: NavigationUiState?,
        totalDistanceMeters: Int,
        contentIntent: PendingIntent,
        stopIntent: PendingIntent,
    ): Notification {
        val builder = Notification.Builder(context, LIVE_CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification_navigation)
            .setContentTitle(title)
            .setContentText(text)
            .setContentIntent(contentIntent)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(Notification.CATEGORY_NAVIGATION)
            .addAction(
                Notification.Action.Builder(null as Icon?, "结束导航", stopIntent).build(),
            )
        if (Build.VERSION.SDK_INT >= PROMOTED_ONGOING_MIN_SDK) {
            builder.setRequestPromotedOngoing(true)
        }
        if (state != null && state.phase == NavigationPhase.Navigating) {
            if (state.maneuverDistanceMeters > 0) {
                builder.setShortCriticalText(formatNavigationDistance(state.maneuverDistanceMeters))
            }
            if (totalDistanceMeters > 0) {
                val traveledMeters = (totalDistanceMeters - state.remainingDistanceMeters)
                    .coerceIn(0, totalDistanceMeters)
                val progressStyle = Notification.ProgressStyle()
                    .setProgressSegments(
                        listOf(
                            Notification.ProgressStyle.Segment(totalDistanceMeters)
                                .setColor(ROUTE_SEGMENT_COLOR),
                        ),
                    )
                    .setProgress(traveledMeters)
                state.maneuverIconBitmap?.let { bitmap ->
                    progressStyle.setProgressTrackerIcon(Icon.createWithBitmap(bitmap))
                }
                builder.setStyle(progressStyle)
            }
        }
        return builder.build()
    }

    private fun buildCompat(
        context: Context,
        title: String,
        text: String,
        contentIntent: PendingIntent,
        stopIntent: PendingIntent,
    ): Notification = NotificationCompat.Builder(context, CHANNEL_ID)
        .setSmallIcon(R.drawable.ic_notification_navigation)
        .setContentTitle(title)
        .setContentText(text)
        .setContentIntent(contentIntent)
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setCategory(NotificationCompat.CATEGORY_NAVIGATION)
        .addAction(0, "结束导航", stopIntent)
        .build()
}
