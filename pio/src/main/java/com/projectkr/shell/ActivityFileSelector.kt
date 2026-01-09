package com.projectkr.shell

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Environment
import android.view.KeyEvent
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.content.PermissionChecker
import com.omarea.common.ui.ProgressBarDialog
import com.projectkr.shell.databinding.ActivityFileSelectorBinding
import com.projectkr.shell.ui.AdapterFileSelector
import java.io.File

class ActivityFileSelector : AppCompatActivity() {
    companion object {
        const val MODE_FILE = 0
        const val MODE_FOLDER = 1
    }

    private var adapterFileSelector: AdapterFileSelector? = null
    var extension = ""
    var mode = MODE_FILE
    private lateinit var binding : ActivityFileSelectorBinding
    override fun onCreate(savedInstanceState: Bundle?) {
        // TODO:ThemeSwitch.switchTheme(this)
        super.onCreate(savedInstanceState)
        binding = ActivityFileSelectorBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val darkMode = ThemeModeState.getThemeMode().isDarkMode
        window.statusBarColor = if (darkMode) {
            resources.getColor(R.color.window_bg_dark, theme)
        } else {
            resources.getColor(R.color.window_bg_light, theme)
        }
        
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
            val decorView = window.decorView
            decorView.systemUiVisibility = if (darkMode) {
                0 // nền tối -> icon sáng
            } else {
                View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
            }
        }

        val toolbar = findViewById<View>(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)
        // setTitle(R.string.app_name)

        // 显示返回按钮
        supportActionBar!!.setHomeButtonEnabled(true)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
        toolbar.setNavigationOnClickListener { _ ->
            finish()
        }

        intent.extras?.run {
            if (containsKey("extension")) {
                extension = "" + intent.extras?.getString("extension")
                if (!extension.startsWith(".")) {
                    extension = ".$extension"
                }
                if (extension.isNotEmpty()) {
                    title = "$title($extension)"
                }
            }
            if (containsKey("mode")) {
                mode = getInt("mode")
                if (mode == MODE_FOLDER) {
                    title = getString(R.string.title_activity_folder_selector)
                }
            }
        }
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK && adapterFileSelector != null && adapterFileSelector!!.goParent()) {
            return true
        } else {
            setResult(RESULT_CANCELED, Intent())
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        var grant = true
        for (result in grantResults) {
            if (result == PackageManager.PERMISSION_DENIED) {
                grant = false
            }
        }

        if (requestCode == 111) {
            if (!grant) {
                Toast.makeText(applicationContext, "You do not have permission to read the file!", Toast.LENGTH_LONG).show()
            } else {
                loadData()
            }
        }
    }

    private fun checkPermission(permission: String): Boolean = PermissionChecker.checkSelfPermission(this, permission) == PermissionChecker.PERMISSION_GRANTED
    private fun requestPermissions() {
        ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE), 111)
    }

    override fun onResume() {
        super.onResume()
        loadData()
    }

    private fun loadData() {
        if (checkPermission(Manifest.permission.READ_EXTERNAL_STORAGE) && checkPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            val sdcard = File(Environment.getExternalStorageDirectory().absolutePath)
            if (sdcard.exists() && sdcard.isDirectory) {
                val list = sdcard.listFiles()
                if (list == null) {
                    Toast.makeText(applicationContext, "Failed to retrieve file list!", Toast.LENGTH_LONG).show()
                    return
                }
                val onSelected =  Runnable {
                    val file: File? = adapterFileSelector!!.selectedFile
                    if (file != null) {
                        this.setResult(RESULT_OK, Intent().putExtra("file", file.absolutePath))
                        this.finish()
                    }
                }
                adapterFileSelector = if (mode == MODE_FOLDER) {
                    AdapterFileSelector.FolderChooser(sdcard, onSelected, ProgressBarDialog(this))
                } else {
                    AdapterFileSelector.FileChooser(sdcard, onSelected, ProgressBarDialog(this), extension)
                }

                binding.fileSelectorList.adapter = adapterFileSelector
            }
        } else {
            requestPermissions()
        }
    }
}
