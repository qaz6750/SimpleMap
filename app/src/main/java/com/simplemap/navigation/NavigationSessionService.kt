package com.simplemap.navigation

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import com.simplemap.MainActivity
import com.simplemap.R

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
            NavigationSessionCoordinator.finish(this)
            return START_NOT_STICKY
        }
        val destination = intent?.getStringExtra(EXTRA_DESTINATION).orEmpty().ifBlank { "目的地" }
        val contentIntent = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).apply {
                setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_launcher)
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
        startForeground(NOTIFICATION_ID, notification)
        if (NavigationSessionCoordinator.session.value == null) {
            runCatching { NavigationSessionCoordinator.activate(this) }
                .onFailure {
                    NavigationSessionCoordinator.reportActivationFailure(
                        it.localizedMessage ?: "导航引擎初始化失败",
                    )
                    stopSelf()
                }
        }
        return START_NOT_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        NavigationSessionCoordinator.onServiceDestroyed(this)
        super.onDestroy()
    }

    companion object {
        private const val CHANNEL_ID = "navigation_session"
        private const val NOTIFICATION_ID = 1001
        private const val EXTRA_DESTINATION = "destination"
        private const val ACTION_STOP = "com.simplemap.navigation.STOP"

        fun start(context: Context, destination: String): Boolean {
            val intent = Intent(context, NavigationSessionService::class.java)
                .putExtra(EXTRA_DESTINATION, destination)
            return runCatching { context.startForegroundService(intent) }.isSuccess
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, NavigationSessionService::class.java))
        }
    }
}
