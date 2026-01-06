package com.omarea.krscript.config

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.omarea.krscript.R
import com.omarea.krscript.model.ClickableNode

class IconPathAnalysis {

    // Lấy logo với fallback mặc định
    fun loadLogo(context: Context, clickableNode: ClickableNode): Drawable =
        loadLogo(context, clickableNode, true) ?: defaultLogo(context)

    // Lấy logo với tùy chọn fallback
    fun loadLogo(context: Context, clickableNode: ClickableNode, useDefault: Boolean): Drawable? =
        listOf(clickableNode.logoPath, clickableNode.iconPath)
            .firstNotNullOfOrNull { it.takeIf(String::isNotEmpty)?.let { path -> loadFromPath(context, path, clickableNode.pageConfigDir) } }
            ?: if (useDefault) defaultLogo(context) else null

    // Lấy icon
    fun loadIcon(context: Context, clickableNode: ClickableNode): Drawable? =
        clickableNode.iconPath.takeIf(String::isNotEmpty)?.let { loadFromPath(context, it, clickableNode.pageConfigDir) }

    // Load Drawable từ path
    private fun loadFromPath(context: Context, path: String, pageConfigDir: String): Drawable? =
        PathAnalysis(context, pageConfigDir).parsePath(path)?.use { stream ->
            BitmapFactory.decodeStream(stream)?.let(BitmapDrawable::new)
        }

    // Drawable mặc định
    private fun defaultLogo(context: Context): Drawable =
        context.getDrawable(R.drawable.kr_shortcut_logo)!!
}
