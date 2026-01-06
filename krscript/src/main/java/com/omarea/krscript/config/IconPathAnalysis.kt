package com.omarea.krscript.config

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import com.omarea.krscript.R
import com.omarea.krscript.model.ClickableNode

class IconPathAnalysis {

    // Lấy logo với fallback về mặc định
    fun loadLogo(context: Context, clickableNode: ClickableNode): Drawable =
        loadLogo(context, clickableNode, true) ?: context.getDrawable(R.drawable.kr_shortcut_logo)!!

    // Lấy logo với tùy chọn fallback
    fun loadLogo(context: Context, clickableNode: ClickableNode, useDefault: Boolean): Drawable? {
        return clickableNode.logoPath.takeIf { it.isNotEmpty() }?.let { loadFromPath(context, it, clickableNode.pageConfigDir) }
            ?: clickableNode.iconPath.takeIf { it.isNotEmpty() }?.let { loadFromPath(context, it, clickableNode.pageConfigDir) }
            ?: if (useDefault) context.getDrawable(R.drawable.kr_shortcut_logo) else null
    }

    // Lấy icon (chỉ từ iconPath)
    fun loadIcon(context: Context, clickableNode: ClickableNode): Drawable? =
        clickableNode.iconPath.takeIf { it.isNotEmpty() }?.let { loadFromPath(context, it, clickableNode.pageConfigDir) }

    // Chuyển bitmap thành drawable (sửa deprecated)
    private fun bitmap2Drawable(context: Context, bitmap: android.graphics.Bitmap) =
        BitmapDrawable(context.resources, bitmap)

    // Hàm tiện ích load drawable từ path
    private fun loadFromPath(context: Context, path: String, pageConfigDir: String): Drawable? {
        return PathAnalysis(context, pageConfigDir).parsePath(path)?.use { stream ->
            BitmapFactory.decodeStream(stream)?.let { bitmap -> bitmap2Drawable(context, bitmap) }
        }
    }
}
