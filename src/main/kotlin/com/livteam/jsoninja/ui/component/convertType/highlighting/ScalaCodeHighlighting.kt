package com.livteam.jsoninja.ui.component.convertType.highlighting

internal object ScalaCodeHighlighting {
    val profile = TypeCodeProfile(
        identifiers = TypeCodeIdentifierRules(
            TypeCodeDeclarationStyle.NAME_FIRST,
            "Any AnyRef AnyVal Nothing Null Unit Boolean Byte Short Int Long Float Double Char String List Seq Set Map Option Some None Either Vector Array".split(' ').toSet(),
            typeDeclarations = setOf("class", "object", "trait", "enum", "type"),
            fieldDeclarations = setOf("val", "var"),
        ),
        keywords = "abstract case catch class def do else enum export extends false final finally for forSome given if implicit import lazy match new null object override package private protected return sealed super then this throw trait true try type val var while with yield".split(' ').toSet(),
        hasNestedBlockComments = true,
        nativeProbe = "case class User(name: String) // comment",
        nativeStringProbe = "\"ordinary\"; val s = \"\"\"first\n// JSONINJA_LITERAL\"\"\"",
        specialTokenScanner = TypeCodeTokenScanner(::scan),
    )
    fun scan(buffer: CharSequence, start: Int, end: Int, check: () -> Unit, hasImplicitInterpolation: Boolean = false): TypeCodeScanResult? {
        fun matches(offset: Int, value: String) = offset + value.length <= end && value.indices.all { buffer[offset + it] == value[it] }
        var scanned = 0
        fun poll() { if (++scanned and 255 == 0) check() }
        if (buffer[start] == '`') {
            var offset = start + 1
            while (offset < end && buffer[offset] !in "`\r\n") { poll(); offset++ }
            if (offset < end && buffer[offset] == '`') offset++
            return TypeCodeScanResult(offset, TypeCodeToken.IDENTIFIER)
        }
        if (buffer[start] == '@') {
            var offset = start + 1
            while (offset < end && (buffer[offset].isLetterOrDigit() || buffer[offset] in "_.")) { poll(); offset++ }
            return TypeCodeScanResult(offset, TypeCodeToken.METADATA)
        }
        var quote = start
        if (buffer[start].isLetter() || buffer[start] == '_') {
            while (quote < end && (buffer[quote].isLetterOrDigit() || buffer[quote] == '_')) { poll(); quote++ }
        }
        if (quote >= end || buffer[quote] != '"') return null
        val isTriple = matches(quote, "\"\"\"")
        val isInterpolated = hasImplicitInterpolation || quote != start
        var offset = quote + if (isTriple) 3 else 1
        var interpolationDepth = 0
        while (offset < end) {
            poll()
            if (isInterpolated && matches(offset, "\${")) { interpolationDepth++; offset += 2; continue }
            if (interpolationDepth > 0) {
                when (buffer[offset]) {
                    '{' -> interpolationDepth++
                    '}' -> interpolationDepth--
                    '"', '\'' -> {
                        val nestedQuote = buffer[offset++]
                        while (offset < end && buffer[offset] != nestedQuote) { poll(); offset += if (buffer[offset] == '\\' && offset + 1 < end) 2 else 1 }
                    }
                }
                if (offset < end) offset++
                continue
            }
            if (isTriple && matches(offset, "\"\"\"")) { offset += 3; break }
            if (!isTriple) {
                if (buffer[offset] == '"') { offset++; break }
                if (buffer[offset] in "\r\n") break
                if (buffer[offset] == '\\') { offset = (offset + 2).coerceAtMost(end); continue }
            }
            offset++
        }
        return TypeCodeScanResult(offset, TypeCodeToken.STRING)
    }
}
