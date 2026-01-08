package com.projectkr.shell.ui;

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
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class AdapterFileSelector extends BaseAdapter {
    private File[] fileArray;
    private Runnable fileSelected;
    private File currentDir;
    private File selectedFile;
    private final Handler handler = new Handler();
    private ProgressBarDialog progressBarDialog;
    private String extension;
    private boolean hasParent = false;
    private boolean folderChooserMode = false;

    private AdapterFileSelector(File rootDir, Runnable fileSelected, ProgressBarDialog progressBarDialog, String extension) {
        init(rootDir, fileSelected, progressBarDialog, extension);
    }

    public static AdapterFileSelector FolderChooser(File rootDir, Runnable fileSelected, ProgressBarDialog progressBarDialog) {
        AdapterFileSelector adapterFileSelector = new AdapterFileSelector(rootDir, fileSelected, progressBarDialog, null);
        adapterFileSelector.folderChooserMode = true;
        return adapterFileSelector;
    }

    public static AdapterFileSelector FileChooser(File rootDir, Runnable fileSelected, ProgressBarDialog progressBarDialog, String extension) {
        AdapterFileSelector adapterFileSelector = new AdapterFileSelector(rootDir, fileSelected, progressBarDialog, extension);
        adapterFileSelector.folderChooserMode = false;
        return adapterFileSelector;
    }

    private void init(File rootDir, Runnable fileSelected, ProgressBarDialog progressBarDialog, String extension) {
        this.fileSelected = fileSelected;
        this.progressBarDialog = progressBarDialog;
        if (extension != null) {
            this.extension = extension.startsWith(".") ? extension : "." + extension;
        }
        loadDir(rootDir);
    }

    private void loadDir(final File dir) {
        progressBarDialog.showDialog("加载中...");
        new Thread(() -> {
            // Chỉ kiểm tra parent != null để xác định nút ...
            hasParent = dir.getParent() != null;

            File[] files;
            if (dir.getAbsolutePath().equals("/")) {
                files = listFilesShell(dir); // root thì dùng shell ls
            } else {
                File[] rawFiles = dir.listFiles();
                if (rawFiles != null) {
                    final String ext = extension;
                    files = Arrays.stream(rawFiles)
                            .filter(f -> folderChooserMode ? f.isDirectory() : (f.isDirectory() || (ext == null || ext.isEmpty() || f.getName().endsWith(ext))))
                            .toArray(File[]::new);
                } else {
                    files = new File[0];
                }
            }

            // Sắp xếp thư mục trước, file sau, theo tên
            Arrays.sort(files, (f1, f2) -> {
                if (f1.isDirectory() && !f2.isDirectory()) return -1;
                if (!f1.isDirectory() && f2.isDirectory()) return 1;
                return f1.getName().compareToIgnoreCase(f2.getName());
            });

            fileArray = files;
            currentDir = dir;

            handler.post(() -> {
                notifyDataSetChanged();
                progressBarDialog.hideDialog();
            });
        }).start();
    }

    private File[] listFilesShell(File dir) {
        try {
            Process process = Runtime.getRuntime().exec(new String[]{"sh", "-c", "ls -1ALF \"" + dir.getAbsolutePath() + "\""});
            BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()));
            List<File> fileList = new ArrayList<>();
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;
                boolean isDir = line.endsWith("/");
                if (isDir) line = line.substring(0, line.length() - 1);
                File f = new File(dir, line);
                fileList.add(f);
            }
            process.waitFor();
            return fileList.toArray(new File[0]);
        } catch (Exception e) {
            e.printStackTrace();
            return new File[0];
        }
    }

    public boolean goParent() {
        if (hasParent) {
            loadDir(new File(currentDir.getParent()));
            return true;
        }
        return false;
    }

    @Override
    public int getCount() {
        if (fileArray == null) return hasParent ? 1 : 0;
        return hasParent ? fileArray.length + 1 : fileArray.length;
    }

    @Override
    public Object getItem(int position) {
        if (hasParent) {
            return position == 0 ? new File(currentDir.getParent()) : fileArray[position - 1];
        } else {
            return fileArray[position];
        }
    }

    @Override
    public long getItemId(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        final View view;
        if (hasParent && position == 0) {
            view = View.inflate(parent.getContext(), R.layout.list_item_dir, null);
            ((TextView) view.findViewById(R.id.ItemTitle)).setText("...");
            view.setOnClickListener(v -> goParent());
            return view;
        } else {
            final File file = (File) getItem(position);
            if (file.isDirectory()) {
                view = View.inflate(parent.getContext(), R.layout.list_item_dir, null);
                view.setOnClickListener(v -> {
                    if (!file.exists()) {
                        Toast.makeText(view.getContext(), "所选的文件已被删除，请重新选择！", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    File[] filesInDir = file.listFiles();
                    if (filesInDir != null && filesInDir.length > 0) {
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
                view = View.inflate(parent.getContext(), R.layout.list_item_file, null);
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
        return this.selectedFile;
    }

    public void refresh() {
        if (this.currentDir != null) {
            loadDir(currentDir);
        }
    }
}
