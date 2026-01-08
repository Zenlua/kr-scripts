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
import com.omarea.common.ui.DialogHelper.Companion.warning
import com.omarea.common.ui.DialogHelper.Companion.alert
import com.omarea.common.ui.DialogHelper.Companion.confirm

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

        /* ================= Dark mode chuẩn API 21–36 ================= */

        private fun isNightMode(context: Context): Boolean {
            return (context.resources.configuration.uiMode
                    and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
        }

        private fun applySystemBarAppearance(window: Window, context: Context) {
            val light = !isNightMode(context)

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

        /* ============================================================= */

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
                if (title.isEmpty()) visibility = View.GONE else text = title
            }

            dialogView.findViewById<TextView>(R.id.confirm_message)?.apply {
                if (message.isEmpty()) visibility = View.GONE else text = message
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

        private fun getCustomDialogView(
            context: Context,
            layout: Int,
            title: String = "",
            message: String = "",
            contentView: View? = null
        ): View {

            val view = LayoutInflater.from(context).inflate(layout, null)

            view.findViewById<TextView>(R.id.confirm_title)?.apply {
                if (title.isEmpty()) visibility = View.GONE else text = title
            }

            view.findViewById<TextView>(R.id.confirm_message)?.apply {
                if (message.isEmpty()) visibility = View.GONE else text = message
            }

            contentView?.let {
                view.findViewById<FrameLayout>(R.id.confirm_custom_view)?.addView(it)
            }

            return view
        }

        private fun setOutsideTouchDismiss(view: View, dialogWrap: DialogWrap): DialogWrap {
            val dialog = dialogWrap.dialog
            dialog.window?.decorView?.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    val rect = Rect()
                    view.getGlobalVisibleRect(rect)
                    if (!rect.contains(event.x.toInt(), event.y.toInt()) && dialogWrap.isCancelable) {
                        dialogWrap.dismiss()
                    }
                }
                true
            }
            return dialogWrap
        }

        fun customDialog(context: Context, view: View, cancelable: Boolean = true): DialogWrap {

            val dialog = AlertDialog.Builder(
                context,
                if (context is Activity) R.style.custom_alert_dialog else 0
            )
                .setView(view)
                .setCancelable(cancelable)
                .create()

            dialog.show()

            dialog.window?.let { window ->
                if (context is Activity) {
                    setWindowBlurBg(window, context)
                    applySystemBarAppearance(window, context)
                } else {
                    window.setBackgroundDrawable(Color.TRANSPARENT.toDrawable())
                }
            }

            return setOutsideTouchDismiss(view, DialogWrap(dialog).setCancelable(cancelable))
        }

        fun setWindowBlurBg(window: Window, activity: Activity) {

            val blurBitmap =
                if (disableBlurBg) null
                else FastBlurUtility.getBlurBackgroundDrawer(activity)

            if (blurBitmap != null) {
                window.setBackgroundDrawable(blurBitmap.toDrawable(activity.resources))
            } else {
                val color = if (isNightMode(activity))
                    Color.argb(255, 18, 18, 18)
                else
                    Color.argb(255, 245, 245, 245)

                window.setBackgroundDrawable(color.toDrawable())
            }

            applySystemBarAppearance(window, activity)
        }
    }
}
