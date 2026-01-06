package com.omarea.common.ui

import android.content.DialogInterface
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.widget.*
import com.omarea.common.R
import com.omarea.common.model.SelectItem

class DialogItemChooser(
    darkMode: Boolean,
    private var items: ArrayList<SelectItem>,
    private val multiple: Boolean = false,
    private var callback: Callback? = null,
    alwaysSmallDialog: Boolean? = null
) : DialogFullScreen(
    if (items.size > 7 && alwaysSmallDialog != true) R.layout.dialog_item_chooser
    else R.layout.dialog_item_chooser_small,
    darkMode
) {

    private var title: String = ""
    private var message: String = ""

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val absListView = view.findViewById<AbsListView>(R.id.item_list).apply { setup(this) }

        view.findViewById<View>(R.id.btn_cancel)?.setOnClickListener { dismiss() }
        view.findViewById<View>(R.id.btn_confirm)?.setOnClickListener { onConfirm(absListView) }

        setupSelectAll(view, absListView)
        setupSearch(view, absListView)

        updateTitle()
        updateMessage()
    }

    private fun setup(gridView: AbsListView) {
        gridView.adapter = AdapterItemChooser(gridView.context, items, multiple)
    }

    private fun setupSelectAll(view: View, absListView: AbsListView) {
        val selectAll = view.findViewById<CompoundButton>(R.id.select_all) ?: return

        if (!multiple) {
            selectAll.visibility = View.GONE
            return
        }

        val adapter = absListView.adapter as? AdapterItemChooser ?: return
        selectAll.visibility = View.VISIBLE
        selectAll.isChecked = items.all { it.selected }

        selectAll.setOnClickListener { button ->
            adapter.setSelectAllState((button as CompoundButton).isChecked)
        }

        adapter.setSelectStateListener(object : AdapterItemChooser.SelectStateListener {
            override fun onSelectChange(selected: List<SelectItem>) {
                selectAll.isChecked = selected.size == items.size
            }
        })
    }

    private fun setupSearch(view: View, absListView: AbsListView) {
        if (items.size <= 5) return

        val searchBox = view.findViewById<EditText>(R.id.search_box) ?: return
        val clearBtn = view.findViewById<View>(R.id.search_box_clear)

        fun updateClearButtonVisibility() {
            clearBtn?.visibility = if (searchBox.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) { updateClearButtonVisibility() }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                (absListView.adapter as? Filterable)?.filter?.filter(s?.toString() ?: "")
            }
        })

        updateClearButtonVisibility()
        clearBtn?.setOnClickListener { searchBox.text = null }
    }

    private fun updateTitle() {
        view?.findViewById<TextView>(R.id.dialog_title)?.apply {
            text = title
            visibility = if (title.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    private fun updateMessage() {
        view?.findViewById<TextView>(R.id.dialog_desc)?.apply {
            text = message
            visibility = if (message.isNotEmpty()) View.VISIBLE else View.GONE
        }
    }

    fun setTitle(title: String) = apply {
        this.title = title
        updateTitle()
    }

    fun setMessage(message: String) = apply {
        this.message = message
        updateMessage()
    }

    private fun onConfirm(gridView: AbsListView) {
        (gridView.adapter as? AdapterItemChooser)?.let { adapter ->
            callback?.onConfirm(adapter.getSelectedItems(), adapter.getSelectStatus())
        }
        dismiss()
    }

    interface Callback {
        fun onConfirm(selected: List<SelectItem>, status: BooleanArray)
    }
}
