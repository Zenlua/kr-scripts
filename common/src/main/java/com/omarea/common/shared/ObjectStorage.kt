package com.omarea.common.shared

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.*

open class ObjectStorage<T : Serializable>(private val context: Context) {

    private val objectStorageDir = "objects/"

    private fun getSaveFile(configFile: String): File {
        val path = FileWrite.getPrivateFilePath(context, objectStorageDir + configFile)
        return File(path)
    }

    open fun save(obj: T?, configFile: String): Boolean {
        val file = getSaveFile(configFile)
        file.parentFile?.takeIf { !it.exists() }?.mkdirs()

        if (obj == null) {
            if (file.exists()) file.delete()
            return true
        }

        return try {
            FileOutputStream(file).use { fos ->
                ObjectOutputStream(fos).use { it.writeObject(obj) }
            }
            true
        } catch (ex: Exception) {
            ex.printStackTrace()
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(context, "Storage configuration failed!", Toast.LENGTH_SHORT).show()
            }
            false
        }
    }

    @Suppress("UNCHECKED_CAST")
    open fun load(configFile: String): T? {
        val file = getSaveFile(configFile)
        if (!file.exists()) return null

        return try {
            FileInputStream(file).use { fis ->
                ObjectInputStream(fis).use { it.readObject() as T }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            null
        }
    }

    inline fun <reified R : T> loadSafe(configFile: String): R? {
        return try {
            val obj = load(configFile)
            if (obj is R) obj else null
        } catch (ex: Exception) {
            ex.printStackTrace()
            null
        }
    }

    open fun remove(configFile: String) {
        getSaveFile(configFile).takeIf { it.exists() }?.delete()
    }

    open fun exists(configFile: String): Boolean = getSaveFile(configFile).exists()
}
