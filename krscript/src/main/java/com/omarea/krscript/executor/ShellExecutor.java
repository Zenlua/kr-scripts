package com.omarea.krscript.executor;

import android.content.Context;
import android.util.Log;
import android.widget.Toast;

import com.omarea.krscript.model.RunnableNode;
import com.omarea.krscript.model.ShellHandlerBase;

import java.io.DataOutputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Objects;

public class ShellExecutor {
    private boolean started = false;
    private final String sessionTag = "pio_" + System.currentTimeMillis();
    private void killProcess(Context context) {
        ScriptEnvironmen.executeResultRoot(
                context,
                String.format("shell_progres=\'%s\' killtree", sessionTag),
                null);

    public Process execute(final Context context, RunnableNode nodeInfo, String cmds, Runnable onExit, HashMap<String, String> params, ShellHandlerBase shellHandlerBase) {
        if (started) {
            return null;
        }

        final Process process = ScriptEnvironmen.getRuntime();
        if (process == null) {
            Toast.makeText(context, "Failed to start command line process", Toast.LENGTH_SHORT).show();
            if (onExit != null) {
                onExit.run();
            }
        } else {
            final Runnable forceStopRunnable = (nodeInfo.getInterruptable() || nodeInfo.getShell().equals(RunnableNode.Companion.getShellModeBgTask()))? (() -> {

                killProcess(context);

                try {
                    process.getInputStream().close();
                } catch (Exception ignored) {}
                try {
                    process.getOutputStream().close();
                } catch (Exception ignored) {}
                try {
                    process.getErrorStream().close();
                } catch (Exception ignored) {}

                try {
                    process.destroyForcibly();
                } catch (Exception ex) {
                    Log.e("KrScriptError", Objects.requireNonNull(ex.getMessage()));
                }
            }) : null;
            new SimpleShellWatcher().setHandler(context, process, shellHandlerBase, onExit);

            final OutputStream outputStream = process.getOutputStream();
            DataOutputStream dataOutputStream = new DataOutputStream(outputStream);
            try {
                shellHandlerBase.sendMessage(shellHandlerBase.obtainMessage(ShellHandlerBase.EVENT_START, "shell@android:\n"));
                shellHandlerBase.sendMessage(shellHandlerBase.obtainMessage(ShellHandlerBase.EVENT_START, cmds + "\n\n"));
                shellHandlerBase.onStart(forceStopRunnable);
                dataOutputStream.writeBytes("sleep 0.2;\n");

                ScriptEnvironmen.executeShell(context, dataOutputStream, cmds, params, nodeInfo, sessionTag);
            } catch (Exception ex) {
                process.destroy();
            }
            started = true;
        }
        return process;
    }
}
