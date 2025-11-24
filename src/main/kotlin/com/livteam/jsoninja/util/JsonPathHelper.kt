package com.livteam.jsoninja.util

import com.intellij.json.psi.*
import com.intellij.openapi.util.text.StringUtil
import com.intellij.psi.PsiElement

object JsonPathHelper {

    private val IDENTIFIER_REGEX = Regex("^[a-zA-Z_][a-zA-Z0-9_]*$")

    fun getJsonPath(element: PsiElement): String? {
        return buildPath(element, isJmes = false)
    }

    fun getJmesPath(element: PsiElement): String? {
        return buildPath(element, isJmes = true)
    }

    private fun buildPath(element: PsiElement, isJmes: Boolean): String? {
        var current: PsiElement? = element
        
        // leaf token에서 시작해 구조적 JSON 요소를 찾을 때까지 상위로 이동
        while (current != null && current !is JsonValue && current !is JsonProperty && current !is JsonFile) {
            current = current.parent
        }
        
        if (current == null) return null
        if (current is JsonFile) return if (isJmes) "@" else "$"

        val parts = mutableListOf<String>()
        
        while (current != null) {
            if (current is JsonFile) break
            
            val parent = current.parent
            
            if (current is JsonProperty) {
                // 현재 property이며, 이 경로(Parent -> Property)가 key를 결정
                val name = current.name
                addPropertyPath(parts, name, isJmes)
            } else if (parent is JsonArray && current is JsonValue) {
                // 배열 안의 value이므로 이 경로(Array -> Value)에서 index를 계산
                val index = parent.valueList.indexOf(current)
                if (index >= 0) {
                    parts.add("[$index]")
                }
            }
            
            current = parent
        }
        
        if (parts.isEmpty()) return if (isJmes) "@" else "$"

        val path = parts.asReversed().joinToString("")
        
        return if (isJmes) {
             if (path.startsWith(".")) path.substring(1) else path
        } else {
             if (path.startsWith("[")) "$" + path else "$." + path.removePrefix(".")
        }
    }

    private fun addPropertyPath(parts: MutableList<String>, name: String, isJmes: Boolean) {
        if (needsQuotes(name)) {
            val escapedName = StringUtil.escapeStringCharacters(name)
            if (isJmes) {
                parts.add(".\\\"$escapedName\\\"")
            } else {
                // JsonPath에서는 일관성과 안전한 escaping을 위해 bracket 표기법 ["key"]을 사용
                parts.add("[\"$escapedName\"]")
            }
        } else {
            parts.add(".$name")
        }
    }

    private fun needsQuotes(name: String): Boolean {
        // 알파벳·숫자·밑줄 외 문자가 있거나 숫자로 시작하면 true
        // 단순화된 검증이지만 대부분의 경우를 커버
        return !IDENTIFIER_REGEX.matches(name)
    }
}
