package com.livteam.jsoninja.ui.component.convertType.highlighting

internal object RustCodeHighlighting {
    val profile = TypeCodeProfile(
        identifiers = TypeCodeIdentifierRules(
            TypeCodeDeclarationStyle.NAME_FIRST,
            "bool char str i8 i16 i32 i64 i128 isize u8 u16 u32 u64 u128 usize f32 f64 String Vec Option Result Box HashMap BTreeMap HashSet BTreeSet Cow".split(' ').toSet(),
            fieldDeclarations = setOf("let", "const", "static"),
        ),
        keywords = "as async await break const continue crate dyn else enum extern false fn for if impl in let loop match mod move mut pub ref return self Self static struct super trait true type unsafe use where while abstract become box do final macro override priv typeof unsized virtual yield try union".split(' ').toSet(),
        stringPrefixes = setOf("b", "c"),
        hasNestedBlockComments = true,
        allowsMultilineQuotedStrings = true,
        allowsEscapedNewlines = true,
        nativeProbe = "pub struct User { name: String } // comment",
        nativeStringProbe = "\"ordinary\"; r##\"raw \" /* JSONINJA_LITERAL */\"##",
        specialTokenScanner = TypeCodeTokenScanner(::scan),
    )
    private fun scan(buffer: CharSequence, start: Int, end: Int, check: () -> Unit): TypeCodeScanResult? {
        fun matches(offset: Int, value: String) = offset + value.length <= end && value.indices.all { buffer[offset + it] == value[it] }
        var scanned = 0
        fun poll() { if (++scanned and 255 == 0) check() }
        if (matches(start, "///") || matches(start, "//!")) {
            var offset = start + 3
            while (offset < end && buffer[offset] !in "\r\n") { poll(); offset++ }
            return TypeCodeScanResult(offset, TypeCodeToken.DOC_COMMENT)
        }
        if (matches(start, "#[") || matches(start, "#![")) {
            var offset = start + if (matches(start, "#![")) 3 else 2
            while (offset < end && (buffer[offset].isLetterOrDigit() || buffer[offset] in "_ :")) { poll(); offset++ }
            return TypeCodeScanResult(offset, TypeCodeToken.METADATA)
        }
        val rawStart = when { matches(start, "br") || matches(start, "cr") -> start + 2; buffer[start] == 'r' -> start + 1; else -> -1 }
        if (rawStart >= 0) {
            var offset = rawStart
            while (offset < end && buffer[offset] == '#') { poll(); offset++ }
            val hashes = offset - rawStart
            if (offset < end && buffer[offset] == '"' && hashes <= 255) {
                offset++
                while (offset < end) {
                    poll()
                    if (buffer[offset++] != '"') continue
                    var closingHashes = 0
                    while (closingHashes < hashes && offset < end && buffer[offset] == '#') { poll(); offset++; closingHashes++ }
                    if (closingHashes == hashes) break
                }
                return TypeCodeScanResult(offset, TypeCodeToken.STRING)
            }
            if (rawStart == start + 1 && matches(start, "r#") && start + 2 < end && (buffer[start + 2].isLetter() || buffer[start + 2] == '_')) {
                offset = start + 3
                while (offset < end && (buffer[offset].isLetterOrDigit() || buffer[offset] == '_')) { poll(); offset++ }
                return TypeCodeScanResult(offset, TypeCodeToken.IDENTIFIER)
            }
        }
        if (buffer[start] == '\'' && start + 1 < end && (buffer[start + 1].isLetter() || buffer[start + 1] == '_')) {
            var offset = start + 2
            while (offset < end && (buffer[offset].isLetterOrDigit() || buffer[offset] == '_')) { poll(); offset++ }
            return if (offset < end && buffer[offset] == '\'') TypeCodeScanResult(offset + 1, TypeCodeToken.STRING)
            else TypeCodeScanResult(offset, TypeCodeToken.IDENTIFIER)
        }
        return null
    }
}
