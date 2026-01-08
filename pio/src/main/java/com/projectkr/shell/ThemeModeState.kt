package com.projectkr.shell

import android.Manifest
import android.app.Activity
import android.app.WallpaperManager
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.core.content.PermissionChecker
import com.omarea.common.ui.ThemeMode

object ThemeModeState {
    private var themeMode: ThemeMode = ThemeMode()

    private fun checkPermission(context: Context, permission: String): Boolean =
        PermissionChecker.checkSelfPermission(context, permission) == PermissionChecker.PERMISSION_GRANTED

    /** Xác định hệ thống đang ở dark mode hay không – cách hiện đại, không dùng UiModeManager */
    private fun isSystemInDarkMode(context: Context): Boolean {
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }

    /** Áp dụng appearance đúng cho status bar và navigation bar trên mọi API */
    private fun applySystemBarsAppearance(window: Window, isDark: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ (API 30+)
            window.insetsController?.let { controller ->
                // Xóa appearance cũ
                controller.setSystemBarsAppearance(
                    0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
                // Nếu là light mode → dùng icon sáng (light status/navigation bar)
                if (!isDark) {
                    controller.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    )
                }
            }
        } else {
            // Android 6.0 đến 10 (API 23-29)
            @Suppress("DEPRECATION")
            var flags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            if (!isDark) {
                flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                } else {
                    flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
                }
            }
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = flags
        }

        // Đảm bảo vẽ nền system bar
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
    }

    fun switchTheme(activity: Activity? = null): ThemeMode {
        if (activity == null) return themeMode

        val isDark = isSystemInDarkMode(activity)
        themeMode.isDarkMode = isDark

        val config = ThemeConfig(activity)
        val allowTransparent = config.getAllowTransparentUI() &&
                checkPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) &&
                checkPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)

        if (allowTransparent) {
            // Dùng wallpaper làm nền
            activity.setTheme(if (isDark) R.style.AppThemeWallpaper else R.style.AppThemeWallpaperLight)

            val wallpaperManager = WallpaperManager.getInstance(activity)
            val wallpaperInfo = wallpaperManager.wallpaperInfo

            if (wallpaperInfo != null && wallpaperInfo.packageName != null) {
                // Dynamic (live) wallpaper
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            } else {
                val wallpaperDrawable = wallpaperManager.drawable
                activity.window.setBackgroundDrawable(wallpaperDrawable)
            }

            // Với nền trong suốt, status bar thường nên theo màu wallpaper
            // Bạn có thể mở comment đoạn dưới nếu muốn tự động detect màu wallpaper
            // val isWallpaperDark = isDarkColor(wallpaperManager.drawable)
            // applySystemBarsAppearance(activity.window, isWallpaperDark)

            // Hiện tại giữ theo hệ thống (sạch sẽ hơn)
            applySystemBarsAppearance(activity.window, isDark)
        } else {
            // Không dùng wallpaper → theme bình thường
            if (isDark) {
                activity.setTheme(R.style.AppThemeDark)
                themeMode.isLightStatusBar = false
            } else {
                activity.setTheme(R.style.AppThemeLight) // hoặc theme light mặc định của bạn
                themeMode.isLightStatusBar = true
            }

            applySystemBarsAppearance(activity.window, isDark)
        }

        return themeMode
    }

    /** Kiểm tra xem wallpaper có màu tối không (tùy chọn dùng để set light/dark status bar) */
    private fun isDarkColor(wallPaper: Drawable?): Boolean {
        if (wallPaper !is BitmapDrawable) return false
        val bitmap = wallPaper.bitmap
        if (bitmap.width <= 0 || bitmap.height <= 0) return false

        val h = bitmap.height - 1
        val w = bitmap.width - 1
        val pointCount = if (h > 24 && w > 24) 24 else 1

        var darkPoint = 0
        var lightPoint = 0

        for (i in 0..pointCount) {
            val x = w / pointCount * i
            val y = h / pointCount * i
            val pixel = bitmap.getPixel(x, y)

            val red = Color.red(pixel)
            val green = Color.green(pixel)
            val blue = Color.blue(pixel)

            if (red > 150 && green > 150 && blue > 150) {
                lightPoint++
            } else {
                darkPoint++
            }
        }

        return darkPoint > lightPoint
    }

    fun getThemeMode(): ThemeMode = themeMode
}
