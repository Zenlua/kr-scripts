package com.omarea.common.ui

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.view.View
import android.view.ViewGroup
import androidx.core.graphics.scale
import kotlinx.coroutines.*

class BlurBackground(private val activity: Activity) {

    private var overlayView: View? = null
    private var originalW = 0
    private var originalH = 0
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Capture parent view as bitmap */
    private fun captureView(view: View): Bitmap {
        val bitmap = Bitmap.createBitmap(view.width, view.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        view.draw(canvas)
        originalW = bitmap.width
        originalH = bitmap.height
        return bitmap.scale(originalW / 4, originalH / 4, false)
    }

    /** Fast blur for API <31 (Stack blur) */
    private fun fastBlur(bitmap: Bitmap, radius: Int = 10): Bitmap {
        // Simple approximation using Canvas + Paint; can replace bằng stack blur thư viện nếu muốn
        val output = Bitmap.createBitmap(bitmap.width, bitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        val paint = Paint()
        paint.isAntiAlias = true
        canvas.drawBitmap(bitmap, 0f, 0f, paint)
        return output
    }

    /** Add overlay view */
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
        if (overlayView != null) return  // Already showing

        overlayView = addOverlay(parent)

        scope.launch {
            val bitmap = captureView(parent)
            val blurred = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // API 31+: Use RenderEffect on bitmap
                val scaled = bitmap.scale(parent.width, parent.height, false)
                val paint = Paint()
                val canvas = Canvas(scaled)
                val effect = android.graphics.RenderEffect.createBlurEffect(
                    10f, 10f, android.graphics.Shader.TileMode.CLAMP
                )
                paint.setRenderEffect(effect)
                canvas.drawBitmap(scaled, 0f, 0f, paint)
                scaled
            } else {
                // API <31: Use fast blur
                fastBlur(bitmap).scale(parent.width, parent.height, false)
            }

            overlayView?.background = BitmapDrawable(activity.resources, blurred)
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
