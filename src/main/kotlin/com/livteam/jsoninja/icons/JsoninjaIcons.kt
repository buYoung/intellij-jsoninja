package com.livteam.jsoninja.icons

import com.intellij.ui.IconManager
import javax.swing.Icon

/**
 * Icon constants for JSONinja. Paths are resolved through the icon mapper to pick up the New UI variants.
 */
object JsoninjaIcons {
    @JvmField
    val ToolWindowIcon: Icon = IconManager.getInstance()
        .getIcon("icons/classic/toolWindowIcon-16.svg", JsoninjaIcons::class.java.classLoader)

    @JvmField
    val PrettyIcon: Icon = IconManager.getInstance()
        .getIcon("icons/classic/prettyIcon-16.svg", JsoninjaIcons::class.java.classLoader)

    @JvmField
    val UglyIcon: Icon = IconManager.getInstance()
        .getIcon("icons/classic/uglyIcon-16.svg", JsoninjaIcons::class.java.classLoader)

    @JvmField
    val EscapeIcon: Icon = IconManager.getInstance()
        .getIcon("icons/classic/escapeIcon-16.svg", JsoninjaIcons::class.java.classLoader)

    @JvmField
    val UnescapeIcon: Icon = IconManager.getInstance()
        .getIcon("icons/classic/unescapeIcon-16.svg", JsoninjaIcons::class.java.classLoader)

    @JvmField
    val GenerateIcon: Icon = IconManager.getInstance()
        .getIcon("icons/classic/generateIcon-16.svg", JsoninjaIcons::class.java.classLoader)

    @JvmField
    val DiffIcon: Icon = IconManager.getInstance()
        .getIcon("icons/classic/diffIcon-16.svg", JsoninjaIcons::class.java.classLoader)
}
