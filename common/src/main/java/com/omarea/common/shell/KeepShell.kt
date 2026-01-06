package com.omarea.common.shell

import android.util.Log
import java.io.BufferedReader
import java.io.OutputStream
import java.nio.charset.Charset
import java.util.*
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.thread

class KeepShell(private var rootMode: Boolean = true) {

    private var process: Process? = null
    private var out: OutputStream? = null
    private var reader: BufferedReader? = null
    private var currentIsIdle = true

    val isIdle: Boolean
        get() = currentIsIdle

    private val lock = ReentrantLock()
    private val LOCK_TIMEOUT = 10_000L
    private var enterLockTime = 0L

    private val checkRootState = """
        if [[ $(id -u 2>&1) == '0' ]] || [[ "$UID" == '0' ]] || [[ $(whoami 2>&1) == 'root' ]] || [[ $(set | grep 'USER_ID=0') == 'USER_ID=0' ]]; then
          echo 'success'
        else
          if [[ -d /cache ]]; then
            echo 1 > /cache/vtools_root
            if [[ -f /cache/vtools_root ]] && [[ $(cat /cache/vtools_root) == '1' ]]; then
              echo 'success'
              rm -rf /cache/vtools_root
              return
            fi
          fi
          exit 1
        fi
    """.trimIndent()

    // Thoát shell
    fun tryExit() {
        try { out?.close() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        try { process?.destroy() } catch (_: Exception) {}
        enterLockTime = 0L
        out = null
        reader = null
        process = null
        currentIsIdle = true
    }

    // Kiểm tra root
    fun checkRoot(): Boolean {
        val result = doCmdSync(checkRootState).lowercase(Locale.getDefault())
        val denied = listOf("error", "permission denied", "not allowed", "not found")
        return if (denied.any { result.contains(it) }) {
            if (rootMode) tryExit()
            false
        } else result.contains("success")
    }

    // Khởi tạo shell
    private fun getRuntimeShell() {
        if (process != null) return

        val initThread = Thread {
            try {
                lock.lockInterruptibly()
                enterLockTime = System.currentTimeMillis()

                process = if (rootMode) ShellExecutor.getSuperUserRuntime()
                          else ShellExecutor.getRuntime()
                out = process?.outputStream
                reader = process?.inputStream?.bufferedReader()

                if (rootMode) {
                    out?.write(checkRootState.toByteArray(Charset.defaultCharset()))
                    out?.flush()
                }

                // Thread đọc lỗi shell
                thread(isDaemon = true) {
                    process?.errorStream?.bufferedReader()?.use { errReader ->
                        while (true) {
                            val line = errReader.readLine() ?: break
                            Log.e("KeepShellError", line)
                        }
                    }
                }

            } catch (ex: Exception) {
                Log.e("KeepShellInit", ex.message ?: "")
            } finally {
                enterLockTime = 0L
                lock.unlock()
            }
        }

        initThread.start()
        initThread.join(10_000)
        if (process == null && initThread.state != Thread.State.TERMINATED) {
            enterLockTime = 0L
            initThread.interrupt()
        }
    }

    private val startTag = "|SH>>|"
    private val endTag = "|<<SH|"
    private val startTagBytes = "\necho '$startTag'\n".toByteArray(Charset.defaultCharset())
    private val endTagBytes = "\necho '$endTag'\n".toByteArray(Charset.defaultCharset())

    // Thực thi lệnh shell đồng bộ
    fun doCmdSync(cmd: String): String {
        if (lock.isLocked && enterLockTime > 0 && System.currentTimeMillis() - enterLockTime > LOCK_TIMEOUT) {
            tryExit()
            Log.e("KeepShell", "Lock timeout exceeded")
        }

        getRuntimeShell()
        val outputBuilder = StringBuilder()

        try {
            lock.lockInterruptibly()
            currentIsIdle = false

            out?.apply {
                write(startTagBytes)
                write(cmd.toByteArray(Charset.defaultCharset()))
                write(endTagBytes)
                flush()
            }

            var reading = false
            while (true) {
                val line = reader?.readLine() ?: break

                when {
                    line.contains(startTag) -> {
                        outputBuilder.clear()
                        outputBuilder.append(line.substringAfter(startTag))
                        reading = true
                    }
                    line.contains(endTag) -> {
                        outputBuilder.append(line.substringBefore(endTag))
                        break
                    }
                    reading -> {
                        outputBuilder.append(line).append("\n")
                    }
                }
            }

            return outputBuilder.toString().trim()
        } catch (e: Exception) {
            tryExit()
            Log.e("KeepShellCmd", e.message ?: "")
            return "error"
        } finally {
            enterLockTime = 0L
            lock.unlock()
            currentIsIdle = true
        }
    }

    // Thực thi lệnh với translation
    fun doCmdSync(cmd: String, translation: ShellTranslation): String {
        val rows = doCmdSync(cmd).lines()
        return translation.resolveRows(rows)
    }
}
