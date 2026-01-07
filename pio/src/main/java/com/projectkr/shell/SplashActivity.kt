package com.projectkr.shell

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.os.*
import android.widget.TextView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.omarea.common.shell.ShellExecutor
import com.omarea.krscript.executor.ScriptEnvironmen
import com.projectkr.shell.databinding.ActivitySplashBinding
import java.io.BufferedReader
import java.io.DataOutputStream
import java.util.HashMap

class SplashActivity : Activity() {

    private lateinit var binding: ActivitySplashBinding
    private val mainHandler = Handler(Looper.getMainLooper())
    private var hasRoot = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (ScriptEnvironmen.isInited()) {
            if (isTaskRoot) {
                gotoHome()
            }
            return
        }

        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        updateThemeStyle()
        checkRootAndStart()
    }

    private fun updateThemeStyle() {
        // Cho phép layout vẽ dưới system bars (thay cho systemUiVisibility)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        // Màu system bars
        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = getColor(R.color.splash_bg_color)

        // Điều khiển icon status/navigation bar
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
    }

    /**
     * LẦN ĐẦU MỞ APP:
     * - Gọi su (Magisk có thể popup)
     * - Deny / không root vẫn chạy
     */
    private fun checkRootAndStart() {
        Thread {
            hasRoot = requestRootOnce()

            mainHandler.post {
                startToFinish()
            }
        }.start()
    }

    /**
     * Gọi su → cho phép Magisk popup
     * Allow = true, Deny / no root = false
     */
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

    /**
     * GIỮ NGUYÊN LUỒNG CODE GỐC
     */
    private fun startToFinish() {
        binding.startStateText.text = getString(R.string.pop_started)

        val config = KrScriptConfig().init(this)

        if (config.beforeStartSh.isNotEmpty()) {
            BeforeStartThread(
                this,
                config,
                hasRoot,
                UpdateLogViewHandler(binding.startStateText) {
                    gotoHome()
                }
            ).start()
        } else {
            gotoHome()
        }
    }

    private fun gotoHome() {
        val target =
            if (intent?.getBooleanExtra("JumpActionPage", false) == true) {
                Intent(this, ActionPage::class.java).apply {
                    putExtras(this@SplashActivity.intent!!)
                }
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
                    logView.text =
                        rows.joinToString("\n", if (ignored) "……\n" else "")
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
                    if (hasRoot)
                        ShellExecutor.getSuperUserRuntime()
                    else
                        ShellExecutor.getRuntime()

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

                    StreamReadThread(
                        process.inputStream.bufferedReader(),
                        logHandler
                    ).start()

                    StreamReadThread(
                        process.errorStream.bufferedReader(),
                        logHandler
                    ).start()

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