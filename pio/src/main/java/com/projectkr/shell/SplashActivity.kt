package com.projectkr.shell

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.widget.TextView
import android.widget.TextView
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.omarea.common.shell.ShellExecutor
import com.omarea.common.ui.DialogHelper
import com.omarea.krscript.executor.ScriptEnvironmen
import com.projectkr.shell.databinding.ActivitySplashBinding
import java.io.BufferedReader
import java.io.DataOutputStream
import java.util.*

class SplashActivity : Activity() {

    private lateinit var binding: ActivitySplashBinding
    private val mainHandler = Handler(Looper.getMainLooper())
    private var hasRoot = false

    private val requiredPermissions = arrayOf(
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        android.Manifest.permission.POST_NOTIFICATIONS
    )
    private val REQUEST_CODE_PERMISSIONS = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ScriptEnvironmen.isInited()) {
            if (isTaskRoot) gotoHome()
            return
        }

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Hiệu ứng logo
        mainHandler.postDelayed({
            val blink = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.blink)
            binding.startLogoXml.startAnimation(blink)
        }, 1500)

        applyTheme()

        if (!hasAgreed()) showAgreementDialog() else checkRootAndStart()
    }

    // =================== DIALOG ĐIỀU KHOẢN ===================
    private fun showAgreementDialog() {
        DialogHelper.warning(
            this,
            getString(R.string.permission_dialog_title),
            getString(R.string.permission_dialog_message),
            { requestPermissions() }, // Đồng ý
            { finish() } // Hủy
        )
    }

    // =================== REQUEST QUYỀN ===================
    private fun requestPermissions() {
        val toRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, toRequest.toTypedArray(), REQUEST_CODE_PERMISSIONS)
        } else {
            saveAgreement()
            checkRootAndStart()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                saveAgreement()
                checkRootAndStart()
            } else finish()
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }

    // =================== LƯU TRẠNG THÁI ===================
    private fun saveAgreement() {
        getSharedPreferences("kr-script-config", Context.MODE_PRIVATE)
            .edit().putBoolean("agreed_permissions", true).apply()
    }

    private fun hasAgreed() = getSharedPreferences("kr-script-config", Context.MODE_PRIVATE)
        .getBoolean("agreed_permissions", false)

    // =================== GIAO DIỆN ===================
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
    private fun checkRootAndStart() {
        Thread {
            hasRoot = tryRoot()
            mainHandler.post { startToFinish() }
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
            BeforeStartThread(this, config, hasRoot, UpdateLogHandler(binding.startStateText) { gotoHome() }).start()
        } else {
            gotoHome()
        }
    }

    private fun gotoHome() {
        val target = if (intent?.getBooleanExtra("JumpActionPage", false) == true)
            Intent(this, ActionPage::class.java).apply { putExtras(intent!!) }
        else Intent(this, MainActivity::class.java)

        startActivity(target)
        finish()
    }

    // =================== LOG HANDLER ===================
    private class UpdateLogHandler(
        private val logView: TextView,
        private val onExit: Runnable
    ) {
        private val handler = Handler(Looper.getMainLooper())
        private val rows = LinkedList<String>()
        private var ignored = false

        fun onLogOutput(log: String) {
            handler.post {
                synchronized(rows) {
                    if (rows.size > 6) {
                        rows.removeFirst()
                        ignored = true
                    }
                    rows.add(log)
                    logView.text = rows.joinToString("\n", if (ignored) "……\n" else "")
                }
            }
        }

        fun onExit() = handler.post { onExit.run() }
    }

    // =================== SHELL THREAD ===================
    private class BeforeStartThread(
        private val context: Context,
        private val config: KrScriptConfig,
        private val hasRoot: Boolean,
        private val logHandler: UpdateLogHandler
    ) : Thread() {
        private val params = config.variables

        override fun run() {
            try {
                val process = if (hasRoot) ShellExecutor.getSuperUserRuntime() else ShellExecutor.getRuntime()
                process?.let {
                    val os = DataOutputStream(it.outputStream)
                    ScriptEnvironmen.executeShell(context, os, config.beforeStartSh, params, null, "pio-splash")
                    StreamReaderThread(it.inputStream.bufferedReader(), logHandler).start()
                    StreamReaderThread(it.errorStream.bufferedReader(), logHandler).start()
                    it.waitFor()
                }
            } catch (_: Exception) {
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