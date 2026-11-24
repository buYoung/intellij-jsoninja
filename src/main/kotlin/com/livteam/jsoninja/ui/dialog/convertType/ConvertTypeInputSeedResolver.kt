package com.livteam.jsoninja.ui.dialog.convertType

import com.fasterxml.jackson.databind.ObjectMapper
import com.livteam.jsoninja.model.SupportedLanguage
import java.util.Locale

data class ConvertTypeSeedResolution(
    val selectedTabIndex: Int,
    val jsonInputText: String,
    val typeInputText: String,
    val detectedSourceLanguage: SupportedLanguage? = null,
)

class ConvertTypeInputSeedResolver(
    private val objectMapper: ObjectMapper,
) {
    fun resolve(
        seedText: String,
        sourceFileExtension: String? = null,
    ): ConvertTypeSeedResolution {
        val detectedLanguage = detectLanguageByExtension(sourceFileExtension)
        if (seedText.isBlank()) {
            return ConvertTypeSeedResolution(
                selectedTabIndex = 0,
                jsonInputText = "",
                typeInputText = "",
                detectedSourceLanguage = detectedLanguage,
            )
        }

        val isJsonSeed = runCatching { objectMapper.readTree(seedText) }.isSuccess
        return if (isJsonSeed) {
            ConvertTypeSeedResolution(
                selectedTabIndex = 0,
                jsonInputText = seedText,
                typeInputText = "",
                detectedSourceLanguage = null,
            )
        } else {
            ConvertTypeSeedResolution(
                selectedTabIndex = 1,
                jsonInputText = "",
                typeInputText = seedText,
                detectedSourceLanguage = detectedLanguage,
            )
        }
    }

    companion object {
        fun detectLanguageByExtension(fileExtension: String?): SupportedLanguage? {
            return when (fileExtension?.lowercase(Locale.ROOT)) {
                "kt", "kts" -> SupportedLanguage.KOTLIN
                "java" -> SupportedLanguage.JAVA
                "ts", "tsx", "mts", "cts" -> SupportedLanguage.TYPESCRIPT
                "go" -> SupportedLanguage.GO
                "c", "h" -> SupportedLanguage.C
                "cpp", "cc", "cxx", "hpp", "hh", "hxx" -> SupportedLanguage.CPP
                "cs" -> SupportedLanguage.CSHARP
                "py", "pyi" -> SupportedLanguage.PYTHON
                "rs" -> SupportedLanguage.RUST
                "scala", "sc" -> SupportedLanguage.SCALA
                "js", "jsx", "mjs", "cjs" -> SupportedLanguage.JSDOC
                else -> null
            }
        }
    }
}
