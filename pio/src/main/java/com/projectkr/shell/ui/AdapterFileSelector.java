package com.projectkr.shell.ui;

import android.os.Environment;
import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;
import android.widget.Toast;

import com.google.android.material.snackbar.Snackbar;
import com.omarea.common.ui.DialogHelper;
import com.omarea.common.ui.ProgressBarDialog;
import com.projectkr.shell.R;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileFilter;
import java.io.InputStreamReader;
import java.util.ArrayList;

public class AdapterFileSelector extends BaseAdapter {
    private File[] fileArray;
    private Runnable fileSelected;
    private File currentDir;
    private File selectedFile;
    private final Handler handler = new Handler();
    private ProgressBarDialog progressBarDialog;
    private String extension;
    private boolean hasParent = false; // 是否还有父级
    private String rootDir = "/"; // 根目录
    private boolean folderChooserMode = false; // 是否是目录选择模式
    private boolean isRootFallback = false; // 是否 đang ở / dùng shell ls

    private AdapterFileSelector(File rootDir, Runnable fileSelected, ProgressBarDialog progressBarDialog, String extension) {
        init(rootDir, fileSelected, progressBarDialog, extension);
    }

    public static AdapterFileSelector FolderChooser(File rootDir, Runnable fileSelected, ProgressBarDialog progressBarDialog) {
        AdapterFileSelector adapter = new AdapterFileSelector(rootDir, fileSelected, progressBarDialog, null);
        adapter.folderChooserMode = true;
        return adapter;
    }

    public static AdapterFileSelector FileChooser(File rootDir, Runnable fileSelected, ProgressBarDialog progressBarDialog, String extension) {
        AdapterFileSelector adapter = new AdapterFileSelector(rootDir, fileSelected, progressBarDialog, extension);
        adapter.folderChooserMode = false;
        return adapter;
    }

    private void init(File rootDir, Runnable fileSelected, ProgressBarDialog progressBarDialog, String extension) {
        this.rootDir = rootDir.getAbsolutePath();
        this.fileSelected = fileSelected;
        this.progressBarDialog = progressBarDialog;
        if (extension != null) {
            if (extension.startsWith(".")) {
                this.extension = extension;
            } else {
                this.extension = "." + extension;
            }
        }
        loadDir(rootDir);
    }

    private void loadDir(final File dir) {
        progressBarDialog.showDialog("加载中...");
        new Thread(() -> {
            // Xác định parent
            File parent = dir.getParentFile();
            if (dir.getAbsolutePath().equals(Environment.getExternalStorageDirectory().getAbsolutePath()) && !isRootFallback) {
                // sdcard vẫn dùng listFiles bình thường
                hasParent = true;
                isRootFallback = false;
            } else if (dir.getAbsolutePath().equals("/") && isRootFallback) {
                hasParent = true; // vẫn hiện ... khi ở /
            } else if (parent != null) {
                hasParent = parent.exists() && parent.canRead();
            } else {
                hasParent = false;
            }

            // Liệt kê file
            if (dir.exists() && dir.canRead()) {
                if (dir.getAbsolutePath().equals("/") && isRootFallback) {
                    fileArray = listRootWithShell(dir);
                } else {
                    File[] files = dir.listFiles(file -> folderChooserMode ? file.isDirectory()
                            : (file.exists() && (extension == null || extension.isEmpty() || file.getName().endsWith(extension))));
                    fileArray = files != null ? files : new File[0];
                    isRootFallback = false;
                }
            }

            currentDir = dir;
            sortFiles();

            handler.post(() -> {
                notifyDataSetChanged();
                progressBarDialog.hideDialog();
            });
        }).start();
    }

