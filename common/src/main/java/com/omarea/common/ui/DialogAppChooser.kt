package com.omarea.common.ui

import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.AbsListView
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.Filterable
import com.omarea.common.R
import com.omarea.krscript.R

class DialogAppChooser(
    darkMode: Boolean,
    private var packages: ArrayList<AdapterAppChooser.AppInfo>,
    private val multiple: Boolean = false,
    private var callback: Callback? = null
) : DialogFullScreen(R.layout.dialog_app_chooser, darkMode) {

    private var allowAllSelect = true
    private var excludeApps: Array<String> = arrayOf()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val absListView = view.findViewById<AbsListView>(R.id.app_list)
        setup(absListView)

        view.findViewById<View>(R.id.btn_cancel).setOnClickListener { dismiss() }
        view.findViewById<View>(R.id.btn_confirm).setOnClickListener { onConfirm(absListView) }

        setupSelectAll(view, absListView)
        setupSearchBox(view, absListView)
    }

    private fun setup(gridView: AbsListView) {
        val filtered = packages.filter { !excludeApps.contains(it.packageName) }
        gridView.adapter = AdapterAppChooser(gridView.context, ArrayList(filtered), multiple)
    }

    private fun setupSelectAll(view: View, gridView: AbsListView) {
        val selectAll = view.findViewById<CompoundButton?>(R.id.select_all) ?: return
        val adapter = gridView.adapter as? AdapterAppChooser ?: return

        if (!multiple || !allowAllSelect) {
            selectAll.visibility = View.GONE
            return
        }

        selectAll.visibility = View.VISIBLE
        selectAll.isChecked = packages.all { it.selected }

        selectAll.setOnClickListener {
            adapter.setSelectAllState((it as CompoundButton).isChecked)
        }

        adapter.setSelectStateListener(object : AdapterAppChooser.SelectStateListener {
            override fun onSelectChange(selected: List<AdapterAppChooser.AppInfo>) {
                selectAll.isChecked = selected.size == packages.size
            }
        })
    }

    private fun setupSearchBox(view: View, gridView: AbsListView) {
        val clearBtn = view.findViewById<View>(R.id.search_box_clear)
        val searchBox = view.findViewById<EditText>(R.id.search_box)

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) {
                clearBtn.visibility = if (!s.isNullOrEmpty()) View.VISIBLE else View.GONE
            }

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                (gridView.adapter as? Filterable)?.filter?.filter(s?.toString() ?: "")
            }
        })

        clearBtn.visibility = if (searchBox.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        clearBtn.setOnClickListener { searchBox.text = null }
    }

    private fun onConfirm(gridView: AbsListView) {
        val apps = (gridView.adapter as? AdapterAppChooser)?.getSelectedItems() ?: emptyList()
        callback?.onConfirm(apps)
        dismiss()
    }

    fun setExcludeApps(apps: Array<String>): DialogAppChooser {
        this.excludeApps = apps
        if (view != null) {
            Log.w("@DialogAppChooser", "Unable to set the exclusion list, the list has already been loaded")
        }
        return this
    }

    fun setAllowAllSelect(allow: Boolean): DialogAppChooser {
        this.allowAllSelect = allow
        view?.findViewById<CompoundButton?>(R.id.select_all)?.visibility = if (allow) View.VISIBLE else View.GONE
        return this
    }

    interface Callback {
        fun onConfirm(apps: List<AdapterAppChooser.AppInfo>)
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
    }
}
