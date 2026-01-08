package com.omarea.common.ui

import android.app.Activity
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
        val isCancelable get() = mCancelable
        val isShowing get() = d.isShowing
        val context: Context get() = d.context

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

        /* ========================= DARK MODE CORE ========================= */

        private fun isNightMode(context: Context): Boolean {
            val mode = context.resources.configuration.uiMode and
                    Configuration.UI_MODE_NIGHT_MASK
            return mode == Configuration.UI_MODE_NIGHT_YES
        }

        private fun setSystemBars(window: Window, light: Boolean) {
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

        /* ========================= PUBLIC API ========================= */

        fun helpInfo(
            context: Context,
            title: String = "",
            message: String = "",
            onDismiss: Runnable? = null
        ): DialogWrap {
            val view = getCustomView(
                context,
                R.layout.dialog_help_info,
                title,
                message,
                null
            )

            val dialog = customDialog(context, view)

            view.findViewById<View>(R.id.btn_confirm)?.setOnClickListener {
                dialog.dismiss()
            }

            if (onDismiss != null) {
                dialog.setOnDismissListener { onDismiss.run() }
            }

            return dialog
        }

        fun confirm(
            context: Context,
            title: String = "",
            message: String = "",
            onConfirm: Runnable? = null,
            onCancel: Runnable? = null
        ): DialogWrap {
            return openConfirm(
                context,
                R.layout.dialog_confirm,
                title,
                message,
                onConfirm,
                onCancel
            )
        }

        /** ✅ KHÔI PHỤC API warning() */
        fun warning(
            context: Context,
            title: String = "",
            message: String = "",
            onConfirm: Runnable? = null,
            onCancel: Runnable? = null
        ): DialogWrap {
            return openConfirm(
                context,
                R.layout.dialog_warning,
                title,
                message,
                onConfirm,
                onCancel
            )
        }

        fun alert(
            context: Context,
            title: String = "",
            message: String = "",
            onConfirm: Runnable? = null
        ): DialogWrap {
            return openConfirm(
                context,
                R.layout.dialog_alert,
                title,
                message,
                onConfirm,
                null
            )
        }

        /* ========================= INTERNAL ========================= */

        private fun getCustomView(
            context: Context,
            layout: Int,
            title: String,
            message: String,
            custom: View?
        ): View {
            val view = LayoutInflater.from(context).inflate(layout, null)

            view.findViewById<TextView?>(R.id.confirm_title)?.apply {
                if (title.isEmpty()) visibility = View.GONE else text = title
            }

            view.findViewById<TextView?>(R.id.confirm_message)?.apply {
                if (message.isEmpty()) visibility = View.GONE else text = message
            }

            if (custom != null) {
                view.findViewById<FrameLayout?>(R.id.confirm_custom_view)
                    ?.addView(custom)
            }

            return view
        }

        private fun openConfirm(
            context: Context,
            layout: Int,
            title: String,
            message: String,
            onConfirm: Runnable?,
            onCancel: Runnable?
        ): DialogWrap {

            val view = getCustomView(context, layout, title, message, null)
            val dialog = customDialog(context, view)

            view.findViewById<View?>(R.id.btn_confirm)?.setOnClickListener {
                dialog.dismiss()
                onConfirm?.run()
            }

            view.findViewById<View?>(R.id.btn_cancel)?.setOnClickListener {
                dialog.dismiss()
                onCancel?.run()
            }

            return dialog
        }

        private fun setOutsideTouchDismiss(
            content: View,
            wrap: DialogWrap
        ): DialogWrap {

            val root = wrap.dialog.window?.decorView ?: return wrap

            root.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP && wrap.isCancelable) {
                    val rect = Rect()
                    content.getGlobalVisibleRect(rect)
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

            val dialog = AlertDialog.Builder(
                context,
                R.style.custom_alert_dialog
            )
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

                setSystemBars(window, !dark)

                window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT
                )
            }

            return setOutsideTouchDismiss(
                view,
                DialogWrap(dialog).setCancelable(cancelable)
            )
        }

        /* ========================= ANIMATION API (KHÔI PHỤC) ========================= */

        fun animDialog(dialog: AlertDialog?): DialogWrap? {
            if (dialog != null && !dialog.isShowing) {
                dialog.window?.setWindowAnimations(R.style.windowAnim)
                dialog.show()
            }
            return dialog?.let { DialogWrap(it) }
        }

        fun animDialog(builder: AlertDialog.Builder): DialogWrap {
            val dialog = builder.create()
            return animDialog(dialog)!!
        }

        /* ========================= BLUR BACKGROUND ========================= */

        fun setWindowBlurBg(window: Window, activity: Activity) {
            val dark = isNightMode(activity)

            val blurDrawable =
                if (!disableBlurBg)
                    FastBlurUtility
                        .getBlurBackgroundDrawer(activity)
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

            setSystemBars(window, !dark)
        }
    }
}
