package com.projectkr.shell

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.os.*
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.omarea.common.shell.ShellExecutor
import com.omarea.common.ui.DialogHelper
import com.omarea.krscript.executor.ScriptEnvironmen
import com.projectkr.shell.databinding.ActivitySplashBinding
import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.util.*
import android.Manifest
import android.net.Uri
import android.provider.Settings

class SplashActivity : Activity() {

    private lateinit var binding: ActivitySplashBinding
    private val mainHandler = Handler(Looper.getMainLooper())

    @Volatile private var hasRoot = false
    @Volatile private var started = false
    @Volatile private var starting = false

    private val REQUEST_CODE_PERMISSIONS = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        applyLanguageFromFile()
        super.onCreate(savedInstanceState)

        if (ScriptEnvironmen.isInited()) {
            if (isTaskRoot) gotoHome()
            return
        }

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        mainHandler.postDelayed({
            binding.startLogoXml.startAnimation(
                android.view.animation.AnimationUtils.loadAnimation(this, R.anim.blink)
            )
        }, 1500)

        applyTheme()

        if (!hasAgreed()) {
            showAgreementDialog()
        }
    }

    // =================== LANGUAGE ===================
    private fun applyLanguageFromFile() {
        try {
            val file = File(filesDir, "kr-script/language")
            if (!file.exists()) return

            val lang = file.readText().trim()
            if (lang.isEmpty()) return

            val locale = if (lang.contains("-")) {
                val p = lang.split("-")
                Locale(p[0], p[1])
            } else Locale(lang)

            Locale.setDefault(locale)

            val config = Configuration(resources.configuration)
            config.setLocale(locale)

            @Suppress("DEPRECATION")
            resources.updateConfiguration(config, resources.displayMetrics)
        } catch (_: Exception) {}
    }

    // =================== AGREEMENT ===================
    private fun showAgreementDialog() {
        DialogHelper.warning(
            this,
            getString(R.string.permission_dialog_title),
            getString(R.string.permission_dialog_message),
            { requestAppPermissions() },
            { finish() }
        )
    }

    private fun saveAgreement() {
        getSharedPreferences("kr-script-config", MODE_PRIVATE)
            .edit()
            .putBoolean("agreed_permissions", true)
            .apply()
    }

    private fun hasAgreed(): Boolean =
        getSharedPreferences("kr-script-config", MODE_PRIVATE)
            .getBoolean("agreed_permissions", false)

    // =================== PERMISSION ===================
    private fun hasAllFilesPermission(): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
            Environment.isExternalStorageManager()
        else
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.READ_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED

    private fun requestAllFilesPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startActivity(
                Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:$packageName")
                }
            )
        } else {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun requestAppPermissions() {
        saveAgreement()

        if (!hasAllFilesPermission()) {
            requestAllFilesPermission()
            return
        }

        started = true
        checkRootAndStart()
    }

    override fun onResume() {
        super.onResume()

        if (hasAgreed()) {
            started = true
            starting = false
            checkRootAndStart()
        } else finish()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
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
        window.statusBarColor = getColor(R.color.splash_bg_color)
        window.navigationBarColor = getColor(R.color.splash_bg_color)

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

        Thread {
            hasRoot = tryRoot()
            mainHandler.post {
                starting = false
                startToFinish()
            }
        }.start()
    }

    private fun tryRoot(): Boolean = try {
        Runtime.getRuntime().exec("su").apply {
            outputStream.write("exit\n".toByteArray())
            outputStream.flush()
        }.waitFor() == 0
    } catch (_: Exception) { false }

    // =================== START ===================
    private fun startToFinish() {
        binding.startStateText.text = getString(R.string.pop_started)
        val config = KrScriptConfig().init(this)

        if (config.beforeStartSh.isNotEmpty()) {
            BeforeStartThread(this, config, hasRoot,
                UpdateLogHandler(binding.startStateText) { gotoHome() }
            ).start()
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
    private class UpdateLogHandler(
        private val view: TextView,
        private val onExit: Runnable
    ) {
        private val handler = Handler(Looper.getMainLooper())
        private val rows = LinkedList<String>()
        private var ignored = false

        fun onLogOutput(line: String) {
            handler.post {
                synchronized(rows) {
                    if (rows.size > 6) {
                        rows.removeFirst()
                        ignored = true
                    }
                    rows.add(line)
                    view.text = rows.joinToString("\n", if (ignored) "……\n" else "")
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

        override fun run() {
            try {
                val process = if (hasRoot)
                    ShellExecutor.getSuperUserRuntime()
                else ShellExecutor.getRuntime()

                process?.let {
                    val os = DataOutputStream(it.outputStream)
                    ScriptEnvironmen.executeShell(
                        context, os,
                        config.beforeStartSh,
                        config.variables,
                        null,
                        "pio-splash"
                    )
                    StreamReaderThread(it.inputStream.bufferedReader(), logHandler).start()
                    StreamReaderThread(it.errorStream.bufferedReader(), logHandler).start()
                    it.waitFor()
                }
            } finally {
                logHandler.onExit()
            }
        }
    }

    private class StreamReaderThread(
        private val reader: BufferedReader,
        private val logHandler: UpdateLogHandler
    ) : Thread() {
        override fun run() {
            reader.forEachLine { logHandler.onLogOutput(it) }
        }
    }
}