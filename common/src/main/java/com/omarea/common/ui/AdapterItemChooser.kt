package com.omarea.common.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.omarea.common.R
import com.omarea.common.model.SelectItem
import java.util.Locale.getDefault

class AdapterItemChooser(
    private val context: Context,
    private var items: ArrayList<SelectItem>,
    private val multiple: Boolean
) : BaseAdapter(), Filterable {

    interface SelectStateListener {
        fun onSelectChange(selected: List<SelectItem>)
    }

    private var selectStateListener: SelectStateListener? = null
    private var filter: Filter? = null
    internal var filterItems: ArrayList<SelectItem> = ArrayList(items)
    private val mLock = Any()

    // ------------------------ Filter ------------------------
    override fun getFilter(): Filter {
        if (filter == null) filter = ArrayFilter(this)
        return filter!!
    }

    private class ArrayFilter(private val adapter: AdapterItemChooser) : Filter() {

        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            @Suppress("UNCHECKED_CAST")
            adapter.filterItems = results?.values as? ArrayList<SelectItem> ?: arrayListOf()
            if (adapter.filterItems.isNotEmpty()) adapter.notifyDataSetChanged()
            else adapter.notifyDataSetInvalidated()
        }

        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val results = FilterResults()
            val prefix = constraint?.toString()?.lowercase(getDefault()) ?: ""

            val allItems: ArrayList<SelectItem>
            synchronized(adapter.mLock) { allItems = ArrayList(adapter.items) }

            val newValues = if (prefix.isEmpty()) {
                allItems
            } else {
                val selected = adapter.getSelectedItems()
                allItems.filter { item ->
                    item in selected ||
                    (item.title?.lowercase(getDefault())?.split(" ")?.any { it.contains(prefix) } == true)
                } as ArrayList<SelectItem>
            }

            results.values = newValues
            results.count = newValues.size
            return results
        }
    }

    // ------------------------ BaseAdapter ------------------------
    override fun getCount() = filterItems.size
    override fun getItem(position: Int) = filterItems[position]
    override fun getItemId(position: Int) = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: View.inflate(
            context,
            if (multiple) R.layout.item_multiple_chooser_item else R.layout.item_single_chooser_item,
            null
        )
        updateRow(position, view)
        return view
    }

    fun updateRow(position: Int, convertView: View) {
        val item = getItem(position)
        val viewHolder = (convertView.tag as? ViewHolder) ?: ViewHolder().apply {
            itemTitle = convertView.findViewById(R.id.ItemTitle)
            itemDesc = convertView.findViewById(R.id.ItemDesc)
            imgView = convertView.findViewById(R.id.ItemIcon)
            checkBox = convertView.findViewById(R.id.ItemChecBox)
            convertView.tag = this
        }

        convertView.setOnClickListener {
            if (multiple) {
                item.selected = !item.selected
                viewHolder.checkBox?.isChecked = item.selected
            } else if (!item.selected) {
                items.find { it.selected }?.selected = false
                item.selected = true
                notifyDataSetChanged()
            }
            selectStateListener?.onSelectChange(getSelectedItems())
        }

        viewHolder.itemTitle?.text = item.title
        viewHolder.itemDesc?.apply {
            if (item.title.isNullOrEmpty()) {
                visibility = View.GONE
            } else {
                text = item.title
                visibility = View.VISIBLE
            }
        }
        viewHolder.checkBox?.isChecked = item.selected
    }

    fun updateRow(position: Int, listView: OverScrollGridView, newItem: SelectItem) {
        val visibleFirst = listView.firstVisiblePosition
        val visibleLast = listView.lastVisiblePosition
        if (position in visibleFirst..visibleLast) {
            filterItems[position] = newItem
            val childView = listView.getChildAt(position - visibleFirst)
            updateRow(position, childView)
        }
    }

    // ------------------------ Utility ------------------------
    fun setSelectAllState(allSelected: Boolean) {
        items.forEach { it.selected = allSelected }
        notifyDataSetChanged()
    }

    fun setSelectStateListener(listener: SelectStateListener?) {
        selectStateListener = listener
    }

    fun getSelectedItems(): List<SelectItem> = items.filter { it.selected }

    fun getSelectStatus(): BooleanArray = items.map { it.selected }.toBooleanArray()

    class ViewHolder {
        var itemTitle: TextView? = null
        var itemDesc: TextView? = null
        var imgView: ImageView? = null
        var checkBox: CompoundButton? = null
    }
}
