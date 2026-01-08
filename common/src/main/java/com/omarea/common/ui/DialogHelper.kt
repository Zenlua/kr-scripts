package com.omarea.common.ui

import android.app.Activity
import android.app.UiModeManager
import android.content.Context
import android.content.DialogInterface
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.view.*
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.graphics.drawable.toDrawable
import com.omarea.common.R

class DialogHelper {

    class DialogButton(
        val text: String,
        val onClick: Runnable? = null,
        val dismiss: Boolean = true
    )

    class DialogWrap(private val d: AlertDialog) {
        private var mCancelable = true

        val dialog: AlertDialog get() = d
        val isShowing: Boolean get() = d.isShowing
        val context: Context get() = d.context
        val isCancelable get() = mCancelable

        fun setCancelable(cancelable: Boolean): DialogWrap {
            mCancelable = cancelable
            d.setCancelable(cancelable)
            return this
        }

        fun setOnDismissListener(listener: DialogInterface.OnDismissListener): DialogWrap {
            d.setOnDismissListener(listener)
            return this
        }

        fun dismiss() {
            try { d.dismiss() } catch (_: Exception) {}
        }

        fun hide() {
            try { d.hide() } catch (_: Exception) {}
        }
    }

    companion object {

        var disableBlurBg = false

        /* ========================= DARK MODE (API LATEST) ========================= */

        /**
         * Xác định Night Mode chính xác cho mọi API
         */
        private fun isNightMode(context: Context): Boolean {
            return when (AppCompatDelegate.getDefaultNightMode()) {
                AppCompatDelegate.MODE_NIGHT_YES -> true
                AppCompatDelegate.MODE_NIGHT_NO -> false
                else -> {
                    val uiMode = context.resources.configuration.uiMode
                    (uiMode and Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES
                }
            }
        }

        /**
         * Áp dụng Light/Dark system bars – chuẩn Android 11+
         */
        private fun applySystemBars(window: Window, lightStatusBar: Boolean) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.setSystemBarsAppearance(
                    if (lightStatusBar)
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    else 0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                )
            } else {
                @Suppress("DEPRECATION")
                window.decorView.systemUiVisibility =
                    if (lightStatusBar) View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR else 0
            }
        }

        /* ========================= PUBLIC API (GIỮ NGUYÊN) ========================= */

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

        fun confirm(
            context: Context,
            title: String = "",
            message: String = "",
            onConfirm: DialogButton? = null,
            onCancel: DialogButton? = null
        ): DialogWrap {
            return confirm(context, title, message, null, onConfirm, onCancel)
        }

        fun confirm(
            context: Context,
            title: String = "",
            message: String = "",
            contentView: View? = null,
            onConfirm: DialogButton? = null,
            onCancel: DialogButton? = null
        ): DialogWrap {
            val view = getCustomDialogView(context, R.layout.dialog_confirm, title, message, contentView)
            val dialog = customDialog(context, view)

            view.findViewById<TextView>(R.id.btn_confirm)?.apply {
                text = onConfirm?.text ?: text
                setOnClickListener {
                    if (onConfirm == null || onConfirm.dismiss) dialog.dismiss()
                    onConfirm?.onClick?.run()
                }
            }

            view.findViewById<TextView>(R.id.btn_cancel)?.apply {
                text = onCancel?.text ?: text
                setOnClickListener {
                    if (onCancel == null || onCancel.dismiss) dialog.dismiss()
                    onCancel?.onClick?.run()
                }
            }

            return dialog
        }

        /* ========================= INTERNAL ========================= */

        private fun getCustomDialogView(
            context: Context,
            layout: Int,
            title: String,
            message: String,
            customView: View?
        ): View {
            val view = LayoutInflater.from(context).inflate(layout, null)

            view.findViewById<TextView>(R.id.confirm_title)?.apply {
                visibility = if (title.isEmpty()) View.GONE else View.VISIBLE
                text = title
            }

            view.findViewById<TextView>(R.id.confirm_message)?.apply {
                visibility = if (message.isEmpty()) View.GONE else View.VISIBLE
                text = message
            }

            customView?.let {
                view.findViewById<FrameLayout>(R.id.confirm_custom_view)?.addView(it)
            }

            return view
        }

        private fun openContinueAlert(
            context: Context,
            layout: Int,
            title: String,
            message: String,
            onConfirm: Runnable?,
            onCancel: Runnable?
        ): DialogWrap {
            val view = getCustomDialogView(context, layout, title, message, null)
            val dialog = customDialog(context, view)

            view.findViewById<View>(R.id.btn_confirm)?.setOnClickListener {
                dialog.dismiss()
                onConfirm?.run()
            }

            view.findViewById<View>(R.id.btn_cancel)?.setOnClickListener {
                dialog.dismiss()
                onCancel?.run()
            }

            return dialog
        }

        private fun setOutsideTouchDismiss(view: View, wrap: DialogWrap): DialogWrap {
            wrap.dialog.window?.decorView?.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP && wrap.isCancelable) {
                    val rect = Rect()
                    view.getGlobalVisibleRect(rect)
                    if (!rect.contains(event.rawX.toInt(), event.rawY.toInt())) {
                        wrap.dismiss()
                    }
                    true
                } else false
            }
            return wrap
        }

        /* ========================= DIALOG CORE ========================= */

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

            dialog.window?.let { window ->
                val dark = isNightMode(context)

                if (context is Activity) {
                    setWindowBlurBg(window, context)
                } else {
                    val bg = if (dark)
                        Color.argb(255, 18, 18, 18)
                    else
                        Color.argb(255, 245, 245, 245)
                    window.setBackgroundDrawable(bg.toDrawable())
                }

                applySystemBars(window, !dark)

                window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
                )
            }

            return setOutsideTouchDismiss(view, DialogWrap(dialog).setCancelable(cancelable))
        }

        /* ========================= BLUR BACKGROUND ========================= */

        fun setWindowBlurBg(window: Window, activity: Activity) {
            val dark = isNightMode(activity)

            val blurDrawable =
                if (!disableBlurBg)
                    FastBlurUtility.getBlurBackgroundDrawer(activity)
                        ?.toDrawable(activity.resources)
                else null

            if (blurDrawable != null) {
                window.setBackgroundDrawable(blurDrawable)
            } else {
                val color = if (dark)
                    Color.argb(255, 18, 18, 18)
                else
                    Color.argb(255, 245, 245, 245)
                window.setBackgroundDrawable(color.toDrawable())
            }

            applySystemBars(window, !dark)
        }
    }
}
