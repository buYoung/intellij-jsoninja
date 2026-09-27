package com.livteam.jsoninja.services.typeConversion.languages

import com.livteam.jsoninja.model.SupportedLanguage
import com.livteam.jsoninja.services.typeConversion.languages.jsdoc.JsDocTypeRenderer
import com.livteam.jsoninja.services.typeConversion.languages.jsdoc.JsDocTypePolicy
import com.livteam.jsoninja.services.typeConversion.languages.scala.ScalaTypeRenderer
import com.livteam.jsoninja.services.typeConversion.languages.scala.ScalaTypePolicy
import com.livteam.jsoninja.services.typeConversion.languages.rust.RustTypeRenderer
import com.livteam.jsoninja.services.typeConversion.languages.rust.RustTypePolicy
import com.livteam.jsoninja.services.typeConversion.languages.python.PythonTypeRenderer
import com.livteam.jsoninja.services.typeConversion.languages.python.PythonTypePolicy
import com.livteam.jsoninja.services.typeConversion.languages.csharp.CSharpTypeRenderer
import com.livteam.jsoninja.services.typeConversion.languages.csharp.CSharpTypePolicy
import com.livteam.jsoninja.services.typeConversion.languages.cpp.CppTypeRenderer
import com.livteam.jsoninja.services.typeConversion.languages.cpp.CppTypePolicy
import com.livteam.jsoninja.services.typeConversion.languages.c.CTypeRenderer
import com.livteam.jsoninja.services.typeConversion.languages.c.CTypePolicy
import com.livteam.jsoninja.services.typeConversion.languages.kotlin.KotlinTypeRenderer
import com.livteam.jsoninja.services.typeConversion.languages.kotlin.KotlinTypePolicy
import com.livteam.jsoninja.services.typeConversion.languages.java.JavaTypeRenderer
import com.livteam.jsoninja.services.typeConversion.languages.java.JavaTypePolicy
import com.livteam.jsoninja.services.typeConversion.languages.typescript.TypescriptTypeRenderer
import com.livteam.jsoninja.services.typeConversion.languages.typescript.TypescriptTypePolicy
import com.livteam.jsoninja.services.typeConversion.languages.go.GoTypeRenderer
import com.livteam.jsoninja.services.typeConversion.languages.go.GoTypePolicy

internal data class TypeLanguageSupport(val renderer: TypeLanguageRenderer, val policy: TypeLanguagePolicy)

internal object TypeLanguageRegistry {
    private val languages = mapOf(
        SupportedLanguage.JSDOC to TypeLanguageSupport(JsDocTypeRenderer(), JsDocTypePolicy),
        SupportedLanguage.SCALA to TypeLanguageSupport(ScalaTypeRenderer(), ScalaTypePolicy),
        SupportedLanguage.RUST to TypeLanguageSupport(RustTypeRenderer(), RustTypePolicy),
        SupportedLanguage.PYTHON to TypeLanguageSupport(PythonTypeRenderer(), PythonTypePolicy),
        SupportedLanguage.CSHARP to TypeLanguageSupport(CSharpTypeRenderer(), CSharpTypePolicy),
        SupportedLanguage.CPP to TypeLanguageSupport(CppTypeRenderer(), CppTypePolicy),
        SupportedLanguage.C to TypeLanguageSupport(CTypeRenderer(), CTypePolicy),
        SupportedLanguage.KOTLIN to TypeLanguageSupport(KotlinTypeRenderer(), KotlinTypePolicy),
        SupportedLanguage.JAVA to TypeLanguageSupport(JavaTypeRenderer(), JavaTypePolicy),
        SupportedLanguage.TYPESCRIPT to TypeLanguageSupport(TypescriptTypeRenderer(), TypescriptTypePolicy),
        SupportedLanguage.GO to TypeLanguageSupport(GoTypeRenderer(), GoTypePolicy),
    )

    fun forLanguage(language: SupportedLanguage): TypeLanguageSupport = languages.getValue(language)
}
