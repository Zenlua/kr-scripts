package com.projectkr.shell;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.ProviderInfo;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.StatFs;
import android.provider.DocumentsProvider;
import android.system.ErrnoException;
import android.system.Os;
import android.system.StructStat;
import android.webkit.MimeTypeMap;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.List;

public class MTDataFilesProvider extends DocumentsProvider {

    public static final String[] g = {"root_id", "mime_types", "flags", "icon", "title", "summary", "document_id"};
    public static final String[] h = {"document_id", "mime_type", "_display_name", "last_modified", "flags", "_size", "mt_extras"};

    public String b;
    public File c;
    public File d;
    public File e;
    public File f;

    public static boolean a(File file) {
        if (file == null || !file.exists()) return false;

        try {
            if ((Os.lstat(file.getPath()).st_mode & 61440) == 40960) {
                return file.delete();
            }
        } catch (Exception ignored) {}

        if (file.isDirectory()) {
            File[] files = file.listFiles();
            if (files != null) {
                for (File f : files) {
                    if (!a(f)) return false;
                }
            }
        }

        return file.delete();
    }

    private static String formatSizeSmart(long bytes) {
        long gb = bytes / (1024L * 1024L * 1024L);
        if (gb > 0) return gb + "GB";
        long mb = bytes / (1024L * 1024L);
        return mb + "MB";
    }

    public static String c(File file) {
        if (file.isDirectory()) return "vnd.android.document/directory";
        String name = file.getName();
        int lastIndexOf = name.lastIndexOf('.');
        if (lastIndexOf < 0) return "application/octet-stream";
        String mimeTypeFromExtension = MimeTypeMap.getSingleton().getMimeTypeFromExtension(name.substring(lastIndexOf + 1).toLowerCase());
        return mimeTypeFromExtension != null ? mimeTypeFromExtension : "application/octet-stream";
    }

    @Override
    public void attachInfo(Context context, ProviderInfo providerInfo) {
        super.attachInfo(context, providerInfo);
        this.b = context.getPackageName();
        File parentFile = context.getFilesDir().getParentFile();
        this.c = parentFile;
        String path = parentFile.getPath();
        if (path.startsWith("/data/user/")) {
            this.d = new File("/data/user_de/" + path.substring(11));
        }
        File externalFilesDir = context.getExternalFilesDir(null);
        if (externalFilesDir != null) this.e = externalFilesDir.getParentFile();
        this.f = context.getObbDir();
    }

    public final File b(String str, boolean z) throws FileNotFoundException {
        if (!str.startsWith(this.b)) throw new FileNotFoundException(str + " not found");
        String substring2 = str.substring(this.b.length());
        if (substring2.startsWith("/")) substring2 = substring2.substring(1);
        if (substring2.isEmpty()) return null;

        String substring;
        int indexOf = substring2.indexOf('/');
        if (indexOf == -1) {
            substring = "";
        } else {
            String substring3 = substring2.substring(0, indexOf);
            substring = substring2.substring(indexOf + 1);
            substring2 = substring3;
        }

        File file = null;
        if (substring2.equalsIgnoreCase("data")) file = new File(this.c, substring);
        else if (substring2.equalsIgnoreCase("android_data") && this.e != null) file = new File(this.e, substring);
        else if (substring2.equalsIgnoreCase("android_obb") && this.f != null) file = new File(this.f, substring);
        else if (substring2.equalsIgnoreCase("user_de_data") && this.d != null) file = new File(this.d, substring);

        if (file == null) throw new FileNotFoundException(str + " not found");
        if (z) {
            try {
                Os.lstat(file.getPath());
            } catch (Exception unused) {
                throw new FileNotFoundException(str + " not found");
            }
        }

        return file;
    }

