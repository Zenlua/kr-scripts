package com.omarea.krscript.model

class PageMenuOption(currentConfigXml: String) : RunnableNode(currentConfigXml) {
    // 类型为普通菜单项还是其它具有特定行为的菜单项
    // 例如，类型为finish 点击后会关闭当前页面，类型为refresh点击后会刷新当前页面，而类型为file点击后则需要先选择文件
    var type: String = ""
    // 是否显示为悬浮按钮
    var isFab = false;

    // 文件mime类型（仅限type=file有效）
    var mime: String = ""
    // 文件后缀（仅限type=file有效）
    var suffix: String = ""
    // 需要加载的page
    var page: String = ""
}