package com.omarea.common.ui

import android.app.Dialog
import android.os.Build
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.omarea.common.R

open class DialogFullScreen(
    private val layoutRes: Int,
    darkMode: Boolean
) : DialogFragment() {

    private val themeResId: Int = if (darkMode) R.style.dialog_full_screen_dark else R.style.dialog_full_screen_light
    lateinit var currentView: View
        private set

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        currentView = inflater.inflate(layoutRes, container, false)
        return currentView
    }

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialogTheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) themeResId else -1
        return Dialog(requireActivity(), dialogTheme)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        dialog?.window?.apply {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.M) {
                setWindowAnimations(android.R.style.Animation_Translucent)
            }
            context?.let { DialogHelper.setWindowBlurBg(this, it) }
        }
    }

    /** Close dialog safely */
    fun closeView() {
        if (isAdded && !isRemoving) {
            dismissAllowingStateLoss()
        }
    }
}
