package com.projectkr.shell

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.webkit.MimeTypeMap
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import java.io.File

class OpenFileActivity : AppCompatActivity() {

    companion object {
        private val mimeTypeMap = MimeTypeMap.getSingleton()

        // Hàm trả về MIME type của file
        fun getMimeType(path: String): String {
            val fileExtension = MimeTypeMap.getFileExtensionFromUrl(path.toLowerCase())
            return mimeTypeMap.getMimeTypeFromExtension(fileExtension)
                ?: if (path.endsWith(".apk")) "application/vnd.android.package-archive" else "*/*"
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Kiểm tra nếu không có đường dẫn
        val filePath = intent.getStringExtra("path") ?: run {
            showToast("No file path provided")
            finish()
            return
        }

        val file = File(filePath)
        
        // Kiểm tra nếu tệp không tồn tại
        if (!file.exists()) {
            showToast("File does not exist")
            finish()
            return
        }

        // Chuyển đường dẫn file thành Uri
        val uri = FileProvider.getUriForFile(this, "${applicationContext.packageName}.provider", file)
        
        // Lấy MIME type của tệp
        val mimeType = getMimeType(file.name)

        // Tạo intent để mở file
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, mimeType)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        // Mở file, bắt ngoại lệ nếu không tìm thấy ứng dụng thích hợp
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            showToast("No application found to open this file")
        }

        finish()
    }

    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}