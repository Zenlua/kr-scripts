package com.projectkr.shell.ui

import android.app.Activity
import android.graphics.drawable.Drawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TabHost
import android.widget.TextView
import com.projectkr.shell.R

@Suppress("DEPRECATION")
class TabIconHelper(
    private val tabHost: TabHost,
    private val activity: Activity
) {
    private val views = ArrayList<View>()
    private val inflater: LayoutInflater = LayoutInflater.from(activity)

    fun newTabSpec(drawable: Drawable, content: Int): String =
        newTabSpec("", drawable, content)

    fun newTabSpec(text: String, drawable: Drawable, content: Int): String {
        val tabId = "tab_${views.size}"
        val view = createTabView(text, drawable)

        views.add(view)
        addTabInternal(tabId, view, content)

        return tabId
    }

    private fun createTabView(text: String, drawable: Drawable): View {
        val view = inflater.inflate(R.layout.list_item_tab, null, false)

        view.findViewById<ImageView>(R.id.ItemIcon).setImageDrawable(drawable)
        view.findViewById<TextView>(R.id.ItemTitle).text = text

        view.alpha = if (views.isEmpty()) 1f else 0.3f
        return view
    }

    @Suppress("DEPRECATION")
    private fun addTabInternal(tabId: String, indicator: View, content: Int) {
        tabHost.addTab(
            tabHost.newTabSpec(tabId)
                .setContent(content)
                .setIndicator(indicator)
        )
    }

    fun updateHighlight() {
        updateHighlightInternal()
    }

    @Suppress("DEPRECATION")
    private fun updateHighlightInternal() {
        val widget = tabHost.tabWidget
        val current = tabHost.currentTab

        for (i in 0 until widget.tabCount) {
            widget.getChildAt(i)?.alpha =
                if (i == current) 1f else 0.3f
        }
    }

    fun getColorAccent(): Int {
        val tv = TypedValue()
        activity.theme.resolveAttribute(
            androidx.appcompat.R.attr.colorAccent,
            tv,
            true
        )
        return tv.data
    }
}
