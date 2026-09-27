package com.livteam.jsoninja.services.typeConversion.languages.csharp

import com.livteam.jsoninja.model.typeConversion.TypeDeclaration
import com.livteam.jsoninja.model.typeConversion.TypeDeclarationKind
import com.livteam.jsoninja.services.typeConversion.languages.TypeLanguagePolicy

internal object CSharpTypePolicy : TypeLanguagePolicy {
    override fun escapeDeclarationName(candidate: String): String = if (candidate in setOf("System", "List", "Dictionary", "JsonPropertyName", "JsonPropertyNameAttribute")) "${candidate}Model" else candidate
    override val objectDeclarationKind = TypeDeclarationKind.CLASS
    override val reservedFieldNames = "abstract as base bool break byte case catch char checked class const continue decimal default delegate do double else enum event explicit extern false finally fixed float for foreach goto if implicit in int interface internal is lock long namespace new null object operator out override params private protected public readonly ref return sbyte sealed short sizeof stackalloc static string struct switch this throw true try typeof uint ulong unchecked unsafe ushort using virtual void volatile while".split(' ').toSet()

    override fun normalizeDeclaration(declaration: TypeDeclaration): TypeDeclaration {
        val used = mutableSetOf(declaration.name)
        return declaration.copy(fields = declaration.fields.map { field ->
            var candidate = field.name
            var suffix = 2
            if (candidate == declaration.name) candidate += "Value"
            val base = candidate
            while (!used.add(candidate)) candidate = "$base${suffix++}"
            field.copy(name = candidate)
        })
    }

    override fun resolveEnumValues(members: List<Pair<String, String?>>) = CSharpEnumValues.resolve(members)
}
