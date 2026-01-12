package com.projectkr.shell

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class PIO : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(applyLang(base))
    }

    private fun applyLang(base: Context): Context {
        val dir = File(base.filesDir, "kr-script")
        val langFile = File(dir, "language")
        val logFile = File(dir, "language.log")

        fun log(msg: String) {
            val time = SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                .format(Date())
            logFile.appendText("[$time] $msg\n")
            Log.d("PIO-LANG", msg)
        }

        if (!langFile.exists()) {
            log("language file not found")
            return base
        }

        val tag = langFile.readText().trim()
        log("language content='$tag'")

        if (tag.isEmpty()) {
            log("language file empty")
            return base
        }

        val parts = tag.replace('_', '-').split('-')
        val locale = if (parts.size >= 2)
            Locale(parts[0], parts[1])
        else
            Locale(parts[0])

        Locale.setDefault(locale)
        log("set Locale = $locale")

        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)
        log("apply Configuration.setLocale")

        return base.createConfigurationContext(config)
            .also { log("createConfigurationContext DONE") }
    }
}