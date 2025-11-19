package com.livteam.jsoninja.util

import com.intellij.json.psi.*
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
        
        // Navigate up from leaf tokens to a structural JSON element
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
                // We are at a property. This edge (Parent -> Property) defines the key.
                val name = current.name
                addPropertyPath(parts, name, isJmes)
            } else if (parent is JsonArray && current is JsonValue) {
                // We are a value in an array. This edge (Array -> Value) defines the index.
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
        if (isJmes) {
            if (needsQuotes(name)) {
                val escapedName = name.replace("\"", "\\\"")
                parts.add(".\\\"$escapedName\\\"")
            } else {
                parts.add(".$name")
            }
        } else {
            // JsonPath
            if (needsQuotes(name)) {
                val escapedName = name.replace("'", "\\'")
                parts.add("['$escapedName']")
            } else {
                parts.add(".$name")
            }
        }
    }

    private fun needsQuotes(name: String): Boolean {
        // Simple check: if it contains anything other than alphanumeric or underscore, or starts with digit
        // This is a simplification but covers most cases.
        return !IDENTIFIER_REGEX.matches(name)
    }
}
