package com.omarea.common.shell

import android.content.Context
import java.lang.Exception
import java.lang.StringBuilder
import java.util.*

/**
 * 从Resource解析字符串，实现输出内容多语言
 * + 支持 @img:/path/to/image
 */
class ShellTranslation(val context: Context) {

    // 示例：
    // @string:home_shell_01
    private val regex1 = Regex("^@(string|dimen):[_a-zA-Z0-9]+.*", RegexOption.IGNORE_CASE)

    // 示例：
    // @string/home_shell_01
    private val regex2 = Regex("^@(string|dimen)/[_a-zA-Z0-9]+.*", RegexOption.IGNORE_CASE)

    // 示例：
    // @img:/sdcard/pic.png
    private val imgRegex = Regex("^@img:(.+)", RegexOption.IGNORE_CASE)

    /**
     * 解析单行（旧 API）
     * 只返回文本，不处理 image
     */
    fun resolveRow(originRow: String): String {
        val separator = when {
            regex1.matches(originRow) -> ':'
            regex2.matches(originRow) -> '/'
            else -> null
        }

        if (separator != null) {
            val row = originRow.trim()
            val resources = context.resources
            val type = row.substring(1, row.indexOf(separator)).lowercase(Locale.ENGLISH)
            val name = row.substring(row.indexOf(separator) + 1)

            try {
                val id = resources.getIdentifier(name, type, context.packageName)
                when (type) {
                    "string" -> return resources.getString(id)
                    "dimen" -> return resources.getDimension(id).toString()
                }
            } catch (_: Exception) {
                if (row.contains("[(") && row.contains(")]")) {
                    return row.substring(row.indexOf("[(") + 2, row.indexOf(")]"))
                }
            }
        }

        return originRow
    }

    /**
     * ✅ API MỚI
     * 解析 shell 输出，返回 Text / Image
     */
    fun resolveLines(rows: List<String>): List<ShellOutput> {
        val outputs = mutableListOf<ShellOutput>()

        for (row in rows) {
            val line = row.trim()

            // @img:/path
            val imgMatch = imgRegex.find(line)
            if (imgMatch != null) {
                val path = imgMatch.groupValues[1].trim()
                outputs.add(ShellOutput.Image(path))
                continue
            }

            // 普通文本（支持 string / dimen）
            outputs.add(ShellOutput.Text(resolveRow(row)))
        }

        return outputs
    }

    /**
     * 旧 API（KeepShell 正在使用）
     * 只拼接文本，忽略 image
     */
    fun resolveRows(rows: List<String>): String {
        val builder = StringBuilder()
        var rowIndex = 0

        for (item in resolveLines(rows)) {
            if (item is ShellOutput.Text) {
                if (rowIndex > 0) {
                    builder.append('\n')
                }
                builder.append(item.text)
                rowIndex++
            }
        }

        return builder.toString()
    }

    /**
     * 旧 API：直接执行 shell 并翻译
     */
    fun getTranslatedResult(shellCommand: String, executor: KeepShell?): String {
        val shell = executor ?: KeepShellPublic.getDefaultInstance()
        val rows = shell.doCmdSync(shellCommand).split("\n")
        return if (rows.isNotEmpty()) {
            resolveRows(rows)
        } else {
            ""
        }
    }
}
