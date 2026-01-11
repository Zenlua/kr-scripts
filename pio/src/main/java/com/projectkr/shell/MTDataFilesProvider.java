package com.projectkr.shell;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;
import android.provider.DocumentsProvider;
import android.system.Os;
import android.text.format.Formatter;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;

public class MTDataFilesProvider extends DocumentsProvider {

    public static final String[] ROOT_PROJECTION = {
            "root_id", "mime_types", "flags", "icon",
            "title", "summary", "document_id"
    };

    public static final String[] DOC_PROJECTION = {
            "document_id", "mime_type", "_display_name",
            "last_modified", "flags", "_size", "mt_extras"
    };

    private String pkg;
    private File dataDir;
    private File userDeDir;
    private File androidDataDir;
    private File obbDir;

    // ===================== UTILS =====================

    private static boolean deleteRecursive(File file) {
        if (file == null || !file.exists()) return false;

        try {
            if ((Os.lstat(file.getPath()).st_mode & 61440) == 40960) {
                return file.delete();
            }
        } catch (Exception ignored) {}

        if (file.isDirectory()) {
            File[] list = file.listFiles();
            if (list != null) {
                for (File f : list) {
                    if (!deleteRecursive(f)) return false;
                }
            }
        }
        return file.delete();
    }

    private static String getMimeType(File file) {
        if (file.isDirectory()) {
            return "vnd.android.document/directory";
        }
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return "application/octet-stream";
        String ext = name.substring(dot + 1).toLowerCase();
        String mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext);
        return mime != null ? mime : "application/octet-stream";
    }

    private static long dirSize(File dir) {
        if (dir == null || !dir.exists()) return 0;
        if (dir.isFile()) return dir.length();

        long size = 0;
        File[] list = dir.listFiles();
        if (list != null) {
            for (File f : list) {
                size += dirSize(f);
            }
        }
        return size;
    }

    // ===================== INIT =====================

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        super.attachInfo(context, info);

        pkg = context.getPackageName();
        dataDir = context.getFilesDir().getParentFile();

        if (dataDir != null && dataDir.getPath().startsWith("/data/user/")) {
            userDeDir = new File("/data/user_de/" + dataDir.getPath().substring(11));
        }

        File ext = context.getExternalFilesDir(null);
        if (ext != null) androidDataDir = ext.getParentFile();

        obbDir = context.getObbDir();
    }

    // ===================== PATH MAP =====================

    private File resolveFile(String docId, boolean mustExist)
            throws FileNotFoundException {

        if (!docId.startsWith(pkg)) {
            throw new FileNotFoundException(docId);
        }

        String sub = docId.substring(pkg.length());
        if (sub.startsWith("/")) sub = sub.substring(1);
        if (sub.isEmpty()) return null;

        String type;
        String rest = "";

        int idx = sub.indexOf('/');
        if (idx > 0) {
            type = sub.substring(0, idx);
            rest = sub.substring(idx + 1);
        } else {
            type = sub;
        }

        File base = null;
        if ("data".equalsIgnoreCase(type)) base = dataDir;
        else if ("android_data".equalsIgnoreCase(type)) base = androidDataDir;
        else if ("android_obb".equalsIgnoreCase(type)) base = obbDir;
        else if ("user_de_data".equalsIgnoreCase(type)) base = userDeDir;

        if (base == null) throw new FileNotFoundException(docId);

        File out = rest.isEmpty() ? base : new File(base, rest);

        if (mustExist && !out.exists()) {
            throw new FileNotFoundException(docId);
        }
        return out;
    }

    // ===================== ROOT =====================

    @Override
    public Cursor queryRoots(String[] projection) {
        if (projection == null) projection = ROOT_PROJECTION;

        Context ctx = getContext();
        ApplicationInfo app = ctx.getApplicationInfo();

        long used =
                dirSize(dataDir) +
                dirSize(userDeDir) +
                dirSize(androidDataDir) +
                dirSize(obbDir);

        StatFs stat = new StatFs(Environment.getDataDirectory().getAbsolutePath());
        long total = stat.getTotalBytes();

        String summary =
                Formatter.formatFileSize(ctx, total)
                        + " • "
                        + Formatter.formatFileSize(ctx, used)
                        + " "
                        + ctx.getString(R.string.storage_used);

        MatrixCursor c = new MatrixCursor(projection);
        MatrixCursor.RowBuilder r = c.newRow();

        r.add("root_id", pkg);
        r.add("document_id", pkg);
        r.add("title", app.loadLabel(ctx.getPackageManager()));
        r.add("summary", summary);
        r.add("flags", 17);
        r.add("mime_types", "*/*");
        r.add("icon", app.icon);

        return c;
    }

    // ===================== DOCUMENT =====================

    @Override
    public Cursor queryDocument(String docId, String[] projection) {
        if (projection == null) projection = DOC_PROJECTION;

        MatrixCursor c = new MatrixCursor(projection);
        try {
            addRow(c, docId, resolveFile(docId, true));
        } catch (FileNotFoundException ignored) {}
        return c;
    }

    @Override
    public Cursor queryChildDocuments(String docId, String[] projection, String sortOrder) {
        if (projection == null) projection = DOC_PROJECTION;
        if (docId.endsWith("/")) docId = docId.substring(0, docId.length() - 1);

        MatrixCursor c = new MatrixCursor(projection);

        try {
            File f = resolveFile(docId, true);
            if (f == null) {
                addRow(c, docId + "/data", dataDir);
                if (androidDataDir != null) addRow(c, docId + "/android_data", androidDataDir);
                if (obbDir != null) addRow(c, docId + "/android_obb", obbDir);
                if (userDeDir != null) addRow(c, docId + "/user_de_data", userDeDir);
            } else {
                File[] list = f.listFiles();
                if (list != null) {
                    for (File x : list) {
                        addRow(c, docId + "/" + x.getName(), x);
                    }
                }
            }
        } catch (FileNotFoundException ignored) {}

        return c;
    }

    private void addRow(MatrixCursor c, String docId, File f) {
        if (f == null) return;

        int flags = 0;
        if (f.canWrite()) flags |= f.isDirectory() ? 8 : 2;
        if (f.getParentFile() != null && f.getParentFile().canWrite()) {
            flags |= 4 | 64;
        }

        MatrixCursor.RowBuilder r = c.newRow();
        r.add("document_id", docId);
        r.add("_display_name", f.getName());
        r.add("_size", f.length());
        r.add("mime_type", getMimeType(f));
        r.add("last_modified", f.lastModified());
        r.add("flags", flags);
        r.add("mt_path", f.getAbsolutePath());
    }

    // ===================== FILE OPS =====================

    @Override
    public ParcelFileDescriptor openDocument(
            String docId, String mode, CancellationSignal signal)
            throws FileNotFoundException {

        return ParcelFileDescriptor.open(
                resolveFile(docId, false),
                ParcelFileDescriptor.parseMode(mode)
        );
    }

    @Override
    public void deleteDocument(String docId) throws FileNotFoundException {
        File f = resolveFile(docId, true);
        if (!deleteRecursive(f)) throw new FileNotFoundException(docId);
    }

    @Override
    public boolean onCreate() {
        return true;
    }
}