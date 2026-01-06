package com.omarea.common.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.scale
import kotlinx.coroutines.*
import android.renderscript.*

class BlurBackground(private val activity: Activity) {

    private var overlayView: View? = null
    private var originalW = 0
    private var originalH = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Capture ViewGroup as bitmap */
    private fun captureView(view: View): Bitmap? {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        originalW = bitmap.width
        originalH = bitmap.height
        return bitmap.scale(originalW / 4, originalH / 4, false)
    }

    /** Blur bitmap using RenderScript */
    private suspend fun blurBitmap(bitmap: Bitmap?): Bitmap? = withContext(Dispatchers.Default) {
        bitmap ?: return@withContext null
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val rs = RenderScript.create(activity)
        try {
            val blurScript = ScriptIntrinsicBlur.create(rs, Element.U8_4(rs))
            val inputAlloc = Allocation.createFromBitmap(rs, bitmap)
            val outputAlloc = Allocation.createFromBitmap(rs, output)
            blurScript.setRadius(10f)
            blurScript.setInput(inputAlloc)
            blurScript.forEach(outputAlloc)
            outputAlloc.copyTo(output)
        } finally {
            rs.destroy()
        }
        output
    }

    /** Add overlay View for blur */
    private fun addOverlay(parent: ViewGroup): View {
        val overlay = View(activity)
        overlay.layoutParams = ViewGroup.LayoutParams(parent.width, parent.height)
        overlay.alpha = 0f
        parent.addView(overlay)
        return overlay
    }

    /** Fade in/out overlay */
    private fun fadeOverlay(inFade: Boolean) {
        scope.launch {
            val start = if (inFade) 0f else 1f
            val end = if (inFade) 1f else 0f
            val steps = 20
            val delayMs = 10L
            for (i in 0..steps) {
                val alpha = start + (end - start) * i / steps
                overlayView?.alpha = alpha
                delay(delayMs)
            }
            if (!inFade) {
                (overlayView?.parent as? ViewGroup)?.removeView(overlayView)
                overlayView = null
            }
        }
    }

    /** Public: blur the whole parent view */
    fun blurParent(parent: ViewGroup) {
        if (overlayView != null) return

        overlayView = addOverlay(parent)

        scope.launch {
            val bitmap = captureView(parent)
            val blurred = blurBitmap(bitmap)?.scale(parent.width, parent.height, false)

            overlayView?.background = android.graphics.drawable.BitmapDrawable(activity.resources, blurred)
            fadeOverlay(true)
        }
    }

    /** Remove blur */
    fun clearBlur() {
        fadeOverlay(false)
    }

    /** Cancel ongoing coroutine to avoid leaks */
    fun cancel() {
        scope.cancel()
    }
}
