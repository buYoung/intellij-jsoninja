package com.livteam.jsoninja.ui.component.convertType.highlighting

internal object JSDocCodeHighlighting {
    val profile = TypeCodeProfile(
        identifiers = TypeCodeIdentifierRules(
            TypeCodeDeclarationStyle.NAME_FIRST,
            "string number boolean bigint symbol undefined null any unknown never Object Array String Number Boolean Function Promise Map Set Date RegExp".split(' ').toSet(),
            fieldDeclarations = setOf("const", "let", "var"),
            allowsKeywordFieldNames = true,
        ),
        keywords = "async await break case catch class const continue debugger default delete do else export extends false finally for function if import in instanceof let new null return static super switch this throw true try typeof var void while with yield".split(' ').toSet(),
        hasDocumentationTags = true,
        nativeProbe = "/** @typedef {Object} User */ const value = 1;",
        specialTokenScanner = TypeCodeTokenScanner(::scan),
    )
    fun scan(buffer: CharSequence, start: Int, end: Int, check: () -> Unit): TypeCodeScanResult? {
        var scanned = 0
        fun poll() { if (++scanned and 255 == 0) check() }
        if (buffer[start] == '`') {
            var offset = start + 1
            var interpolationDepth = 0
            while (offset < end) {
                poll()
                val c = buffer[offset++]
                if (c == '\\') { if (offset < end) offset++; continue }
                if (c == '$' && offset < end && buffer[offset] == '{') { interpolationDepth++; offset++; continue }
                if (interpolationDepth > 0) {
                    if (c == '{') interpolationDepth++
                    if (c == '}') interpolationDepth--
                    if (c in "\"'") {
                        while (offset < end && buffer[offset] != c) { poll(); offset += if (buffer[offset] == '\\' && offset + 1 < end) 2 else 1 }
                        if (offset < end) offset++
                    }
                } else if (c == '`') break
            }
            return TypeCodeScanResult(offset, TypeCodeToken.STRING)
        }
        if (buffer[start] == '/' && start + 1 < end && buffer[start + 1] !in "/*") {
            var previous = start - 1
            while (previous >= 0 && buffer[previous].isWhitespace()) { poll(); previous-- }
            if (previous < 0 || buffer[previous] in "=([{,:!?;") {
                var offset = start + 1
                var isCharacterClass = false
                while (offset < end && buffer[offset] !in "\r\n") {
                    poll()
                    val c = buffer[offset++]
                    if (c == '\\') { if (offset < end) offset++; continue }
                    if (c == '[') isCharacterClass = true
                    if (c == ']') isCharacterClass = false
                    if (c == '/' && !isCharacterClass) {
                        while (offset < end && buffer[offset].isLetter()) { poll(); offset++ }
                        break
                    }
                }
                return TypeCodeScanResult(offset, TypeCodeToken.STRING)
            }
        }
        return null
    }
}
