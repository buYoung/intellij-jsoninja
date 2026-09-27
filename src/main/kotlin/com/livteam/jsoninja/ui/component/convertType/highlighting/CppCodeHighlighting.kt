package com.livteam.jsoninja.ui.component.convertType.highlighting

internal object CppCodeHighlighting {
    val profile = TypeCodeProfile(
        identifiers = CCodeHighlighting.profile.identifiers!!.copy(
            predefinedTypes = CCodeHighlighting.profile.identifiers.predefinedTypes + "string string_view vector map unordered_map optional variant monostate array tuple pair unique_ptr shared_ptr".split(' '),
            primitiveKeywords = CCodeHighlighting.profile.identifiers.primitiveKeywords + setOf("auto", "char16_t", "char32_t", "wchar_t"),
        ),
        keywords = "alignas alignof and and_eq asm auto bitand bitor bool break case catch char char16_t char32_t class compl const constexpr const_cast continue decltype default delete do double dynamic_cast else enum explicit export extern false float for friend goto if inline int long mutable namespace new noexcept not not_eq nullptr operator or or_eq private protected public register reinterpret_cast return short signed sizeof static static_assert static_cast struct switch template this thread_local throw true try typedef typeid typename union unsigned using virtual void volatile wchar_t while xor xor_eq".split(' ').toSet(),
        stringPrefixes = setOf("u8", "u", "U", "L"),
        rawStringPrefixes = setOf("R", "u8R", "uR", "UR", "LR"),
        hasPreprocessor = true,
        hasCLineSplicing = true,
        hasQuotedDigitSeparators = true,
        nativeProbe = "struct User { std::string name; int id; };",
        nativeStringProbe = "\"ordinary\"; auto raw = R\"tag(\" /* JSONINJA_LITERAL */)tag\";",
    )
}
