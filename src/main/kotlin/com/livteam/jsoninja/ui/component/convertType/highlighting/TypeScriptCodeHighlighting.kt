package com.livteam.jsoninja.ui.component.convertType.highlighting

internal object TypeScriptCodeHighlighting {
    val profile = JSDocCodeHighlighting.profile.copy(
        keywords = JSDocCodeHighlighting.profile.keywords + "abstract any as asserts bigint boolean constructor declare enum implements infer interface is keyof module namespace never number object of override private protected public readonly require string symbol type undefined unique unknown using".split(' '),
        identifiers = JSDocCodeHighlighting.profile.identifiers!!.copy(
            predefinedTypes = JSDocCodeHighlighting.profile.identifiers.predefinedTypes + "Record Partial Required Pick Omit Readonly Exclude Extract NonNullable Parameters ReturnType ReadonlyArray".split(' '),
        ),
        nativeProbe = "interface User { name: string; }",
    )
}
