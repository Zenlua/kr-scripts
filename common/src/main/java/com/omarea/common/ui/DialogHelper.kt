package com.omarea.common.ui

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.Rect
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import com.omarea.common.R
import androidx.core.graphics.drawable.toDrawable
import android.os.Build
import android.view.WindowInsetsController
import android.content.res.Configuration // Thêm import này

class DialogHelper {
    class DialogButton(val text: String, val onClick: Runnable? = null, val dismiss: Boolean = true)

    class DialogWrap(private val d: AlertDialog) {
        val context: Context = dialog.context
        private var mCancelable = true
        val isCancelable: Boolean get() = mCancelable

        fun setCancelable(cancelable: Boolean): DialogWrap {
            mCancelable = cancelable
            d.setCancelable(cancelable)
            return this
        }

        fun setOnDismissListener(onDismissListener: DialogInterface.OnDismissListener): DialogWrap {
            d.setOnDismissListener(onDismissListener)
            return this
        }

        val dialog: AlertDialog get() = d

        fun dismiss() {
            try { d.dismiss() } catch (_: Exception) {}
        }

        fun hide() {
            try { d.hide() } catch (_: Exception) {}
        }

        val isShowing: Boolean get() = d.isShowing
    }

    companion object {
        var disableBlurBg = false

        // Các hàm khác giữ nguyên (helpInfo, confirm, alert, v.v.) ...

        fun customDialog(context: Context, view: View, cancelable: Boolean = true): DialogWrap {
            val useBlur = (
                    context is Activity &&
                            context.window.attributes.flags and WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER == 0
                    )

            val dialog = (if (useBlur) {
                AlertDialog.Builder(context, R.style.custom_alert_dialog)
            } else {
                AlertDialog.Builder(context)
            }).setView(view)
                .setCancelable(cancelable)
                .create()

            if (context is Activity) {
                dialog.show()
                dialog.window?.run {
                    setWindowBlurBg(this, context)
                    applySystemBarsAppearance(this, context)
                }
            } else {
                dialog.window?.run {
                    setWindowAnimations(R.style.windowAnim2)
                }
                dialog.show()
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            }

            return setOutsideTouchDismiss(view, DialogWrap(dialog).setCancelable(cancelable))
        }

        // Hàm mới: Xác định chế độ tối mà KHÔNG dùng UiModeManager
        private fun isNightMode(context: Context): Boolean {
            val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return currentNightMode == Configuration.UI_MODE_NIGHT_YES
        }

        private fun applySystemBarsAppearance(window: Window, context: Context) {
            val isDark = isNightMode(context)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.let { controller ->
                    // Xóa appearance cũ
                    controller.setSystemBarsAppearance(
                        0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    )

                    // Nếu là light mode → bật icon sáng (trắng) cho status & navigation bar
                    if (!isDark) {
                        controller.setSystemBarsAppearance(
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                        )
                    }
                }
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility = if (!isDark) {
                    View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                } else {
                    0
                }
            }
        }

        fun setWindowBlurBg(window: Window, activity: Activity) {
            val wallpaperMode = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER != 0
            val isDark = isNightMode(activity)

            window.run {
                val blurBitmap = if (disableBlurBg || wallpaperMode) {
                    null
                } else {
                    FastBlurUtility.getBlurBackgroundDrawer(activity)
                }

                if (blurBitmap != null) {
                    setBackgroundDrawable(blurBitmap.toDrawable(activity.resources))
                } else {
                    try {
                        val backgroundColor = if (isDark) {
                            Color.argb(255, 18, 18, 18)
                        } else {
                            Color.argb(255, 245, 245, 245)
                        }
                        setBackgroundDrawable(backgroundColor.toDrawable())
                    } catch (_: Exception) {
                        setBackgroundDrawable(Color.argb(0, 245, 245, 245).toDrawable())
                    }
                }

                // Áp dụng appearance cho system bars
                applySystemBarsAppearance(this, activity)
            }
        }

        // Các hàm còn lại (confirm, helpInfo, alert, v.v.) giữ nguyên 100%
        // ... (không thay đổi gì)
    }
}
