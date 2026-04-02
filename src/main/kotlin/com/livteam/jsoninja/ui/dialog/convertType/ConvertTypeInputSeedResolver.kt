package com.livteam.jsoninja.ui.dialog.convertType

import com.fasterxml.jackson.databind.ObjectMapper

data class ConvertTypeSeedResolution(
    val selectedTabIndex: Int,
    val jsonInputText: String,
    val typeInputText: String,
)

class ConvertTypeInputSeedResolver(
    private val objectMapper: ObjectMapper,
) {
    fun resolve(seedText: String): ConvertTypeSeedResolution {
        if (seedText.isBlank()) {
            return ConvertTypeSeedResolution(
                selectedTabIndex = 0,
                jsonInputText = "",
                typeInputText = "",
            )
        }

        val isJsonSeed = runCatching { objectMapper.readTree(seedText) }.isSuccess
        return if (isJsonSeed) {
            ConvertTypeSeedResolution(
                selectedTabIndex = 0,
                jsonInputText = seedText,
                typeInputText = "",
            )
        } else {
            ConvertTypeSeedResolution(
                selectedTabIndex = 1,
                jsonInputText = "",
                typeInputText = seedText,
            )
        }
    }
}
