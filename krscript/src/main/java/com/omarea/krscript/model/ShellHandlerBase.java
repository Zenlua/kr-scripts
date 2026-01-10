package com.omarea.krscript.model;

import android.graphics.Color;
import android.os.Handler;
import android.os.Message;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;

import java.util.regex.Pattern;

/**
 * Created by Hello on 2018/04/01.
 */
public abstract class ShellHandlerBase extends Handler {

    /**
     * 处理启动信息
     */
    public static final int EVENT_START = 0;

    /**
     * 命令行输出内容
     */
    public static final int EVENT_REDE = 2;

    /**
     * 命令行错误输出
     */
    public static final int EVENT_READ_ERROR = 4;

    /**
     * 脚本写入日志
     */
    public static final int EVENT_WRITE = 6;

    /**
     * 图片输出（新增，不影响旧代码）
     * msg.obj 通常是图片路径 String / File / Uri
     */
    public static final int EVENT_IMAGE = 8;

    /**
     * 处理 Exit value
     */
    public static final int EVENT_EXIT = -2;

    protected abstract void onProgress(int current, int total);

    protected abstract void onStart(Object msg);

    public abstract void onStart(Runnable forceStop);

    protected abstract void onExit(Object msg);

    /**
     * 输出格式化文本内容
     */
    protected abstract void updateLog(final SpannableString msg);

    /**
     * 输出图片（默认空实现，子类按需 override）
     */
    protected void onImage(Object msg) {
        // 默认不处理，保证旧代码不受影响
    }

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

            case EVENT_IMAGE:
                onImage(msg.obj);
                break;
        }
    }

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
        updateLog(msg, "#00cc55");
    }

    protected void onWrite(Object msg) {
        updateLog(msg, "#808080");
    }

    protected void onError(Object msg) {
        updateLog(msg, "#ff0000");
    }

    /**
     * 输出指定颜色的文本内容
     */
    protected void updateLog(final Object msg, final String color) {
        if (msg != null) {
            String msgStr = msg.toString();
            SpannableString spannableString = new SpannableString(msgStr);
            spannableString.setSpan(
                    new ForegroundColorSpan(Color.parseColor(color)),
                    0,
                    msgStr.length(),
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
                    0,
                    msgStr.length(),
                    Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            );
            updateLog(spannableString);
        }
    }
}
