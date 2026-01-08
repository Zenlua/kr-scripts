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
import android.view.View
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.core.content.PermissionChecker
import com.omarea.common.ui.ThemeMode

object ThemeModeState {

    private var themeMode: ThemeMode = ThemeMode()

    private fun checkPermission(context: Context, permission: String): Boolean {
        return PermissionChecker.checkSelfPermission(
            context,
            permission
        ) == PermissionChecker.PERMISSION_GRANTED
    }

    fun switchTheme(activity: Activity? = null): ThemeMode {
        if (activity == null) return themeMode

        val uiModeManager =
            activity.applicationContext.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager
        val nightMode = uiModeManager.nightMode == UiModeManager.MODE_NIGHT_YES

        val allowWallpaper =
            ThemeConfig(activity).getAllowTransparentUI() &&
                    checkPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) &&
                    checkPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)

        if (allowWallpaper) {
            val wallpaperManager = WallpaperManager.getInstance(activity)
            val wallpaperInfo = wallpaperManager.wallpaperInfo

            if (nightMode) {
                themeMode.isDarkMode = true
                activity.setTheme(R.style.AppThemeWallpaper)
            } else {
                themeMode.isDarkMode = false
                activity.setTheme(R.style.AppThemeWallpaperLight)
            }

            if (wallpaperInfo != null) {
                activity.window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
            } else {
                activity.window.setBackgroundDrawable(wallpaperManager.drawable)
            }
        } else {
            if (nightMode) {
                themeMode.isDarkMode = true
                themeMode.isLightStatusBar = false
                activity.setTheme(R.style.AppThemeDark)
            } else {
                themeMode.isDarkMode = false
            }
        }

        applySystemBarStyle(activity, themeMode.isDarkMode)
        return themeMode
    }

    private fun applySystemBarStyle(activity: Activity, darkMode: Boolean) {
        val window = activity.window

        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController ?: return

            if (!darkMode) {
                controller.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
                themeMode.isLightStatusBar = true
            } else {
                controller.setSystemBarsAppearance(0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility =
                if (!darkMode) {
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                            View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or
                            View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
                } else {
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                }
        }
    }

    private fun isDarkColor(wallPaper: Drawable): Boolean {
        val bitmap = (wallPaper as BitmapDrawable).bitmap
        val h = bitmap.height - 1
        val w = bitmap.width - 1

        var darkPoint = 0
        var lightPoint = 0

        val pointCount = if (h > 24 && w > 24) 24 else 1

        for (i in 0..pointCount) {
            val y = h / pointCount * i
            val x = w / pointCount * i
            val pixel = bitmap.getPixel(x, y)

            val r = Color.red(pixel)
            val g = Color.green(pixel)
            val b = Color.blue(pixel)

            if (r > 150 && g > 150 && b > 150) {
                lightPoint++
            } else {
                darkPoint++
            }
        }
        return darkPoint > lightPoint
    }

    fun getThemeMode(): ThemeMode = themeMode
}
