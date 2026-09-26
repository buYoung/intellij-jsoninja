package com.livteam.jsoninja.icons

import com.intellij.icons.AllIcons
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.IconLoader.getIcon
import com.livteam.jsoninja.model.JsonIconPack
import com.livteam.jsoninja.model.SupportedLanguage
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

    @JvmField
    val PrettyIconV3: Icon = load("/icons/classic/v3/prettyIcon-v3-20.svg")

    @JvmField
    val UglyIconV3: Icon = load("/icons/classic/v3/uglyIcon-v3-20.svg")

    @JvmField
    val EscapeIconV3: Icon = load("/icons/classic/v3/escapeIcon-v3-20.svg")

    @JvmField
    val UnescapeIconV3: Icon = load("/icons/classic/v3/unescapeIcon-v3-20.svg")

    @JvmField
    val GenerateIconV3: Icon = load("/icons/classic/v3/generateIcon-v3-20.svg")

    @JvmField
    val DiffIconV3: Icon = load("/icons/classic/v3/diffIcon-v3-20.svg")

    @JvmField
    val OpenJsonFileIconV3: Icon = load("/icons/classic/v3/openJsonFileIcon-v3-20.svg")

    @JvmField
    val LoadJsonFromApiIconV3: Icon = load("/icons/classic/v3/loadJsonFromApiIcon-v3-20.svg")

    @JvmField
    val ConvertTypeIconV3: Icon = load("/icons/classic/v3/convertTypeIcon-v3-20.svg")

    @JvmField
    val JsonToTypeIconV3: Icon = load("/icons/classic/v3/jsonToTypeIcon-v3-20.svg")

    @JvmField
    val TypeToJsonIconV3: Icon = load("/icons/classic/v3/typeToJsonIcon-v3-20.svg")

    fun getPrettyIcon(project: Project?): Icon = getIcon(project, PrettyIcon, PrettyIconV2, PrettyIconV3)
    fun getUglifyIcon(project: Project?): Icon = getIcon(project, UglyIcon, UglyIconV2, UglyIconV3)
    fun getEscapeIcon(project: Project?): Icon = getIcon(project, EscapeIcon, EscapeIconV2, EscapeIconV3)
    fun getUnescapeIcon(project: Project?): Icon = getIcon(project, UnescapeIcon, UnescapeIconV2, UnescapeIconV3)
    fun getGenerateIcon(project: Project?): Icon = getIcon(project, GenerateIcon, GenerateIconV2, GenerateIconV3)
    fun getDiffIcon(project: Project?): Icon = getIcon(project, DiffIcon, DiffIconV2, DiffIconV3)

    fun getOpenJsonFileIcon(project: Project?): Icon =
        getIcon(project, AllIcons.Actions.MenuOpen, AllIcons.Actions.MenuOpen, OpenJsonFileIconV3)

    fun getLoadJsonFromApiIcon(project: Project?): Icon =
        getIcon(project, AllIcons.Actions.Download, AllIcons.Actions.Download, LoadJsonFromApiIconV3)

    fun getConvertTypeIcon(project: Project?): Icon = getIcon(project, GenerateIcon, GenerateIconV2, ConvertTypeIconV3)
    fun getJsonToTypeIcon(project: Project?): Icon = getIcon(project, GenerateIcon, GenerateIconV2, JsonToTypeIconV3)
    fun getTypeToJsonIcon(project: Project?): Icon = getIcon(project, GenerateIcon, GenerateIconV2, TypeToJsonIconV3)

    fun getLanguageIcon(project: Project?, language: SupportedLanguage): Icon {
        val directory = if (getIconPack(project) == JsonIconPack.VERSION_3) "v3/" else ""
        return load("/icons/languages/$directory${language.name.lowercase()}.svg")
    }

    private fun getIcon(project: Project?, v1Icon: Icon, v2Icon: Icon, v3Icon: Icon): Icon {
        return when (getIconPack(project)) {
            JsonIconPack.VERSION_1 -> v1Icon
            JsonIconPack.VERSION_2 -> v2Icon
            JsonIconPack.VERSION_3 -> v3Icon
        }
    }

    private fun getIconPack(project: Project?): JsonIconPack {
        return JsonIconPack.fromPersistedValue(project?.let { JsoninjaSettingsState.getInstance(it).iconPack })
    }
}
