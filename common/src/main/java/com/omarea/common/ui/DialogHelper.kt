package com.omarea.common.ui

import android.app.Activity
import android.app.UiModeManager
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
import androidx.core.graphics.drawable.toDrawable
import com.omarea.common.R

class DialogHelper {

    class DialogButton(val text: String, val onClick: Runnable? = null, val dismiss: Boolean = true)

    class DialogWrap(private val d: AlertDialog) {
        val dialog: AlertDialog get() = d
        val context: Context get() = d.context

        private var mCancelable = true
        val isCancelable: Boolean get() = mCancelable

        fun setCancelable(cancelable: Boolean) = apply {
            mCancelable = cancelable
            d.setCancelable(cancelable)
        }

        fun setOnDismissListener(listener: DialogInterface.OnDismissListener) = apply {
            d.setOnDismissListener(listener)
        }

        fun dismiss() = runCatching { d.dismiss() }
        fun hide() = runCatching { d.hide() }
        val isShowing: Boolean get() = d.isShowing
    }

    companion object {
        var disableBlurBg = false

        /** Show dialog with animation */
        fun animDialog(dialog: AlertDialog?): DialogWrap? {
            dialog?.takeIf { !it.isShowing }?.window?.setWindowAnimations(R.style.windowAnim)
            dialog?.show()
            return dialog?.let { DialogWrap(it) }
        }

        fun animDialog(builder: AlertDialog.Builder): DialogWrap {
            val dialog = builder.create()
            animDialog(dialog)
            return DialogWrap(dialog)
        }

        /** HELP INFO */
        fun helpInfo(context: Context, message: String, onDismiss: Runnable? = null) =
            helpInfo(context, context.getString(R.string.help_title), message, onDismiss)

        fun helpInfo(
            context: Context,
            title: String,
            message: String,
            onDismiss: Runnable? = null
        ): DialogWrap {
            val view = LayoutInflater.from(context).inflate(R.layout.dialog_help_info, null)
            view.findViewById<TextView>(R.id.confirm_title)?.let { tv ->
                tv.text = title.takeIf { it.isNotEmpty() } ?: ""
                tv.visibility = if (title.isNotEmpty()) View.VISIBLE else View.GONE
            }
            view.findViewById<TextView>(R.id.confirm_message)?.let { tv ->
                tv.text = message.takeIf { it.isNotEmpty() } ?: ""
                tv.visibility = if (message.isNotEmpty()) View.VISIBLE else View.GONE
            }

            val dialog = customDialog(context, view, onDismiss == null)
            view.findViewById<View>(R.id.btn_confirm)?.setOnClickListener {
                dialog.dismiss()
                onDismiss?.run()
            }

            return dialog
        }

        fun helpInfo(
            context: Context,
            title: String = "",
            message: String = "",
            contentView: View,
            onConfirm: Runnable? = null
        ): DialogWrap {
            val view = getCustomDialogView(context, R.layout.dialog_help_info, title, message, contentView)
            val dialog = customDialog(context, view)
            view.findViewById<View>(R.id.btn_confirm)?.setOnClickListener {
                dialog.dismiss()
                onConfirm?.run()
            }
            return dialog
        }

        /** CONFIRM / WARNING / ALERT */
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
                    if (onConfirm?.dismiss != false) dialog.dismiss()
                    onConfirm?.onClick?.run()
                }
            }

            view.findViewById<TextView>(R.id.btn_cancel)?.apply {
                text = onCancel?.text ?: text
                setOnClickListener {
                    if (onCancel?.dismiss != false) dialog.dismiss()
                    onCancel?.onClick?.run()
                }
            }

            return dialog
        }

        fun warning(
            context: Context,
            title: String = "",
            message: String = "",
            onConfirm: Runnable? = null,
            onCancel: Runnable? = null
        ) = openContinueAlert(context, R.layout.dialog_warning, title, message, onConfirm, onCancel)

        fun alert(
            context: Context,
            title: String = "",
            message: String = "",
            contentView: View? = null,
            onConfirm: Runnable? = null
        ): DialogWrap {
            val view = if (contentView != null)
                getCustomDialogView(context, R.layout.dialog_alert, title, message, contentView)
            else getCustomDialogView(context, R.layout.dialog_alert, title, message)

            val dialog = customDialog(context, view)
            view.findViewById<View>(R.id.btn_confirm)?.setOnClickListener {
                dialog.dismiss()
                onConfirm?.run()
            }
            return dialog
        }

        /** CUSTOM DIALOG VIEW */
        private fun getCustomDialogView(
            context: Context,
            layout: Int,
            title: String = "",
            message: String = "",
            contentView: View? = null
        ): View {
            val view = LayoutInflater.from(context).inflate(layout, null)
            view.findViewById<TextView>(R.id.confirm_title)?.let {
                it.text = title
                it.visibility = if (title.isEmpty()) View.GONE else View.VISIBLE
            }
            view.findViewById<TextView>(R.id.confirm_message)?.let {
                it.text = message
                it.visibility = if (message.isEmpty()) View.GONE else View.VISIBLE
            }
            contentView?.let {
                view.findViewById<FrameLayout>(R.id.confirm_custom_view)?.addView(it)
            }
            return view
        }

        /** DIALOG UTILS */
        private fun openContinueAlert(
            context: Context,
            layout: Int,
            title: String = "",
            message: String = "",
            onConfirm: Runnable? = null,
            onCancel: Runnable? = null
        ): DialogWrap {
            val view = getCustomDialogView(context, layout, title, message)
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

        private fun setOutsideTouchDismiss(view: View, dialogWrap: DialogWrap): DialogWrap {
            dialogWrap.dialog.window?.decorView?.setOnTouchListener { _, event ->
                if (event?.action == MotionEvent.ACTION_UP) {
                    val rect = Rect().apply { view.getGlobalVisibleRect(this) }
                    if (!rect.contains(event.x.toInt(), event.y.toInt()) && dialogWrap.isCancelable) {
                        dialogWrap.dismiss()
                    }
                    true
                } else false
            }
            return dialogWrap
        }

        private fun getWindowBackground(context: Context, defaultColor: Int = Color.TRANSPARENT): Int =
            context.obtainStyledAttributes(intArrayOf(android.R.attr.background)).use { it.getColor(0, defaultColor) }

        private fun isNightMode(context: Context): Boolean {
            return when (AppCompatDelegate.getDefaultNightMode()) {
                AppCompatDelegate.MODE_NIGHT_YES -> true
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM,
                AppCompatDelegate.MODE_NIGHT_UNSPECIFIED -> {
                    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
                    uiModeManager.nightMode == UiModeManager.MODE_NIGHT_YES
                }
                else -> false
            }
        }

        fun customDialog(context: Context, view: View, cancelable: Boolean = true): DialogWrap {
            val useBlur = context is Activity && context.window.attributes.flags and WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER == 0

            val builder = if (useBlur) AlertDialog.Builder(context, R.style.custom_alert_dialog) else AlertDialog.Builder(context)
            val dialog = builder.setView(view).setCancelable(cancelable).create()

            if (context is Activity) {
                dialog.show()
                dialog.window?.run { 
                    setWindowBlurBg(this, context)
                    decorView.systemUiVisibility = context.window.decorView.systemUiVisibility
                }
            } else {
                dialog.show()
                dialog.window?.run {
                    setWindowAnimations(R.style.windowAnim2)
                    setBackgroundDrawableResource(android.R.color.transparent)
                }
            }

            return setOutsideTouchDismiss(view, DialogWrap(dialog).setCancelable(cancelable))
        }

        fun setWindowBlurBg(window: Window, activity: Activity) {
            val wallpaperMode = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER != 0
            val blurBitmap = if (disableBlurBg || wallpaperMode) null else FastBlurUtility.getBlurBackgroundDrawer(activity)

            window.run {
                if (blurBitmap != null) {
                    setBackgroundDrawable(blurBitmap.toDrawable(activity.resources))
                } else {
                    val bg = runCatching { getWindowBackground(activity) }.getOrElse { Color.argb(255, 245, 245, 245) }
                    setBackgroundDrawable(if (bg == Color.TRANSPARENT) {
                        if (isNightMode(activity)) Color.argb(255, 18, 18, 18).toDrawable()
                        else Color.argb(255, 245, 245, 245).toDrawable()
                    } else bg.toDrawable())
                }
            }
        }
    }
}