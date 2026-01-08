package com.omarea.krscript.ui

import android.app.Dialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.DialogInterface
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Message
import android.text.SpannableString
import android.text.SpannableStringBuilder
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.DialogFragment
import com.omarea.common.ui.DialogHelper
import com.omarea.krscript.R
import com.omarea.krscript.databinding.KrDialogLogBinding
import com.omarea.krscript.executor.ShellExecutor
import com.omarea.krscript.model.RunnableNode
import com.omarea.krscript.model.ShellHandlerBase

class DialogLogFragment : DialogFragment() {

    private var binding: KrDialogLogBinding? = null

    private var running = false
    private var canceled = false
    private var uiVisible = false

    private var nodeInfo: RunnableNode? = null
    private lateinit var onExit: Runnable
    private lateinit var script: String
    private var params: HashMap<String, String>? = null
    private var themeResId: Int = 0
    private var onDismissRunnable: Runnable? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = KrDialogLogBinding.inflate(inflater, container, false)
        return binding!!.root
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        return Dialog(
            requireActivity(),
            if (themeResId != 0)
                themeResId
            else
                R.style.kr_full_screen_dialog_light
        )
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        uiVisible = true

        activity?.let {
            dialog?.window?.let { win ->
                DialogHelper.setWindowBlurBg(win, it)
            }
        }

