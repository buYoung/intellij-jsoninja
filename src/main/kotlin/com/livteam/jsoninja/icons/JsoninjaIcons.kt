package com.livteam.jsoninja.icons

import com.intellij.ui.IconManager
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon


object JsoninjaIcons {
    private fun load(path: String, cacheKey: Int, flags: Int): Icon {
        return IconManager.getInstance()
            .loadRasterizedIcon(path, JsoninjaIcons::class.java.classLoader, cacheKey, flags)
    }

    private fun load(expUIPath: String, path: String, cacheKey: Int, flags: Int): Icon {
        return IconManager.getInstance()
            .loadRasterizedIcon(path, expUIPath, JsoninjaIcons::class.java.classLoader, cacheKey, flags)
    }

    @JvmField
    val ToolWindowIcon: Icon = load("icons/expui/toolWindowIcon-20.svg", "icons/classic/toolWindowIcon-16.svg", 0, 2)

    @JvmField
    val PrettyIcon: Icon = load("icons/expui/prettyIcon-20.svg", "icons/classic/prettyIcon-16.svg", 423984, 2)

    @JvmField
    val UglyIcon: Icon = load("icons/expui/uglyIcon-20.svg", "icons/classic/uglyIcon-16.svg", 912837, 2)

    @JvmField
    val EscapeIcon: Icon = load("icons/expui/escapeIcon-20.svg", "icons/classic/escapeIcon-16.svg", 283746, 2)

    @JvmField
    val UnescapeIcon: Icon = load("icons/expui/unescapeIcon-20.svg", "icons/classic/unescapeIcon-16.svg", 572819, 2)

    @JvmField
    val GenerateIcon: Icon = load("icons/expui/generateIcon-20.svg", "icons/classic/generateIcon-16.svg", 384726, 2)

    @JvmField
    val DiffIcon: Icon = load("icons/expui/diffIcon-20.svg", "icons/classic/diffIcon-16.svg", 192837, 2)

    @JvmField
    val PrettyIconV2: Icon =
        load("icons/expui/v2/prettyIcon-v2-20.svg", "icons/classic/v2/prettyIcon-v2-16.svg", 657483, 2)

    @JvmField
    val UglyIconV2: Icon = load("icons/expui/v2/uglyIcon-v2-20.svg", "icons/classic/v2/uglyIcon-v2-16.svg", 293847, 2)

    @JvmField
    val EscapeIconV2: Icon =
        load("icons/expui/v2/escapeIcon-v2-20.svg", "icons/classic/v2/escapeIcon-v2-16.svg", 847362, 2)

    @JvmField
    val UnescapeIconV2: Icon =
        load("icons/expui/v2/unescapeIcon-v2-20.svg", "icons/classic/v2/unescapeIcon-v2-16.svg", 472839, 2)

    @JvmField
    val GenerateIconV2: Icon =
        load("icons/expui/v2/generateIcon-v2-20.svg", "icons/classic/v2/generateIcon-v2-16.svg", 564738, 2)

    @JvmField
    val DiffIconV2: Icon = load("icons/expui/v2/diffIcon-v2-20.svg", "icons/classic/v2/diffIcon-v2-16.svg", -1766843, 2)
}
