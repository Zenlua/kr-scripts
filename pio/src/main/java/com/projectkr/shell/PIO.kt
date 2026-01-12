package com.projectkr.shell

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import java.io.File
import java.util.Locale

class PIO : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(applyLanguageFromFile(base))
    }

    private fun applyLanguageFromFile(base: Context): Context = runCatching {
        val file = File(base.filesDir, "kr-script/language")
        if (!file.exists()) return base

        val lang = file.readText().trim()
        if (lang.isEmpty()) return base

        val locale = lang.split("-").let {
            if (it.size == 2) Locale(it[0], it[1]) else Locale(lang)
        }

        Locale.setDefault(locale)

        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)

        base.createConfigurationContext(config)
    }.getOrElse { base }
}