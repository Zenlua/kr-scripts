package com.omarea.common.ui

import android.content.Context
import android.graphics.drawable.Drawable
import android.util.LruCache
import android.view.View
import android.view.ViewGroup
import android.widget.*
import com.omarea.common.R
import kotlinx.coroutines.*
import java.util.*
import java.util.Locale.getDefault

class AdapterAppChooser(
    private val context: Context,
    private var apps: ArrayList<AppInfo>,
    private val multiple: Boolean
) : BaseAdapter(), Filterable {

    interface SelectStateListener {
        fun onSelectChange(selected: List<AppInfo>)
    }

    open class AppInfo {
        var appName: String = ""
        var packageName: String = ""
        var notFound: Boolean = false
        var selected: Boolean = false
    }

    private var selectStateListener: SelectStateListener? = null
    private var filter: Filter? = null
    internal var filterApps: ArrayList<AppInfo> = ArrayList(apps)
    private val mLock = Any()
    private val iconCache = LruCache<String, Drawable>(100)
    private val adapterScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    init {
    filterApps.sortWith(compareBy<AppInfo> { !it.selected }.thenBy { it.appName.lowercase(Locale.getDefault()) })
    }

    override fun getCount() = filterApps.size
    override fun getItem(position: Int) = filterApps[position]
    override fun getItemId(position: Int) = position.toLong()

    // ------------------------ Filter ------------------------
    override fun getFilter(): Filter {
        if (filter == null) filter = ArrayFilter(this)
        return filter!!
    }

    private class ArrayFilter(private val adapter: AdapterAppChooser) : Filter() {
        override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
            @Suppress("UNCHECKED_CAST")
            adapter.filterApps = results?.values as? ArrayList<AppInfo> ?: arrayListOf()
            if (adapter.filterApps.isNotEmpty()) adapter.notifyDataSetChanged()
            else adapter.notifyDataSetInvalidated()
        }

        private fun searchStr(valueText: String, keyword: String) =
            valueText.contains(keyword) || valueText.split(" ").any { it.contains(keyword) }

        override fun performFiltering(constraint: CharSequence?): FilterResults {
            val results = FilterResults()
            val prefix = constraint?.toString()?.lowercase(getDefault()) ?: ""

            val allApps: ArrayList<AdapterAppChooser.AppInfo>
            synchronized(adapter.mLock) { allApps = ArrayList(adapter.apps) }

            val newValues = if (prefix.isEmpty()) {
                allApps
            } else {
                val selected = adapter.getSelectedItems()
                allApps.filter {
                    it in selected ||
                            searchStr(it.appName.lowercase(getDefault()), prefix) ||
                            searchStr(it.packageName.lowercase(getDefault()), prefix)
                } as ArrayList<AdapterAppChooser.AppInfo>
            }

            results.values = newValues
            results.count = newValues.size
            return results
        }
    }

    // ------------------------ Icon Loader ------------------------
    private suspend fun loadIconAsync(app: AppInfo): Drawable? {
        val pkg = app.packageName
        iconCache.get(pkg)?.let { return it }

        return withContext(Dispatchers.IO) {
            try {
                val icon = context.packageManager.getApplicationIcon(pkg)
                iconCache.put(pkg, icon)
                icon
            } catch (_: Exception) {
                app.notFound = true
                null
            }
        }
    }

    // ------------------------ View ------------------------
    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: View.inflate(
            context,
            if (multiple) R.layout.app_multiple_chooser_item else R.layout.app_single_chooser_item,
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
                apps.find { it.selected }?.selected = false
                item.selected = true
                notifyDataSetChanged()
            }
            selectStateListener?.onSelectChange(getSelectedItems())
        }

        viewHolder.itemTitle?.text = item.appName
        viewHolder.itemDesc?.text = item.packageName
        viewHolder.checkBox?.isChecked = item.selected

        val imgView = viewHolder.imgView ?: return
        imgView.tag = item.packageName
        adapterScope.launch {
            val icon = loadIconAsync(item)
            if (iconViewMatchesTag(imgView, item.packageName)) {
                imgView.setImageDrawable(icon)
            }
        }
    }

    private fun iconViewMatchesTag(view: ImageView, pkg: String) = view.tag == pkg

    // ------------------------ Utility ------------------------
    fun setSelectAllState(allSelected: Boolean) {
        apps.forEach { it.selected = allSelected }
        notifyDataSetChanged()
    }

    fun setSelectStateListener(listener: SelectStateListener?) {
        selectStateListener = listener
    }

    fun getSelectedItems(): List<AppInfo> = apps.filter { it.selected }

    class ViewHolder {
        var packageName: String? = null
        var itemTitle: TextView? = null
        var itemDesc: TextView? = null
        var imgView: ImageView? = null
        var checkBox: CompoundButton? = null
    }
}
