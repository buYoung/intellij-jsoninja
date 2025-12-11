package com.livteam.jsoninja.icons

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader.getIcon
import com.livteam.jsoninja.model.JsonIconPack
import com.livteam.jsoninja.settings.JsoninjaSettingsState
import javax.swing.Icon


object JsoninjaIcons {
    private fun load(path: String): Icon {
        return getIcon(path, javaClass)
    }

    @JvmField
    val ToolWindowIcon: Icon = load("/icons/classic/toolWindowIcon-16.svg")

    @JvmField
    val PrettyIcon: Icon = load("/icons/classic/prettyIcon-16.svg")

    @JvmField
    val UglyIcon: Icon = load("/icons/classic/uglyIcon-16.svg")

    @JvmField
    val EscapeIcon: Icon = load("/icons/classic/escapeIcon-16.svg")

    @JvmField
    val UnescapeIcon: Icon = load("/icons/classic/unescapeIcon-16.svg")

    @JvmField
    val GenerateIcon: Icon = load("/icons/classic/generateIcon-16.svg")

    @JvmField
    val DiffIcon: Icon = load("/icons/classic/diffIcon-16.svg")

    @JvmField
    val PrettyIconV2: Icon =
        load("/icons/classic/v2/prettyIcon-v2-16.svg")

    @JvmField
    val UglyIconV2: Icon = load("/icons/classic/v2/uglyIcon-v2-16.svg")

    @JvmField
    val EscapeIconV2: Icon =
        load("/icons/classic/v2/escapeIcon-v2-16.svg")

    @JvmField
    val UnescapeIconV2: Icon =
        load("/icons/classic/v2/unescapeIcon-v2-16.svg")

    @JvmField
    val GenerateIconV2: Icon =
        load("/icons/classic/v2/generateIcon-v2-16.svg")

    @JvmField
    val DiffIconV2: Icon = load("/icons/classic/v2/diffIcon-v2-16.svg")

    fun getPrettyIcon(project: Project?): Icon = getIcon(project, PrettyIcon, PrettyIconV2)
    fun getUglifyIcon(project: Project?): Icon = getIcon(project, UglyIcon, UglyIconV2)
    fun getEscapeIcon(project: Project?): Icon = getIcon(project, EscapeIcon, EscapeIconV2)
    fun getUnescapeIcon(project: Project?): Icon = getIcon(project, UnescapeIcon, UnescapeIconV2)
    fun getGenerateIcon(project: Project?): Icon = getIcon(project, GenerateIcon, GenerateIconV2)
    fun getDiffIcon(project: Project?): Icon = getIcon(project, DiffIcon, DiffIconV2)

    private fun getIcon(project: Project?, v1Icon: Icon, v2Icon: Icon): Icon {
        if (project == null) return v2Icon
        val settings = JsoninjaSettingsState.getInstance(project)
        return if (settings.iconPack == JsonIconPack.VERSION_1.name) v1Icon else v2Icon
    }
}
