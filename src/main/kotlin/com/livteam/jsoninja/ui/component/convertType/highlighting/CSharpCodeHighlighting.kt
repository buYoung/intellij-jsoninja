package com.livteam.jsoninja.ui.component.convertType.highlighting

internal object CSharpCodeHighlighting {
    val profile = TypeCodeProfile(
        contextualKeywords = setOf("record", "init", "required", "var", "get", "set", "value", "async", "await"),
        identifiers = TypeCodeIdentifierRules(
            TypeCodeDeclarationStyle.TYPE_FIRST,
            "String Boolean Byte Int16 Int32 Int64 UInt16 UInt32 UInt64 Single Double Decimal Object List Dictionary IList IDictionary IEnumerable ICollection Nullable DateTime Guid Task".split(' ').toSet(),
            "bool byte char decimal double float int long object sbyte short string uint ulong ushort var void".split(' ').toSet(),
            hasBracketAttributes = true,
        ),
        keywords = "abstract as base bool break byte case catch char checked class const continue decimal default delegate do double else enum event explicit extern false finally fixed float for foreach goto if implicit in int interface internal is lock long namespace new null object operator out override params private protected public readonly ref return sbyte sealed short sizeof stackalloc static string struct switch this throw true try typeof uint ulong unchecked unsafe ushort using virtual void volatile while".split(' ').toSet() + setOf("record", "init", "required", "var", "get", "set", "value", "async", "await"),
        hasPreprocessor = true,
        nativeProbe = "public class User { public string Name { get; set; } }",
        nativeStringProbe = "\"ordinary\"; var text = @\"first\n\"\" /* JSONINJA_LITERAL */\";",
        specialTokenScanner = TypeCodeTokenScanner(::scan),
    )

    private fun scan(buffer: CharSequence, start: Int, end: Int, check: () -> Unit): TypeCodeScanResult? {
        fun matches(offset: Int, value: String) = offset + value.length <= end && value.indices.all { buffer[offset + it] == value[it] }
        var scanned = 0
        fun poll() { if (++scanned and 255 == 0) check() }
        if (matches(start, "///")) {
            var offset = start + 3
            while (offset < end && buffer[offset] !in "\r\n") { poll(); offset++ }
            return TypeCodeScanResult(offset, TypeCodeToken.DOC_COMMENT)
        }
        val first = buffer[start]
        if (first !in "@\"$") return null
        if (first == '@' && start + 1 < end && (buffer[start + 1].isLetter() || buffer[start + 1] == '_')) {
            var offset = start + 2
            while (offset < end && (buffer[offset].isLetterOrDigit() || buffer[offset] == '_')) { poll(); offset++ }
            return TypeCodeScanResult(offset, TypeCodeToken.IDENTIFIER)
        }
        var quoteStart = start
        while (quoteStart < end && buffer[quoteStart] == '$') { poll(); quoteStart++ }
        var quoteEnd = quoteStart
        while (quoteEnd < end && buffer[quoteEnd] == '"') { poll(); quoteEnd++ }
        val quoteCount = quoteEnd - quoteStart
        if (quoteCount >= 3) {
            var offset = quoteEnd
            var closingQuotes = 0
            while (offset < end) {
                poll()
                closingQuotes = if (buffer[offset++] == '"') closingQuotes + 1 else 0
                if (closingQuotes == quoteCount) break
            }
            return TypeCodeScanResult(offset, TypeCodeToken.STRING)
        }
        val prefix = listOf("$@", "@$", "@", "$").firstOrNull { matches(start, "$it\"") } ?: return null
        val isVerbatim = '@' in prefix
        val isInterpolated = '$' in prefix
        var offset = start + prefix.length + 1
        var braceDepth = 0
        var expressionQuote: Char? = null
        while (offset < end) {
            poll()
            val character = buffer[offset]
            if (expressionQuote != null) {
                if (character == '\\') offset = (offset + 2).coerceAtMost(end)
                else { if (character == expressionQuote) expressionQuote = null; offset++ }
                continue
            }
            if (braceDepth > 0) {
                when (character) {
                    '{' -> braceDepth++
                    '}' -> braceDepth--
                    '\'', '"' -> expressionQuote = character
                }
                offset++
                continue
            }
            if (character == '"') {
                if (isVerbatim && matches(offset, "\"\"")) { offset += 2; continue }
                offset++
                break
            }
            if (isInterpolated && character == '{') {
                if (matches(offset, "{{")) offset += 2 else { braceDepth = 1; offset++ }
                continue
            }
            if (!isVerbatim && character in "\r\n") break
            if (!isVerbatim && character == '\\') offset = (offset + 2).coerceAtMost(end) else offset++
        }
        return TypeCodeScanResult(offset, TypeCodeToken.STRING)
    }
}
