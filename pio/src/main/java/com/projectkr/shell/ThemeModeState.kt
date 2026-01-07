package com.projectkr.shell

import android.Manifest
import android.app.Activity
import android.app.UiModeManager
import android.app.WallpaperManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.core.content.PermissionChecker
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.omarea.common.ui.ThemeMode

object ThemeModeState {

    private val themeMode = ThemeMode()

    private fun hasPermission(activity: Activity, permission: String): Boolean =
        PermissionChecker.checkSelfPermission(
            activity,
            permission
        ) == PermissionChecker.PERMISSION_GRANTED

    private fun canUseWallpaper(activity: Activity): Boolean =
        ThemeConfig(activity).getAllowTransparentUI() &&
                hasPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) &&
                hasPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)

    fun switchTheme(activity: Activity? = null): ThemeMode {
        activity ?: return themeMode

        val uiModeManager =
            activity.applicationContext.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        val isNight = uiModeManager.nightMode == UiModeManager.MODE_NIGHT_YES

        if (canUseWallpaper(activity)) {
            applyWallpaperTheme(activity, isNight)
        } else {
            applyNormalTheme(activity, isNight)
        }

        if (!themeMode.isDarkMode) {
            applyLightSystemBar(activity)
        }

        return themeMode
    }

    private fun applyWallpaperTheme(activity: Activity, isNight: Boolean) {
        val wallpaperManager = WallpaperManager.getInstance(activity)
        val wallpaperInfo = wallpaperManager.wallpaperInfo

        themeMode.isDarkMode = isNight

        activity.setTheme(
            if (isNight) {
                R.style.AppThemeWallpaper
            } else {
                R.style.AppThemeWallpaperLight
            }
        )

        if (wallpaperInfo?.packageName != null) {
            activity.window.addFlags(android.view.WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
        } else {
            activity.window.setBackgroundDrawable(wallpaperManager.drawable)
        }
    }

    private fun applyNormalTheme(activity: Activity, isNight: Boolean) {
        themeMode.isDarkMode = isNight
        themeMode.isLightStatusBar = !isNight

        if (isNight) {
            activity.setTheme(R.style.AppThemeDark)
        }
    }

    /**
     * Light status bar + navigation bar
     * Không dùng API deprecated
     * Chuẩn Android 11+
     */
    private fun applyLightSystemBar(activity: Activity) {
        val window = activity.window

        // Edge-to-edge (API 21+)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        val controller = WindowInsetsControllerCompat(
            window,
            window.decorView
        )

        // Icon status bar màu đen
        controller.isAppearanceLightStatusBars = true

        // Icon navigation bar màu đen (Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            controller.isAppearanceLightNavigationBars = true
        }
    }

    private fun isDarkColor(drawable: Drawable): Boolean {
        val bitmap = (drawable as? BitmapDrawable)?.bitmap ?: return false

        val h = bitmap.height - 1
        val w = bitmap.width - 1
        val sample = if (h > 24 && w > 24) 24 else 1

        var dark = 0
        var light = 0

        repeat(sample + 1) { i ->
            val x = w / sample * i
            val y = h / sample * i
            val pixel = bitmap.getPixel(x, y)

            if (
                Color.red(pixel) > 150 &&
                Color.green(pixel) > 150 &&
                Color.blue(pixel) > 150
            ) {
                light++
            } else {
                dark++
            }
        }
        return dark > light
    }

    fun getThemeMode(): ThemeMode = themeMode
}
