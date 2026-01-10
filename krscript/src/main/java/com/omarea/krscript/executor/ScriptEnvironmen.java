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
import com.omarea.common.shell.ShellOutput;
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
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

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
        SharedPreferences sp = context.getSharedPreferences("kr-script-config", Context.MODE_PRIVATE);
        return init(
                context,
                sp.getString("executor", "kr-script/executor.sh"),
                sp.getString("toolkitDir", "kr-script/toolkit")
        );
    }

    public static boolean init(Context context, String executor, String toolkitDir) {
        if (inited) return true;

        shellTranslation = new ShellTranslation(context.getApplicationContext());
        rooted = KeepShellPublic.INSTANCE.checkRoot();

        try {
            if (toolkitDir != null && !toolkitDir.isEmpty()) {
                TOOKIT_DIR = new ExtractAssets(context).extractResources(toolkitDir);
            }

            String fileName = executor.startsWith(ASSETS_FILE)
                    ? executor.substring(ASSETS_FILE.length())
                    : executor;

            InputStream is = context.getAssets().open(fileName);
            byte[] buf = new byte[is.available()];
            is.read(buf);
            String envShell = new String(buf, Charset.defaultCharset()).replace("\r", "");

            HashMap<String, String> env = getEnvironment(context);
            for (String k : env.keySet()) {
                envShell = envShell.replace("$({" + k + "})", env.get(k));
            }

            String outPath = FileWrite.INSTANCE.getPrivateFilePath(context, fileName);
            envShell = envShell.replace("$({EXECUTOR_PATH})", outPath);

            inited = FileWrite.INSTANCE.writePrivateFile(
                    envShell.getBytes(Charset.defaultCharset()),
                    fileName,
                    context
            );

            if (inited) {
                environmentPath = outPath;
            }

            privateShell = rooted
                    ? KeepShellPublic.INSTANCE.getDefaultInstance()
                    : new KeepShell(false);

            return inited;
        } catch (Exception e) {
            return false;
        }
    }

    // ================================
    // 🟢 API CŨ – GIỮ NGUYÊN (String)
    // ================================
    public static String executeResultRoot(
            Context context,
            String script,
            NodeInfoBase nodeInfo
    ) {
        List<ShellOutput> outputs =
                executeResultRootOutputs(context, script, nodeInfo);

        StringBuilder sb = new StringBuilder();
        for (ShellOutput o : outputs) {
            if (o instanceof ShellOutput.Text) {
                sb.append(((ShellOutput.Text) o).getText()).append("\n");
            }
        }
        return sb.toString().trim();
    }

    // ================================
    // 🔥 API MỚI – HỖ TRỢ @img
    // ================================
    public static List<ShellOutput> executeResultRootOutputs(
            Context context,
            String script,
            NodeInfoBase nodeInfo
    ) {
        if (!inited) init(context);
        if (script == null || script.isEmpty()) return new ArrayList<>();

        String path;
        if (script.startsWith(ASSETS_FILE)) {
            path = extractScript(context, script);
        } else {
            path = createShellCache(context, script);
        }

        StringBuilder sb = new StringBuilder("\n");

        if (nodeInfo != null && !nodeInfo.getCurrentPageConfigPath().isEmpty()) {
            String dir = nodeInfo.getPageConfigDir();
            String file = nodeInfo.getCurrentPageConfigPath();

            sb.append("export PAGE_CONFIG_DIR='").append(dir).append("'\n");
            sb.append("export PAGE_CONFIG_FILE='").append(file).append("'\n");

            if (file.startsWith(ASSETS_FILE)) {
                sb.append("export PAGE_WORK_DIR='")
                        .append(new ExtractAssets(context).getExtractPath(dir))
                        .append("'\n");
                sb.append("export PAGE_WORK_FILE='")
                        .append(new ExtractAssets(context).getExtractPath(file))
                        .append("'\n");
            } else {
                sb.append("export PAGE_WORK_DIR='").append(dir).append("'\n");
                sb.append("export PAGE_WORK_FILE='").append(file).append("'\n");
            }
        } else {
            sb.append("export PAGE_CONFIG_DIR=''\n");
            sb.append("export PAGE_CONFIG_FILE=''\n");
            sb.append("export PAGE_WORK_DIR=''\n");
            sb.append("export PAGE_WORK_FILE=''\n");
        }

        sb.append("\n\n")
          .append(environmentPath)
          .append(" \"")
          .append(path)
          .append("\"");

        String raw = privateShell.doCmdSync(sb.toString());
        List<String> rows = Arrays.asList(raw.split("\n"));

        return shellTranslation != null
                ? shellTranslation.resolveLines(rows)
                : new ArrayList<>();
    }

    // ================================
    // (CÁC HÀM PHỤ – GIỮ NGUYÊN)
    // ================================

    private static String createShellCache(Context context, String script) {
        String md5 = md5(script);
        String path = "kr-script/cache/" + md5 + ".sh";
        if (new File(path).exists()) return path;

        byte[] bytes = ("#!/system/bin/sh\n\n" + script)
                .replace("\r\n", "\n")
                .replace("\r", "\n")
                .getBytes();

        if (FileWrite.INSTANCE.writePrivateFile(bytes, path, context)) {
            return FileWrite.INSTANCE.getPrivateFilePath(context, path);
        }
        return "";
    }

    private static String extractScript(Context context, String fileName) {
        if (fileName.startsWith(ASSETS_FILE)) {
            fileName = fileName.substring(ASSETS_FILE.length());
        }
        return FileWrite.INSTANCE.writePrivateShellFile(fileName, fileName, context);
    }

    private static String md5(String s) {
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");
            byte[] b = md.digest(s.getBytes());
            StringBuilder sb = new StringBuilder();
            for (byte x : b) {
                sb.append(String.format("%02x", x));
            }
            return sb.toString();
        } catch (Exception e) {
            return "";
        }
    }

    private static HashMap<String, String> getEnvironment(Context context) {
        HashMap<String, String> p = new HashMap<>();
        p.put("TOOLKIT", TOOKIT_DIR);
        p.put("START_DIR", FileWrite.INSTANCE.getPrivateFileDir(context));
        p.put("TEMP_DIR", context.getCacheDir().getAbsolutePath());
        p.put("ROOT_PERMISSION", rooted ? "true" : "false");
        p.put("SDCARD_PATH", Environment.getExternalStorageDirectory().getAbsolutePath());
        p.put("ANDROID_SDK", String.valueOf(Build.VERSION.SDK_INT));
        return p;
    }
}
