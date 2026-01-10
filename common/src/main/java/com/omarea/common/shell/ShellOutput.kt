package com.omarea.common.shell

sealed class ShellOutput {
    data class Text(val text: String) : ShellOutput()
    data class Image(
        val path: String,
        val caption: String? = null
    ) : ShellOutput()
}
