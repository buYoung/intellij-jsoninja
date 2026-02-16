package com.livteam.jsoninja.ui.dialog.generateJson.schema

data class SchemaStoreCatalogItem(
    val name: String,
    val url: String
)

enum class SchemaStoreCatalogState {
    LOADING,
    READY,
    FAILED
}

sealed class SchemaUrlComboBoxItem(open val displayText: String) {
    class CatalogEntry(val schemaStoreCatalogItem: SchemaStoreCatalogItem) : SchemaUrlComboBoxItem(
        schemaStoreCatalogItem.name
    )

    class StatusEntry(override val displayText: String) : SchemaUrlComboBoxItem(displayText)

    override fun toString(): String = displayText
}
