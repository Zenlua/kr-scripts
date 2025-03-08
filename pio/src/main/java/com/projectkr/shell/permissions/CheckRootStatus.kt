package com.projectkr.shell.permissions

import android.Manifest
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.Button
import androidx.core.content.PermissionChecker
import com.omarea.common.shell.KeepShellPublic
import com.omarea.common.ui.DialogHelper
import com.projectkr.shell.R
import kotlin.system.exitProcess

/**
 * 检查获取root权限
 * Created by helloklf on 2017/6/3.
 */

class CheckRootStatus(var context: Context, private var next: Runnable? = null) {
    var myHandler: Handler = Handler(Looper.getMainLooper())

    var therad: Thread? = null
    fun forceGetRoot() {
        if (lastCheckResult) {
            if (next != null) {
                myHandler.post(next)
            }
        } else {
            var completed = false
            therad = Thread {
                rootStatus = KeepShellPublic.checkRoot()
                if (completed) {
                    return@Thread
                }

                completed = true

                if (lastCheckResult) {
                    if (next != null) {
                        myHandler.post(next)
                    }
                } else {
                    myHandler.post {
                        KeepShellPublic.tryExit()
                        val layoutInflater = LayoutInflater.from(context)
                        val layout = layoutInflater.inflate(R.layout.alertdialog, null)

                        // first u need to get button from layout by its id
                        (layout.findViewById<Button>(R.id.button_exit)!!).setOnClickListener {
                            exitProcess(0)
                        }
                        (layout.findViewById<Button>(R.id.button_skip)!!).setOnClickListener {
                            if (next != null){
                                myHandler.post(next)}
                        }
                        (layout.findViewById<Button>(R.id.button_retry)!!).setOnClickListener {
                               KeepShellPublic.tryExit()
                            if (therad != null && therad!!.isAlive && !therad!!.isInterrupted){
                                therad!!.interrupt()
                                therad = null
                            }
                            forceGetRoot()
                        }
                        DialogHelper.customDialog(context, layout, cancelable = false)
                    }
                }
            }
            therad!!.start()
            Thread(Runnable {
                Thread.sleep(1000 * 15)

                if (!completed) {
                    KeepShellPublic.tryExit()
                    myHandler.post {
                        DialogHelper.confirm(context,
                        context.getString(R.string.error_root),
                        context.getString(R.string.error_su_timeout),
                        null,
                        DialogHelper.DialogButton(context.getString(R.string.btn_retry), {
                            if (therad != null && therad!!.isAlive && !therad!!.isInterrupted) {
                                therad!!.interrupt()
                                therad = null
                            }
                            forceGetRoot()
                        }),
                        DialogHelper.DialogButton(context.getString(R.string.btn_exit), {
                            exitProcess(0)
                        }))
                    }
                }
            }).start()
        }
    }

    companion object {
        private var rootStatus = false
        private fun checkPermission(context: Context, permission: String): Boolean = PermissionChecker.checkSelfPermission(context, permission) == PermissionChecker.PERMISSION_GRANTED
        fun grantPermission(context: Context) {
            val cmds = StringBuilder()
            /*
            // 必需的权限
            val requiredPermission = arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            requiredPermission.forEach {
                if (!checkPermission(context, it)) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        val option = it.substring("android.permission.".length)
                        cmds.append("appops set ${context.packageName} ${option} allow\n")
                    }
                    cmds.append("pm grant ${context.packageName} $it\n")
                }
            }
            */

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (!checkPermission(context, Manifest.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)) {
                    cmds.append("dumpsys deviceidle whitelist +${context.packageName};\n")
                }
            }
            KeepShellPublic.doCmdSync(cmds.toString())
        }

        // 最后的ROOT检测结果
        val lastCheckResult: Boolean
            get() {
                return rootStatus
            }
    }
}
