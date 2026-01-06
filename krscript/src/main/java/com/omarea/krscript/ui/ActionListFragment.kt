package com.omarea.krscript.ui

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.omarea.common.model.SelectItem
import com.omarea.common.ui.DialogHelper
import com.omarea.common.ui.DialogItemChooser
import com.omarea.common.ui.ProgressBarDialog
import com.omarea.common.ui.ThemeMode
import com.omarea.krscript.BgTaskThread
import com.omarea.krscript.HiddenTaskThread
import com.omarea.krscript.R
import com.omarea.krscript.TryOpenActivity
import com.omarea.krscript.config.IconPathAnalysis
import com.omarea.krscript.executor.ScriptEnvironmen
import com.omarea.krscript.model.*
import com.omarea.krscript.shortcut.ActionShortcutManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ActionListFragment : Fragment(), PageLayoutRender.OnItemClickListener {

    companion object {
        fun create(
            actionInfos: ArrayList<NodeInfoBase>?,
            krScriptActionHandler: KrScriptActionHandler? = null,
            autoRunTask: AutoRunTask? = null,
            themeMode: ThemeMode? = null
        ): ActionListFragment {
            val fragment = ActionListFragment()
            fragment.setListData(actionInfos, krScriptActionHandler, autoRunTask, themeMode)
            return fragment
        }
    }

    private var actionInfos: ArrayList<NodeInfoBase>? = null
    private var krScriptActionHandler: KrScriptActionHandler? = null
    private var autoRunTask: AutoRunTask? = null
    private var themeMode: ThemeMode? = null
    private lateinit var progressBarDialog: ProgressBarDialog
    private lateinit var rootGroup: ListItemGroup
    var hiddenTaskRunning = false

    private fun setListData(
        actionInfos: ArrayList<NodeInfoBase>?,
        krScriptActionHandler: KrScriptActionHandler? = null,
        autoRunTask: AutoRunTask? = null,
        themeMode: ThemeMode? = null
    ) {
        if (actionInfos != null) {
            this.actionInfos = actionInfos
            this.krScriptActionHandler = krScriptActionHandler
            this.autoRunTask = autoRunTask
            this.themeMode = themeMode
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.kr_action_list_fragment, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        progressBarDialog = ProgressBarDialog(requireActivity())

        rootGroup = ListItemGroup(requireContext(), true, GroupNode(""))

        actionInfos?.let { infos ->
            PageLayoutRender(requireContext(), infos, this, rootGroup)
            val layout = rootGroup.getView()
            view.findViewById<ScrollView>(R.id.kr_content)?.apply {
                removeAllViews()
                addView(layout)
            }
            triggerAction(autoRunTask)
        }
    }

    private fun triggerAction(autoRunTask: AutoRunTask?) {
        autoRunTask?.key?.takeIf { it.isNotEmpty() }?.let { key ->
            onActionClick(rootGroup.triggerActionByKey(key))
        }
    }

    private fun nodeUnlocked(clickableNode: ClickableNode): Boolean {
        val currentSDK = Build.VERSION.SDK_INT
        when {
            clickableNode.targetSdkVersion > 0 && currentSDK != clickableNode.targetSdkVersion -> {
                DialogHelper.helpInfo(
                    requireContext(),
                    getString(R.string.kr_sdk_discrepancy),
                    getString(R.string.kr_sdk_discrepancy_message).format(clickableNode.targetSdkVersion)
                )
                return false
            }
            currentSDK > clickableNode.maxSdkVersion -> {
                DialogHelper.helpInfo(
                    requireContext(),
                    getString(R.string.kr_sdk_overtop),
                    getString(R.string.kr_sdk_message).format(clickableNode.minSdkVersion, clickableNode.maxSdkVersion)
                )
                return false
            }
            currentSDK < clickableNode.minSdkVersion -> {
                DialogHelper.helpInfo(
                    requireContext(),
                    getString(R.string.kr_sdk_too_low),
                    getString(R.string.kr_sdk_message).format(clickableNode.minSdkVersion, clickableNode.maxSdkVersion)
                )
                return false
            }
        }

        val message = if (clickableNode.lockShell.isNotEmpty()) {
            ScriptEnvironmen.executeResultRoot(requireContext(), clickableNode.lockShell, clickableNode)
        } else {
            null
        }

        val unlocked = message?.let { it == "unlock" || it == "unlocked" || it == "false" || it == "0" }
            ?: !clickableNode.locked

        if (!unlocked) {
            Toast.makeText(requireContext(), message ?: getString(R.string.kr_lock_message), Toast.LENGTH_LONG).show()
        }

        return unlocked
    }

    //==================== OnItemClickListener ====================

    override fun onSwitchClick(item: SwitchNode, onCompleted: Runnable) {
        if (!nodeUnlocked(item)) return
        val toValue = !item.checked
        val callback = {
            actionExecute(item, item.setState ?: return@let, onCompleted, hashMapOf("state" to if (toValue) "1" else "0"))
        }

        when {
            item.confirm -> DialogHelper.warning(requireActivity(), item.title, item.desc, callback)
            item.warning.isNotEmpty() -> DialogHelper.warning(requireActivity(), item.title, item.warning, callback)
            else -> callback()
        }
    }

    override fun onPageClick(item: PageNode, onCompleted: Runnable) {
        if (!nodeUnlocked(item)) return
        try {
            when {
                item.link.isNotEmpty() -> {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(item.link)).apply {
                        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    })
                }
                item.activity.isNotEmpty() -> TryOpenActivity(requireContext(), item.activity).tryOpen()
                else -> krScriptActionHandler?.onSubPageClick(item)
            }
        } catch (ex: Exception) {
            Toast.makeText(requireContext(), getString(R.string.kr_slice_activity_fail), Toast.LENGTH_SHORT).show()
        }
    }

    override fun onItemLongClick(clickableNode: ClickableNode) {
        if (clickableNode.key.isEmpty()) {
            DialogHelper.alert(
                requireActivity(),
                getString(R.string.kr_shortcut_create_fail),
                getString(R.string.kr_ushortcut_nsupported)
            )
            return
        }

        krScriptActionHandler?.addToFavorites(clickableNode, object : KrScriptActionHandler.AddToFavoritesHandler {
            override fun onAddToFavorites(clickableNode: ClickableNode, intent: Intent?) {
                intent?.let {
                    DialogHelper.confirm(requireActivity(), getString(R.string.kr_shortcut_create),
                        getString(R.string.kr_shortcut_create_desc).format(clickableNode.title)
                    ) {
                        val result = ActionShortcutManager(requireContext()).addShortcut(
                            intent,
                            IconPathAnalysis().loadLogo(requireContext(), clickableNode),
                            clickableNode
                        )
                        Toast.makeText(requireContext(),
                            if (result) getString(R.string.kr_shortcut_create_success) else getString(R.string.kr_shortcut_create_fail),
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        })
    }

    override fun onPickerClick(item: PickerNode, onCompleted: Runnable) {
        if (!nodeUnlocked(item)) return

        val callback = { pickerExecute(item, onCompleted) }

        when {
            item.confirm -> DialogHelper.warning(requireActivity(), item.title, item.desc, callback)
            item.warning.isNotEmpty() -> DialogHelper.warning(requireActivity(), item.title, item.warning, callback)
            else -> callback()
        }
    }

    private fun pickerExecute(item: PickerNode, onCompleted: Runnable) {
        val paramInfo = ActionParamInfo().apply {
            options = item.options
            optionsSh = item.optionsSh
            separator = item.separator
        }

        lifecycleScope.launch {
            progressBarDialog.showDialog(getString(R.string.kr_param_options_load))
            val valueFromShell = withContext(Dispatchers.IO) {
                item.getState?.let { executeScriptGetResult(it, item) }
            }
            paramInfo.valueFromShell = valueFromShell

            val options = withContext(Dispatchers.IO) { getParamOptions(paramInfo, item) }
            progressBarDialog.hideDialog()

            if (options.isNullOrEmpty()) {
                Toast.makeText(requireContext(), getString(R.string.picker_not_item), Toast.LENGTH_SHORT).show()
                return@launch
            }

            val darkMode = themeMode?.isDarkMode ?: false
            DialogItemChooser(darkMode, options, item.multiple, object : DialogItemChooser.Callback {
                override fun onConfirm(selected: List<SelectItem>, status: BooleanArray) {
                    if (item.multiple) {
                        pickerExecute(item, selected.joinToString(item.separator) { it.value }, onCompleted)
                    } else {
                        if (selected.isNotEmpty()) {
                            pickerExecute(item, selected[0].value, onCompleted)
                        } else {
                            Toast.makeText(context, getString(R.string.picker_select_none), Toast.LENGTH_SHORT).show()
                        }
                    }
                }
            }).show(parentFragmentManager, "picker-item-chooser")
        }
    }

    private fun pickerExecute(pickerNode: PickerNode, toValue: String, onExit: Runnable) {
        val script = pickerNode.setState ?: return
        actionExecute(pickerNode, script, onExit, hashMapOf("state" to toValue))
    }

    override fun onActionClick(item: ActionNode, onCompleted: Runnable) {
        if (!nodeUnlocked(item)) return

        val callback = { actionExecute(item, onCompleted) }

        when {
            item.confirm -> DialogHelper.warning(requireActivity(), item.title, item.desc, callback)
            item.warning.isNotEmpty() && (item.params.isNullOrEmpty()) -> DialogHelper.warning(requireActivity(), item.title, item.warning, callback)
            else -> callback()
        }
    }

    //==================== Execute Actions ====================

    private fun getParamOptions(actionParamInfo: ActionParamInfo, nodeInfoBase: NodeInfoBase): ArrayList<SelectItem>? {
        val options = ArrayList<SelectItem>()
        val shellResult = if (actionParamInfo.optionsSh.isNotEmpty()) {
            executeScriptGetResult(actionParamInfo.optionsSh, nodeInfoBase)
        } else ""
        if (shellResult.isNotEmpty() && shellResult != "error" && shellResult != "null") {
            shellResult.lines().forEach { line ->
                val parts = line.split("|")
                options.add(SelectItem().apply {
                    title = parts.getOrNull(1) ?: parts[0]
                    value = parts[0]
                })
            }
        } else if (!actionParamInfo.options.isNullOrEmpty()) {
            options.addAll(actionParamInfo.options!!)
        } else return null
        return options
    }

    private fun executeScriptGetResult(shellScript: String, nodeInfoBase: NodeInfoBase): String {
        return ScriptEnvironmen.executeResultRoot(requireContext(), shellScript, nodeInfoBase)
    }

    private fun actionExecute(nodeInfo: RunnableNode, script: String, onExit: Runnable, params: HashMap<String, String>? = null) {
        val ctx = requireContext()
        when (nodeInfo.shell) {
            RunnableNode.shellModeBgTask -> {
                BgTaskThread.startTask(ctx, script, params, nodeInfo, onExit) {
                    krScriptActionHandler?.onActionCompleted(nodeInfo)
                }
            }
            RunnableNode.shellModeHidden -> {
                if (hiddenTaskRunning) {
                    Toast.makeText(ctx, getString(R.string.kr_hidden_task_running), Toast.LENGTH_SHORT).show()
                } else {
                    hiddenTaskRunning = true
                    HiddenTaskThread.startTask(ctx, script, params, nodeInfo, onExit) {
                        hiddenTaskRunning = false
                        krScriptActionHandler?.onActionCompleted(nodeInfo)
                    }
                }
            }
            else -> {
                val darkMode = themeMode?.isDarkMode ?: false
                DialogLogFragment.create(nodeInfo, onExit, Runnable {
                    krScriptActionHandler?.onActionCompleted(nodeInfo)
                }, script, params, darkMode).apply {
                    isCancelable = false
                    show(parentFragmentManager, "")
                }
            }
        }
    }
}