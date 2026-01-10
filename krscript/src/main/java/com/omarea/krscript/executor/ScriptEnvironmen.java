package com.omarea.krscript.executor;

import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageInfo;
import android.os.Build;
import android.os.Environment;

import com.omarea.common.shared.FileWrite;
import com.omarea.common.shared.MagiskExtend;
import com.omarea.common.shell.KeepShell;
import com.omarea.common.shell.KeepShellPublic;
import com.omarea.common.shell.ShellTranslation;
import com.omarea.krscript.FileOwner;
import com.omarea.krscript.model.NodeInfoBase;

import java.io.DataOutputStream;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.Charset;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Arrays;

public class ScriptEnvironmen {
    private static final String ASSETS_FILE = "file:///android_asset/";
    private static boolean inited = false;
    private static String environmentPath = "";
    private static String TOOKIT_DIR = "";
    private static boolean rooted = false;
    private static KeepShell privateShell;
    private static ShellTranslation shellTranslation;

    public static boolean isInited() {
        return inited;
    }

    private static boolean init(Context context) {
        SharedPreferences configSpf = context.getSharedPreferences(
                "kr-script-config", Context.MODE_PRIVATE
        );
        return init(
                context,
                configSpf.getString("executor", "kr-script/executor.sh"),
                configSpf.getString("toolkitDir", "kr-script/toolkit")
        );
    }

    public static boolean init(Context context, String executor, String toolkitDir) {
        if (inited) {
            return true;
        }

        shellTranslation = new ShellTranslation(context.getApplicationContext());
        rooted = KeepShellPublic.INSTANCE.checkRoot();

        try {
            if (toolkitDir != null && !toolkitDir.isEmpty()) {
                TOOKIT_DIR = new ExtractAssets(context).extractResources(toolkitDir);
            }

            String fileName = executor;
            if (fileName.startsWith(ASSETS_FILE)) {
                fileName = fileName.substring(ASSETS_FILE.length());
            }

            InputStream inputStream = context.getAssets().open(fileName);
            byte[] bytes = new byte[inputStream.available()];
            inputStream.read(bytes);
            String envShell = new String(bytes, Charset.defaultCharset()).replace("\r", "");

            HashMap<String, String> environment = getEnvironment(context);
            for (String key : environment.keySet()) {
                String value = environment.get(key);
                if (value == null) value = "";
                envShell = envShell.replace("$({" + key + "})", value);
            }

            String outputPathAbs = FileWrite.INSTANCE.getPrivateFilePath(context, fileName);
            envShell = envShell.replace("$({EXECUTOR_PATH})", outputPathAbs);

            inited = FileWrite.INSTANCE.writePrivateFile(
                    envShell.getBytes(Charset.defaultCharset()),
                    fileName,
                    context
            );

            if (inited) {
                environmentPath = outputPathAbs;
            }

            SharedPreferences.Editor editor =
                    context.getSharedPreferences("kr-script-config", Context.MODE_PRIVATE).edit();
            editor.putString("executor", executor);
            editor.putString("toolkitDir", toolkitDir);
            editor.apply();

            privateShell = rooted
                    ? KeepShellPublic.INSTANCE.getDefaultInstance()
                    : new KeepShell(rooted);

            return inited;
        } catch (Exception ex) {
            return false;
        }
    }

    private static String md5(String string) {
        if (string.isEmpty()) return "";
        try {
            MessageDigest md5 = MessageDigest.getInstance("MD5");
            byte[] bytes = md5.digest(string.getBytes());
            StringBuilder result = new StringBuilder();
            for (byte b : bytes) {
                String temp = Integer.toHexString(b & 0xff);
                if (temp.length() == 1) temp = "0" + temp;
                result.append(temp);
            }
            return result.toString();
        } catch (NoSuchAlgorithmException e) {
            return "";
        }
    }

    private static String createShellCache(Context context, String script) {
        String md5 = md5(script);
        String outputPath = "kr-script/cache/" + md5 + ".sh";
        if (new File(outputPath).exists()) {
            return outputPath;
        }

        byte[] bytes = ("#!/system/bin/sh\n\n" + script)
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .getBytes();

        if (FileWrite.INSTANCE.writePrivateFile(bytes, outputPath, context)) {
            return FileWrite.INSTANCE.getPrivateFilePath(context, outputPath);
        }
        return "";
    }

