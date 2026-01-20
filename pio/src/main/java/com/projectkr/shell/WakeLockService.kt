package com.projectkr.shell

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.os.Process
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import android.app.PendingIntent

class WakeLockService : androidx.core.app.JobIntentService() {
    private var wakeLock: PowerManager.WakeLock? = null
    private val WAKE_LOCK_TAG = "${applicationContext.packageName}.WAKE_LOCK" // Thay com.projectkr.shell bằng packageName
    private var isWakeLockActive = false  // Biến kiểm tra trạng thái WakeLock

    override fun onHandleWork(intent: Intent) {
        if (intent.action == ACTION_TOGGLE_WAKELOCK) {
            val powerManager = applicationContext.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (wakeLock == null) {
                // Sử dụng PARTIAL_WAKE_LOCK để giữ CPU hoạt động mà không làm sáng màn hình
                wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, WAKE_LOCK_TAG)
            }

            if (isWakeLockActive) {
                // Nếu WakeLock đang hoạt động, giải phóng nó
                wakeLock?.release()
                isWakeLockActive = false
            } else {
                // Nếu WakeLock không hoạt động, giữ nó chạy liên tục
                wakeLock?.acquire()
                isWakeLockActive = true
            }
        } else if (intent.action == ACTION_STOP_SERVICE) {
            // Khi nhấn nút Stop, giải phóng WakeLock và dừng dịch vụ
            wakeLock?.release()
            stopSelf()

            // Đóng ứng dụng hoàn toàn (bao gồm cả dịch vụ nền)
            Process.killProcess(Process.myPid())
        }
    }

    override fun onCreate() {
        super.onCreate()

        // Kiểm tra trạng thái thông báo từ SharedPreferences
        val themeConfig = ThemeConfig(this)
        val allowNotificationUI = themeConfig.getAllowNotificationUI()  // Kiểm tra trạng thái bật/tắt thông báo

        if (allowNotificationUI) {
            // Kiểm tra API Level để cấu hình NotificationChannel cho API 26 trở lên
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    getString(R.string.wakelock_service_running),  // Lấy tên ứng dụng từ strings.xml
                    NotificationManager.IMPORTANCE_DEFAULT
                ).apply {
                    setSound(null, null)
                    enableLights(false)
                    enableVibration(false)
                    setLockscreenVisibility(Notification.VISIBILITY_SECRET)
                }

                val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                notificationManager.createNotificationChannel(channel)
            }

            // Thực hiện dịch vụ dưới dạng foreground
            val notification = NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle(getString(R.string.app_name))  // Lấy tên ứng dụng từ strings.xml
                .setContentText(getString(R.string.service_active_with_wakelock))  // Sử dụng chuỗi từ strings.xml
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_DEFAULT)
                .addAction(R.drawable.ic_stop, getString(R.string.stop), stopServicePendingIntent())
                .addAction(R.drawable.ic_toggle, getString(R.string.toggle_wakelock), toggleWakeLockPendingIntent())
                .build()

            startForeground(1, notification)
        }
    }

    private fun stopServicePendingIntent(): PendingIntent {
        val intent = Intent(this, WakeLockService::class.java)
        intent.action = ACTION_STOP_SERVICE
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun toggleWakeLockPendingIntent(): PendingIntent {
        val intent = Intent(this, WakeLockService::class.java)
        intent.action = ACTION_TOGGLE_WAKELOCK
        return PendingIntent.getService(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT)
    }

    override fun onDestroy() {
        super.onDestroy()
        wakeLock?.release()
    }

    companion object {
        private const val CHANNEL_ID = "WakeLockServiceChannel"
        private const val ACTION_TOGGLE_WAKELOCK = "${applicationContext.packageName}.action.TOGGLE_WAKELOCK"  // Thay com.projectkr.shell bằng packageName
        private const val ACTION_STOP_SERVICE = "${applicationContext.packageName}.action.STOP_SERVICE"  // Thay com.projectkr.shell bằng packageName

        fun startService(context: Context) {
            val intent = Intent(context, WakeLockService::class.java)
            ContextCompat.startForegroundService(context, intent)
        }

        fun stopService(context: Context) {
            val intent = Intent(context, WakeLockService::class.java)
            context.stopService(intent)
        }
    }
}