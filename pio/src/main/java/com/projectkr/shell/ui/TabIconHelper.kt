package com.projectkr.shell.ui

import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import androidx.viewpager2.widget.ViewPager2
import com.projectkr.shell.R

class TabIconHelper(
    private val tabLayout: TabLayout,
    private val viewPager: ViewPager2,
    private val inflater: LayoutInflater
) {

    fun attach(
        tabCount: Int,
        bindView: (position: Int, view: View) -> Unit
    ) {
        TabLayoutMediator(tabLayout, viewPager) { tab, position ->
            val view = inflater.inflate(R.layout.list_item_tab, null)
            bindView(position, view)
            view.alpha = if (position == 0) 1f else 0.3f
            tab.customView = view
        }.attach()

        tabLayout.addOnTabSelectedListener(object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) {
                tab.customView?.alpha = 1f
            }

            override fun onTabUnselected(tab: TabLayout.Tab) {
                tab.customView?.alpha = 0.3f
            }

            override fun onTabReselected(tab: TabLayout.Tab) {}
        })
    }
}
