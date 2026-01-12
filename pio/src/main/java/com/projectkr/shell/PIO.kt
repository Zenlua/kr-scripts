package com.projectkr.shell

import android.app.Application
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.util.Log
import java.io.File
import java.util.Locale

class PIO : Application() {

    override fun attachBaseContext(base: Context) {
        Log.d("LANG", "Application.attachBaseContext")
        super.attachBaseContext(wrapContext(base))
    }

    override fun onCreate() {
        super.onCreate()
        Log.d("LANG", "Application.onCreate sdk=${Build.VERSION.SDK_INT}")
    }

    companion object {

        private const val TAG = "LANG"

        /**
         * Áp dụng locale từ:
         * /data/data/com.projectkr.shell/files/kr-script/language
         */
        fun wrapContext(base: Context): Context {
            val file = File(base.filesDir, "kr-script/language")
            if (!file.exists()) {
                Log.d(TAG, "language file not found")
                return base
            }

            val tag = file.readText().trim()
            if (tag.isEmpty()) {
                Log.d(TAG, "language file empty")
                return base
            }

            Log.d(TAG, "language tag=$tag")

            val locale = if (tag.contains("-")) {
                val sp = tag.split("-")
                if (sp.size == 2) Locale(sp[0], sp[1]) else Locale(sp[0])
            } else {
                Locale(tag)
            }

            Locale.setDefault(locale)

            val config = Configuration(base.resources.configuration)
            config.setLocale(locale)

            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                base.createConfigurationContext(config)
            } else {
                @Suppress("DEPRECATION")
                base.resources.updateConfiguration(config, base.resources.displayMetrics)
                base
            }
        }
    }
}