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
import android.view.WindowInsetsController
import android.view.WindowManager
import androidx.core.content.PermissionChecker
import com.omarea.common.ui.ThemeMode

object ThemeModeState {
    private var themeMode: ThemeMode = ThemeMode()

    private fun checkPermission(context: Context, permission: String): Boolean =
        PermissionChecker.checkSelfPermission(context, permission) ==
                PermissionChecker.PERMISSION_GRANTED

    private fun isSystemDarkMode(context: Context): Boolean {
        val nightModeFlags =
            context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == Configuration.UI_MODE_NIGHT_YES
    }

    fun switchTheme(activity: Activity? = null): ThemeMode {
        if (activity != null) {
            val nightMode = isSystemDarkMode(activity)

            if (
                ThemeConfig(activity).getAllowTransparentUI() &&
                checkPermission(activity, Manifest.permission.READ_EXTERNAL_STORAGE) &&
                checkPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE)
            ) {
                val wallpaper = WallpaperManager.getInstance(activity)
                val wallpaperInfo = wallpaper.wallpaperInfo

                if (nightMode) {
                    themeMode.isDarkMode = true
                    activity.setTheme(R.style.AppThemeWallpaper)
                } else {
                    themeMode.isDarkMode = false
                    activity.setTheme(R.style.AppThemeWallpaperLight)
                }

                if (wallpaperInfo != null && wallpaperInfo.packageName != null) {
                    activity.window.addFlags(WindowManager.LayoutParams.FLAG_SHOW_WALLPAPER)
                } else {
                    activity.window.setBackgroundDrawable(wallpaper.drawable)
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

            applySystemBarMode(activity, themeMode.isDarkMode)
        }
        return themeMode
    }

    private fun applySystemBarMode(activity: Activity, darkMode: Boolean) {
        val window = activity.window

        window.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS)
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val controller = window.insetsController ?: return

            if (darkMode) {
                controller.setSystemBarsAppearance(
                    0,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            } else {
                controller.setSystemBarsAppearance(
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS,
                    WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or
                            WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
                )
            }
        } else {
            var flags = View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN or
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE

            if (!darkMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                flags = flags or View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }

            if (!darkMode && Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flags = flags or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            }

            window.decorView.systemUiVisibility = flags
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
