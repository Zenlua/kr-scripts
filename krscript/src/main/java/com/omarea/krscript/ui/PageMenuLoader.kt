package com.omarea.krscript.ui

import android.content.Context
import com.omarea.krscript.executor.ScriptEnvironmen
import com.omarea.krscript.model.PageMenuOption
import com.omarea.krscript.model.PageNode

class PageMenuLoader(private val applicationContext: Context, private val pageNode: PageNode) {
    private var menuOptions:ArrayList<PageMenuOption>? = null

    fun load(): ArrayList<PageMenuOption>? {
        if (menuOptions != null) {
            return menuOptions
        }
    
        menuOptions = ArrayList()
    
        if (pageNode.pageMenuOptionsSh.isNotEmpty()) {
            val result = ScriptEnvironmen.executeResultRoot(
                applicationContext,
                pageNode.pageMenuOptionsSh,
                pageNode
            )
    
            if (result != "error") {
                result.lineSequence()
                    .filter { it.isNotBlank() }
                    .forEach { item ->
                        val option = PageMenuOption(pageNode.pageConfigPath)
    
                        val parts = item.split("|", limit = 2)
                        option.key = parts[0]
                        option.title = parts.getOrElse(1) { parts[0] }
    
                        menuOptions!!.add(option)
                    }
            }
        } else if (pageNode.pageMenuOptions != null) {
            menuOptions = ArrayList(pageNode.pageMenuOptions!!)
        }
    
        return menuOptions
    }
}
