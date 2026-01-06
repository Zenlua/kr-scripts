package com.omarea.krscript.ui

import android.app.AlertDialog
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowInsetsController
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
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

class ActionListFragment : androidx.fragment.app.Fragment(), PageLayoutRender.OnItemClickListener {
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

    private lateinit var progressBarDialog: ProgressBarDialog
    private var krScriptActionHandler: KrScriptActionHandler? = null
    private var autoRunTask: AutoRunTask? = null
    private var themeMode: ThemeMode? = null

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

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.kr_action_list_fragment, container, false)
    }

    private lateinit var rootGroup: ListItemGroup
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        this.progressBarDialog = ProgressBarDialog(this.activity!!)

        rootGroup = ListItemGroup(this.context!!, true, GroupNode(""))

        if (actionInfos != null) {
            PageLayoutRender(this.context!!, actionInfos!!, this, rootGroup)
            val layout = rootGroup.getView()

            val rootView = this.view?.findViewById<ScrollView>(R.id.kr_content)
            rootView?.removeAllViews()
            rootView?.addView(layout)
            triggerAction(autoRunTask)
        }
    }

    private fun triggerAction(autoRunTask: AutoRunTask?) {
        autoRunTask?.run {
            if (!key.isNullOrEmpty()) {
                onCompleted(rootGroup.triggerActionByKey(key!!))
            }
        }
    }

    private fun nodeUnlocked(clickableNode: ClickableNode): Boolean {
        val currentSDK = Build.VERSION.SDK_INT
        if (clickableNode.targetSdkVersion > 0 && currentSDK != clickableNode.targetSdkVersion) {
            DialogHelper.helpInfo(
                context!!,
                getString(R.string.kr_sdk_discrepancy),
                getString(R.string.kr_sdk_discrepancy_message).format(clickableNode.targetSdkVersion)
            )
            return false
        } else if (currentSDK > clickableNode.maxSdkVersion) {
            DialogHelper.helpInfo(
                context!!,
                getString(R.string.kr_sdk_overtop),
                getString(R.string.kr_sdk_message).format(clickableNode.minSdkVersion, clickableNode.maxSdkVersion)
            )
            return false
        } else if (currentSDK < clickableNode.minSdkVersion) {
            DialogHelper.helpInfo(
                context!!,
                getString(R.string.kr_sdk_too_low),
                getString(R.string.kr_sdk_message).format(clickableNode.minSdkVersion, clickableNode.maxSdkVersion)
            )
            return false
        }

        var message = ""
        val unlocked = (if (clickableNode.lockShell.isNotEmpty()) {
            message = ScriptEnvironmen.executeResultRoot(context, clickableNode.lockShell, clickableNode)
            message == "unlock" || message == "unlocked" || message == "false" || message == "0"
        } else {
            !clickableNode.locked
        })
        if (!unlocked) {
            Toast.makeText(
                context,
                if (message.isNotEmpty()) message else getString(R.string.kr_lock_message),
                Toast.LENGTH_LONG
            ).show()
        }
        return unlocked
    }

    /**
     * 当switch项被点击
     */
    override fun onSwitchClick(item: SwitchNode, onCompleted: Runnable) {
        if (nodeUnlocked(item)) {
            val toValue = !item.checked
            if (item.confirm) {
                DialogHelper.warning(activity!!, item.title, item.desc) {
                    switchExecute(item, toValue, onCompleted)
                }
            } else if (item.warning.isNotEmpty()) {
                DialogHelper.warning(activity!!, item.title, item.warning) {
                    switchExecute(item, toValue, onCompleted)
                }
            } else {
                switchExecute(item, toValue, onCompleted)
            }
        }
    }

    private fun switchExecute(switchNode: SwitchNode, toValue: Boolean, onExit: Runnable) {
        val script = switchNode.setState ?: return
        actionExecute(switchNode, script, onExit, hashMapOf("state" to if (toValue) "1" else "0"))
    }

    override fun onPageClick(item: PageNode, onCompleted: Runnable) {
        if (nodeUnlocked(item)) {
            if (context != null && item.link.isNotEmpty()) {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(item.link))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    context?.startActivity(intent)
                } catch (ex: Exception) {
                    Toast.makeText(context, context?.getString(R.string.kr_slice_activity_fail), Toast.LENGTH_SHORT).show()
                }
            } else if (context != null && item.activity.isNotEmpty()) {
                TryOpenActivity(context!!, item.activity).tryOpen()
            } else {
                krScriptActionHandler?.onSubPageClick(item)
            }
        }
    }

    // 长按 添加收藏
    override fun onItemLongClick(clickableNode: ClickableNode) {
        if (clickableNode.key.isEmpty()) {
            DialogHelper.alert(
                activity!!,
                getString(R.string.kr_shortcut_create_fail),
                getString(R.string.kr_ushortcut_nsupported)
            )
        } else {
            krScriptActionHandler?.addToFavorites(clickableNode, object : KrScriptActionHandler.AddToFavoritesHandler {
                override fun onAddToFavorites(clickableNode: ClickableNode, intent: Intent?) {
                    if (intent != null) {
                        DialogHelper.confirm(activity!!,
                            getString(R.string.kr_shortcut_create),
                            String.format(getString(R.string.kr_shortcut_create_desc), clickableNode.title)
                        ) {
                            val result = ActionShortcutManager(context!!)
                                .addShortcut(intent, IconPathAnalysis().loadLogo(context!!, clickableNode), clickableNode)
                            if (!result) {
                                Toast.makeText(context, R.string.kr_shortcut_create_fail, Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(context, getString(R.string.kr_shortcut_create_success), Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                }
            })
        }
    }

    /**
     * Picker点击
     */
    override fun onPickerClick(item: PickerNode, onCompleted: Runnable) {
        if (nodeUnlocked(item)) {
            if (item.confirm) {
                DialogHelper.warning(activity!!, item.title, item.desc) { pickerExecute(item, onCompleted) }
            } else if (item.warning.isNotEmpty()) {
                DialogHelper.warning(activity!!, item.title, item.warning) { pickerExecute(item, onCompleted) }
            } else {
                pickerExecute(item, onCompleted)
            }
        }
    }

    private fun pickerExecute(item: PickerNode, onCompleted: Runnable) {
        val paramInfo = ActionParamInfo()
        paramInfo.options = item.options
        paramInfo.optionsSh = item.optionsSh
        paramInfo.separator = item.separator

        val handler = Handler(Looper.getMainLooper()) // ✅ sửa deprecated

        progressBarDialog.showDialog(getString(R.string.kr_param_options_load))
        Thread {
            if (item.getState != null) {
                paramInfo.valueFromShell = executeScriptGetResult(item.getState!!, item)
            }

            val options = getParamOptions(paramInfo, item)
            val optionsSorted = options?.let { ActionParamsLayoutRender.setParamOptionsSelectedStatus(paramInfo, it) }

            handler.post {
                progressBarDialog.hideDialog()

                if (optionsSorted != null) {
                    val darkMode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                        val controller = activity?.window?.insetsController
                        controller?.systemBarsAppearance?.let {
                            it and WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS == 0
                        } ?: false
                    } else {
                        val systemUiVisibility = activity!!.window.decorView.systemUiVisibility
                        (systemUiVisibility and View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR) == 0
                    }

                    DialogItemChooser(darkMode, optionsSorted, item.multiple, object : DialogItemChooser.Callback {
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
                    }).show(parentFragmentManager, "picker-item-chooser") // ✅ sửa deprecated
                } else {
                    Toast.makeText(context, getString(R.string.picker_not_item), Toast.LENGTH_SHORT).show()
                }
            }
        }.start()
    }

    private fun pickerExecute(pickerNode: PickerNode, toValue: String, onExit: Runnable) {
        val script = pickerNode.setState ?: return
        actionExecute(pickerNode, script, onExit, hashMapOf("state" to toValue))
    }

    private fun getParamOptions(actionParamInfo: ActionParamInfo, nodeInfoBase: NodeInfoBase): ArrayList<SelectItem>? {
        val options = ArrayList<SelectItem>()
        var shellResult = ""
        if (actionParamInfo.optionsSh.isNotEmpty()) {
            shellResult = executeScriptGetResult(actionParamInfo.optionsSh, nodeInfoBase)
        }

        if (shellResult.isNotEmpty() && shellResult != "error" && shellResult != "null") {
            for (item in shellResult.split("\n")) {
                if (item.contains("|")) {
                    val itemSplit = item.split("|")
                    options.add(SelectItem().apply {
                        title = itemSplit.getOrElse(1) { itemSplit[0] }
                        value = itemSplit[0]
                    })
                } else {
                    options.add(SelectItem().apply {
                        title = item
                        value = item
                    })
                }
            }
        } else if (actionParamInfo.options != null) {
            options.addAll(actionParamInfo.options!!)
        } else {
            return null
        }

        return options
    }

    private fun executeScriptGetResult(shellScript: String, nodeInfoBase: NodeInfoBase): String {
        return ScriptEnvironmen.executeResultRoot(context!!, shellScript, nodeInfoBase)
    }

    var hiddenTaskRunning = false
    private fun actionExecute(nodeInfo: RunnableNode, script: String, onExit: Runnable, params: HashMap<String, String>?) {
        val context = context!!

        if (nodeInfo.shell == RunnableNode.shellModeBgTask) {
            BgTaskThread.startTask(context, script, params, nodeInfo, onExit) {
                krScriptActionHandler?.onActionCompleted(nodeInfo)
            }
        } else if (nodeInfo.shell == RunnableNode.shellModeHidden) {
            if (hiddenTaskRunning) {
                Toast.makeText(context, getString(R.string.kr_hidden_task_running), Toast.LENGTH_SHORT).show()
            } else {
                hiddenTaskRunning = true
                HiddenTaskThread.startTask(context, script, params, nodeInfo, onExit) {
                    hiddenTaskRunning = false
                    krScriptActionHandler?.onActionCompleted(nodeInfo)
                }
            }
        } else {
            val darkMode = themeMode?.isDarkMode ?: false
            val dialog = DialogLogFragment.create(nodeInfo, onExit, {
                krScriptActionHandler?.onActionCompleted(nodeInfo)
            }, script, params, darkMode)
            dialog.isCancelable = false
            dialog.show(parentFragmentManager, "") // ✅ sửa deprecated
        }
    }
}