        nodeInfo?.let { node ->
            if (node.reloadPage) {
                binding?.btnHide?.visibility = View.GONE
            }

            val handler = openExecutor(node)
            ShellExecutor().execute(
                activity,
                node,
                script,
                onExit,
                params,
                handler
            )
        } ?: dismissAllowingStateLoss()
    }

    override fun onResume() {
        super.onResume()
        dialog?.setOnKeyListener { _, keyCode, event ->
            if (!uiVisible || !running) return@setOnKeyListener false
            event.action == KeyEvent.ACTION_DOWN &&
                (keyCode == KeyEvent.KEYCODE_VOLUME_UP ||
                 keyCode == KeyEvent.KEYCODE_VOLUME_DOWN)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding = null
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        uiVisible = false
        onDismissRunnable?.run()
        onDismissRunnable = null
    }

    private fun openExecutor(nodeInfo: RunnableNode): ShellHandlerBase {
        var forceStopRunnable: Runnable? = null

        binding?.btnHide?.setOnClickListener {
            uiVisible = false
            dismissAllowingStateLoss()
        }

        binding?.btnExit?.setOnClickListener {
            if (running && !canceled) {
                canceled = true
                forceStopRunnable?.run()
                binding?.btnExit?.text = context?.getString(R.string.btn_exit)
                binding?.btnHide?.visibility = View.GONE
            } else {
                dismissAllowingStateLoss()
            }
        }

        binding?.btnCopy?.setOnClickListener {
            try {
                val cm = requireContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(
                    ClipData.newPlainText(
                        "log",
                        binding?.shellOutput?.text.toString()
                    )
                )
                Toast.makeText(context, R.string.copy_success, Toast.LENGTH_SHORT).show()
            } catch (_: Exception) {
                Toast.makeText(context, R.string.copy_fail, Toast.LENGTH_SHORT).show()
            }
        }

        if (nodeInfo.interruptable) {
            binding?.btnExit?.visibility = View.VISIBLE
            binding?.btnHide?.visibility = View.VISIBLE
        } else {
            binding?.btnExit?.visibility = View.GONE
            binding?.btnHide?.visibility = View.GONE
        }

        binding?.title?.apply {
            if (nodeInfo.title.isNotEmpty()) text = nodeInfo.title
            else visibility = View.GONE
        }

        binding?.desc?.apply {
            if (nodeInfo.desc.isNotEmpty()) text = nodeInfo.desc
            else visibility = View.GONE
        }

        binding?.actionProgress?.isIndeterminate = true

        return MyShellHandler(
            object : IActionEventHandler {
                override fun onStart(forceStop: Runnable?) {
                    running = true
                    canceled = false
                    forceStopRunnable = forceStop
                    binding?.btnExit?.apply {
                        visibility = View.VISIBLE
                        text = context?.getString(R.string.btn_cancel)
                        setBackgroundColor(context?.getColor(com.omarea.common.R.color.warning) ?: Color.parseColor("#FFB300"))
                    }
                }

                override fun onCompleted() {
                    running = false
                    onExit.run()
                    binding?.btnExit?.apply {
                        visibility = View.VISIBLE
                        text = context?.getString(R.string.btn_exit)
                        setBackgroundColor(context?.getColor(com.omarea.R.color.colorAccent) ?: Color.parseColor("#4ba5ff"))
                    }
                    binding?.btnHide?.visibility = View.GONE
                    binding?.actionProgress?.visibility = View.GONE
                    isCancelable = true
                }

                override fun onSuccess() {
                    if (nodeInfo.autoOff) dismissAllowingStateLoss()
                }
            },
            binding?.shellOutput,
            binding?.actionProgress
        )
    }

    interface IActionEventHandler {
        fun onStart(forceStop: Runnable?)
        fun onCompleted()
        fun onSuccess()
    }

    class MyShellHandler(
        private val handler: IActionEventHandler,
        private val logView: TextView?,
        private val progress: ProgressBar?
    ) : ShellHandlerBase() {

        private val context = logView?.context

        private fun getColor(resId: Int): Int =
            if (Build.VERSION.SDK_INT >= 23)
                context!!.getColor(resId)
            else
                context!!.resources.getColor(resId)

        private val errorColor = getColor(R.color.kr_shell_log_error)
        private val basicColor = getColor(R.color.kr_shell_log_basic)
        private val scriptColor = getColor(R.color.kr_shell_log_script)
        private val endColor = getColor(R.color.kr_shell_log_end)

        private var hasError = false

        private val logBuffer = SpannableStringBuilder()
        @Volatile
        private var flushing = false

        override fun handleMessage(msg: Message) {
            when (msg.what) {
                EVENT_START -> onStart(msg.obj)
                EVENT_REDE -> onReader(msg.obj)
                EVENT_READ_ERROR -> onError(msg.obj)
                EVENT_WRITE -> onWrite(msg.obj)
                EVENT_EXIT -> onExit(msg.obj)
            }
        }

        override fun onStart(msg: Any) {}

        override fun updateLog(msg: SpannableString) {
            appendBuffered(msg)
        }

        override fun onReader(msg: Any) {
            updateColored(msg, basicColor)
        }

        override fun onWrite(msg: Any) {
            updateColored(msg, scriptColor)
        }

        override fun onError(msg: Any) {
            hasError = true
            updateColored(msg, errorColor)
        }

        override fun onStart(forceStop: Runnable?) {
            handler.onStart(forceStop)
            logView?.text = ""
        }

        override fun onExit(msg: Any?) {
            updateColored(context?.getString(R.string.kr_shell_completed), endColor)
            handler.onCompleted()
            if (!hasError) handler.onSuccess()
        }

        override fun onProgress(current: Int, total: Int) {
            when (current) {
                -1 -> {
                    progress?.visibility = View.VISIBLE
                    progress?.isIndeterminate = true
                }
                total -> progress?.visibility = View.GONE
                else -> {
                    progress?.visibility = View.VISIBLE
                    progress?.isIndeterminate = false
                    progress?.max = total
                    progress?.progress = current
                }
            }
        }

        private fun updateColored(msg: Any?, color: Int) {
            val text = SpannableString(msg?.toString() ?: "").apply {
                setSpan(
                    android.text.style.ForegroundColorSpan(color),
                    0,
                    length,
                    SpannableString.SPAN_EXCLUSIVE_EXCLUSIVE
                )
            }
            appendBuffered(text)
        }

        private fun appendBuffered(text: SpannableString) {
            synchronized(logBuffer) {
                logBuffer.append(text)
            }
            if (!flushing) {
                flushing = true
                logView?.postDelayed({
                    val out: SpannableStringBuilder
                    synchronized(logBuffer) {
                        out = SpannableStringBuilder(logBuffer)
                        logBuffer.clear()
                    }
                    logView.append(out)
                    (logView.parent as? ScrollView)
                        ?.fullScroll(ScrollView.FOCUS_DOWN)
                    flushing = false
                }, 80)
            }
        }
    }

    companion object {
        fun create(
            nodeInfo: RunnableNode,
            onExit: Runnable,
            onDismiss: Runnable,
            script: String,
            params: HashMap<String, String>?,
            darkMode: Boolean = false
        ): DialogLogFragment {
            return DialogLogFragment().apply {
                this.nodeInfo = nodeInfo
                this.onExit = onExit
                this.script = script
                this.params = params
                this.themeResId =
                    if (darkMode)
                        R.style.kr_full_screen_dialog_dark
                    else
                        R.style.kr_full_screen_dialog_light
                this.onDismissRunnable = onDismiss
            }
        }
    }
}
