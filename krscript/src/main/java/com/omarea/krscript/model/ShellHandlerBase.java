package com.omarea.krscript.model;

import android.graphics.Color;
import android.os.Handler;
import android.os.Message;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;

import java.util.regex.Pattern;

/**
 * ShellHandlerBase: base class cho việc xử lý output từ ShellExecutor
 * Có thể override để hiển thị text, màu sắc, và extension hỗ trợ HTML hoặc ảnh
 */
public abstract class ShellHandlerBase extends Handler {

    // === Các sự kiện mặc định ===
    public static final int EVENT_START = 0;         // Start script
    public static final int EVENT_REDE = 2;          // Standard output
    public static final int EVENT_READ_ERROR = 4;    // Error output
    public static final int EVENT_WRITE = 6;         // Write log
    public static final int EVENT_EXIT = -2;         // Exit code

    // === Abstract methods bắt buộc override ===
    protected abstract void onProgress(int current, int total);
    protected abstract void onStart(Object msg);
    public abstract void onStart(Runnable forceStop);
    protected abstract void onExit(Object msg);
    protected abstract void updateLog(final SpannableString msg);

    @Override
    public void handleMessage(Message msg) {
        super.handleMessage(msg);
        switch (msg.what) {
            case EVENT_EXIT:
                onExit(msg.obj);
                break;
            case EVENT_START:
                onStart(msg.obj);
                break;
            case EVENT_REDE:
                onReaderMsg(msg.obj);
                break;
            case EVENT_READ_ERROR:
                onError(msg.obj);
                break;
            case EVENT_WRITE:
                onWrite(msg.obj);
                break;
        }
    }

    // === Xử lý output ===
    protected void onReaderMsg(Object msg) {
        if (msg != null) {
            String log = msg.toString().trim();
            if (Pattern.matches("^progress:\\[[\\-0-9\\\\]{1,}/[0-9\\\\]{1,}]$", log)) {
                String[] values = log.substring("progress:[".length(), log.indexOf("]")).split("/");
                int start = Integer.parseInt(values[0]);
                int total = Integer.parseInt(values[1]);
                onProgress(start, total);
            } else {
                onReader(msg);
            }
        }
    }

    protected void onReader(Object msg) {
        // Text bình thường màu xanh
        updateLog(msg, "#00cc55");
    }

    protected void onWrite(Object msg) {
        updateLog(msg, "#808080");
    }

    protected void onError(Object msg) {
        updateLog(msg, "#ff0000");
    }

    // === Xuất ra màu sắc text ===
    protected void updateLog(final Object msg, final String color) {
        if (msg != null) {
            String msgStr = msg.toString();
            SpannableString spannableString = new SpannableString(msgStr);
            spannableString.setSpan(
                    new ForegroundColorSpan(Color.parseColor(color)),
                    0, msgStr.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            updateLog(spannableString);
        }
    }

    protected void updateLog(final Object msg, final int color) {
        if (msg != null) {
            String msgStr = msg.toString();
            SpannableString spannableString = new SpannableString(msgStr);
            spannableString.setSpan(
                    new ForegroundColorSpan(color),
                    0, msgStr.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            updateLog(spannableString);
        }
    }

    // === Extension: hỗ trợ HTML hoặc hình ảnh trong log ===
    // Nếu muốn hiển thị HTML hoặc hình ảnh, override method này trong subclass
    public void updateHtmlLog(CharSequence html) {
        // Mặc định không làm gì, override để dùng TextView.setText(Html.fromHtml(html))
    }

    public void updateImageLog(Object image) {
        // Mặc định không làm gì, override để hiển thị hình ảnh trong log
        // 'image' có thể là Bitmap, Drawable, hoặc Uri tùy implementation
    }
}
