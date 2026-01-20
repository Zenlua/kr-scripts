package com.projectkr.shell

import android.content.Context
import androidx.core.content.edit

class ThemeConfig(private val context: Context) {
    private val config = context.getSharedPreferences("kr-script-config", Context.MODE_PRIVATE)

    fun getAllowTransparentUI(): Boolean {
        return config.getBoolean("TransparentUI", false)
    }

    fun setAllowTransparentUI(allow: Boolean) {
        config.edit { putBoolean("TransparentUI", allow) }
        if (context is Activity) {
            context.recreate()
        }
    }

    fun getAllowNotificationUI(): Boolean {
        return config.getBoolean("NotificationUI", true)
    }

    fun setAllowNotificationUI(allow: Boolean) {
        config.edit { putBoolean("NotificationUI", allow) }
        if (allow) {
            WakeLockService.startService(context)
        } else {
            WakeLockService.stopService(context)
        }
    }
}