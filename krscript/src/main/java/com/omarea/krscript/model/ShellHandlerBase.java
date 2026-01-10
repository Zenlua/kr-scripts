package com.omarea.krscript.model

import android.graphics.BitmapFactory
import android.graphics.Color
import android.os.Handler
import android.os.Message
import android.text.Spannable
import android.text.SpannableString
import android.text.style.ForegroundColorSpan
import android.widget.ImageView
import android.widget.TextView
import java.io.File
import java.util.regex.Pattern

/**
 * ShellHandlerBase hỗ trợ log text và hiển thị ảnh
 */
abstract class ShellHandlerBase : Handler() {

    companion object {
        const val EVENT_START = 0
        const val EVENT_REDE = 2
        const val EVENT_READ_ERROR = 4
        const val EVENT_WRITE = 6
        const val EVENT_EXIT = -2
    }

    // ** UI mới: thêm ImageView hỗ trợ ảnh **
    protected var imageView: ImageView? = null
    protected var textView: TextView? = null

    fun attachViews(text: TextView?, image: ImageView?) {
        this.textView = text
        this.imageView = image
    }

    protected abstract fun onProgress(current: Int, total: Int)
    protected abstract fun onStart(msg: Any)
    abstract fun onStart(forceStop: Runnable?)
    protected abstract fun onExit(msg: Any)
    protected abstract fun updateLog(msg: SpannableString)

    override fun handleMessage(msg: Message) {
        super.handleMessage(msg)
        when (msg.what) {
            EVENT_START -> onStart(msg.obj)
            EVENT_REDE -> onReaderMsg(msg.obj)
            EVENT_READ_ERROR -> onError(msg.obj)
            EVENT_WRITE -> onWrite(msg.obj)
            EVENT_EXIT -> onExit(msg.obj)
        }
    }

    protected fun onReaderMsg(msg: Any?) {
        if (msg == null) return

        val log = msg.toString().trim()
        // Kiểm tra định dạng progress
        if (Pattern.matches("^progress:\\[[\\-0-9\\\\]{1,}/[0-9\\\\]{1,}]$", log)) {
            val values = log.substring("progress:[".length, log.indexOf("]")).split("/")
            val start = values[0].toInt()
            val total = values[1].toInt()
            onProgress(start, total)
        } else if (log.startsWith("@img:")) {
            // ** msg là đường dẫn ảnh **
            val path = log.removePrefix("@img:")
            showImage(path)
        } else {
            onReader(msg)
        }
    }

    protected fun onReader(msg: Any?) {
        updateLog(msg, "#00cc55")
    }

    protected fun onWrite(msg: Any?) {
        updateLog(msg, "#808080")
    }

    protected fun onError(msg: Any?) {
        updateLog(msg, "#ff0000")
    }

    // Các phương thức update log
    protected fun updateLog(msg: Any?, color: String) {
        if (msg == null) return
        val str = msg.toString()
        val spannable = SpannableString(str)
        spannable.setSpan(ForegroundColorSpan(Color.parseColor(color)), 0, str.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        updateLog(spannable)
    }

    protected fun updateLog(msg: Any?, color: Int) {
        if (msg == null) return
        val str = msg.toString()
        val spannable = SpannableString(str)
        spannable.setSpan(ForegroundColorSpan(color), 0, str.length, Spannable.SPAN_EXCLUSIVE_EXCLUSIVE)
        updateLog(spannable)
    }

    // ** Hiển thị ảnh trong ImageView **
    protected fun showImage(path: String) {
        val file = File(path)
        if (!file.exists()) return
        val bitmap = BitmapFactory.decodeFile(file.absolutePath)
        imageView?.post {
            imageView?.setImageBitmap(bitmap)
            imageView?.visibility = android.view.View.VISIBLE
        }
    }
}
