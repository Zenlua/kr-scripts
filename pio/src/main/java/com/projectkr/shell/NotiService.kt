package com.projectkr.shell

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.os.Build
import android.os.IBinder

class NotiService : Service() {
    private val CHANNEL_ID = "notification_id"

    // Xóa thông báo
    private fun deleteNotification(id: Int) {
        val notificationManager = getSystemService(NotificationManager::class.java)
        notificationManager.cancel(id)
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val message = intent?.getStringExtra("message")
        val title = intent?.getStringExtra("title") ?: getString(R.string.app_name) // Sử dụng tên app nếu không có title
        val id = intent?.getIntExtra("id", 1) ?: 1

        // Kiểm tra nếu muốn xóa thông báo
        if ("true" == intent?.getStringExtra("delete")) {
            deleteNotification(id)
            return START_NOT_STICKY
        } else if (message != null) {
            showNotification(id, message, title)
        }

        return START_NOT_STICKY
    }

    // Hiển thị thông báo
    private fun showNotification(id: Int, message: String, title: String) {
        val notificationManager = getSystemService(NotificationManager::class.java)

        // Tạo Notification Channel nếu cần
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val notificationChannel = NotificationChannel(
                CHANNEL_ID,
                "Notification",
                NotificationManager.IMPORTANCE_DEFAULT
            ).apply {
                // Thiết lập thông tin cho Notification Channel
                sound = null
                enableLights(false)
                enableVibration(false)
                importance = NotificationManager.IMPORTANCE_DEFAULT
            }

            notificationManager?.createNotificationChannel(notificationChannel)
        }

        // Tạo Notification Builder
        val builder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
        } else {
            Notification.Builder(this)
        }

        builder.apply {
            setContentTitle(title)  // Tiêu đề là tên app mặc định
            setContentText(message)
            setStyle(Notification.BigTextStyle().bigText(message))
            setSmallIcon(android.R.drawable.ic_notification_clear_all)
            setAutoCancel(true)
        }

        notificationManager?.notify(id, builder.build())
    }
}