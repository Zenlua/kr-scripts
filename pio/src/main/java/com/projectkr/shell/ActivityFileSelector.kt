package com.projectkr.shell.ui

import android.widget.BaseAdapter
import com.omarea.common.ui.ProgressBarDialog
import java.io.File

abstract class AdapterFileSelector(
    startDir: File,
    private val onSelected: Runnable,
    private val dialog: ProgressBarDialog
) : BaseAdapter() {

    protected var currentDir: File = startDir
    protected var files: Array<File> = emptyArray()

    init {
        reload()
    }

    protected fun reload() {
        files = try {
            currentDir.listFiles() ?: emptyArray()
        } catch (e: Exception) {
            emptyArray()
        }
        notifyDataSetChanged()
    }

    /**
     * ============================
     *  🔥 HÀM ĐÃ SỬA Ở ĐÂY
     * ============================
     * Cho phép:
     *   /sdcard → /
     * Không chặn parent nữa
     */
    fun goParent(): Boolean {
        val parent = currentDir.parentFile ?: return false
        currentDir = parent
        reload()
        return true
    }

    fun getCurrentPath(): String {
        return currentDir.absolutePath
    }

    abstract val selectedFile: File?

    // ============================
    // Các subclass
    // ============================

    class FileChooser(
        startDir: File,
        onSelected: Runnable,
        dialog: ProgressBarDialog,
        private val extension: String
    ) : AdapterFileSelector(startDir, onSelected, dialog) {

        override val selectedFile: File?
            get() = null
    }

    class FolderChooser(
        startDir: File,
        onSelected: Runnable,
        dialog: ProgressBarDialog
    ) : AdapterFileSelector(startDir, onSelected, dialog) {

        override val selectedFile: File?
            get() = currentDir
    }
}
