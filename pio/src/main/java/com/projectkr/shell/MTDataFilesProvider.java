//
// Optimized & fixed version
//
package com.projectkr.shell;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;
import android.provider.DocumentsProvider;
import android.system.ErrnoException;
import android.system.Os;
import android.system.StructStat;
import android.text.format.Formatter;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

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

    // ===================== INIT =====================

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        super.attachInfo(context, info);

        pkg = context.getPackageName();

        dataDir = context.getFilesDir().getParentFile();

        if (dataDir.getPath().startsWith("/data/user/")) {
            userDeDir = new File("/data/user_de/" + dataDir.getPath().substring(11));
        }

        File ext = context.getExternalFilesDir(null);
        if (ext != null) androidDataDir = ext.getParentFile();

        obbDir = context.getObbDir();
    }

    // ===================== PATH MAP =====================

    private File resolveFile(String docId, boolean mustExist) throws FileNotFoundException {
        if (!docId.startsWith(pkg)) {
            throw new FileNotFoundException(docId + " not found");
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

        if (base == null) throw new FileNotFoundException(docId + " not found");

        File out = rest.isEmpty() ? base : new File(base, rest);

        if (mustExist) {
            try {
                Os.lstat(out.getPath());
            } catch (Exception e) {
                throw new FileNotFoundException(docId + " not found");
            }
        }

        return out;
    }

    // ===================== ROOT =====================

    @Override
    public Cursor queryRoots(String[] projection) {
        if (projection == null) projection = ROOT_PROJECTION;

        Context ctx = getContext();
        ApplicationInfo app = ctx.getApplicationInfo();

        // ==== STORAGE INFO ====
        File dir = Environment.getDataDirectory();
        StatFs stat = new StatFs(dir.getAbsolutePath());

        long total, free;
        if (Build.VERSION.SDK_INT >= 18) {
            total = stat.getTotalBytes();
            free = stat.getAvailableBytes();
        } else {
            long bs = stat.getBlockSize();
            total = stat.getBlockCount() * bs;
            free = stat.getAvailableBlocks() * bs;
        }

        long used = total - free;

        String freeStr = Formatter.formatFileSize(ctx, free);
        String usedStr = Formatter.formatFileSize(ctx, used);

        String summary = freeStr + " / " + usedStr + " " +
                ctx.getString(R.string.storage_used);

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

    private void addRow(MatrixCursor c, String docId, File f) {
        if (f == null) {
            c.newRow()
                    .add("document_id", pkg)
                    .add("_display_name", pkg)
                    .add("_size", 0)
                    .add("mime_type", "vnd.android.document/directory")
                    .add("last_modified", 0)
                    .add("flags", 0);
            return;
        }

        int flags = 0;
        if (f.canWrite()) flags |= f.isDirectory() ? 8 : 2;
        if (f.getParentFile() != null && f.getParentFile().canWrite()) {
            flags |= 4 | 64;
        }

        String name;
        boolean realFile = true;
        String p = f.getPath();

        if (p.equals(dataDir.getPath())) name = "data";
        else if (androidDataDir != null && p.equals(androidDataDir.getPath())) name = "android_data";
        else if (obbDir != null && p.equals(obbDir.getPath())) name = "android_obb";
        else if (userDeDir != null && p.equals(userDeDir.getPath())) name = "user_de_data";
        else {
            name = f.getName();
            realFile = false;
        }

        MatrixCursor.RowBuilder r = c.newRow();
        r.add("document_id", docId);
        r.add("_display_name", name);
        r.add("_size", f.length());
        r.add("mime_type", getMimeType(f));
        r.add("last_modified", f.lastModified());
        r.add("flags", flags);
        r.add("mt_path", f.getAbsolutePath());

        if (!realFile) {
            try {
                StructStat st = Os.lstat(p);
                String extra = st.st_mode + "|" + st.st_uid + "|" + st.st_gid;
                if ((st.st_mode & 61440) == 40960) {
                    extra += "|" + Os.readlink(p);
                }
                r.add("mt_extras", extra);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public Cursor queryDocument(String docId, String[] projection) {
        if (projection == null) projection = DOC_PROJECTION;
        MatrixCursor c = new MatrixCursor(projection);
        addRow(c, docId, resolveFile(docId, true));
        return c;
    }

    @Override
    public Cursor queryChildDocuments(String docId, String[] projection, String sortOrder) {
        if (projection == null) projection = DOC_PROJECTION;
        if (docId.endsWith("/")) docId = docId.substring(0, docId.length() - 1);

        MatrixCursor c = new MatrixCursor(projection);
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
        return c;
    }

    // ===================== FILE OPS =====================

    @Override
    public ParcelFileDescriptor openDocument(String docId, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        return ParcelFileDescriptor.open(resolveFile(docId, false),
                ParcelFileDescriptor.parseMode(mode));
    }

    @Override
    public void deleteDocument(String docId) throws FileNotFoundException {
        File f = resolveFile(docId, true);
        if (!deleteRecursive(f)) {
            throw new FileNotFoundException("Failed to delete " + docId);
        }
    }

    @Override
    public String renameDocument(String docId, String name) throws FileNotFoundException {
        File f = resolveFile(docId, true);
        File n = new File(f.getParentFile(), name);
        if (!f.renameTo(n)) throw new FileNotFoundException();
        return docId.substring(0, docId.lastIndexOf('/')) + "/" + name;
    }

    @Override
    public boolean onCreate() {
        return true;
    }
}
