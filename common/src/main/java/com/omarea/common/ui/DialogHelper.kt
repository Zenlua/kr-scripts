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

    class DialogWrap(private val dialog: AlertDialog) {
        val context: Context = dialog.context
        private var mCancelable = true
        val isCancelable get() = mCancelable

        fun setCancelable(cancelable: Boolean): DialogWrap {
            mCancelable = cancelable
            dialog.setCancelable(cancelable)
            return this
        }

        fun setOnDismissListener(listener: DialogInterface.OnDismissListener): DialogWrap {
            dialog.setOnDismissListener(listener)
            return this
        }

        fun dismiss() = try { dialog.dismiss() } catch (_: Exception) {}
        fun hide() = try { dialog.hide() } catch (_: Exception) {}
        val isShowing: Boolean get() = dialog.isShowing
        val alertDialog: AlertDialog get() = dialog
    }

    companion object {
        var disableBlurBg = false

        fun animDialog(dialog: AlertDialog?): DialogWrap? {
            dialog?.let {
                if (!it.isShowing) it.window?.setWindowAnimations(R.style.windowAnim)
                it.show()
            }
            return dialog?.let { DialogWrap(it) }
        }

        fun animDialog(builder: AlertDialog.Builder): DialogWrap {
            val dialog = builder.create()
            animDialog(dialog)
            return DialogWrap(dialog)
        }

        fun helpInfo(
            context: Context,
            title: String = context.getString(R.string.help_title),
            message: String,
            onDismiss: Runnable? = null
        ): DialogWrap {
            val view = LayoutInflater.from(context).inflate(R.layout.dialog_help_info, null)

            view.findViewById<TextView>(R.id.confirm_title)?.apply {
                if (title.isEmpty()) visibility = View.GONE else text = title
            }

            view.findViewById<TextView>(R.id.confirm_message)?.apply {
                if (message.isEmpty()) visibility = View.GONE else text = message
            }

            val dialogWrap = customDialog(context, view, onDismiss == null)
            view.findViewById<View>(R.id.btn_confirm)?.setOnClickListener {
                dialogWrap.dismiss()
                onDismiss?.run()
            }

            return dialogWrap
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
            val dialogWrap = customDialog(context, view)

            view.findViewById<TextView>(R.id.btn_confirm)?.apply {
                text = onConfirm?.text ?: text
                setOnClickListener {
                    if (onConfirm?.dismiss != false) dialogWrap.dismiss()
                    onConfirm?.onClick?.run()
                }
            }

            view.findViewById<TextView>(R.id.btn_cancel)?.apply {
                text = onCancel?.text ?: text
                setOnClickListener {
                    if (onCancel?.dismiss != false) dialogWrap.dismiss()
                    onCancel?.onClick?.run()
                }
            }

            return dialogWrap
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
            val dialog = dialogWrap.alertDialog
            dialog.window?.decorView?.setOnTouchListener { _, event ->
                if (event?.action == MotionEvent.ACTION_UP) {
                    val rect = Rect()
                    view.getGlobalVisibleRect(rect)
                    if (!rect.contains(event.x.toInt(), event.y.toInt()) && dialogWrap.isCancelable) {
                        dialogWrap.dismiss()
                    }
                    true
                } else false
            }
            return dialogWrap
        }

        fun customDialog(context: Context, view: View, cancelable: Boolean = true): DialogWrap {
            val useBlur = context is Activity && context.window.attributes.flags and WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER == 0
            val dialogBuilder = if (useBlur) AlertDialog.Builder(context, R.style.custom_alert_dialog) else AlertDialog.Builder(context)
            val dialog = dialogBuilder.setView(view).setCancelable(cancelable).create()

            if (context is Activity) {
                dialog.show()
                dialog.window?.run {
                    setWindowBlurBg(this, context)
                    decorView.systemUiVisibility = context.window.decorView.systemUiVisibility
                }
            } else {
                dialog.show()
                dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
            }

            return setOutsideTouchDismiss(view, DialogWrap(dialog).setCancelable(cancelable))
        }

        private fun isNightMode(context: Context): Boolean {
            return when (AppCompatDelegate.getDefaultNightMode()) {
                AppCompatDelegate.MODE_NIGHT_YES -> true
                AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM, AppCompatDelegate.MODE_NIGHT_UNSPECIFIED -> {
                    val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
                    uiModeManager.nightMode == UiModeManager.MODE_NIGHT_YES
                }
                else -> false
            }
        }

        fun setWindowBlurBg(window: Window, activity: Activity) {
            val wallpaperMode = activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER != 0
            val blurBitmap = if (disableBlurBg || wallpaperMode) null else FastBlurUtility.getBlurBackgroundDrawer(activity)

            window.run {
                setBackgroundDrawable(blurBitmap?.toDrawable(activity.resources) ?: run {
                    val bgColor = getWindowBackground(activity)
                    if (bgColor == Color.TRANSPARENT) {
                        if (activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_DIM_BEHIND != 0) {
                            setDimAmount(0.9f)
                            Color.TRANSPARENT.toDrawable()
                        } else {
                            val color = if (wallpaperMode || isNightMode(activity)) Color.argb(255, 18, 18, 18) else Color.argb(255, 245, 245, 245)
                            color.toDrawable()
                        }
                    } else bgColor.toDrawable()
                })
            }
        }

        private fun getWindowBackground(context: Context, defaultColor: Int = Color.TRANSPARENT): Int {
            val typedArray = context.obtainStyledAttributes(intArrayOf(android.R.attr.background))
            val color = typedArray.getColor(0, defaultColor)
            typedArray.recycle()
            return color
        }
    }
}
