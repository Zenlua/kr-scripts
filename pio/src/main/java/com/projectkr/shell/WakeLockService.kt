package com.projectkr.shell

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class WakeLockService : Service() {

    private var wakeLock: PowerManager.WakeLock? = null
    private val WAKE_LOCK_TAG = "${BuildConfig.APPLICATION_ID}.WAKE_LOCK"
    private var isWakeLockActive = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action

        if (action == ACTION_TOGGLE_WAKELOCK) {
            toggleWakeLock()
        } else if (action == ACTION_STOP_SERVICE) {
            stopWakeLockAndService()
        }

        return START_STICKY  // Giữ service sống lại nếu bị kill (trừ khi swipe recents)
    }

    private fun toggleWakeLock() {
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager

        if (wakeLock == null) {
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                WAKE_LOCK_TAG
            )
        }

        if (isWakeLockActive) {
            wakeLock?.release()
            isWakeLockActive = false
        } else {
            wakeLock?.acquire()
            isWakeLockActive = true
        }
    }

    private fun stopWakeLockAndService() {
        wakeLock?.release()
        wakeLock = null
        isWakeLockActive = false
        stopForeground(true)
        stopSelf()
    }

    override fun onCreate() {
        super.onCreate()

        val themeConfig = ThemeConfig(this)
        val allowNotificationUI = themeConfig.getAllowNotificationUI()

        if (allowNotificationUI) {
            createNotificationChannel()

            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getString(R.string.service_active_with_wakelock))
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .addAction(R.mipmap.ic_launcher, getString(R.string.stop), stopServicePendingIntent())
                .addAction(R.mipmap.ic_launcher, getString(R.string.toggle_wakelock), toggleWakeLockPendingIntent())
                .build()

            startForeground(1, notification)
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                getString(R.string.wakelock_service_running),
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                setSound(null, null)
                enableLights(false)
                enableVibration(false)
                setLockscreenVisibility(Notification.VISIBILITY_SECRET)
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun stopServicePendingIntent(): PendingIntent {
        val intent = Intent(this, WakeLockService::class.java).apply { action = ACTION_STOP_SERVICE }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT

        return PendingIntent.getService(this, 0, intent, flags)
    }

    private fun toggleWakeLockPendingIntent(): PendingIntent {
        val intent = Intent(this, WakeLockService::class.java).apply { action = ACTION_TOGGLE_WAKELOCK }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S)
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_UPDATE_CURRENT

        return PendingIntent.getService(this, 1, intent, flags)
    }

    override fun onDestroy() {
        wakeLock?.release()
        wakeLock = null
        super.onDestroy()
    }

    // <-- THÊM PHẦN NÀY: Xử lý khi swipe khỏi recents -->
    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)

        wakeLock?.release()
        wakeLock = null
        isWakeLockActive = false

        stopForeground(true)  // Remove notification
        stopSelf()            // Dừng service
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val CHANNEL_ID = "WakeLockServiceChannel"
        private const val ACTION_TOGGLE_WAKELOCK = "${BuildConfig.APPLICATION_ID}.action.TOGGLE_WAKELOCK"
        private const val ACTION_STOP_SERVICE = "${BuildConfig.APPLICATION_ID}.action.STOP_SERVICE"

        fun startService(context: Context) {
            val intent = Intent(context, WakeLockService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            context.stopService(Intent(context, WakeLockService::class.java))
        }
    }
}