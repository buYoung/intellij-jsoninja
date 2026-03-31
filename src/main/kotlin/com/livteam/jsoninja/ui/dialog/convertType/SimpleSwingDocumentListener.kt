package com.livteam.jsoninja.ui.dialog.convertType

import javax.swing.event.DocumentEvent
import javax.swing.event.DocumentListener

class SimpleSwingDocumentListener(
    private val onChange: () -> Unit,
) : DocumentListener {
    override fun insertUpdate(event: DocumentEvent) = onChange()

    override fun removeUpdate(event: DocumentEvent) = onChange()

    override fun changedUpdate(event: DocumentEvent) = onChange()
}
