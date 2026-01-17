package com.projectkr.shell

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CompoundButton
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import com.omarea.common.shared.FilePathResolver
import com.omarea.common.ui.DialogHelper
import com.omarea.common.ui.ProgressBarDialog
import com.omarea.krscript.config.PageConfigReader
import com.omarea.krscript.config.PageConfigSh
import com.omarea.krscript.model.*
import com.omarea.krscript.ui.ActionListFragment
import com.omarea.krscript.ui.ParamsFileChooserRender
import com.omarea.vtools.FloatMonitor
import com.projectkr.shell.databinding.ActivityMainBinding
import com.omarea.common.shell.KeepShellPublic
import com.projectkr.shell.ui.TabIconHelper
import androidx.core.view.isVisible

class MainActivity : AppCompatActivity() {

    private val progressBarDialog = ProgressBarDialog(this)
    private val handler = Handler(Looper.getMainLooper())
    private lateinit var krScriptConfig: KrScriptConfig
    private val hasRoot by lazy { KeepShellPublic.checkRoot() }
    private lateinit var binding: ActivityMainBinding

    private var fileSelectedInterface: ParamsFileChooserRender.FileSelectedInterface? = null

    // Launcher cho file picker hệ thống (SAF - ACTION_OPEN_DOCUMENT)
    private val fileChooserLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val uri = result.data?.data
            val path = uri?.let { FilePathResolver().getPath(this, it) }
            fileSelectedInterface?.onFileSelected(path)
        } else {
            fileSelectedInterface?.onFileSelected(null)
        }
        fileSelectedInterface = null
    }

    // Launcher cho ActivityFileSelector nội bộ (nếu có extension cụ thể)
    private val innerFileSelectorLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val path = result.data?.getStringExtra("file")
            fileSelectedInterface?.onFileSelected(path)
        } else {
            fileSelectedInterface?.onFileSelected(null)
        }
        fileSelectedInterface = null
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeModeState.switchTheme(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = findViewById<Toolbar>(R.id.toolbar)
        setSupportActionBar(toolbar)
        title = getString(R.string.app_name)

        krScriptConfig = KrScriptConfig()

        binding.mainTabhost.setup()
        val tabIconHelper = TabIconHelper(binding.mainTabhost, this)

        if (hasRoot && krScriptConfig.allowHomePage) {
            tabIconHelper.newTabSpec(
                getString(R.string.tab_home),
                ContextCompat.getDrawable(this, R.drawable.tab_home),
                R.id.main_tabhost_cpu
            )
        } else {
            binding.mainTabhostCpu.visibility = View.GONE
        }

        binding.mainTabhost.setOnTabChangedListener {
            tabIconHelper.updateHighlight()
        }

        progressBarDialog.showDialog(getString(R.string.please_wait))

        Thread {
            val page2Config = krScriptConfig.pageListConfig
            val favoritesConfig = krScriptConfig.favoriteConfig

            val pages = getItems(page2Config)
            val favorites = getItems(favoritesConfig)

            handler.post {
                progressBarDialog.hideDialog()

                if (!favorites.isNullOrEmpty()) {
                    updateFavoritesTab(favorites, favoritesConfig)
                    tabIconHelper.newTabSpec(
                        getString(R.string.tab_favorites),
                        ContextCompat.getDrawable(this, R.drawable.tab_favorites),
                        R.id.main_tabhost_2
                    )
                } else {
                    binding.mainTabhost2.visibility = View.GONE
                }

                if (!pages.isNullOrEmpty()) {
                    updateMoreTab(pages, page2Config)
                    tabIconHelper.newTabSpec(
                        getString(R.string.tab_pages),
                        ContextCompat.getDrawable(this, R.drawable.tab_pages),
                        R.id.main_tabhost_3
                    )
                } else {
                    binding.mainTabhost3.visibility = View.GONE
                }
            }
        }.start()

        if (hasRoot && krScriptConfig.allowHomePage) {
            supportFragmentManager.beginTransaction()
                .replace(R.id.main_tabhost_cpu, FragmentHome())
                .commit()
        }
    }

    private fun getItems(pageNode: PageNode): ArrayList<NodeInfoBase>? {
        var items: ArrayList<NodeInfoBase>? = null

        if (pageNode.pageConfigSh.isNotEmpty()) {
            items = PageConfigSh(this, pageNode.pageConfigSh, null).execute()
        }
        if (items == null && pageNode.pageConfigPath.isNotEmpty()) {
            items = PageConfigReader(this.applicationContext, pageNode.pageConfigPath, null).readConfigXml()
        }

        return items
    }

    private fun updateFavoritesTab(items: ArrayList<NodeInfoBase>, pageNode: PageNode) {
        val favoritesFragment = ActionListFragment.create(
            items,
            getKrScriptActionHandler(pageNode, true),
            null,
            ThemeModeState.getThemeMode()
        )
        supportFragmentManager.beginTransaction()
            .replace(R.id.list_favorites, favoritesFragment)
            .commit()
    }

    private fun updateMoreTab(items: ArrayList<NodeInfoBase>, pageNode: PageNode) {
        val allItemFragment = ActionListFragment.create(
            items,
            getKrScriptActionHandler(pageNode, false),
            null,
            ThemeModeState.getThemeMode()
        )
        supportFragmentManager.beginTransaction()
            .replace(R.id.list_pages, allItemFragment)
            .commit()
    }

    private fun reloadFavoritesTab() {
        Thread {
            val favoritesConfig = krScriptConfig.favoriteConfig
            val favorites = getItems(favoritesConfig)
            favorites?.let {
                handler.post {
                    updateFavoritesTab(it, favoritesConfig)
                }
            }
        }.start()
    }

    private fun reloadMoreTab() {
        Thread {
            val page2Config = krScriptConfig.pageListConfig
            val pages = getItems(page2Config)
            pages?.let {
                handler.post {
                    updateMoreTab(it, page2Config)
                }
            }
        }.start()
    }

    private fun getKrScriptActionHandler(pageNode: PageNode, isFavoritesTab: Boolean): KrScriptActionHandler {
        return object : KrScriptActionHandler {
            override fun onActionCompleted(runnableNode: RunnableNode) {
                if (runnableNode.autoFinish) {
                    finishAndRemoveTask()
                } else if (runnableNode.reloadPage) {
                    if (isFavoritesTab) {
                        reloadFavoritesTab()
                    } else {
                        reloadMoreTab()
                    }
                }
            }

            override fun addToFavorites(
                clickableNode: ClickableNode,
                addToFavoritesHandler: AddToFavoritesHandler
            ) {
                val page = clickableNode as? PageNode
                    ?: if (clickableNode is RunnableNode) pageNode else return

                val intent = Intent().apply {
                    component = ComponentName(
                        this@MainActivity.applicationContext,
                        ActionPage::class.java
                    )
                    addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS)
                    addFlags(Intent.FLAG_ACTIVITY_NO_HISTORY)

                    if (clickableNode is RunnableNode) {
                        putExtra("autoRunItemId", clickableNode.key)
                    }
                    putExtra("page", page)
                }

                addToFavoritesHandler.onAddToFavorites(clickableNode, intent)
            }

            override fun onSubPageClick(pageNode: PageNode) {
                _openPage(pageNode)
            }

            override fun openFileChooser(fileSelectedInterface: ParamsFileChooserRender.FileSelectedInterface): Boolean {
                return chooseFilePath(fileSelectedInterface)
            }
        }
    }

    private fun chooseFilePath(fileSelectedInterface: ParamsFileChooserRender.FileSelectedInterface): Boolean {
        this.fileSelectedInterface = fileSelectedInterface
        return try {
            val suffix = fileSelectedInterface.suffix()
            if (!suffix.isNullOrEmpty()) {
                val intent = Intent(this, ActivityFileSelector::class.java).apply {
                    putExtra("extension", suffix)
                }
                innerFileSelectorLauncher.launch(intent)
            } else {
                val mime = fileSelectedInterface.mimeType() ?: "*/*"
                val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                    addCategory(Intent.CATEGORY_OPENABLE)
                    type = mime
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        putExtra(Intent.EXTRA_MIME_TYPES, arrayOf(mime))
                    }
                }
                fileChooserLauncher.launch(intent)
            }
            true
        } catch (e: Exception) {
            Toast.makeText(this, "Unable to open the file selector: ${e.message}", Toast.LENGTH_SHORT).show()
            false
        }
    }

    fun _openPage(pageNode: PageNode) {
        OpenPageHelper(this).openPage(pageNode)
    }

    private fun getDensity(): Int {
        return resources.displayMetrics.densityDpi
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        menu.findItem(R.id.action_graph)?.isVisible = binding.mainTabhostCpu.isVisible
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.option_menu_info -> {
                val layout = LayoutInflater.from(this).inflate(R.layout.dialog_about, null)
                val transparentUi = layout.findViewById<CompoundButton>(R.id.transparent_ui)
                val themeConfig = ThemeConfig(this)

                transparentUi.setOnClickListener {
                    val isChecked = (it as CompoundButton).isChecked
                    themeConfig.setAllowTransparentUI(isChecked)
                }
                transparentUi.isChecked = themeConfig.getAllowTransparentUI()

                DialogHelper.customDialog(this, layout)
            }

            R.id.option_menu_reboot -> {
                DialogPower(this).showPowerMenu()
            }

            R.id.action_graph -> {
                if (FloatMonitor.isShown) {
                    FloatMonitor(this).hidePopupWindow()
                    return true
                }

                if (Settings.canDrawOverlays(this)) {
                    FloatMonitor(this).showPopupWindow()
                    Toast.makeText(this, getString(R.string.float_monitor_tips), Toast.LENGTH_LONG).show()
                } else {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                    }
                    Toast.makeText(this, getString(R.string.permission_float), Toast.LENGTH_LONG).show()
                    try {
                        startActivity(intent)
                    } catch (_: Exception) {
                    }
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }
}
