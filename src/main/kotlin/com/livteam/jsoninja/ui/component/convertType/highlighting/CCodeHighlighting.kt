package com.livteam.jsoninja.ui.component.convertType.highlighting

internal object CCodeHighlighting {
    val profile = TypeCodeProfile(
        identifiers = TypeCodeIdentifierRules(
            TypeCodeDeclarationStyle.TYPE_FIRST,
            "size_t ptrdiff_t int8_t int16_t int32_t int64_t uint8_t uint16_t uint32_t uint64_t intptr_t uintptr_t".split(' ').toSet(),
            "char double float int long short signed unsigned void _Bool bool".split(' ').toSet(),
        ),
        keywords = setOf(
            "auto", "break", "case", "char", "const", "continue", "default", "do", "double", "else", "enum",
            "extern", "float", "for", "goto", "if", "inline", "int", "long", "register", "restrict", "return",
            "short", "signed", "sizeof", "static", "struct", "switch", "typedef", "union", "unsigned", "void",
            "volatile", "while", "_Alignas", "_Alignof", "_Atomic", "_Bool", "_Complex", "_Generic", "_Imaginary",
            "_Noreturn", "_Static_assert", "_Thread_local", "bool", "true", "false",
        ),
        stringPrefixes = setOf("u8", "u", "U", "L"),
        hasPreprocessor = true,
        hasCLineSplicing = true,
        nativeProbe = "struct User { int id; char *name; }; // comment\nconst char *s = \"text\";",
    )
}