    private static String extractScript(Context context, String fileName) {
        if (fileName.startsWith(ASSETS_FILE)) {
            fileName = fileName.substring(ASSETS_FILE.length());
        }
        return FileWrite.INSTANCE.writePrivateShellFile(fileName, fileName, context);
    }

    /**
     * 🔴 API CŨ – GIỮ NGUYÊN
     * ✅ Chỉ nâng cấp ShellTranslation để hỗ trợ @img
     */
    public static String executeResultRoot(Context context, String script, NodeInfoBase nodeInfoBase) {
        if (!inited) init(context);
        if (script == null || script.isEmpty()) return "";

        String path = script.trim().startsWith(ASSETS_FILE)
                ? extractScript(context, script.trim())
                : createShellCache(context, script);

        StringBuilder sb = new StringBuilder("\n");

        if (nodeInfoBase != null && !nodeInfoBase.getCurrentPageConfigPath().isEmpty()) {
            sb.append("export PAGE_CONFIG_DIR='").append(nodeInfoBase.getPageConfigDir()).append("'\n");
            sb.append("export PAGE_CONFIG_FILE='").append(nodeInfoBase.getCurrentPageConfigPath()).append("'\n");
        } else {
            sb.append("export PAGE_CONFIG_DIR=''\n");
            sb.append("export PAGE_CONFIG_FILE=''\n");
            sb.append("export PAGE_WORK_DIR=''\n");
            sb.append("export PAGE_WORK_FILE=''\n");
        }

        sb.append("\n").append(environmentPath).append(" \"").append(path).append("\"");

        String raw = privateShell.doCmdSync(sb.toString());

        if (shellTranslation != null) {
            // 🔥 NEW: parse img + text (UI mới dùng)
            shellTranslation.resolveLines(Arrays.asList(raw.split("\n")));

            // ✅ API cũ: chỉ trả String
            return shellTranslation.resolveRows(Arrays.asList(raw.split("\n")));
        }

        return raw;
    }

    static Process getRuntime() {
        try {
            return rooted
                    ? Runtime.getRuntime().exec("su")
                    : Runtime.getRuntime().exec("sh");
        } catch (Exception ex) {
            return null;
        }
    }

    public static void executeShell(
            Context context,
            DataOutputStream dataOutputStream,
            String cmds,
            HashMap<String, String> params,
            NodeInfoBase nodeInfo,
            String tag) {

        if (params == null) params = new HashMap<>();

        ArrayList<String> envp = getVariables(params);
        StringBuilder envpCmds = new StringBuilder();
        for (String param : envp) {
            envpCmds.append("export ").append(param).append("\n");
        }

        try {
            dataOutputStream.write(envpCmds.toString().getBytes(StandardCharsets.UTF_8));
            dataOutputStream.write(getExecuteScript(context, cmds, tag).getBytes(StandardCharsets.UTF_8));
            dataOutputStream.writeBytes("\nexit\nexit\n");
            dataOutputStream.flush();
        } catch (Exception ignored) {}
    }

    // ===== helpers giữ nguyên =====

    private static String getExecuteScript(Context context, String script, String tag) {
        if (!inited) init(context);
        String cachePath = script.trim().startsWith(ASSETS_FILE)
                ? extractScript(context, script.trim())
                : createShellCache(context, script);
        return environmentPath + " \"" + cachePath + "\" \"" + tag + "\"";
    }

    private static HashMap<String, String> getEnvironment(Context context) {
        HashMap<String, String> params = new HashMap<>();
        params.put("TOOLKIT", TOOKIT_DIR);
        params.put("TEMP_DIR", context.getCacheDir().getAbsolutePath());
        params.put("ANDROID_SDK", "" + Build.VERSION.SDK_INT);
        params.put("ROOT_PERMISSION", rooted ? "true" : "false");
        params.put("SDCARD_PATH", Environment.getExternalStorageDirectory().getAbsolutePath());
        return params;
    }

    private static ArrayList<String> getVariables(HashMap<String, String> params) {
        ArrayList<String> envp = new ArrayList<>();
        for (String key : params.keySet()) {
            String value = params.get(key);
            if (value == null) value = "";
            envp.add(key + "='" + value.replace("'", "'\\''") + "'");
        }
        return envp;
    }
}