    @Override
    public Bundle call(String method, String arg, Bundle extras) {
        Bundle bundle2 = new Bundle();
        bundle2.putBoolean("result", false);

        String str3 = "";
        String message = "";

        try {
            Uri uri = extras != null ? extras.getParcelable("uri") : null;
            if (uri != null) {
                List<String> pathSegments = uri.getPathSegments();
                str3 = pathSegments.size() >= 4 ? pathSegments.get(3) : pathSegments.get(1);
            }

            if ("mt:setPermissions".equals(method)) {
                File b3 = b(str3, true);
                if (b3 != null) {
                    try {
                        Os.chmod(b3.getPath(), extras.getInt("permissions"));
                        bundle2.putBoolean("result", true);
                    } catch (ErrnoException e) {
                        message = e.getMessage();
                    }
                }
            } else if ("mt:createSymlink".equals(method)) {
                File b2 = b(str3, false);
                if (b2 != null) {
                    try {
                        Os.symlink(extras.getString("path"), b2.getPath());
                        bundle2.putBoolean("result", true);
                    } catch (ErrnoException e) {
                        message = e.getMessage();
                    }
                }
            } else if ("mt:setLastModified".equals(method)) {
                File b1 = b(str3, true);
                if (b1 != null) {
                    bundle2.putBoolean("result", b1.setLastModified(extras.getLong("time")));
                }
            } else {
                message = "Unsupported method: " + method;
            }
        } catch (Exception e) {
            message = e.toString();
        }

        if (!message.isEmpty()) bundle2.putString("message", message);

        return bundle2;
    }

