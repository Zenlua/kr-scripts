package com.projectkr.shell

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.util.Log
import java.io.File
import java.util.Locale

class PIO : Application() {

    override fun attachBaseContext(base: Context) {
        super.attachBaseContext(applyLang(base))
    }

    private fun applyLang(base: Context): Context {
        val file = File(base.filesDir, "kr-script/language")
        if (!file.exists()) return base

        val tag = file.readText().trim()
        if (tag.isEmpty()) return base

        val locale = tag.split('-', '_').let {
            if (it.size >= 2) Locale(it[0], it[1]) else Locale(it[0])
        }

        Locale.setDefault(locale)

        val config = Configuration(base.resources.configuration)
        config.setLocale(locale)

        Log.d("PIO-LANG", "apply locale=$locale")

        return base.createConfigurationContext(config)
    }
}