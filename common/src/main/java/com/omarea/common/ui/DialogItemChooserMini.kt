package com.omarea.common.ui

import android.content.Context
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.widget.AbsListView
import android.widget.EditText
import android.widget.Filterable
import android.widget.TextView
import com.omarea.common.R
import com.omarea.common.model.SelectItem

class DialogItemChooserMini(
    private val context: Context,
    private var items: ArrayList<SelectItem>,
    private var selectedItems: ArrayList<SelectItem>,
    private val multiple: Boolean = false
) {

    companion object {
        fun singleChooser(context: Context, items: Array<String>, checkedItem: Int): DialogItemChooserMini {
            val options = ArrayList(items.map {
                SelectItem().apply { title = it; value = it }
            })
            val selectItems = if (checkedItem in items.indices) arrayListOf(options[checkedItem]) else arrayListOf()
            return DialogItemChooserMini(context, options, selectItems, false)
        }
    }

    private val layout = R.layout.dialog_item_chooser_small
    private var view: View? = null
    private var dialog: DialogHelper.DialogWrap? = null

    private var title: String = ""
    private var message: String = ""
    private var callback: Callback? = null

    fun show(): DialogHelper.DialogWrap {
        if (dialog?.isShowing != true) {
            onViewCreated(createView())
            dialog = DialogHelper.customDialog(context, view!!)
        }
        return dialog!!
    }

    private fun dismiss() {
        dialog?.dismiss()
    }

    private fun createView(): View {
        view?.let { return it }
        view = LayoutInflater.from(context).inflate(layout, null)
        return view!!
    }

    private fun onViewCreated(view: View) {
        val absListView = view.findViewById<AbsListView>(R.id.item_list).apply { setup(this) }

        view.findViewById<View>(R.id.btn_cancel)?.setOnClickListener { dismiss() }
        view.findViewById<View>(R.id.btn_confirm)?.setOnClickListener { onConfirm(absListView) }

        setupSearch(view, absListView)
        updateTitle()
        updateMessage()
    }

    private fun setupSearch(view: View, absListView: AbsListView) {
        if (items.size <= 5) return

        val searchBox = view.findViewById<EditText>(R.id.search_box) ?: return
        val clearBtn = view.findViewById<View>(R.id.search_box_clear)

        fun updateClearBtnVisibility() {
            clearBtn?.visibility = if (searchBox.text.isNullOrEmpty()) View.GONE else View.VISIBLE
        }

        searchBox.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun afterTextChanged(s: Editable?) { updateClearBtnVisibility() }
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                (absListView.adapter as? Filterable)?.filter?.filter(s?.toString() ?: "")
            }
        })

        updateClearBtnVisibility()
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

    fun setTitle(resId: Int) = setTitle(context.getString(resId))
    fun setTitle(title: String) = apply { this.title = title; updateTitle() }

    fun setMessage(resId: Int) = setMessage(context.getString(resId))
    fun setMessage(message: String) = apply { this.message = message; updateMessage() }

    fun setCallback(callback: Callback?) = apply { this.callback = callback }

    private fun setup(gridView: AbsListView) {
        gridView.adapter = AdapterItemChooser2(gridView.context, items, selectedItems, multiple)
    }

    private fun onConfirm(gridView: AbsListView) {
        (gridView.adapter as? AdapterItemChooser2)?.let { adapter ->
            callback?.onConfirm(adapter.getSelectedItems(), adapter.getSelectStatus())
        }
        dismiss()
    }

    interface Callback {
        fun onConfirm(selected: List<SelectItem>, status: BooleanArray)
    }
}
