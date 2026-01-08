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
import android.view.WindowInsetsController  // ← 必须添加这一行
import android.view.WindowManager
import androidx.core.content.PermissionChecker
import com.omarea.common.ui.ThemeMode

object ThemeModeState {
    private var themeMode: ThemeMode = ThemeMode()

    private fun checkPermission(context: Context, permission: String): Boolean =
        PermissionChecker.checkSelfPermission(context, permission) == PermissionChecker.PERMISSION_GRANTED

    /** 当前系统是否处于暗色模式（不使用已废弃的 UiModeManager） */
    private fun isSystemInDarkMode(context: Context): Boolean {
        val uiMode = context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return uiMode == Configuration.UI_MODE_NIGHT_YES
    }

    /** 统一处理状态栏和导航栏的亮/暗图标（支持 Android 11+ 的新 API） */
    private fun applySystemBarsAppearance(window: Window, isDark: Boolean) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11 (API 30) 及以上
            window.insetsController?.let { controller ->
                // 先清除旧的 appearance
                controller.setSystemBarsAppearance(
                    0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
                // Light 模式下使用亮色图标（白色背景 + 黑色图标）
                if (!isDark) {
                    controller.setSystemBarsAppearance(
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                        WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                    )
                }
            }
        } else {
            // Android 6.0 ~ 10
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

        // 确保可以绘制系统栏背景
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
            // 使用壁纸作为背景
            activity.setTheme(if (isDark) R.style.AppThemeWallpaper else R.style.AppThemeWallpaperLight)

            val wallpaperManager = WallpaperManager.getInstance(activity)
            val wallpaperInfo = wallpaperManager.wallpaperInfo

            if (wallpaperInfo != null && wallpaperInfo.packageName != null) {
                // 动态壁纸
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            } else {
                val wallpaperDrawable = wallpaperManager.drawable
                activity.window.setBackgroundDrawable(wallpaperDrawable)
            }

            // 这里统一按照系统暗色模式设置图标颜色（最安全）
            applySystemBarsAppearance(activity.window, isDark)
        } else {
            // 普通不透明主题
            if (isDark) {
                activity.setTheme(R.style.AppThemeDark)
                themeMode.isLightStatusBar = false
            } else {
                // ← 这里修复 Unresolved reference 'AppThemeLight'
                // 请根据你项目中实际的 light 主题替换下面的名字
                activity.setTheme(R.style.AppTheme)  // 常见替代：AppTheme、MainTheme、Theme_AppCompat_Light_NoActionBar 等
                themeMode.isLightStatusBar = true
            }

            applySystemBarsAppearance(activity.window, isDark)
        }

        return themeMode
    }

    /** 可选：根据壁纸颜色自动判断是否使用亮色状态栏图标 */
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
