package com.projectkr.shell

import android.app.Application
import android.content.Context
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat

class PIO : Application() {

    companion object {
        private const val PREFS_NAME = "app_settings"
        private const val KEY_LANGUAGE = "language"

        // Mặc định là English
        var currentLanguage: String = "en"
            private set

        // Gọi sớm để áp dụng ngôn ngữ đã lưu (hoặc mặc định English)
        fun applySavedLanguage(context: Context) {
            val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val savedLang = prefs.getString(KEY_LANGUAGE, "en") ?: "en"
            setLanguage(context, savedLang)
        }

        // Hàm thay đổi ngôn ngữ (ví dụ: "en" hoặc "vi")
        fun setLanguage(context: Context, languageCode: String) {
            currentLanguage = languageCode

            // Lưu vào SharedPreferences
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_LANGUAGE, languageCode)
                .apply()

            // Áp dụng locale mới (cách tốt nhất 2025+)
            val appLocale = LocaleListCompat.forLanguageTags(languageCode)
            AppCompatDelegate.setApplicationLocales(appLocale)
        }
    }

    override fun onCreate() {
        super.onCreate()
        applySavedLanguage(this)
    }
}