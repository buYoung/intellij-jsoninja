package com.livteam.jsoninja.model

enum class JsonIconPack {
    VERSION_1,
    VERSION_2,
    VERSION_3;

    companion object {
        fun fromPersistedValue(value: String?): JsonIconPack {
            return entries.firstOrNull { it.name == value } ?: VERSION_3
        }
    }
}
