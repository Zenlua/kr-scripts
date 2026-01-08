package com.omarea.common.ui

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.Window
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.drawable.toDrawable
import com.omarea.common.R

class DialogHelper {

    class DialogButton(val text: String, val onClick: Runnable? = null, val dismiss: Boolean = true)

    class DialogWrap(private val d: AlertDialog) {
        val context: Context = d.context
        private var mCancelable = true

        val isCancelable: Boolean
            get() = mCancelable

        fun setCancelable(cancelable: Boolean): DialogWrap {
            mCancelable = cancelable
            d.setCancelable(cancelable)
            return this
        }

        fun setOnDismissListener(onDismissListener: DialogInterface.OnDismissListener): DialogWrap {
            d.setOnDismissListener(onDismissListener)
            return this
        }

        val dialog: AlertDialog
            get() = d

        fun dismiss() { try { d.dismiss() } catch (_: Exception) {} }
        fun hide() { try { d.hide() } catch (_: Exception) {} }

        val isShowing: Boolean
            get() = d.isShowing
    }

    companion object {

        var disableBlurBg = false

        /* ================= animDialog (GIỮ NGUYÊN) ================= */

        fun animDialog(dialog: AlertDialog?): DialogWrap? {
            if (dialog != null && !dialog.isShowing) {
                dialog.window?.setWindowAnimations(R.style.windowAnim)
                dialog.show()
            }
            return if (dialog != null) DialogWrap(dialog) else null
        }

        fun animDialog(builder: AlertDialog.Builder): DialogWrap {
            val dialog = builder.create()
            animDialog(dialog)
            return DialogWrap(dialog)
        }

        /* ================= helpInfo (GIỮ NGUYÊN) ================= */

        fun helpInfo(context: Context, message: String, onDismiss: Runnable? = null): DialogWrap {
            return helpInfo(context, context.getString(R.string.help_title), message, onDismiss)
        }

        fun helpInfo(
            context: Context,
            title: String,
            message: String,
            onDismiss: Runnable? = null
        ): DialogWrap {
            val layoutInflater = LayoutInflater.from(context)
            val dialogView = layoutInflater.inflate(R.layout.dialog_help_info, null)

            dialogView.findViewById<TextView>(R.id.confirm_title)?.apply {
                text = title
                visibility = if (title.isEmpty()) View.GONE else View.VISIBLE
            }

            dialogView.findViewById<TextView>(R.id.confirm_message)?.apply {
                text = message
                visibility = if (message.isEmpty()) View.GONE else View.VISIBLE
            }

            val d = customDialog(context, dialogView, onDismiss == null)
            dialogView.findViewById<View>(R.id.btn_confirm)?.setOnClickListener {
                d.dismiss()
            }

            if (onDismiss != null) {
                d.setOnDismissListener { onDismiss.run() }
            }

            return d
        }

        /* ================= Dark Mode detect (GIỮ NGUYÊN LOGIC) ================= */

        private fun isNightMode(context: Context): Boolean {
            return when (AppCompatDelegate.getDefaultNightMode()) {
                AppCompatDelegate.MODE_NIGHT_YES -> true
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                AppCompatDelegate.MODE_NIGHT_UNSPECIFIED -> {
                    val uiModeManager =
                        context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
                    uiModeManager.nightMode == UiModeManager.MODE_NIGHT_YES
                }
                else -> false
            }
        }

        /* ================= Dark Mode API mới ================= */

        private fun applySystemBarAppearance(window: Window, light: Boolean) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(
                    if (light) WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS else 0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                    if (light) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else 0
            }
        }

        /* ================= customDialog (CHỈ SỬA DARK MODE) ================= */

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
                    applySystemBarAppearance(this, !isNightMode(context))
                }
            } else {
                dialog.window?.setWindowAnimations(R.style.windowAnim2)
                dialog.show()
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            }

            return setOutsideTouchDismiss(view, DialogWrap(dialog).setCancelable(cancelable))
        }

        /* ================= Blur background (GIỮ NGUYÊN) ================= */

        fun setWindowBlurBg(window: Window, activity: Activity) {
            val wallpaperMode =
                activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER != 0

            val blurBitmap =
                if (disableBlurBg || wallpaperMode) null
                else FastBlurUtility.getBlurBackgroundDrawer(activity)

            if (blurBitmap != null) {
                window.setBackgroundDrawable(blurBitmap.toDrawable(activity.resources))
            } else {
                val bg = if (wallpaperMode || isNightMode(activity))
                    Color.argb(255, 18, 18, 18)
                else
                    Color.argb(255, 245, 245, 245)

                window.setBackgroundDrawable(bg.toDrawable())
            }

            applySystemBarAppearance(window, !isNightMode(activity))
        }

        /* ================= Outside dismiss (GIỮ NGUYÊN) ================= */

        private fun setOutsideTouchDismiss(view: View, dialogWrap: DialogWrap): DialogWrap {
            dialogWrap.dialog.window?.decorView?.setOnTouchListener { _, event ->
                if (event?.action == MotionEvent.ACTION_UP) {
                    val rect = Rect()
                    view.getGlobalVisibleRect(rect)
                    if (!rect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                        if (dialogWrap.isCancelable) dialogWrap.dismiss()
                    }
                    true
                } else false
            }
            return dialogWrap
        }
    }
}
