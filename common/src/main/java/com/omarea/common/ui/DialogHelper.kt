package com.omarea.common.ui

import android.app.Activity
import android.content.Context
import android.content.DialogInterface
import android.content.res.Configuration
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

        // === THÊM HÀM BỊ THIẾU: setOutsideTouchDismiss ===
        private fun setOutsideTouchDismiss(view: View, dialogWrap: DialogWrap): DialogWrap {
            val dialog = dialogWrap.dialog
            val rootView = dialog.window?.decorView
            rootView?.setOnTouchListener { _, event ->
                if (event?.action == MotionEvent.ACTION_UP) {
                    val x = event.x.toInt()
                    val y = event.y.toInt()
                    val rect = Rect()
                    view.getGlobalVisibleRect(rect)
                    if (!rect.contains(x, y)) {
                        if (dialogWrap.isCancelable) {
                            dialogWrap.dismiss()
                        }
                    }
                    true
                } else {
                    false
                }
            }
            return dialogWrap
        }

        // === Các hàm tiện ích cơ bản (giữ nguyên như code gốc của bạn) ===
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
                view.findViewById<FrameLayout?>(R.id.confirm_custom_view)?.addView(it)
            }
            return view
        }

        // === customDialog – đã có sẵn trong code bạn gửi ===
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

        // === Xác định dark mode mà không dùng UiModeManager ===
        private fun isNightMode(context: Context): Boolean {
            val currentNightMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
            return currentNightMode == Configuration.UI_MODE_NIGHT_YES
        }

        // === Áp dụng appearance cho status bar + navigation bar ===
        private fun applySystemBarsAppearance(window: Window, context: Context) {
            val isDark = isNightMode(context)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                window.insetsController?.let { controller ->
                    // Xóa appearance cũ trước
                    controller.setSystemBarsAppearance(
                        0,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    )
                    // Nếu light mode → dùng icon sáng (light icons)
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

        // === Blur background + system bars ===
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

                applySystemBarsAppearance(this, activity)
            }
        }

        // === Các hàm dialog phổ biến (bạn có thể thêm đầy đủ hơn nếu cần) ===
        fun helpInfo(context: Context, message: String, onDismiss: Runnable? = null): DialogWrap {
            return helpInfo(context, context.getString(R.string.help_title), message, onDismiss)
        }

        fun helpInfo(context: Context, title: String, message: String, onDismiss: Runnable? = null): DialogWrap {
            val view = getCustomDialogView(context, R.layout.dialog_help_info, title, message)
            val dialog = customDialog(context, view)
            view.findViewById<View>(R.id.btn_confirm).setOnClickListener {
                dialog.dismiss()
                onDismiss?.run()
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
            val view = getCustomDialogView(context, R.layout.dialog_confirm, title, message)
            val dialog = customDialog(context, view)
            view.findViewById<View?>(R.id.btn_cancel)?.setOnClickListener {
                dialog.dismiss()
                onCancel?.run()
            }
            view.findViewById<View?>(R.id.btn_confirm)?.setOnClickListener {
                dialog.dismiss()
                onConfirm?.run()
            }
            return dialog
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

        // Bạn có thể tiếp tục thêm các hàm confirm overload khác, warning, v.v. nếu cần
    }
}
