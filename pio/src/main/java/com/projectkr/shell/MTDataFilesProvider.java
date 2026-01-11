package com.projectkr.shell;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
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

    public static final String[] g = {
            "root_id", "mime_types", "flags", "icon",
            "title", "summary", "document_id"
    };

    public static final String[] h = {
            "document_id", "mime_type", "_display_name",
            "last_modified", "flags", "_size", "mt_extras"
    };

    public String b;
    public File c; // data
    public File d; // user_de
    public File e; // android_data
    public File f; // obb

    // ===================== DELETE =====================

    public static boolean a(File file) {
        if (file == null || !file.exists()) return false;

        try {
            if ((Os.lstat(file.getPath()).st_mode & 61440) == 40960) {
                return file.delete();
            }
        } catch (Exception ignored) {}

        if (file.isDirectory()) {
            File[] list = file.listFiles();
            if (list != null) {
                for (File x : list) {
                    if (!a(x)) return false;
                }
            }
        }
        return file.delete();
    }

    // ===================== MIME =====================

    public static String c(File file) {
        if (file.isDirectory()) {
            return "vnd.android.document/directory";
        }
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot < 0) return "application/octet-stream";

        String mime = MimeTypeMap.getSingleton()
                .getMimeTypeFromExtension(name.substring(dot + 1).toLowerCase());

        return mime != null ? mime : "application/octet-stream";
    }

    // ===================== INIT =====================

    @Override
    public void attachInfo(Context context, ProviderInfo info) {
        super.attachInfo(context, info);

        b = context.getPackageName();

        File parent = context.getFilesDir().getParentFile();
        c = parent;

        String path = parent.getPath();
        if (path.startsWith("/data/user/")) {
            d = new File("/data/user_de/" + path.substring(11));
        }

        File ext = context.getExternalFilesDir(null);
        if (ext != null) {
            e = ext.getParentFile();
        }

        f = context.getObbDir();
    }

    // ===================== PATH =====================

    public File b(String docId, boolean mustExist) throws FileNotFoundException {
        if (!docId.startsWith(b)) {
            throw new FileNotFoundException(docId + " not found");
        }

        String sub = docId.substring(b.length());
        if (sub.startsWith("/")) sub = sub.substring(1);
        if (sub.isEmpty()) return null;

        String type;
        String rest = "";

        int idx = sub.indexOf('/');
        if (idx >= 0) {
            type = sub.substring(0, idx);
            rest = sub.substring(idx + 1);
        } else {
            type = sub;
        }

        File base = null;
        if ("data".equalsIgnoreCase(type)) base = c;
        else if ("android_data".equalsIgnoreCase(type)) base = e;
        else if ("android_obb".equalsIgnoreCase(type)) base = f;
        else if ("user_de_data".equalsIgnoreCase(type)) base = d;

        if (base == null) throw new FileNotFoundException(docId);

        File out = rest.isEmpty() ? base : new File(base, rest);

        if (mustExist) {
            try {
                Os.lstat(out.getPath());
            } catch (Exception ex) {
                throw new FileNotFoundException(docId);
            }
        }
        return out;
    }

    // ===================== ROOT =====================

    @Override
    public Cursor queryRoots(String[] projection) {
        Context ctx = getContext();
        ApplicationInfo app = ctx.getApplicationInfo();

        if (projection == null) projection = g;

        StatFs stat = new StatFs(Environment.getDataDirectory().getAbsolutePath());
        long free = stat.getAvailableBytes();

        String summary =
                ctx.getString(R.string.storage_available)
                        + ": "
                        + Formatter.formatFileSize(ctx, free);

        MatrixCursor c = new MatrixCursor(projection);
        MatrixCursor.RowBuilder r = c.newRow();

        r.add("root_id", b);
        r.add("document_id", b);
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
        if (projection == null) projection = h;

        MatrixCursor c = new MatrixCursor(projection);
        d(c, docId, null);
        return c;
    }

    @Override
    public Cursor queryChildDocuments(String docId, String[] projection, String sortOrder) {
        if (projection == null) projection = h;
        if (docId.endsWith("/")) docId = docId.substring(0, docId.length() - 1);

        MatrixCursor c = new MatrixCursor(projection);

        File f0 = b(docId, true);
        if (f0 == null) {
            d(c, docId + "/data", null);
            if (e != null && e.exists()) d(c, docId + "/android_data", e);
            if (f != null && f.exists()) d(c, docId + "/android_obb", f);
            if (d != null && d.exists()) d(c, docId + "/user_de_data", d);
        } else {
            File[] list = f0.listFiles();
            if (list != null) {
                for (File x : list) {
                    d(c, docId + "/" + x.getName(), x);
                }
            }
        }
        return c;
    }

    public void d(MatrixCursor c0, String docId, File f0) {
        File f = f0 != null ? f0 : b(docId, true);

        if (f == null) return;

        int flags = 0;
        if (f.canWrite()) flags |= f.isDirectory() ? 8 : 2;
        if (f.getParentFile() != null && f.getParentFile().canWrite()) {
            flags |= 4 | 64;
        }

        MatrixCursor.RowBuilder r = c0.newRow();
        r.add("document_id", docId);
        r.add("_display_name", f.getName());
        r.add("_size", f.length());
        r.add("mime_type", c(f));
        r.add("last_modified", f.lastModified());
        r.add("flags", flags);
        r.add("mt_path", f.getAbsolutePath());

        try {
            StructStat st = Os.lstat(f.getPath());
            StringBuilder sb = new StringBuilder();
            sb.append(st.st_mode).append("|")
              .append(st.st_uid).append("|")
              .append(st.st_gid);
            if ((st.st_mode & 61440) == 40960) {
                sb.append("|").append(Os.readlink(f.getPath()));
            }
            r.add("mt_extras", sb.toString());
        } catch (Exception ignored) {}
    }

    // ===================== OPS =====================

    @Override
    public ParcelFileDescriptor openDocument(String docId, String mode,
                                             CancellationSignal signal)
            throws FileNotFoundException {
        return ParcelFileDescriptor.open(b(docId, false),
                ParcelFileDescriptor.parseMode(mode));
    }

    @Override
    public void deleteDocument(String docId) throws FileNotFoundException {
        File f = b(docId, true);
        if (!a(f)) throw new FileNotFoundException(docId);
    }

    @Override
    public boolean onCreate() {
        return true;
    }
}