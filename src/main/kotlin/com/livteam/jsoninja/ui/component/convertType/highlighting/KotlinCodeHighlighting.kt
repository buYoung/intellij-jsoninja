package com.livteam.jsoninja.ui.component.convertType.highlighting

internal object KotlinCodeHighlighting {
    val profile = TypeCodeProfile(
        contextualKeywords = "by catch constructor delegate dynamic field file finally get import init param property receiver set setparam where actual abstract annotation companion const crossinline data enum expect external final infix inline inner internal lateinit noinline open operator out override private protected public reified sealed suspend tailrec vararg".split(' ').toSet(),
        keywords = "as break class continue do else false for fun if in interface is null object package return super this throw true try typealias typeof val var when while by catch constructor delegate dynamic field file finally get import init param property receiver set setparam where actual abstract annotation companion const crossinline data enum expect external final infix inline inner internal lateinit noinline open operator out override private protected public reified sealed suspend tailrec vararg".split(' ').toSet(),
        identifiers = TypeCodeIdentifierRules(
            TypeCodeDeclarationStyle.NAME_FIRST,
            "Any Nothing Unit Boolean Byte Short Int Long Float Double Char String Array List MutableList Set MutableSet Map MutableMap Collection Iterable Sequence Pair Triple Result".split(' ').toSet(),
            typeDeclarations = setOf("class", "interface", "object", "typealias"),
            fieldDeclarations = setOf("val", "var"),
        ),
        hasNestedBlockComments = true,
        nativeProbe = "class User { val name: String = \"value\" }",
        specialTokenScanner = TypeCodeTokenScanner { buffer, start, end, check ->
            if (buffer[start] in "`@\"")
                ScalaCodeHighlighting.scan(buffer, start, end, check, hasImplicitInterpolation = true)
            else null
        },
    )
}
