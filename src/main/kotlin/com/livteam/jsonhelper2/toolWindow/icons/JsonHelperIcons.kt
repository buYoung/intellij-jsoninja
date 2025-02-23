package com.livteam.jsonhelper2.toolWindow.icons

import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object JsonHelperIcons {
    private fun load(path: String): Icon = IconLoader.getIcon(path, JsonHelperIcons::class.java)

    val Prettify: Icon = load("/icons/prettify.svg")
    val Uglify: Icon = load("/icons/uglify.svg")
    val Escape: Icon = load("/icons/escape.svg")
    val Unescape: Icon = load("/icons/unescape.svg")
    val JmesPath: Icon = load("/icons/jmespath.svg")
}
