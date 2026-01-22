package com.omarea.krscript

import android.content.Context
import android.content.Intent
import android.widget.Toast

class TryOpenActivity(private val context: Context, private val activity: String) {

    private fun getIntent(): Intent? {
        try {
            val intent = if (activity.contains("/")) {
                // Nếu activity chứa "/" thì tách ra thành packageName và className
                val info = activity.split("/")
                val packageName = info.first()
                val className = info.last()
                Intent().apply {
                    setClassName(packageName, if (className.startsWith(".")) (packageName + className) else className)
                }
            } else {
                // Nếu activity là một tên đơn giản, tạo Intent theo tên activity đó
                Intent(activity)
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            return intent
        } catch (ex: Exception) {
            return null
        }
    }

    fun tryOpen(): Boolean {
        try {
            // Trực tiếp gọi startActivity
            context.startActivity(getIntent())
            return true
        } catch (ex: SecurityException) {
            // Xử lý khi có lỗi bảo mật (nếu cần)
            Toast.makeText(context, context.getString(R.string.kr_slice_activity_fail), Toast.LENGTH_SHORT).show()
            return false
        } catch (ex: Exception) {
            // Xử lý khi có các lỗi khác
            Toast.makeText(context, context.getString(R.string.kr_slice_activity_fail), Toast.LENGTH_SHORT).show()
            return false
        }
    }
}