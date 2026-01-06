package com.omarea.common.shared

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import java.io.*

open class ObjectStorage<T : Serializable>(private val context: Context) {

    private val objectStorageDir = "objects/"

    protected fun getSaveDir(configFile: String): String {
        return FileWrite.getPrivateFilePath(context, objectStorageDir + configFile)
    }

    open fun load(configFile: String): T? {
        val file = File(getSaveDir(configFile))
        if (!file.exists()) return null

        return try {
            FileInputStream(file).use { fis ->
                ObjectInputStream(fis).use { ois ->
                    ois.readObject() as? T
                }
            }
        } catch (ex: Exception) {
            ex.printStackTrace()
            null
        }
    }

    open fun save(obj: T?, configFile: String): Boolean {
        val file = File(getSaveDir(configFile))
        file.parentFile?.takeIf { !it.exists() }?.mkdirs()

        if (obj == null) {
            if (file.exists()) file.delete()
            return true
        }

        return try {
            FileOutputStream(file).use { fos ->
                ObjectOutputStream(fos).use { oos ->
                    oos.writeObject(obj)
                }
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

    open fun remove(configFile: String) {
        File(getSaveDir(configFile)).takeIf { it.exists() }?.delete()
    }

    open fun exists(configFile: String): Boolean {
        return File(getSaveDir(configFile)).exists()
    }
}
