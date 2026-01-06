package com.omarea.common.ui

import android.app.Activity
import android.app.Dialog
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.renderscript.*
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.ImageView
import androidx.core.graphics.scale
import kotlinx.coroutines.*

class BlurBackground(private val activity: Activity) {

    private var dialogBg: ImageView? = null
    private var originalW = 0
    private var originalH = 0
    private val mainHandler = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    private fun captureScreen(): Bitmap? {
        val decorView = activity.window.decorView
        decorView.destroyDrawingCache()
        decorView.isDrawingCacheEnabled = true
        val bmp = decorView.drawingCache ?: return null
        originalW = bmp.width
        originalH = bmp.height
        return bmp.scale(originalW / 4, originalH / 4, false)
    }

    private suspend fun blur(bitmap: Bitmap?): Bitmap? = withContext(Dispatchers.Default) {
        bitmap ?: return@withContext null
        val output = Bitmap.createBitmap(bitmap)
        val rs = RenderScript.create(activity)
        try {
            val blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
            val inputAlloc = Allocation.createFromBitmap(rs, bitmap)
            val outputAlloc = Allocation.createFromBitmap(rs, output)
            blurScript.setRadius(10f) // 0 < radius <= 25
            blurScript.setInput(inputAlloc)
            blurScript.forEach(outputAlloc)
            outputAlloc.copyTo(output)
        } finally {
            rs.destroy()
        }
        return@withContext output
    }

    private fun fadeImage(inFade: Boolean) {
        scope.launch {
            val start = if (inFade) 0 else 255
            val end = if (inFade) 255 else 0
            val step = 5
            for (alpha in if (inFade) start..end step step else start downTo end step step) {
                dialogBg?.imageAlpha = alpha
                delay(4)
            }
            if (!inFade) dialogBg?.visibility = View.GONE
        }
    }

    private fun handleBlur() {
        dialogBg?.let { imageView ->
            scope.launch {
                var bitmap = captureScreen()
                bitmap = blur(bitmap)
                bitmap = bitmap?.scale(originalW, originalH, false)
                imageView.setImageBitmap(bitmap)
                imageView.visibility = View.VISIBLE
                fadeImage(true)
            }
        }
    }

    fun setScreenBgLight(dialog: Dialog) {
        dialog.window?.let { window ->
            val lp: WindowManager.LayoutParams = window.attributes
            lp.dimAmount = 0.2f
            window.attributes = lp
        }
        handleBlur()
    }
}
