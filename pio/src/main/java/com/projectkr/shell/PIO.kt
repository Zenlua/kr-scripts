package com.projectkr.shell

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PIO : Application() {

    override fun attachBaseContext(base: Context) {
        log(base, "attachBaseContext() CALLED")
        val ctx = applyLanguagePre33(base)
        super.attachBaseContext(ctx)
    }

    override fun onCreate() {
        super.onCreate()
        log(this, "onCreate() CALLED sdk=${Build.VERSION.SDK_INT}")
        applyLanguage33Plus()
    }

    // Android 21 → 32
    private fun applyLanguagePre33(base: Context): Context {
        val tag = readLanguageTag(base)
        log(base, "applyLanguagePre33 tag=$tag")

        if (tag == null) {
            log(base, "NO language file → use system locale")
            return base
        }

        val locale = Locale.forLanguageTag(tag)
        Locale.setDefault(locale)

        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)

        log(base, "setLocale (pre33) = ${locale.toLanguageTag()}")
        return base.createConfigurationContext(config)
    }

    // Android 33+
    private fun applyLanguage33Plus() {
        if (Build.VERSION.SDK_INT < 33) {
            log(this, "SDK < 33 → skip AppCompatDelegate")
            return
        }

        val tag = readLanguageTag(this)
        log(this, "applyLanguage33Plus tag=$tag")

        if (tag == null) {
            log(this, "NO language file → keep system locale")
            return
        }

        val locales = LocaleListCompat.forLanguageTags(tag)
        AppCompatDelegate.setApplicationLocales(locales)

        log(this, "AppCompatDelegate.setApplicationLocales($tag)")
    }

    // ==== FILE HELPERS ====

    private fun readLanguageTag(ctx: Context): String? {
        val dir = File(ctx.filesDir, "kr-script")
        val file = File(dir, "language")

        log(ctx, "language file path=${file.absolutePath}")

        if (!file.exists()) {
            log(ctx, "language file NOT FOUND")
            return null
        }

        val content = file.readText().trim()
        log(ctx, "language file content='$content'")

        return content.ifEmpty {
            log(ctx, "language file EMPTY")
            null
        }
    }

    private fun log(ctx: Context, msg: String) {
        try {
            val dir = File(ctx.filesDir, "kr-script")
            if (!dir.exists()) dir.mkdirs()

            val logFile = File(dir, "language.log")
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                .format(Date())

            logFile.appendText("[$time] $msg\n")
        } catch (_: Throwable) {
        }
    }
}