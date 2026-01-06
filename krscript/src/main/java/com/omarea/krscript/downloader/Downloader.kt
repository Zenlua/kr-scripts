package com.omarea.krscript.downloader

import android.app.Activity
import android.app.DownloadManager
import android.content.Context
import android.content.Context.DOWNLOAD_SERVICE
import android.content.Intent
import android.os.Environment
import android.webkit.URLUtil
import android.widget.Toast
import androidx.core.net.toUri
import androidx.core.content.edit
import com.omarea.common.shared.FileWrite
import com.omarea.common.ui.DialogHelper
import com.omarea.krscript.R
import org.json.JSONObject
import java.io.File
import java.nio.charset.Charset
import java.util.Locale.getDefault

class Downloader(private var context: Context, private var activity: Activity? = null) {

    companion object {
        private const val HISTORY_CONFIG = "kr_downloader"
    }

    fun downloadByBrowser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            addCategory(Intent.CATEGORY_BROWSABLE)
            data = url.toUri()
        }
        activity?.startActivity(intent)
    }

    fun downloadBySystem(
        url: String,
        contentDisposition: String?,
        mimeType: String?,
        taskAliasId: String?,
        fileName: String? = null
    ): Long? {
        return try {
            val request = DownloadManager.Request(url.toUri()).apply {
                setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                setAllowedOverMetered(true)
                setAllowedOverRoaming(true)
                val outName = fileName.takeUnless { it.isNullOrEmpty() }
                    ?: URLUtil.guessFileName(url, contentDisposition, mimeType)
                setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, outName)
            }

            val downloadManager = context.getSystemService(DOWNLOAD_SERVICE) as DownloadManager
            val downloadId = downloadManager.enqueue(request)

            if (!taskAliasId.isNullOrEmpty()) {
                addTaskHistory(downloadId, taskAliasId, url)
            }

            Toast.makeText(context, context.getString(R.string.kr_download_create_success), Toast.LENGTH_SHORT).show()
            DownloaderReceiver.autoRegister(context.applicationContext)

            downloadId
        } catch (ex: Exception) {
            DialogHelper.helpInfo(context, context.getString(R.string.kr_download_create_fail), ex.message ?: "")
            null
        }
    }

    // Lưu lịch sử download
    private fun addTaskHistory(downloadId: Long, taskAliasId: String, url: String) {
        val historyList = context.getSharedPreferences(HISTORY_CONFIG, Context.MODE_PRIVATE)
        val history = JSONObject().apply {
            put("url", url)
            put("taskAliasId", taskAliasId)
        }
        historyList.edit { putString(downloadId.toString(), history.toString(2)) }
    }

    // Lưu trạng thái download
    fun saveTaskStatus(taskAliasId: String?, ratio: Int) {
        FileWrite.writePrivateFile(ratio.toString().toByteArray(Charset.defaultCharset()),
            "downloader/status/$taskAliasId", context)
    }

    // Lưu path file sau khi download xong
    fun saveTaskCompleted(downloadId: Long, absPath: String) {
        val historyList = context.getSharedPreferences(HISTORY_CONFIG, Context.MODE_PRIVATE)
        val historyStr = historyList.getString(downloadId.toString(), null)
        var taskAliasId: String? = ""
        if (historyStr != null) {
            val history = JSONObject(historyStr).apply { put("absPath", absPath) }
            historyList.edit { putString(downloadId.toString(), history.toString(2)) }
            taskAliasId = history.getString("taskAliasId")
        }

        try {
            val file = File(absPath)
            if (file.exists() && file.canRead()) {
                val md5 = FileMD5().getFileMD5(file).lowercase(getDefault())
                FileWrite.writePrivateFile(absPath.toByteArray(Charset.defaultCharset()), "downloader/path/$md5", context)
                taskAliasId?.run {
                    FileWrite.writePrivateFile(absPath.toByteArray(Charset.defaultCharset()), "downloader/result/$taskAliasId", context)
                }
            }
        } catch (_: Exception) {
        }
    }
}
