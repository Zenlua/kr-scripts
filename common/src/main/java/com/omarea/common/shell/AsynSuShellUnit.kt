package com.omarea.common.shell

import android.os.Handler
import java.nio.charset.Charset

/**
 * AsynSuShellUnit: chạy lệnh shell root bất đồng bộ
 * - Gửi stdout về handler với what=1
 * - Gửi stderr về handler với what=5
 * - Thông báo shell khởi tạo: what=0, true/false
 * - Thông báo shell kết thúc: what=10, true/false
 * - Thông báo lỗi exec: what=-1
 */
class AsynSuShellUnit(private val handler: Handler) {

    private var process: Process? = null

    /**
     * Khởi tạo shell root và bắt 2 thread đọc stdout/stderr
     */
    private fun start(): AsynSuShellUnit {
        try {
            if (process == null) {
                process = ShellExecutor.getSuperUserRuntime()

                // Thread đọc stdout
                Thread {
                    try {
                        process?.inputStream?.bufferedReader()?.useLines { lines ->
                            lines.forEach { line ->
                                val trimmed = line.trim()
                                if (trimmed.isNotEmpty()) {
                                    handler.sendMessage(handler.obtainMessage(1, trimmed))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    } finally {
                        destroy()
                    }
                }.start()

                // Thread đọc stderr
                Thread {
                    try {
                        process?.errorStream?.bufferedReader()?.useLines { lines ->
                            lines.forEach { line ->
                                val trimmed = line.trim()
                                if (trimmed.isNotEmpty()) {
                                    handler.sendMessage(handler.obtainMessage(5, trimmed))
                                }
                            }
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }.start()

                handler.sendMessage(handler.obtainMessage(0, true))
            }
        } catch (e: Exception) {
            handler.sendMessage(handler.obtainMessage(0, false))
        }
        return this
    }

    /**
     * Gửi lệnh shell đến process
     */
    fun exec(cmd: String): AsynSuShellUnit {
        if (process == null) start()
        process?.outputStream?.bufferedWriter(Charset.forName("UTF-8"))?.apply {
            try {
                write(cmd)
                write("\n")
                flush()
            } catch (e: Exception) {
                handler.handleMessage(handler.obtainMessage(-1))
            }
        } ?: handler.handleMessage(handler.obtainMessage(-1))
        return this
    }

    /**
     * Đợi shell kết thúc, không callback
     */
    fun waitFor() {
        waitFor(null)
    }

    /**
     * Đợi shell kết thúc, thực hiện callback next khi xong
     */
    fun waitFor(next: (() -> Unit)?) {
        process?.let { p ->
            Thread {
                try {
                    p.outputStream.bufferedWriter(Charset.forName("UTF-8")).use { w ->
                        w.write("exit\n")
                        w.flush()
                    }
                    val result = p.waitFor() == 0
                    handler.sendMessage(handler.obtainMessage(10, result))
                    next?.invoke()
                } catch (e: Exception) {
                    handler.sendMessage(handler.obtainMessage(10, false))
                } finally {
                    destroy()
                }
            }.start()
        }
    }

    /**
     * Dọn dẹp process
     */
    fun destroy() {
        process?.let {
            try { it.outputStream.close() } catch (_: Exception) {}
            try { it.destroy() } catch (_: Exception) {}
        }
        process = null
    }
}
