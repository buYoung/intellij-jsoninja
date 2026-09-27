package com.livteam.jsoninja.ui.component.convertType.highlighting

internal object JavaCodeHighlighting {
    val profile = TypeCodeProfile(
        contextualKeywords = setOf("record", "sealed", "permits", "var", "yield"),
        keywords = "abstract assert boolean break byte case catch char class const continue default do double else enum extends final finally float for goto if implements import instanceof int interface long native new package private protected public return short static strictfp super switch synchronized this throw throws transient try void volatile while true false null record sealed permits var yield".split(' ').toSet(),
        identifiers = TypeCodeIdentifierRules(
            TypeCodeDeclarationStyle.TYPE_FIRST,
            "Object String Boolean Byte Short Integer Long Float Double Character Number List Map Set Collection Iterable Optional BigInteger BigDecimal".split(' ').toSet(),
            "boolean byte char double float int long short void var".split(' ').toSet(),
        ),
        nativeProbe = "class User { private String name; }",
        specialTokenScanner = TypeCodeTokenScanner { buffer, start, end, check ->
            if (buffer[start] == '@') ScalaCodeHighlighting.scan(buffer, start, end, check)
            else if (start + 2 < end && buffer.subSequence(start, start + 3).toString() == "\"\"\"") {
                // Java text blocks retain backslash escaping, unlike Scala raw triple strings.
                var offset = start + 3
                while (offset < end) {
                    if (offset and 255 == 0) check()
                    if (buffer[offset] == '\\') offset = (offset + 2).coerceAtMost(end)
                    else if (offset + 2 < end && buffer[offset] == '"' && buffer[offset + 1] == '"' && buffer[offset + 2] == '"') {
                        offset += 3
                        break
                    } else offset++
                }
                TypeCodeScanResult(offset, TypeCodeToken.STRING)
            } else null
        },
    )
}
