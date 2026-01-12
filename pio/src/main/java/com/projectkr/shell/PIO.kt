package com.projectkr.shell

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.io.File
import java.util.Locale

class PIO : Application() {

    override fun attachBaseContext(base: Context) {
        // API 21–32: apply locale trước khi tạo Resources
        val ctx = if (Build.VERSION.SDK_INT < 33) {
            applyLanguageLegacy(base)
        } else {
            base
        }
        super.attachBaseContext(ctx)
    }

    override fun onCreate() {
        super.onCreate()

        // API 33–36: dùng AppCompat locale
        if (Build.VERSION.SDK_INT >= 33) {
            applyLanguageModern()
        }
    }

    /**
     * Android 5 → 12L (API 21–32)
     */
    private fun applyLanguageLegacy(base: Context): Context {
        return runCatching {
            val tag = readLanguageTag(base) ?: return base

            val locale = Locale.forLanguageTag(tag)
            Locale.setDefault(locale)

            val config = Configuration(base.resources.configuration)
            config.setLocale(locale)

            base.createConfigurationContext(config)
        }.getOrElse { base }
    }

    /**
     * Android 13+ (API 33–36)
     */
    private fun applyLanguageModern() {
        runCatching {
            val tag = readLanguageTag(this) ?: return
            AppCompatDelegate.setApplicationLocales(
                LocaleListCompat.forLanguageTags(tag)
            )
        }
    }

    /**
     * Read language from: /data/data/xxx/files/kr-script/language
     * Example: vi | vi-VN | zh-CN | en
     */
    private fun readLanguageTag(ctx: Context): String? {
        val file = File(ctx.filesDir, "kr-script/language")
        if (!file.exists()) return null

        val tag = file.readText().trim()
        return tag.ifEmpty { null }
    }
}