package com.omarea.common.shell

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
    val isIdle: Boolean get() = currentIsIdle

    private val lock = ReentrantLock()
    private val LOCK_TIMEOUT = 10000L
    private var enterLockTime = 0L

    private val shellOutputCache = StringBuilder()
    private val startTag = "|SH>>|"
    private val endTag = "|<<SH|"
    private val charset: Charset = Charsets.UTF_8
    private val startTagBytes = "\necho '$startTag'\n".toByteArray(charset)
    private val endTagBytes = "\necho '$endTag'\n".toByteArray(charset)

    private val checkRootState = """
    [ "$(id -u 2>/dev/null)" = "0" ] && echo success || exit 1
    """.trimIndent()

    /** Thoát shell an toàn */
    fun tryExit() {
        try { out?.close() } catch (_: Exception) {}
        try { reader?.close() } catch (_: Exception) {}
        try { process?.destroy() } catch (_: Exception) {}
        process = null
        out = null
        reader = null
        enterLockTime = 0L
        currentIsIdle = true
    }

    /** Kiểm tra quyền root */
    fun checkRoot(): Boolean {
        val result = doCmdSync(checkRootState).lowercase(Locale.getDefault())
        return when {
            result == "error" || result.contains("permission denied") || result.contains("not allowed") || result == "not found" -> {
                if (rootMode) tryExit()
                false
            }
            result.contains("success") -> true
            else -> {
                if (rootMode) tryExit()
                false
            }
        }
    }

    /** Khởi tạo shell runtime */
    private fun getRuntimeShell() {
        if (process != null) return
        val t = thread {
            try {
                lock.lockInterruptibly()
                enterLockTime = System.currentTimeMillis()
                process = if (rootMode) ShellExecutor.getSuperUserRuntime() else ShellExecutor.getRuntime()
                out = process!!.outputStream
                reader = process!!.inputStream.bufferedReader(charset)

                // Kiểm tra root ngay khi mở shell root
                if (rootMode) {
                    out?.write(checkRootState.toByteArray(charset))
                    out?.flush()
                }

                // Thread đọc error stream an toàn
                thread(start = true) {
                    try {
                        val errorReader = process!!.errorStream.bufferedReader(charset)
                        while (true) {
                            val line = errorReader.readLine() ?: break
                            Log.e("KeepShellError", line)
                        }
                    } catch (ex: Exception) {
                        Log.e("KeepShellError", ex.message ?: "Unknown error")
                    }
                }

            } catch (ex: Exception) {
                Log.e("KeepShellInit", ex.message ?: "Unknown error")
            } finally {
                enterLockTime = 0L
                lock.unlock()
            }
        }
        t.join(10000)
        if (process == null && t.isAlive) {
            enterLockTime = 0L
            t.interrupt()
        }
    }

    /** Thực thi lệnh shell đồng bộ */
    fun doCmdSync(cmd: String): String {
        // Timeout lock
        if (lock.isLocked && enterLockTime > 0 && System.currentTimeMillis() - enterLockTime > LOCK_TIMEOUT) {
            tryExit()
            Log.e("KeepShell", "Lock timeout: ${System.currentTimeMillis() - enterLockTime}ms")
        }

        getRuntimeShell()

        try {
            lock.lockInterruptibly()
            currentIsIdle = false

            // Gửi lệnh vào shell
            out?.write(startTagBytes)
            out?.write(cmd.toByteArray(charset))
            out?.write(endTagBytes)
            out?.flush()

            // Đọc output
            var unstarted = true
            shellOutputCache.clear()
            while (reader != null) {
                val line = reader!!.readLine() ?: break
                when {
                    line.contains(startTag) -> {
                        shellOutputCache.clear()
                        shellOutputCache.append(line.substringAfter(startTag))
                        unstarted = false
                    }
                    line.contains(endTag) -> {
                        shellOutputCache.append(line.substringBefore(endTag))
                        break
                    }
                    !unstarted -> {
                        shellOutputCache.append(line)
                        shellOutputCache.append("\n")
                    }
                }
            }

            return shellOutputCache.toString().trim()

        } catch (e: Exception) {
            tryExit()
            Log.e("KeepShell", e.message ?: "Unknown error")
            return "error"
        } finally {
            enterLockTime = 0L
            lock.unlock()
            currentIsIdle = true
        }
    }

    /** Thực thi lệnh với dịch ResourceID */
    fun doCmdSync(cmd: String, translation: ShellTranslation): String {
    return translation.getTranslatedResult(cmd, this)
    }
}
