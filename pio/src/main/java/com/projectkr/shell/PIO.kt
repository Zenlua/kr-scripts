package com.projectkr.shell

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate

class PIO : Application() {
    override fun onCreate() {
        super.onCreate()

        // Ép ứng dụng luôn dùng DARK MODE
        AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES)

        // Các lựa chọn khác nếu cần:
        // AppCompatDelegate.MODE_NIGHT_NO          // Luôn light mode
        // AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM // Theo hệ thống (mặc định)
        // AppCompatDelegate.MODE_NIGHT_AUTO_BATTERY // Tự động theo tiết kiệm pin (cũ)
    }
}
