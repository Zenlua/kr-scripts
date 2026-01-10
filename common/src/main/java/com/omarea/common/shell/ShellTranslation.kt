package com.omarea.common.shell

import android.content.Context
import java.lang.StringBuilder
import java.util.*
import kotlin.collections.ArrayList

// 从Resource解析字符串，实现输出内容多语言 + 扩展 @img:
class ShellTranslation(private val context: Context) {

    // @string:name 或 @string/name
    private val resRegex = Regex(
        "^@(string|dimen)(:|/)([a-z0-9_]+)$",
        RegexOption.IGNORE_CASE
    )

    // @img:path[(caption)]
    private val imgRegex =
        Regex("""^@img:(.+?)(?:\[\((.*?)\)\])?$""")

    /**
     * 解析一行输出，返回 Text 或 Image
     */
    fun resolveLine(line: String): ShellOutput {
        val row = line.trim()

        // 1️⃣ Image
        val imgMatch = imgRegex.matchEntire(row)
        if (imgMatch != null) {
            return ShellOutput.Image(
                path = imgMatch.groupValues[1],
                caption = imgMatch.groupValues.getOrNull(2)
            )
        }

        // 2️⃣ Text (resource / fallback)
        return ShellOutput.Text(resolveRows(row))
    }

    /**
     * 解析多行
     */
    fun resolveLines(lines: List<String>): List<ShellOutput> {
        val list = ArrayList<ShellOutput>()
        for (line in lines) {
            list.add(resolveLine(line))
        }
        return list
    }

    /**
     * 原有逻辑：解析 @string / @dimen
     */
    fun resolveRows(originRow: String): String {
        val row = originRow.trim()
        val match = resRegex.matchEntire(row) ?: return fallback(row)

        val type = match.groupValues[1].lowercase(Locale.ENGLISH)
        val name = match.groupValues[3]

        val res = context.resources
        val id = res.getIdentifier(name, type, context.packageName)

        if (id == 0) return fallback(row)

        return try {
            when (type) {
                "string" -> res.getString(id)
                "dimen" -> {
                    val px = res.getDimension(id)
                    px.toInt().toString()
                }
                else -> row
            }
        } catch (_: Exception) {
            fallback(row)
        }
    }

    /**
     * fallback: [(text)]
     */
    private fun fallback(row: String): String {
        return if (row.contains("[(") && row.contains(")]")) {
            row.substringAfter("[(").substringBefore(")]")
        } else {
            row
        }
    }

    /**
     * 原 API —— 只返回 Text，忽略 Image（兼容旧代码）
     */
    fun getTranslatedResult(shellCommand: String, executor: KeepShell?): String {
        val shell = executor ?: KeepShellPublic.getDefaultInstance()
        val rows = shell.doCmdSync(shellCommand).split('\n')

        val sb = StringBuilder()
        for (item in resolveLines(rows)) {
            if (item is ShellOutput.Text) {
                if (sb.isNotEmpty()) sb.append('\n')
                sb.append(item.text)
            }
        }
        return sb.toString()
    }

    /**
     * 新 API —— 返回完整 output（Text + Image）
     */
    fun getTranslatedOutput(
        shellCommand: String,
        executor: KeepShell?
    ): List<ShellOutput> {
        val shell = executor ?: KeepShellPublic.getDefaultInstance()
        val rows = shell.doCmdSync(shellCommand).split('\n')
        return resolveLines(rows)
    }
}
