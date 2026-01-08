package com.omarea.common.ui

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.res.Configuration
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
        val context: Context = dialog.context
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

        fun dismiss() {
            try { d.dismiss() } catch (_: Exception) {}
        }

        fun hide() {
            try { d.hide() } catch (_: Exception) {}
        }

        val isShowing: Boolean
            get() = d.isShowing
    }

    companion object {
        var disableBlurBg = false

        fun animDialog(dialog: AlertDialog?): DialogWrap? {
            if (dialog != null && !dialog.isShowing) {
                dialog.window?.setWindowAnimations(R.style.windowAnim)
                dialog.show()
            }
            return dialog?.let { DialogWrap(it) }
        }

        fun animDialog(builder: AlertDialog.Builder): DialogWrap {
            val dialog = builder.create()
            animDialog(dialog)
            return DialogWrap(dialog)
        }

        fun helpInfo(context: Context, message: String, onDismiss: Runnable? = null): DialogWrap {
            return helpInfo(context, context.getString(R.string.help_title), message, onDismiss)
        }

        fun helpInfo(
            context: Context,
            title: String,
            message: String,
            onDismiss: Runnable? = null
        ): DialogWrap {
            val dialogView = LayoutInflater.from(context)
                .inflate(R.layout.dialog_help_info, null)

            dialogView.findViewById<TextView>(R.id.confirm_title)?.apply {
                visibility = if (title.isNotEmpty()) View.VISIBLE else View.GONE
                text = title
            }

            dialogView.findViewById<TextView>(R.id.confirm_message)?.apply {
                visibility = if (message.isNotEmpty()) View.VISIBLE else View.GONE
                text = message
            }

            val d = customDialog(context, dialogView, onDismiss == null)
            dialogView.findViewById<View>(R.id.btn_confirm)?.setOnClickListener {
                d.dismiss()
            }

            onDismiss?.let {
                d.setOnDismissListener { it.run() }
            }

            return d
        }

        fun confirm(
            context: Context,
            title: String = "",
            message: String = "",
            onConfirm: Runnable? = null,
            onCancel: Runnable? = null
        ): DialogWrap {
            return openContinueAlert(context, R.layout.dialog_confirm, title, message, onConfirm, onCancel)
        }

        fun warning(
            context: Context,
            title: String = "",
            message: String = "",
            onConfirm: Runnable? = null,
            onCancel: Runnable? = null
        ): DialogWrap {
            return openContinueAlert(context, R.layout.dialog_warning, title, message, onConfirm, onCancel)
        }

        private fun openContinueAlert(
            context: Context,
            layout: Int,
            title: String,
            message: String,
            onConfirm: Runnable?,
            onCancel: Runnable?
        ): DialogWrap {
            val view = LayoutInflater.from(context).inflate(layout, null)

            view.findViewById<View>(R.id.btn_confirm)?.setOnClickListener {
                onConfirm?.run()
            }

            view.findViewById<View>(R.id.btn_cancel)?.setOnClickListener {
                onCancel?.run()
            }

            return customDialog(context, view)
        }

        fun alert(
            context: Context,
            title: String = "",
            message: String = "",
            onConfirm: Runnable? = null
        ): DialogWrap {
            return openContinueAlert(context, R.layout.dialog_alert, title, message, onConfirm, null)
        }

        fun customDialog(
            context: Context,
            view: View,
            cancelable: Boolean = true
        ): DialogWrap {
            val dialog = AlertDialog.Builder(context, R.style.custom_alert_dialog)
                .setView(view)
                .setCancelable(cancelable)
                .create()

            dialog.show()

            if (context is Activity) {
                dialog.window?.let { window ->
                    setWindowBlurBg(window, context)

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        window.insetsController?.setSystemBarsAppearance(
                            if (!isNightMode(context))
                                WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                            else 0,
                            WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                        )
                    } else {
                        @Suppress("DEPRECATION")
                        window.decorView.systemUiVisibility =
                            if (!isNightMode(context))
                                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                            else 0
                    }
                }
            }

            return DialogWrap(dialog)
        }

        /** ✅ DARK MODE – API 21 → 36, KHÔNG UiModeManager */
        private fun isNightMode(context: Context): Boolean {
            return when (AppCompatDelegate.getDefaultNightMode()) {
                AppCompatDelegate.MODE_NIGHT_YES -> true
                AppCompatDelegate.MODE_NIGHT_NO -> false
                else -> {
                    val mode =
                        context.resources.configuration.uiMode and
                                Configuration.UI_MODE_NIGHT_MASK
                    mode == Configuration.UI_MODE_NIGHT_YES
                }
            }
        }

        fun setWindowBlurBg(window: Window, activity: Activity) {
            val blurBitmap =
                if (disableBlurBg) null
                else FastBlurUtility.getBlurBackgroundDrawer(activity)

            window.setBackgroundDrawable(
                blurBitmap?.toDrawable(activity.resources)
                    ?: if (isNightMode(activity))
                        Color.argb(255, 18, 18, 18).toDrawable()
                    else
                        Color.argb(255, 245, 245, 245).toDrawable()
            )
        }
    }
}
