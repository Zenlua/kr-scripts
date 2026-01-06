package com.omarea.common.ui

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.omarea.common.R
import com.omarea.common.model.SelectItem
import java.util.*
import java.util.Locale.getDefault

class AdapterItemChooser2(
    private val context: Context,
    private var items: ArrayList<SelectItem>,
    selectedItems: ArrayList<SelectItem>,
    private val multiple: Boolean
) : BaseAdapter(), Filterable {

    private var filter: Filter? = null
    internal var filterItems: ArrayList<SelectItem> = items
    private val mLock = Any()
    private val currentSelected = ArrayList<SelectItem>(selectedItems)

    // ---------------- Filter ----------------
    private class ArrayFilter(private val adapter: AdapterItemChooser2) : Filter() {
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            @Suppress("UNCHECKED_CAST")
            adapter.filterItems = results?.values as? ArrayList<SelectItem> ?: arrayListOf()
            if (adapter.filterItems.isNotEmpty()) {
                adapter.notifyDataSetChanged()
            } else {
                adapter.notifyDataSetInvalidated()
            }
        }

        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val results = FilterResults()
            val prefix = constraint?.toString()?.lowercase(getDefault()) ?: ""

            val values: List<SelectItem> = synchronized(adapter.mLock) { adapter.items.toList() }
            val selected = adapter.currentSelected

            val filtered = if (prefix.isEmpty()) {
                values
            } else {
                values.filter { item ->
                    selected.contains(item) || item.title?.lowercase(getDefault())?.let { title ->
                        title.contains(prefix) || title.split(" ").any { it.contains(prefix) }
                    } == true
                }
            }

            results.values = ArrayList(filtered)
            results.count = filtered.size
            return results
        }
    }

    override fun getFilter(): Filter = filter ?: ArrayFilter(this).also { filter = it }

    // ---------------- Adapter methods ----------------
    override fun getCount(): Int = filterItems.size
    override fun getItem(position: Int): SelectItem = filterItems[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, view: View?, parent: ViewGroup): View {
        val convertView = view ?: View.inflate(
            context,
            if (multiple) R.layout.item_multiple_chooser_item else R.layout.item_single_chooser_item,
            null
        )
        updateRow(position, convertView)
        return convertView
    }

    fun updateRow(position: Int, listView: OverScrollGridView, item: SelectItem) {
        try {
            val first = listView.firstVisiblePosition
            val last = listView.lastVisiblePosition
            if (position in first..last) {
                filterItems[position] = item
                val view = listView.getChildAt(position - first)
                updateRow(position, view)
            }
        } catch (_: Exception) { }
    }

    fun updateRow(position: Int, convertView: View) {
        val item = getItem(position)
        val viewHolder = convertView.tag as? ViewHolder ?: ViewHolder().also {
            it.itemTitle = convertView.findViewById(R.id.ItemTitle)
            it.itemDesc = convertView.findViewById(R.id.ItemDesc)
            it.imgView = convertView.findViewById(R.id.ItemIcon)
            it.checkBox = convertView.findViewById(R.id.ItemChecBox)
            convertView.tag = it
        }

        convertView.setOnClickListener {
            if (currentSelected.contains(item)) {
                if (multiple) {
                    currentSelected.remove(item)
                    viewHolder.checkBox?.isChecked = false
                }
            } else {
                if (multiple) {
                    currentSelected.add(item)
                    viewHolder.checkBox?.isChecked = true
                } else {
                    currentSelected.clear()
                    currentSelected.add(item)
                    notifyDataSetChanged()
                }
            }
        }

        viewHolder.itemTitle?.text = item.title
        viewHolder.itemDesc?.run {
            if (item.title.isNullOrEmpty()) {
                text = item.title
            } else {
                visibility = View.GONE
            }
        }
        viewHolder.checkBox?.isChecked = currentSelected.contains(item)
    }

    // ---------------- Utilities ----------------
    fun getSelectedItems(): List<SelectItem> = currentSelected
    fun getSelectStatus(): BooleanArray = items.map { currentSelected.contains(it) }.toBooleanArray()

    class ViewHolder {
        var itemTitle: TextView? = null
        var itemDesc: TextView? = null
        var imgView: ImageView? = null
        var checkBox: CompoundButton? = null
    }
}
