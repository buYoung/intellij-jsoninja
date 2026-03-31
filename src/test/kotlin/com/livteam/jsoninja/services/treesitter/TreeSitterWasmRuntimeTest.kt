package com.livteam.jsoninja.services.treesitter

import com.livteam.jsoninja.services.BundledResourceService
import com.livteam.jsoninja.services.JsonObjectMapperService
import org.junit.After
import org.junit.Assert.assertTrue
import org.junit.Test

class TreeSitterWasmRuntimeTest {
    private val bundledResourceService = BundledResourceService()
    private val runtime = TreeSitterWasmRuntime(
        bundledResourceService = bundledResourceService,
        objectMapperService = JsonObjectMapperService(),
    )

    @After
    fun tearDown() {
        runtime.dispose()
    }

    @Test
    fun executeQuery_returnsCapturesForAllSupportedLanguages() {
        SupportedLanguage.entries.forEach { language ->
            val query = bundledResourceService.loadText(language.queryResourcePath)
            val sourceCode = sampleSourceCode(language)

            val result = runtime.executeQuery(language, sourceCode, query)

            assertTrue("Expected no runtime error for ${language.name}", result.error == null)
            assertTrue(
                "Expected type.name capture for ${language.name}",
                result.captures.any { capture -> capture.name == "type.name" }
            )
            assertTrue(
                "Expected field.name capture for ${language.name}",
                result.captures.any { capture -> capture.name == "field.name" }
            )
        }
    }

    private fun sampleSourceCode(language: SupportedLanguage): String {
        return when (language) {
            SupportedLanguage.JAVA -> """
                class UserResponse<T> extends BaseResponse implements Identifiable {
                    String name;
                }
            """.trimIndent()

            SupportedLanguage.KOTLIN -> """
                data class UserResponse<T>(
                    val name: String?,
                ) : BaseResponse
            """.trimIndent()

            SupportedLanguage.TYPESCRIPT -> """
                class UserResponse<T> extends BaseResponse {
                  name?: string;
                }
            """.trimIndent()

            SupportedLanguage.GO -> """
                type UserResponse[T any] struct {
                    Name string `json:"name"`
                }
            """.trimIndent()
        }
    }
}
