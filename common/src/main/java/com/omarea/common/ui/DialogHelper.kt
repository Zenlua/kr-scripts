package com.omarea.common.ui

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.util.TypedValue
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
        val context: Context = d.context
        private var mCancelable = true

        val isCancelable: Boolean
            get() = mCancelable

        fun setCancelable(cancelable: Boolean): DialogWrap {
            mCancelable = cancelable
            d.setCancelable(cancelable)
            return this
        }

        fun setOnDismissListener(listener: DialogInterface.OnDismissListener): DialogWrap {
            d.setOnDismissListener(listener)
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

        /* ================== CORE ================== */

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
                if (context is Activity) {
                    setWindowBlurBg(window, context)
                    applySystemBarAppearance(window, context)
                } else {
                    window.setBackgroundDrawableResource(android.R.color.transparent)
                }
            }

            return setOutsideTouchDismiss(view, DialogWrap(dialog).setCancelable(cancelable))
        }

        /* ================== THEME ================== */

        private fun isLightTheme(context: Context): Boolean {
            val value = TypedValue()
            context.theme.resolveAttribute(android.R.attr.isLightTheme, value, true)
            return value.data != 0
        }

        private fun applySystemBarAppearance(window: Window, context: Context) {
            val light = isLightTheme(context)

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

        /* ================== BACKGROUND ================== */

        fun setWindowBlurBg(window: Window, activity: Activity) {
            val wallpaperMode =
                activity.window.attributes.flags and
                        WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER != 0

            val blurDrawable =
                if (!disableBlurBg && !wallpaperMode)
                    FastBlurUtility.getBlurBackgroundDrawer(activity)
                else null

            if (blurDrawable != null) {
                window.setBackgroundDrawable(blurDrawable.toDrawable(activity.resources))
            } else {
                val color = activity.getColor(R.color.dialog_bg_color)
                window.setBackgroundDrawable(color.toDrawable())
            }
        }

        /* ================== OUTSIDE TOUCH ================== */

        private fun setOutsideTouchDismiss(
            view: View,
            dialogWrap: DialogWrap
        ): DialogWrap {

            dialogWrap.dialog.window?.decorView?.setOnTouchListener { _, event ->
                if (event.action == MotionEvent.ACTION_UP) {
                    val loc = IntArray(2)
                    view.getLocationOnScreen(loc)

                    val left = loc[0]
                    val top = loc[1]
                    val right = left + view.width
                    val bottom = top + view.height

                    val x = event.rawX.toInt()
                    val y = event.rawY.toInt()

                    if ((x < left || x > right || y < top || y > bottom)
                        && dialogWrap.isCancelable
                    ) {
                        dialogWrap.dismiss()
                    }
                    true
                } else false
            }
            return dialogWrap
        }

        /* ================== SIMPLE DIALOG APIs ================== */

        private fun getCustomDialogView(
            context: Context,
            layout: Int,
            title: String = "",
            message: String = "",
            contentView: View? = null
        ): View {

            val view = LayoutInflater.from(context).inflate(layout, null)

            view.findViewById<TextView?>(R.id.confirm_title)?.apply {
                if (title.isEmpty()) visibility = View.GONE else text = title
            }

            view.findViewById<TextView?>(R.id.confirm_message)?.apply {
                if (message.isEmpty()) visibility = View.GONE else text = message
            }

            contentView?.let {
                view.findViewById<FrameLayout>(R.id.confirm_custom_view)?.addView(it)
            }

            return view
        }

        fun alert(
            context: Context,
            title: String = "",
            message: String = "",
            onConfirm: Runnable? = null
        ): DialogWrap {
            val view = getCustomDialogView(context, R.layout.dialog_alert, title, message)
            val dialog = customDialog(context, view)
            view.findViewById<View>(R.id.btn_confirm).setOnClickListener {
                dialog.dismiss()
                onConfirm?.run()
            }
            return dialog
        }
    }
}