    private File[] listRootWithShell(File dir) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"ls", "-1A", dir.getAbsolutePath()});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            ArrayList<File> list = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                if (!line.trim().isEmpty()) {
                    list.add(new File(dir, line));
                }
            }
            reader.close();
            process.waitFor();
            return list.toArray(new File[0]);
        } catch (Exception e) {
            return new File[0];
        }
    }

    private void sortFiles() {
        if (fileArray == null) return;
        for (int i = 0; i < fileArray.length; i++) {
            for (int j = i + 1; j < fileArray.length; j++) {
                if ((fileArray[j].isDirectory() && fileArray[i].isFile())
                        || (fileArray[j].isDirectory() == fileArray[i].isDirectory()
                        && fileArray[j].getName().compareToIgnoreCase(fileArray[i].getName()) < 0)) {
                    File t = fileArray[i];
                    fileArray[i] = fileArray[j];
                    fileArray[j] = t;
                }
            }
        }
    }

    public boolean goParent() {
        if (currentDir.getAbsolutePath().equals(Environment.getExternalStorageDirectory().getAbsolutePath())) {
            // Ở /sdcard nhấn ... → vào /
            currentDir = new File("/");
            isRootFallback = true;
            loadDir(currentDir);
            return true;
        } else if (hasParent) {
            loadDir(currentDir.getParentFile());
            return true;
        }
        return false;
    }

    @Override
    public int getCount() {
        if (hasParent) {
            return fileArray == null ? 1 : fileArray.length + 1;
        } else {
            return fileArray == null ? 0 : fileArray.length;
        }
    }

    public void refresh() {
        if (currentDir != null) loadDir(currentDir);
    }

    @Override
    public Object getItem(int position) {
        if (hasParent) {
            if (position == 0) return currentDir.getAbsolutePath().equals("/") ? new File("/") : currentDir.getParentFile();
            return fileArray[position - 1];
        } else {
            return fileArray[position];
        }
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parentView) {
        final View view;
        if (hasParent && position == 0) {
            view = View.inflate(parentView.getContext(), R.layout.list_item_dir, null);
            ((TextView) view.findViewById(R.id.ItemTitle)).setText("...");
            view.setOnClickListener(v -> goParent());
            return view;
        } else {
            final File file = (File) getItem(position);
            if (file.isDirectory()) {
                view = View.inflate(parentView.getContext(), R.layout.list_item_dir, null);
                view.setOnClickListener(v -> {
                    if (!file.exists()) {
                        Toast.makeText(view.getContext(), "所选的文件已被删除，请重新选择！", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    File[] files = file.listFiles();
                    if (files != null && files.length > 0) {
                        loadDir(file);
                    } else {
                        Snackbar.make(view, "该目录下没有文件！", Snackbar.LENGTH_SHORT).show();
                    }
                });
                if (folderChooserMode) {
                    view.setOnLongClickListener(v -> {
                        DialogHelper.Companion.confirm(view.getContext(), "选定目录？", file.getAbsolutePath(), () -> {
                            if (!file.exists()) {
                                Toast.makeText(view.getContext(), "所选的目录已被删除，请重新选择！", Toast.LENGTH_SHORT).show();
                                return;
                            }
                            selectedFile = file;
                            fileSelected.run();
                        }, () -> {});
                        return true;
                    });
                }
            } else {
                view = View.inflate(parentView.getContext(), R.layout.list_item_file, null);
                long fileLength = file.length();
                String fileSize;
                if (fileLength < 1024) fileSize = fileLength + "B";
                else if (fileLength < 1048576) fileSize = String.format("%.2fKB", fileLength / 1024.0);
                else if (fileLength < 1073741824) fileSize = String.format("%.2fMB", fileLength / 1048576.0);
                else fileSize = String.format("%.2fGB", fileLength / 1073741824.0);

                ((TextView) view.findViewById(R.id.ItemText)).setText(fileSize);

                view.setOnClickListener(v -> DialogHelper.Companion.confirm(view.getContext(), "选定文件？", file.getAbsolutePath(), () -> {
                    if (!file.exists()) {
                        Toast.makeText(view.getContext(), "所选的文件已被删除，请重新选择！", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    selectedFile = file;
                    fileSelected.run();
                }, () -> {}));
            }
            ((TextView) view.findViewById(R.id.ItemTitle)).setText(file.getName());
            return view;
        }
    }

    public File getSelectedFile() {
        return selectedFile;
    }
}
