# Service Interface Contract: Type Conversion Services

**Date**: 2026-04-02

## JsonToTypeConversionService

Converts JSON string to type declaration source code.

### Input
- `jsonText: String` — Valid JSON string (object or array)
- `language: SupportedLanguage` — Target language
- `options: JsonToTypeConversionOptions` — Conversion settings

### Output
- `String` — Generated type declaration source code (may include imports, multiple type declarations)

### Error Conditions
- Empty/blank JSON text → validation error
- Invalid JSON syntax → validation error with parse error detail
- Invalid root type name (non-identifier) → validation error
- Depth exceeding `maximumDepth` → partial result with warning comment

---

## TypeToJsonGenerationService

Converts type declaration source code to sample JSON.

### Input
- `sourceCode: String` — Type declaration source code
- `language: SupportedLanguage` — Source language
- `options: TypeToJsonGenerationOptions` — Generation settings
- `rootTypeName: String?` — Optional root type (defaults to first declaration)

### Output
- `String` — Generated JSON string (single object or array of objects)

### Error Conditions
- Empty source code → validation error
- WASM analysis failure → error with diagnostic message
- No type declarations found → error message
- Output count out of range (1-100) → validation error

---

## TypeDeclarationAnalyzerService

Wraps WASM `analyze_source` for Kotlin consumption.

### Input
- `sourceCode: String` — Type declaration source code
- `language: SupportedLanguage` — Source language

### Output
- `TypeAnalysisResult` containing:
  - `declarations: List<TypeDeclaration>` — Parsed type declarations
  - `diagnostics: List<Diagnostic>` — Warnings/errors from parsing

### Error Conditions
- WASM runtime initialization failure → runtime error
- WASM memory allocation failure → runtime error
- Source code parse failure → result with diagnostics (non-fatal)
