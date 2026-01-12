package com.projectkr.shell

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.net.Uri
import android.os.*
import android.provider.Settings
import android.view.animation.AnimationUtils
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.lifecycleScope
import com.omarea.common.shell.KeepShellPublic
import com.omarea.common.shell.ShellExecutor
import com.omarea.common.ui.DialogHelper
import com.omarea.krscript.executor.ScriptEnvironmen
import com.projectkr.shell.databinding.ActivitySplashBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.util.*
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope

class SplashActivity : Activity() {

    private lateinit var binding: ActivitySplashBinding
    private val REQUEST_CODE_PERMISSIONS = 1001

    private var hasRoot = false
    private var started = false
    private var starting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ScriptEnvironmen.isInited() && isTaskRoot) {
            gotoHome()
            return
        }

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        if (!hasAgreed()) showAgreementDialog()

        binding.startLogoXml.postDelayed({
            binding.startLogoXml.startAnimation(AnimationUtils.loadAnimation(this, R.anim.blink))
        }, 1500)

        applyTheme()
    }

    // =================== LANGUAGE ===================
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(applyLanguageFromFile(newBase))
    }

    private fun applyLanguageFromFile(base: Context): Context {
    return try {
        val file = File(base.filesDir, "kr-script/language")
        if (!file.exists()) base
        else {
            val lang = file.readText().trim()
            if (lang.isEmpty()) base
            else {
                val locale = lang.split("-").let { if (it.size == 2) Locale(it[0], it[1]) else Locale(lang) }
                Locale.setDefault(locale)
                val config = Configuration(base.resources.configuration)
                config.setLocale(locale)
                base.createConfigurationContext(config)
            }
        }
    } catch (_: Exception) {
        base
    }
}

    // =================== AGREEMENT ===================
    private fun showAgreementDialog() {
        DialogHelper.warning(
            this,
            getString(R.string.permission_dialog_title),
            getString(R.string.permission_dialog_message),
            Runnable { requestAppPermissions() },
            Runnable { finish() }
        ).setCancelable(false)
    }

    private fun hasAgreed(): Boolean =
        getSharedPreferences("kr-script-config", MODE_PRIVATE)
            .getBoolean("agreed_permissions", false)

    private fun saveAgreement() {
        getSharedPreferences("kr-script-config", MODE_PRIVATE)
            .edit()
            .putBoolean("agreed_permissions", true)
            .apply()
    }

    // =================== PERMISSION ===================
    private fun hasAllFilesPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Environment.isExternalStorageManager()
        else ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED

    private fun requestAllFilesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startActivity(Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                data = Uri.parse("package:$packageName")
            })
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(android.Manifest.permission.READ_EXTERNAL_STORAGE, android.Manifest.permission.WRITE_EXTERNAL_STORAGE),
                REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun requestAppPermissions() {
        saveAgreement()
        if (!hasAllFilesPermission()) requestAllFilesPermission()
        else {
            started = true
            checkRootAndStart()
        }
    }

    override fun onResume() {
        super.onResume()
        if (hasAgreed() && !started) {
            started = true
            checkRootAndStart()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                started = true
                checkRootAndStart()
            } else finish()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    // =================== UI ===================
    private fun applyTheme() {
        WindowCompat.setDecorFitsSystemWindows(window, true)
        val color = getColor(R.color.splash_bg_color)
        window.statusBarColor = color
        window.navigationBarColor = color

        WindowInsetsControllerCompat(window, window.decorView).apply {
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    // =================== ROOT ===================
    @Synchronized
    private fun checkRootAndStart() {
        if (!started || starting) return
        starting = true

        lifecycleScope.launch(Dispatchers.IO) {
            hasRoot = KeepShellPublic.checkRoot()
            withContext(Dispatchers.Main) {
                starting = false
                startToFinish()
            }
        }
    }

    // =================== START ===================
    private fun startToFinish() {
        binding.startStateText.text = getString(R.string.pop_started)
        val config = KrScriptConfig().init(this)

        if (config.beforeStartSh.isNotEmpty()) {
            BeforeStartThread(this, config, hasRoot, UpdateLogHandler(binding.startStateText) { gotoHome() }).start()
        } else gotoHome()
    }

    private fun gotoHome() {
        startActivity(
            if (intent?.getBooleanExtra("JumpActionPage", false) == true)
                Intent(this, ActionPage::class.java).apply { putExtras(intent!!) }
            else Intent(this, MainActivity::class.java)
        )
        finish()
    }

    // =================== LOG ===================
    private class UpdateLogHandler(private val view: TextView, private val onExit: Runnable) {
        private val handler = Handler(Looper.getMainLooper())
        private val rows = ArrayDeque<String>()

        fun onLogOutput(line: String) {
            handler.post {
                synchronized(rows) {
                    if (rows.size >= 6) rows.removeFirst()
                    rows.addLast(line)
                    view.text = if (rows.size >= 6) "……\n${rows.joinToString("\n")}" else rows.joinToString("\n")
                }
            }
        }

        fun onExit() = handler.post { onExit.run() }
    }

    private class BeforeStartThread(
        private val context: Context,
        private val config: KrScriptConfig,
        private val hasRoot: Boolean,
        private val logHandler: UpdateLogHandler
    ) : Thread() {
        init { isDaemon = true }

        override fun run() {
            try {
                val process = if (hasRoot) ShellExecutor.getSuperUserRuntime() else ShellExecutor.getRuntime()
                process?.let {
                    DataOutputStream(it.outputStream).use { os ->
                        ScriptEnvironmen.executeShell(context, os, config.beforeStartSh, config.variables, null, "pio-splash")
                    }
                    readAsync(it.inputStream.bufferedReader(), logHandler)
                    readAsync(it.errorStream.bufferedReader(), logHandler)
                    it.waitFor()
                }
            } finally { logHandler.onExit() }
        }
    }

    companion object {
        private fun readAsync(reader: BufferedReader, logHandler: UpdateLogHandler) = thread(isDaemon = true) {
            try {
                val buffer = mutableListOf<String>()
                var lastUpdate = System.currentTimeMillis()
                reader.forEachLine { line ->
                    buffer.add(line)
                    val now = System.currentTimeMillis()
                    if (buffer.size >= 5 || now - lastUpdate >= 50) {
                        logHandler.onLogOutput(buffer.joinToString("\n"))
                        buffer.clear()
                        lastUpdate = now
                    }
                }
                if (buffer.isNotEmpty()) logHandler.onLogOutput(buffer.joinToString("\n"))
            } catch (_: Exception) {}
        }
    }
}