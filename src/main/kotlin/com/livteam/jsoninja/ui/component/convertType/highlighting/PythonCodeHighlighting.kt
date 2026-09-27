package com.livteam.jsoninja.ui.component.convertType.highlighting

internal object PythonCodeHighlighting {
    val profile = TypeCodeProfile(
        identifiers = TypeCodeIdentifierRules(
            TypeCodeDeclarationStyle.NAME_FIRST,
            "bool int float complex str bytes bytearray memoryview list tuple range dict set frozenset object type Any TypedDict NotRequired Required Optional Union Literal Annotated ClassVar Final TypeAlias TypeVar List Dict Set Tuple Mapping Sequence Iterable Callable Never Self".split(' ').toSet(),
        ),
        keywords = "False None True and as assert async await break class continue def del elif else except finally for from global if import in is lambda nonlocal not or pass raise return try while with yield".split(' ').toSet(),
        hasSlashComments = false,
        allowsEscapedNewlines = true,
        nativeProbe = "class User:\n    name: str = 'value' # comment",
        nativeStringProbe = "\"ordinary\"; value = '''first\n# JSONINJA_LITERAL\nlast'''",
        specialTokenScanner = TypeCodeTokenScanner(::scan),
    )
    private val prefixes = setOf("", "r", "u", "b", "f", "br", "rb", "fr", "rf")

    private fun scan(buffer: CharSequence, start: Int, end: Int, check: () -> Unit): TypeCodeScanResult? {
        var scanned = 0
        fun poll() { if (++scanned and 255 == 0) check() }
        if (buffer[start] == '#') {
            var offset = start + 1
            while (offset < end && buffer[offset] !in "\r\n") { poll(); offset++ }
            return TypeCodeScanResult(offset, TypeCodeToken.LINE_COMMENT)
        }
        if (buffer[start] == '@') {
            var previous = start - 1
            while (previous >= 0 && buffer[previous] in " \t") previous--
            if (previous < 0 || buffer[previous] in "\r\n") {
                var offset = start + 1
                while (offset < end && (buffer[offset].isLetterOrDigit() || buffer[offset] in "_.")) { poll(); offset++ }
                return TypeCodeScanResult(offset, TypeCodeToken.METADATA)
            }
        }
        var quoteStart = start
        while (quoteStart < end && quoteStart - start < 2 && buffer[quoteStart].isLetter()) quoteStart++
        if (quoteStart >= end || buffer[quoteStart] !in "'\"") return null
        if (buffer.subSequence(start, quoteStart).toString().lowercase(java.util.Locale.ROOT) !in prefixes) return null
        val quote = buffer[quoteStart]
        val isTriple = quoteStart + 2 < end && buffer[quoteStart + 1] == quote && buffer[quoteStart + 2] == quote
        var offset = quoteStart + if (isTriple) 3 else 1
        while (offset < end) {
            poll()
            if (buffer[offset] == '\\') {
                val escapedLength = if (offset + 2 < end && buffer[offset + 1] == '\r' && buffer[offset + 2] == '\n') 3 else 2
                offset = (offset + escapedLength).coerceAtMost(end)
                continue
            }
            if (buffer[offset] == quote && (!isTriple || offset + 2 < end && buffer[offset + 1] == quote && buffer[offset + 2] == quote)) {
                offset += if (isTriple) 3 else 1
                break
            }
            if (!isTriple && buffer[offset] in "\r\n") break
            offset++
        }
        return TypeCodeScanResult(offset, TypeCodeToken.STRING)
    }
}
