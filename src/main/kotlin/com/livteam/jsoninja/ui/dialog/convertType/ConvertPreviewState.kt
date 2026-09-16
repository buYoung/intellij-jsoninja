package com.livteam.jsoninja.ui.dialog.convertType

internal sealed interface ConvertPreviewState<out Config> {
    data object Pending : ConvertPreviewState<Nothing>
    data class Invalid(val message: String? = null) : ConvertPreviewState<Nothing>
    data class Ready<Config>(val input: String, val config: Config, val text: String) : ConvertPreviewState<Config>
}
