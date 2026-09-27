package com.livteam.jsoninja.ui.component.convertType.highlighting

internal object GoCodeHighlighting {
    val profile = TypeCodeProfile(
        keywords = "break case chan const continue default defer else fallthrough for func go goto if import interface map package range return select struct switch type var".split(' ').toSet(),
        identifiers = TypeCodeIdentifierRules(
            TypeCodeDeclarationStyle.GO,
            "any comparable bool byte complex64 complex128 error float32 float64 int int8 int16 int32 int64 rune string uint uint8 uint16 uint32 uint64 uintptr true false nil".split(' ').toSet(),
            fieldDeclarations = setOf("var", "const"),
        ),
        nativeProbe = "type User struct { Name string }",
        specialTokenScanner = TypeCodeTokenScanner { buffer, start, end, check ->
            if (buffer[start] != '`') null else {
                var offset = start + 1
                while (offset < end && buffer[offset] != '`') {
                    if (offset and 255 == 0) check()
                    offset++
                }
                if (offset < end) offset++
                TypeCodeScanResult(offset, TypeCodeToken.STRING)
            }
        },
    )
}
