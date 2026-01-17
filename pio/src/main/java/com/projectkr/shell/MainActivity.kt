package com.projectkr.shell

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.provider.Settings
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import android.widget.CompoundButton
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
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
    private var handler = Handler()
    private var krScriptConfig = KrScriptConfig()
    private val hasRoot by lazy { KeepShellPublic.checkRoot() }
    private lateinit var binding: ActivityMainBinding

    private val tabFragments = mutableListOf<Fragment>()
    private val tabTitles = mutableListOf<String>()
    private val tabIcons = mutableListOf<Drawable>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ThemeModeState.switchTheme(this)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val toolbar = findViewById<View>(R.id.toolbar) as Toolbar
        setSupportActionBar(toolbar)
        setTitle(R.string.app_name)

        krScriptConfig = KrScriptConfig()

        val tabLayout: TabLayout = binding.tabLayout       // thêm id="@+id/tab_layout" trong xml
        val viewPager: ViewPager2 = binding.viewPager      // thêm id="@+id/view_pager" trong xml

        val tabIconHelper = TabIconHelper(tabLayout, viewPager, this)

        // Tab Home (nếu có quyền root và allowHomePage)
        if (hasRoot && krScriptConfig.allowHomePage) {
            val icon = getDrawable(R.drawable.tab_home)!!
            val title = getString(R.string.tab_home)
            tabIconHelper.newTabSpec(title, icon, R.id.main_tabhost_cpu) // tham số cuối giữ nguyên nhưng không dùng

            tabFragments.add(FragmentHome())
            tabTitles.add(title)
            tabIcons.add(icon)
        }

        // Tab Favorites và Pages load async
        progressBarDialog.showDialog(getString(R.string.please_wait))
        Thread {
            val page2Config = krScriptConfig.pageListConfig
            val favoritesConfig = krScriptConfig.favoriteConfig

            val pages = getItems(page2Config)
            val favorites = getItems(favoritesConfig)

            handler.post {
                progressBarDialog.hideDialog()

                if (favorites != null && favorites.isNotEmpty()) {
                    val iconFav = ContextCompat.getDrawable(this, R.drawable.tab_favorites)!!
                    val titleFav = getString(R.string.tab_favorites)
                    tabIconHelper.newTabSpec(titleFav, iconFav, R.id.main_tabhost_2)

                    val favFragment = ActionListFragment.create(
                        favorites,
                        getKrScriptActionHandler(favoritesConfig, true),
                        null,
                        ThemeModeState.getThemeMode()
                    )
                    tabFragments.add(favFragment)
                    tabTitles.add(titleFav)
                    tabIcons.add(iconFav)
                }

                if (pages != null && pages.isNotEmpty()) {
                    val iconPages = ContextCompat.getDrawable(this, R.drawable.tab_pages)!!
                    val titlePages = getString(R.string.tab_pages)
                    tabIconHelper.newTabSpec(titlePages, iconPages, R.id.main_tabhost_3)

                    val pagesFragment = ActionListFragment.create(
                        pages,
                        getKrScriptActionHandler(page2Config, false),
                        null,
                        ThemeModeState.getThemeMode()
                    )
                    tabFragments.add(pagesFragment)
                    tabTitles.add(titlePages)
                    tabIcons.add(iconPages)
                }

                // Sau khi add hết tab → setup ViewPager2 + TabLayout
                viewPager.adapter = object : androidx.viewpager2.adapter.FragmentStateAdapter(this) {
                    override fun getItemCount() = tabFragments.size
                    override fun createFragment(position: Int) = tabFragments[position]
                }

                TabLayoutMediator(tabLayout, viewPager) { tab, position ->
                    // Không cần set text/icon vì TabIconHelper đã set custom view
                }.attach()

                tabIconHelper.setupWithViewPager(tabFragments)

                // Optional: update alpha ban đầu
                tabIconHelper.updateHighlight()

                // Refresh menu (vì action_graph phụ thuộc home tab)
                invalidateOptionsMenu()
            }
        }.start()
    }

    // Các hàm getItems, getKrScriptActionHandler, chooseFilePath, onActivityResult, getPath, _openPage, getDensity giữ nguyên

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

    private fun getKrScriptActionHandler(pageNode: PageNode, isFavoritesTab: Boolean): KrScriptActionHandler {
        return object : KrScriptActionHandler {
            override fun onActionCompleted(runnableNode: RunnableNode) {
                if (runnableNode.autoFinish) {
                    finishAndRemoveTask()
                } else if (runnableNode.reloadPage) {
                    // Reload tab tương ứng (có thể cải tiến sau bằng cách notify adapter)
                    if (isFavoritesTab) {
                        // reloadFavoritesTab() → tạm thời bỏ hoặc implement lại nếu cần
                    } else {
                        // reloadMoreTab()
                    }
                }
            }

            // Các override khác giữ nguyên
            override fun addToFavorites(clickableNode: ClickableNode, addToFavoritesHandler: KrScriptActionHandler.AddToFavoritesHandler) {
                val page = clickableNode as? PageNode
                    ?: if (clickableNode is RunnableNode) pageNode else return

                val intent = Intent().apply {
                    component = ComponentName(this@MainActivity.applicationContext, ActionPage::class.java)
                    addFlags(Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS or Intent.FLAG_ACTIVITY_NO_HISTORY)
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

    // Các hàm chooseFilePath, onActivityResult, getPath, _openPage, getDensity giữ nguyên như cũ

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.main, menu)
        // action_graph chỉ visible nếu có tab Home
        menu.findItem(R.id.action_graph).isVisible = (hasRoot && krScriptConfig.allowHomePage)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Giữ nguyên phần xử lý menu cũ
        when (item.itemId) {
            R.id.option_menu_info -> {
                val layoutInflater = LayoutInflater.from(this)
                val layout = layoutInflater.inflate(R.layout.dialog_about, null)
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
                if (FloatMonitor.isShown == true) {
                    FloatMonitor(this).hidePopupWindow()
                    return true
                }
                if (Settings.canDrawOverlays(this)) {
                    FloatMonitor(this).showPopupWindow()
                    Toast.makeText(this, getString(R.string.float_monitor_tips), Toast.LENGTH_LONG).show()
                } else {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.fromParts("package", packageName, null)
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    }
                    Toast.makeText(this, getString(R.string.permission_float), Toast.LENGTH_LONG).show()
                    try {
                        startActivity(intent)
                    } catch (_: Exception) {}
                }
            }
        }
        return super.onOptionsItemSelected(item)
    }
}