    @Override
    public String createDocument(String str, String str2, String str3) throws FileNotFoundException {
        File b = b(str, true);
        if (b != null) {
            File file = new File(b, str3);
            int i = 2;
            while (file.exists()) {
                file = new File(b, str3 + " (" + i + ")");
                i++;
            }
            try {
                if ("vnd.android.document/directory".equals(str2) ? file.mkdir() : file.createNewFile()) {
                    if (str.endsWith("/")) return str + file.getName();
                    else return str + "/" + file.getName();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        throw new FileNotFoundException("Failed to create document in " + str + " with name " + str3);
    }

    public final void d(MatrixCursor matrixCursor, String str, File file) {
        if (file == null) file = null;
        boolean z = false;
        if (file == null) {
            MatrixCursor.RowBuilder newRow = matrixCursor.newRow();
            newRow.add("document_id", this.b);
            newRow.add("_display_name", this.b);
            newRow.add("_size", 0L);
            newRow.add("mime_type", "vnd.android.document/directory");
            newRow.add("last_modified", 0);
            newRow.add("flags", 0);
            return;
        }

        int i = 0;
        if (file.isDirectory() && file.canWrite()) i = 8;
        else if (!file.isDirectory() && file.canWrite()) i = 2;

        if (file.getParentFile() != null && file.getParentFile().canWrite()) i |= 4 | 64;

        String path = file.getPath();
        String name;
        if (path.equals(this.c.getPath())) name = "data";
        else if (this.e != null && path.equals(this.e.getPath())) name = "android_data";
        else if (this.f != null && path.equals(this.f.getPath())) name = "android_obb";
        else if (this.d != null && path.equals(this.d.getPath())) name = "user_de_data";
        else {
            name = file.getName();
            z = true;
        }

        MatrixCursor.RowBuilder newRow2 = matrixCursor.newRow();
        newRow2.add("document_id", str);
        newRow2.add("_display_name", name);
        newRow2.add("_size", file.length());
        newRow2.add("mime_type", c(file));
        newRow2.add("last_modified", file.lastModified());
        newRow2.add("flags", i);
        newRow2.add("mt_path", file.getAbsolutePath());

        if (z) {
            try {
                StructStat lstat = Os.lstat(path);
                StringBuilder sb = new StringBuilder();
                sb.append(lstat.st_mode).append("|").append(lstat.st_uid).append("|").append(lstat.st_gid);
                if ((lstat.st_mode & 61440) == 40960) sb.append("|").append(Os.readlink(path));
                newRow2.add("mt_extras", sb.toString());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void deleteDocument(String str) throws FileNotFoundException {
        File b = b(str, true);
        if (b == null || !a(b)) throw new FileNotFoundException("Failed to delete document " + str);
    }

    @Override
    public String getDocumentType(String str) throws FileNotFoundException {
        File b = b(str, true);
        return b == null ? "vnd.android.document/directory" : c(b);
    }

    @Override
    public boolean isChildDocument(String str, String str2) {
        return str2.startsWith(str);
    }

    @Override
    public String moveDocument(String str, String str2, String str3) throws FileNotFoundException {
        File b = b(str, true);
        File b2 = b(str3, true);
        if (b != null && b2 != null) {
            File file = new File(b2, b.getName());
            if (!file.exists() && b.renameTo(file)) {
                if (str3.endsWith("/")) return str3 + file.getName();
                else return str3 + "/" + file.getName();
            }
        }
        throw new FileNotFoundException("Filed to move document " + str + " to " + str3);
    }

    @Override
    public boolean onCreate() {
        return true;
    }

    @Override
    public ParcelFileDescriptor openDocument(String str, String str2, CancellationSignal cancellationSignal) throws FileNotFoundException {
        File b = b(str, false);
        if (b != null) return ParcelFileDescriptor.open(b, ParcelFileDescriptor.parseMode(str2));
        throw new FileNotFoundException(str + " not found");
    }

    @Override
    public Cursor queryChildDocuments(String str, String[] strArr, String str2) {
        if (str.endsWith("/")) str = str.substring(0, str.length() - 1);
        if (strArr == null) strArr = h;

        MatrixCursor matrixCursor = new MatrixCursor(strArr);
        File b = null;
        try {
            b = b(str, true);
        } catch (FileNotFoundException ignored) {}

        if (b == null) {
            d(matrixCursor, str + "/data", this.c);
            if (this.e != null && this.e.exists()) d(matrixCursor, str + "/android_data", this.e);
            if (this.f != null && this.f.exists()) d(matrixCursor, str + "/android_obb", this.f);
            if (this.d != null && this.d.exists()) d(matrixCursor, str + "/user_de_data", this.d);
        } else {
            File[] listFiles = b.listFiles();
            if (listFiles != null) {
                for (File file : listFiles) d(matrixCursor, str + "/" + file.getName(), file);
            }
        }

        return matrixCursor;
    }

    @Override
    public Cursor queryDocument(String str, String[] strArr) {
        if (strArr == null) strArr = h;
        MatrixCursor matrixCursor = new MatrixCursor(strArr);
        d(matrixCursor, str, null);
        return matrixCursor;
    }

    @Override
    public Cursor queryRoots(String[] strArr) {
        ApplicationInfo applicationInfo = getContext().getApplicationInfo();
        String label = applicationInfo.loadLabel(getContext().getPackageManager()).toString();
        if (strArr == null) strArr = g;

        MatrixCursor matrixCursor = new MatrixCursor(strArr);
        MatrixCursor.RowBuilder newRow = matrixCursor.newRow();
        newRow.add("root_id", this.b);
        newRow.add("document_id", this.b);

        // Lấy dung lượng
        long total = 0, free = 0;
        if (this.c != null && this.c.exists()) {
            try {
                android.os.StatFs statFs = new android.os.StatFs(this.c.getPath());
                total = statFs.getTotalBytes();
                free = statFs.getAvailableBytes();
            } catch (Exception ignored) {}
        }

        String availableSummary = getContext().getString(R.string.storage_available) + ": " + formatSizeSmart(total) + " / " + formatSizeSmart(free);

        newRow.add("summary", availableSummary);
        newRow.add("flags", 17);
        newRow.add("title", label);
        newRow.add("mime_types", "*/*");
        newRow.add("icon", applicationInfo.icon);
        return matrixCursor;
    }

    @Override
    public void removeDocument(String str, String str2) throws FileNotFoundException {
        deleteDocument(str);
    }

    @Override
    public String renameDocument(String str, String str2) throws FileNotFoundException {
        File b = b(str, true);
        if (b == null || !b.renameTo(new File(b.getParentFile(), str2)))
            throw new FileNotFoundException("Failed to rename document " + str + " to " + str2);
        return str.substring(0, str.lastIndexOf('/', str.length() - 2)) + "/" + str2;
    }
}