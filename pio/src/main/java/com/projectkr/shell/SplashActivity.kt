package com.projectkr.shell

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.*
import android.view.LayoutInflater
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.omarea.common.shell.ShellExecutor
import com.omarea.krscript.executor.ScriptEnvironmen
import com.projectkr.shell.databinding.ActivitySplashBinding
import com.projectkr.shell.databinding.DialogConfirmBinding
import java.io.BufferedReader
import java.io.DataOutputStream
import java.util.HashMap
import java.util.Locale

class SplashActivity : Activity() {

    private lateinit var binding: ActivitySplashBinding
    private val mainHandler = Handler(Looper.getMainLooper())
    private var hasRoot = false

    private val requiredPermissions = arrayOf(
        android.Manifest.permission.READ_EXTERNAL_STORAGE,
        android.Manifest.permission.WRITE_EXTERNAL_STORAGE,
        android.Manifest.permission.POST_NOTIFICATIONS
    )

    // ================= QUYỀN =================
    private val requestPermissionsLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
            val allGranted = result.all { it.value }
            if (allGranted) {
                saveAgreement()
                checkRootAndStart()
            } else {
                finish() // từ chối quyền → thoát app
            }
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Nếu app đã init, bỏ qua splash
        if (ScriptEnvironmen.isInited()) {
            if (isTaskRoot) gotoHome()
            return
        }

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Hiệu ứng logo
        Handler(Looper.getMainLooper()).postDelayed({
            val blink = android.view.animation.AnimationUtils.loadAnimation(this, R.anim.blink)
            binding.startLogoXml.startAnimation(blink)
        }, 1500)

        updateThemeStyle()

        // Hiển thị dialog điều khoản hoặc tiếp tục
        if (!hasAgreed()) {
            showAgreementDialog()
        } else {
            checkRootAndStart()
        }
    }

    // ================= DIALOG ĐIỀU KHOẢN =================
    private fun showAgreementDialog() {
        val dialogBinding = DialogConfirmBinding.inflate(LayoutInflater.from(this))

        // Tách text ra string.xml
        dialogBinding.confirmTitle.text = getString(R.string.permission_dialog_title)
        dialogBinding.confirmMessage.text = getString(R.string.permission_dialog_message)

        val dialog = Dialog(this)
        dialog.setContentView(dialogBinding.root)
        dialog.setCancelable(false)
        dialog.show()

        dialogBinding.btnCancel.setOnClickListener {
            dialog.dismiss()
            finish()
        }

        dialogBinding.btnConfirm.setOnClickListener {
            dialog.dismiss()
            requestPermissions()
        }
    }

    private fun requestPermissions() {
        val toRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (toRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(toRequest.toTypedArray())
        } else {
            saveAgreement()
            checkRootAndStart()
        }
    }

    private fun saveAgreement() {
        val config = KrScriptConfig().init(this)
        config.setBoolean("agreed_permissions", true)
        config.save(this)
    }

    private fun hasAgreed(): Boolean {
        val config = KrScriptConfig().init(this)
        return config.getBoolean("agreed_permissions", false)
    }

    // ================= GIAO DIỆN =================
    private fun updateThemeStyle() {
        WindowCompat.setDecorFitsSystemWindows(window, true)

        window.statusBarColor = getColor(R.color.splash_bg_color)
        window.navigationBarColor = getColor(R.color.splash_bg_color)

        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
    }

    // ================= ROOT =================
    private fun checkRootAndStart() {
        Thread {
            hasRoot = requestRootOnce()
            mainHandler.post { startToFinish() }
        }.start()
    }

    private fun requestRootOnce(): Boolean {
        return try {
            val p = Runtime.getRuntime().exec("su")
            p.outputStream.write("exit\n".toByteArray())
            p.outputStream.flush()
            p.waitFor() == 0
        } catch (_: Exception) {
            false
        }
    }

    // ================= START =================
    private fun startToFinish() {
        binding.startStateText.text = getString(R.string.pop_started)

        val config = KrScriptConfig().init(this)
        if (config.beforeStartSh.isNotEmpty()) {
            BeforeStartThread(
                this,
                config,
                hasRoot,
                UpdateLogViewHandler(binding.startStateText) { gotoHome() }
            ).start()
        } else {
            gotoHome()
        }
    }

    private fun gotoHome() {
        val target = if (intent?.getBooleanExtra("JumpActionPage", false) == true) {
            Intent(this, ActionPage::class.java).apply { putExtras(intent!!) }
        } else {
            Intent(this, MainActivity::class.java)
        }
        startActivity(target)
        finish()
    }

    // ================= LOG HANDLER =================
    private class UpdateLogViewHandler(
        private val logView: TextView,
        private val onExit: Runnable
    ) {
        private val handler = Handler(Looper.getMainLooper())
        private val rows = ArrayList<String>()
        private var ignored = false

        fun onLogOutput(log: String) {
            handler.post {
                synchronized(rows) {
                    if (rows.size > 6) {
                        rows.removeAt(0)
                        ignored = true
                    }
                    rows.add(log)
                    logView.text = rows.joinToString("\n", if (ignored) "……\n" else "")
                }
            }
        }

        fun onExit() {
            handler.post { onExit.run() }
        }
    }

    // ================= SHELL THREAD =================
    private class BeforeStartThread(
        private val context: Context,
        private val config: KrScriptConfig,
        private val hasRoot: Boolean,
        private val logHandler: UpdateLogViewHandler
    ) : Thread() {

        private val params: HashMap<String?, String?>? = config.variables

        override fun run() {
            try {
                val process =
                    if (hasRoot) ShellExecutor.getSuperUserRuntime()
                    else ShellExecutor.getRuntime()

                if (process != null) {
                    val os = DataOutputStream(process.outputStream)

                    ScriptEnvironmen.executeShell(
                        context,
                        os,
                        config.beforeStartSh,
                        params,
                        null,
                        "pio-splash"
                    )

                    StreamReadThread(process.inputStream.bufferedReader(), logHandler).start()
                    StreamReadThread(process.errorStream.bufferedReader(), logHandler).start()
                    process.waitFor()
                }
            } catch (_: Exception) {
            } finally {
                logHandler.onExit()
            }
        }
    }

    private class StreamReadThread(
        private val reader: BufferedReader,
        private val logHandler: UpdateLogViewHandler
    ) : Thread() {
        override fun run() {
            while (true) {
                val line = reader.readLine() ?: break
                logHandler.onLogOutput(line)
            }
        }
    }
}
