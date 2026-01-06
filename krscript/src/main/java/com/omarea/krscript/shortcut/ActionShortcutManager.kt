package com.omarea.krscript.shortcut

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo
import android.content.pm.ShortcutManager
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.Icon
import android.os.Build
import android.util.Log
import com.omarea.common.shared.ObjectStorage
import com.omarea.krscript.model.NodeInfoBase
import com.omarea.krscript.model.PageNode

class ActionShortcutManager(private val context: Context) {

    /**
     * Thêm shortcut
     * @param intent Intent của activity cần shortcut
     * @param drawable Icon của shortcut
     * @param config NodeInfoBase dùng để đặt title/index
     */
    fun addShortcut(intent: Intent, drawable: Drawable, config: NodeInfoBase): Boolean {
        // Lưu pageNode vào storage nếu intent có extra
        intent.getSerializableExtraCompat<PageNode>("page")?.let { pageNode ->
            intent.putExtra("shortcutId", saveShortcutTarget(pageNode))
            intent.removeExtra("page")
        }

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            createShortcutOreo(intent, drawable, config)
        } else {
            addShortcutLegacy(intent, drawable, config)
        }
    }

    // Legacy (< Oreo) → dùng broadcast
    private fun addShortcutLegacy(intent: Intent, drawable: Drawable, config: NodeInfoBase): Boolean {
        return try {
            val shortcutIntent = Intent(Intent.ACTION_MAIN).apply {
                component = intent.component
                putExtras(intent)
                flags = Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            }

            val shortcut = Intent("com.android.launcher.action.INSTALL_SHORTCUT").apply {
                putExtra("duplicate", false)
                // Dùng string trực tiếp để tránh deprecated field trên Android cũ
                putExtra("android.intent.extra.shortcut.NAME", config.title)
                putExtra("android.intent.extra.shortcut.ICON", (drawable as BitmapDrawable).bitmap)
                putExtra("android.intent.extra.shortcut.INTENT", shortcutIntent)
            }

            context.sendBroadcast(shortcut)
            true
        } catch (ex: Exception) {
            Log.e("ActionShortcutManager", "addShortcutLegacy failed: ${ex.message}")
            false
        }
    }

    // Lưu pageNode vào ObjectStorage và trả về ID shortcut
    private fun saveShortcutTarget(pageNode: PageNode): String {
        val id = System.currentTimeMillis().toString()
        ObjectStorage<PageNode>(context).save(pageNode, id)
        return id
    }

    // Lấy pageNode từ shortcutId
    fun getShortcutTarget(shortcutId: String): PageNode? =
        ObjectStorage<PageNode>(context).load(shortcutId)

    // Oreo+ → dùng ShortcutManager
    private fun createShortcutOreo(intent: Intent, drawable: Drawable, config: NodeInfoBase): Boolean {
        return try {
            val shortcutManager = context.getSystemService(ShortcutManager::class.java)
                ?: return false

            if (!shortcutManager.isRequestPinShortcutSupported) return false

            val id = "addin_${config.index}"

            val shortcutIntent = Intent(Intent.ACTION_MAIN).apply {
                component = intent.component
                putExtras(intent)
                flags = Intent.FLAG_ACTIVITY_NO_HISTORY or Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
            }

            val info = ShortcutInfo.Builder(context, id)
                .setShortLabel(config.title)
                .setIntent(shortcutIntent)
                .setIcon(Icon.createWithBitmap((drawable as BitmapDrawable).bitmap))
                .setActivity(intent.component!!)
                .build()

            val callbackIntent = PendingIntent.getBroadcast(
                context,
                0,
                Intent(),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Nếu shortcut đã có → update, chưa có → request pin
            shortcutManager.pinnedShortcuts.find { it.id == id }?.let {
                shortcutManager.updateShortcuts(listOf(info))
            } ?: shortcutManager.requestPinShortcut(info, callbackIntent.intentSender)

            true
        } catch (ex: Exception) {
            Log.e("ActionShortcutManager", "createShortcutOreo failed: ${ex.message}")
            false
        }
    }
}

/**
 * Extension an toàn cho getSerializableExtra với class
 */
inline fun <reified T : java.io.Serializable> Intent.getSerializableExtraCompat(name: String): T? {
    return if (Build.VERSION.SDK_INT >= 33) getSerializableExtra(name, T::class.java)
    else getSerializableExtra(name) as? T
}
