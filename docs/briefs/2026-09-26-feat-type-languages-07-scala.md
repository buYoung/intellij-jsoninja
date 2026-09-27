# [feat] Add Scala JSON and type conversion

실행 상태: 구현과 자동 검증을 완료했다. [전체 검증 기록](type-languages/09-verification.md)에 메인 에이전트의 검토·골든·단위·통합 검증 및 남은 수동 확인을 기록했다. 최신 사용자의 골든·단위 테스트 추가 지시가 이 문서의 기존 테스트 추가 제한에 우선한다.

## Work Type

feat

## Current State (As-Is)

- [confirmed] Highlighting review on 2026-09-27 at revision `519470b` traced `CodeInputPanel.rebuildEditor` and `CodePreviewPanel.ensureViewer` through `src/main/kotlin/com/livteam/jsoninja/ui/component/editor/EditorTextFieldFactory.kt:createCodeField`. The route uses the IDE file type/provider and otherwise plain text; it has no bundled Scala lexical profile.
- [confirmed] Source review on 2026-09-26 at revision `1077537` on `codex/dependabot-194` found no SCALA entry in `src/main/kotlin/com/livteam/jsoninja/model/SupportedLanguage.kt` or corresponding Rust entry in `tree-sitter-wasm/src/language.rs`.
- [confirmed] `src/main/resources/icons/languages/v3/scala.svg` and `scala_dark.svg` exist. The base language-icon directory contains only the original four languages, so the old-pack path needs the fallback established by the core brief.
- [confirmed] `tree-sitter-wasm/Cargo.toml:language-grammars`, crate/plugin query directories and analyzer/type_parser dispatch currently cover only Java, Kotlin, TypeScript and Go.
- [confirmed] `src/main/kotlin/com/livteam/jsoninja/ui/dialog/convertType/ConvertTypeDialogPresenter.kt:syncLanguage` synchronizes both tabs and `consumeCurrentPreview` passes text plus extension to the final consumer. Both directions must be complete before this entry is selectable.
- [confirmed] `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/TypeDeclarationAnalyzerService.kt:analyzeSource` loads the language query and invokes the numeric-ID WASM entry, then decodes through `TreeSitterQueryResult.toTypeAnalysisResult`. Rust source edits alone do not change the shipped module.
- [confirmed] `src/test/kotlin/com/livteam/jsoninja/services/typeConversion` contains enum-wide loops and exhaustive four-language expectation helpers. `tree-sitter-wasm/src/tests.rs:get_supported_languages_returns_language_descriptors` compares the entire descriptor list.
- [confirmed] Upstream inspection on 2026-09-26 identified [tree-sitter-scala upstream](https://github.com/tree-sitter/tree-sitter-scala/blob/master/Cargo.toml) as the parser source; the inspected manifest version is 0.26.2. This records source evidence, not a selected or build-verified dependency.
- [confirmed] `tree-sitter-wasm/Cargo.toml` declares tree-sitter 0.25.8, the original 2026-09-26 checkout resolved 0.25.10, and `.gitignore` excludes Cargo.lock. The current worktree has no lockfile; the historical resolution is not current build evidence. Preserve the manifest compatibility line and record exact new resolutions during execution.
- [inferred] A language-local renderer/policy and Rust analyzer/type parser can normalize Scala through the existing IR without changing its wire schema. Confirm with the Stage 1 mapping matrix and actual bundled-WASM verification.

- [confirmed] `TypeToJsonGenerationService.generate` rejects ERROR diagnostics but returns only a JSON String on success; `TypeToJsonDialogPresenter.schedulePreview` and `CodePreviewPanel.setSuccess` receive no warning payload. `TypeToJsonNodeGenerator` maps AnyValue to null, so an analyzer warning alone is not visible loss reporting.
- [confirmed] `TypeDeclarationAnalyzerService.analyzeSource` loads query text for availability but invokes only analyze_source. The existing `parser_and_tree_handles_have_independent_lifetimes` Rust test executes queries only for IDs 0–3; successful existing tests do not exercise this new query.

## Desired Outcome (To-Be)

- Scala input and generated type preview have lightweight lexical highlighting with or without the optional IDE language plugin, following the active IDE theme/editor scheme and the applicable user color settings.
- Users can select Scala in both existing conversion tabs and convert JSON to Scala case classes using syntax shared by Scala 2.13 and Scala 3 where possible, then generate JSON from supported Scala declarations.
- Use persisted enum SCALA, numeric WASM ID 9, resource key `scala`, output extension `.scala` and the existing theme-aware icon.
- Language rules stay cohesive in dedicated modules while common services, presenters, generic IR and sample generation remain reusable.
- The generated syntax and supported handwritten declaration subset have a documented mapping and explicit diagnostics for unsupported or lossy cases.
- A successful service call is followed by a valid visible preview and correct copied/inserted output; build success alone is not completion.

## Scope

### In Scope

- Add the Scala editor-local fallback profile and bind it to the inherited shared resolver for `.scala`; preserve usable native highlighting and its user-defined colors.
- Implement one complete Scala vertical slice: Kotlin renderer/policy, language metadata/options, Rust grammar/analyzer/type parser, both query copies, regenerated WASM, UI resources and final result routing.
- Perform the language-specific investigations below before production edits and keep source/version citations in the handoff record.
- Render case classes using String, a documented integral type, Double, Boolean, List[T], Map[String, T] and Option[T]. Keep generated output free of a mandatory Scala JSON library.
- Escape identifiers and preserve or explicitly warn about renamed JSON keys. Keep backticks and literal escaping local to Scala.
- Handle root arrays/scalars with a legal scoped alias or wrapper and make the reverse path recognize that exact shape. Do not generate invalid top-level Scala 2 aliases.
- Represent unsupported unions/AnyValue conservatively with documented warnings and valid Scala types, preserving generic inference behavior for other languages.
- Extract case-class constructor fields and supported class vals/vars, local aliases, simple enums and named references. Do not turn methods or unrelated object members into JSON fields.
- Normalize Option and supported collection names, including qualified names and Scala 3 nullable unions. Preserve the distinction between absent and nullable fields where source syntax provides it.
- Support the emitted alias scope and nested references; diagnose opaque types, match types, refinements and unresolved implicit/type-level computation.
- Update existing enum-dependent assertions and descriptor expectations only as necessary for this added entry. Use existing dataset content and manual inspection rather than creating new test files/functions/fixtures.
- Keep shared registration edits small. Consolidate actual duplication inside `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/languages/scala` or its Rust peer without reorganizing unrelated languages.

### Out of Scope

- [hard] Adding full IDE language support, semantic highlighting, parser/PSI infrastructure, LSP/WASM coloring, custom color-setting pages/palettes or global file-type associations solely for highlighting.
- [hard] Scala compiler integration, implicit resolution, macro expansion and Circe/Play JSON/uPickle output profiles.
- [hard] Reimplementing Kotlin, Java, TypeScript or Go, renaming their saved values, changing the FFI/schema shape, or changing other siblings’ language semantics.
- [hard] Adding new automated test cases/fixtures or changing lint/format/build-task configuration without a separate user request.
- [deferred] Full language/compiler semantics beyond the supported declaration subset, additional serializer profiles, unrelated query/schema/diff features and plugin publication.

## Constraints

- Preserve existing language identities: JAVA=0/java, KOTLIN=1/kotlin, TYPESCRIPT=2/typescript and GO=3/go. Reserve IDs C=4/c, CPP=5/cpp, CSHARP=6/csharp, PYTHON=7/python, RUST=8/rust, SCALA=9/scala and JSDOC=10/jsdoc. Each feature child appends only its own entry consistently in Kotlin, Rust and bundled WASM; the refactor appends none. Never derive IDs from enum ordinals.
- Preserve persisted keys `jsonToTypeLastLanguage`, `typeToJsonLastLanguage`, `jsonToTypeDefaultNaming`, `jsonToTypeAnnotationStyle`, `jsonToTypeNullableByDefault`, `jsonToTypeUsesExperimentalGoUnionTypes`, and all existing `typeToJson*` option meanings. Keep old enum names and unknown-value fallback to KOTLIN.
- Keep `convert`/`convertDetailed`, `generate` overloads, `TypeAnalysisResult`, `JsonToTypeConversionResult` and callback contracts compatible. Preserve `TypeField.sourceName` versus generated `name` and optionality versus nullability.
- Keep the Rust/Kotlin analysis wire keys and variants in `ir.rs` and `TreeSitterQueryResult.kt` compatible. Unsupported syntax uses existing diagnostics/Unknown behavior. Native analysis may return partial results, while `TypeToJsonGenerationService.generate` must still reject ERROR diagnostics.
- Keep `ConvertPreviewExecutor` cancellation, request ordering, EDT/modality handling and disposal. Pass the existing cancellation callback through `TypeDeclarationAnalyzerService` into `withTransaction`, and release both source and result buffers in finally blocks.
- Keep IntelliJ 2024.3 / sinceBuild 243, the Java 17 build toolchain, Kotlin 2.4.0 from the current gradle/libs.versions.toml, IntelliJ Platform Gradle Plugin 2.11.0, Chicory 1.5.1 and the existing wasm32-wasip1 route. Treat the earlier Kotlin 2.3.10 snapshot as historical, not an instruction to downgrade the current checkout. Do not raise minimum IDE/runtime versions to accommodate a grammar without returning to the parent.
- Use per-language renderer and policy modules behind a small explicit registry. Keep generic JSON shape inference and the JSON document builder language-neutral. Rust analyzer/type_parser dispatch may remain a small exhaustive match, with algorithms in language-local modules.
- Add only necessary grammar dependencies with explicit compatible version constraints and record resolved versions. The manifest declares tree-sitter 0.25.8, the original 2026-09-26 checkout resolved 0.25.10, and tree-sitter-wasm/.gitignore excludes Cargo.lock. The current worktree has no Cargo.lock; obtain and record its actual resolution through the existing Cargo build instead of assuming that historical resolution. Preserve this lockfile policy and pin new grammar choices in Cargo.toml rather than treating the local lock as a committed handoff. Moving upstream manifests are investigation evidence, not approved version pins or proof of WASI/ABI compatibility. Record external scanners and licence/notice obligations for the chosen release.
- Do not create test files, new test functions, new fixture datasets, lint rules, formatter setup or new build tasks under this request. Maintain only existing hardcoded language descriptors and exhaustive expectation branches required by the enum expansion, preserving current assertions for the original four languages. Use existing datasets and bounded manual checks for new behavior; report remaining coverage gaps.
- Keep existing actions and registration IDs. Convert through the existing dialog and result consumers; do not create seven new actions or require each target language IDE plugin. For the seven added languages, an absent optional IDE language plugin must select the bundled lightweight highlighter. Unexpected highlighting failure may recover to plain text so conversion remains usable, but plain-text recovery does not satisfy highlighting acceptance. Rethrow ProcessCanceledException and CancellationException; cancellation is not a highlighting failure to suppress.
- Preserve old icon behavior and use an existing v3 asset fallback for a newly added language when v1/v2 has no asset. Keep both theme variants, all five LocalizationBundle files, placeholder keys and output extensions aligned.
- Keep the crate query and plugin query for each language synchronized. Rebuild and copy WASM through existing Gradle tasks after Rust changes; `-PskipWasmBuild=true` is not evidence that new language analysis is shipped.
- Treat reverse conversion as sample generation from types, not restoration of original JSON values. Preserve supported shape/key semantics and expose lossy representations honestly.
- Announce the exact verification command and working directory before executing it. Record failed, unavailable and unrun checks separately; never mark readiness from compilation alone when UI or WASM proof is missing.
- Own only Scala syntax and helpers. Keep parser extraction separate from type normalization and renderer text generation separate from naming policy; reuse generic helpers only when their semantics are truly identical.
- This child is atomic under the briefset keep-together rule: renderer, parser, metadata, resources and UI exposure must ship as one usable bidirectional feature, not as independently selectable half-implementations.
- Use `docs/briefs/type-languages/06-rust-handoff.md` and `docs/briefs/type-languages/01-core-handoff.md` as inherited contracts. If current implementation has moved the proposed paths, update the handoff and this brief’s paths before editing rather than creating duplicate implementations.

- The C feature child owns the reusable reverse-conversion warning delivery route, and later languages consume it. Preserve existing String-returning generate overloads and original four-language preview behavior. Carry JSON text and diagnostics as one immutable preview result through the same cancellation/sequence checks; show new-language warnings separately from copied/inserted JSON. Do not create seven presenter branches or encode warnings as JSON properties/comments in strict JSON output.
- Query availability, byte equality and WASM compilation do not prove query validity. For each new language, use the rebuilt bundled module to execute parser_create → tree_parse → tree_query against its nonempty generated declaration source and exact shipped query. Record capture names/text and syntax diagnostics with the source/query/module identity. Use existing runtime exports for a bounded manual inspection, without adding test cases or a permanent verification harness.

- Keep highlighting local to the conversion code input and type-output preview. Prefer a usable IDE-provided highlighter and its language-specific color keys; when absent, use this language's bundled lexer-based fallback. Preserve the original requested extension through resolution, including when FileType resolves to plain text.
- For the fallback, return existing DefaultLanguageHighlighterColors keys directly and use the active EditorColorsScheme, including user overrides in Settings | Editor | Color Scheme | Language Defaults. Do not hard-code RGB/TextAttributes, create a separate color settings page or per-theme palette, or guess another plugin's key names. Native language-specific overrides apply through the native provider; fallback colors deliberately use Language Defaults.
- Keep already-open input and preview editors synchronized when the IDE theme, selected color scheme, or attributes within the same scheme change. Prefer the platform's editor/component update path; add a listener only if required, own it with the panel/field lifetime, and refresh appearance on the EDT without replacing document text, caret, selection or undo history.
- Use fresh incremental lexers, immutable lexical profiles and reusable token/key tables. The bundled fallback reads Document/CharSequence text without conversion, WASM/tree-sitter, PSI resolution, a server or network access. Preserve existing native-provider and PSI-backed document behavior; do not wrap a native editor highlighter into a simpler lexer highlighter. Add no highlighting dependencies, grammar-generation tasks, whole-document regex passes per keystroke or per-token RangeHighlighter objects.
- Keep lexer offsets in CharSequence UTF-16 units, separate from WASM UTF-8 byte spans. A saved integer getState()/start() restart must reproduce the same token boundaries and keys as a full scan; honor the supplied end offset, make progress on malformed input and keep long scans cancellable. Include non-ASCII text, escaped delimiters and incomplete multiline tokens in bounded input-editor checks.
- Reuse the shared resolver/lexer route supplied by the C feature child; later children add only their language profile and registration. Keep fallback recovery separate from feature readiness, and keep JSON/JSON5 editors, ordinary IDE file associations and existing native highlighting unchanged.

## Related Files / Entry Points

- `src/main/kotlin/com/livteam/jsoninja/ui/component/editor/EditorTextFieldFactory.kt` — retain the requested extension and attach the selected highlighter using the effective editor scheme.
- `src/main/kotlin/com/livteam/jsoninja/ui/component/convertType/CodeInputPanel.kt` — apply the shared route to editable type input and language changes.
- `src/main/kotlin/com/livteam/jsoninja/ui/component/convertType/CodePreviewPanel.kt` — apply the shared route to generated type previews while preserving JSON/JSON5 output handling.
- `src/main/kotlin/com/livteam/jsoninja/ui/component/convertType/highlighting/TypeCodeHighlighterResolver.kt` (proposed) — shared native-provider priority, fallback dispatch and effective-scheme contract; introduced by the core/C route, not duplicated per language.
- `src/main/kotlin/com/livteam/jsoninja/ui/component/convertType/highlighting/ScalaCodeHighlighting.kt` (proposed) — cohesive Scala keyword/delimiter rules and token classification, separate from conversion rendering/analysis.
- `docs/briefs/2026-09-26-refactor-type-languages-01-core.md` — read the extension contract and preservation boundary before this feature.
- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/languages/scala/ScalaTypeRenderer.kt` (proposed) — implement Scala-specific imports, declarations, literals and warning comments.
- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/languages/scala` (proposed) — colocate only this language’s naming/policy helpers.
- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/languages/TypeLanguageRegistry.kt` (proposed) — add the single composition entry after the core extraction.
- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/JsonToTypeConversionService.kt` — trace inferred source through the renderer facade and result model.
- `src/main/kotlin/com/livteam/jsoninja/model/SupportedLanguage.kt` — append metadata, explicit ID and supported option choices.
- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/JsonToTypeConversionOptions.kt` — append a language-specific annotation style only when required by the documented mapping.
- `tree-sitter-wasm/src/analyzer/scala.rs` (proposed) — extract supported declarations and source spans.
- `tree-sitter-wasm/src/type_parser/scala.rs` (proposed) — normalize language-specific type expressions.
- `tree-sitter-wasm/src/language.rs` — append explicit ID/name/grammar dispatch.
- `tree-sitter-wasm/src/analyzer/mod.rs` — wire the language analyzer without moving its algorithm here.
- `tree-sitter-wasm/src/type_parser/mod.rs` — wire type parsing and reuse shared IR constructors.
- `tree-sitter-wasm/src/ir.rs` — map into the existing serialized representation.
- `tree-sitter-wasm/Cargo.toml` — add only the selected grammar crates and feature membership.
- `tree-sitter-wasm/Cargo.lock` (proposed) — generated locally by the existing Cargo build, currently absent from this worktree and ignored by Git; inspect its resolution after generation without committing it or changing the lockfile policy.
- `tree-sitter-wasm/.gitignore` — preserve the existing Cargo.lock policy when choosing version constraints.
- `tree-sitter-wasm/queries/scala/type-declarations.scm` (proposed) — own query capture patterns for the chosen grammar.
- `src/main/resources/tree-sitter/queries/scala/type-declarations.scm` (proposed) — keep the shipped query equal to its crate counterpart.
- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/TypeDeclarationAnalyzerService.kt` — verify query availability, cancellation, numeric dispatch and buffer release.
- `src/main/kotlin/com/livteam/jsoninja/services/treesitter/TreeSitterQueryResult.kt` — check normalization into existing Kotlin types and diagnostics.
- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/TypeToJsonGenerationService.kt` — observe ERROR gating and final sample generation.
- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/TypeToJsonDocumentBuilder.kt` — confirm root selection and alias/helper normalization reach the expected shape.
- `src/main/kotlin/com/livteam/jsoninja/ui/dialog/convertType` — follow option adapters, language synchronization, placeholders and preview completion.
- `src/main/kotlin/com/livteam/jsoninja/ui/component/convertType/LanguageSelectorComponent.kt` — verify the appended entry becomes selectable only with both directions implemented.
- `src/main/kotlin/com/livteam/jsoninja/icons/JsoninjaIcons.kt` — use the core fallback for missing base assets.
- `src/main/resources/icons/languages/v3/scala.svg` — reuse the existing light/dark icon pair.
- `src/main/resources/messages` — add matching language/placeholder/option strings in all five bundles.
- `src/main/kotlin/com/livteam/jsoninja/utils/ConvertResultUtils.kt` — verify the extension and text reach the final tab/editor consumer.
- `src/test/kotlin/com/livteam/jsoninja/services/typeConversion` — maintain existing enum-dependent helpers and run the three integration suites.
- `tree-sitter-wasm/src/tests.rs` — update the existing full descriptor expectation without deleting old IDs or adding cases.
- `build.gradle.kts` — use the existing WASM build/copy and plugin verification routes.
- [tree-sitter-scala upstream](https://github.com/tree-sitter/tree-sitter-scala/blob/master/Cargo.toml) — verify the released grammar, node-types, external scanners, license and runtime compatibility.
- [Scala case classes](https://docs.scala-lang.org/tour/case-classes.html) — check language-specific syntax and semantics before implementing the mapping.

- `src/main/kotlin/com/livteam/jsoninja/services/treesitter/TreeSitterWasmRuntime.kt` — use RuntimeHandle.instance exports inside withTransaction for bounded query verification.
- `src/main/kotlin/com/livteam/jsoninja/services/treesitter/WasmMemoryBridge.kt` — allocate/decode/release source, query and result buffers during that inspection.
- `tree-sitter-wasm/src/lib.rs` — use the existing parser_create, tree_parse, tree_query, tree_destroy and parser_destroy ABI signatures.

## Execution Plan

### Stage 1 — Investigate syntax and fix the mapping contract

- Starts when: `docs/briefs/type-languages/06-rust-handoff.md` records a ready integrated predecessor and `docs/briefs/type-languages/01-core-handoff.md` defines the renderer/policy contracts. `docs/briefs/type-languages/02-c-handoff.md` identifies the inherited shared diagnostics and highlighting routes, including native/fallback dispatch, standard color keys and scheme-refresh ownership. This child holds the next serialized edit slot from `docs/briefs/2026-09-26-briefset-type-languages.md`.
- Work: Read `docs/briefs/2026-09-26-feat-type-languages-06-rust.md` and verify its handoff against current source before making Scala changes.
  - Inspect case-class constructor parameter lists, class vals/vars, type aliases, object scopes, enums and Scala 3 indentation nodes. Verify supported syntax against the pinned grammar rather than equating grammar coverage with semantic support.
  - The grammar repository advertises Scala 2/3 coverage and its inspected master manifest uses a 0.26 dev dependency. Prove a selected release works within the existing tree-sitter 0.25 runtime line (0.25.10 in the original checkout; re-resolve in the execution checkout) before shipping.
  - Record Option, List/Seq/Vector/Array, Map[String, T], nullable unions and generic reference mappings. Distinguish constructor defaults from optional JSON keys.
  - Define a valid enclosing scope for root aliases under the generated baseline, since top-level alias syntax differs between Scala versions.
  - Record the shipped query’s capture contract against a nonempty generated declaration: required capture names, expected named declarations/fields or documentation blocks, and the corresponding source text. An empty source or merely existing .scm file cannot satisfy the check.
  - Record one mapping row per primitive, object/reference, array/map, nullable/optional field, root alias, union/AnyValue, enum and unsupported construct. Include generated form, accepted input form, resulting IR, source-name policy and warning/error behavior.
  - Verify the selected grammar’s published release, node-types, external scanner build requirements, tree-sitter ABI, WASI target and license. Do not claim build compatibility before Stage 3 executes the existing build route.
  - Define the Highlighting mapping for `.scala` in `docs/briefs/type-languages/07-scala-handoff.md`: native-provider lookup, fallback profile, token categories/color keys, multiline restart state and manual evidence inputs. Inspect the selected language's lexical specification and the actual generated source; use the existing declaration subset and avoid a full grammar implementation.
  - Recognize the recorded Scala baseline keywords, numeric literals, characters, ordinary/triple-quoted strings, // comments and nested /* */ comments. Preserve backtick identifiers and annotation markers, and recognize documentation comments. Interpolated strings may remain one STRING span without evaluating embedded expressions; keep keyword sets and identifier/operator rules aligned with the selected Scala baseline rather than borrowing Java rules unchanged.
- No-op when: Scala already satisfies every acceptance criterion with current bundled-WASM and UI evidence, including its metadata and icons.
- No-op handoff: Publish the same proof and Readiness in `docs/briefs/type-languages/07-scala-handoff.md` without code edits, then let the parent continue the next wave or global acceptance. If proof fails, continue this child’s bounded implementation and re-verification.
- Deliverable: Investigation section in `docs/briefs/type-languages/07-scala-handoff.md` (proposed), using Markdown with Revision, Language (Scala), Sources (URLs and pinned versions), Mapping, Highlighting, Owned files, Checks (command/cwd/input/result), Limitations and Readiness fields; the Mapping and Sources fields are complete before implementation starts.
- Verify: `bounded mapping and grammar-source inspection`; Inputs: tree-sitter-scala pinned release sources, existing IR and the Scala matrix in docs/briefs/type-languages/07-scala-handoff.md; Expected: all required shape categories have an explicit rendering/analysis rule or diagnostic and grammar/toolchain risks have a bounded proof route
- Ends when:
  - [ ] Highlighting has an explicit token-to-key and lexical-boundary contract tied to this language baseline, with native/fallback ownership and bounded manual inputs.
  - [ ] The language baseline and generated declaration style are stated.
  - [ ] Mappings cover both directions and distinguish confirmed source facts from unverified build assumptions.
  - [ ] Every required dependency has a specific purpose and no unrelated scope was added.
- Handoff: Stage 2 consumes the Mapping and Sources sections of `docs/briefs/type-languages/07-scala-handoff.md`.
- Replan when: The selected grammar cannot support the required slice within the current runtime, or the mapping needs a shared wire-schema break. Stop dependent edits and return to the parent with the smallest correction and re-verification proposal; do not quietly downgrade to one-way support.
- Worker decision: Choose grammar release, private helper names and exact valid fallback representation inside the recorded baseline. Unsupported syntax must remain visible and existing-language contracts must not change.

### Stage 2 — Implement cohesive JSON-to-type generation

- Starts when: The Mapping and Sources sections in `docs/briefs/type-languages/07-scala-handoff.md` are complete and the core renderer/policy seam exists.
- Work: Implement `ScalaTypeRenderer` and its focused policy in `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/languages/scala` using the existing common IR.
  - Render case classes using String, a documented integral type, Double, Boolean, List[T], Map[String, T] and Option[T]. Keep generated output free of a mandatory Scala JSON library.
  - Escape identifiers and preserve or explicitly warn about renamed JSON keys. Keep backticks and literal escaping local to Scala.
  - Handle root arrays/scalars with a legal scoped alias or wrapper and make the reverse path recognize that exact shape. Do not generate invalid top-level Scala 2 aliases.
  - Represent unsupported unions/AnyValue conservatively with documented warnings and valid Scala types, preserving generic inference behavior for other languages.
  - Keep import ordering, nested declaration names, collisions, root aliases, maximum-depth/empty-array fallbacks and comment escaping deterministic.
  - Implement against the facade/registry contract but keep the live SupportedLanguage entries and registry unchanged until Stage 4. Compile the language-local collaborators without exposing them. Remove superseded local duplication; do not grow a shared rendering switch.
- Deliverable: Scala rendering/policy modules and render-side observations in `docs/briefs/type-languages/07-scala-handoff.md`.
- Verify: `./gradlew compileKotlin`; Inputs: repository root with ScalaTypeRenderer and its implemented renderer/policy contracts before live registration; Expected: exit 0 and source inspection shows syntax decisions are owned by the language module
- Ends when:
  - [ ] The recorded generated forms are implemented without language syntax in generic inference.
  - [ ] Both valid output and explicit loss warnings use the target language’s syntax.
  - [ ] Old service call signatures and original-language behavior remain unchanged.
- Handoff: Stage 3 receives the actual generated form and its type/key mapping so analysis implements the same contract.
- Replan when: Rendering requires changing generic IR semantics or a user-visible compatibility rule. Return to Stage 1 and the parent before propagating the change into analysis.

### Stage 3 — Implement analysis and build the actual WASM module

- Starts when: Stage 2 generated forms and mapping are available in `docs/briefs/type-languages/07-scala-handoff.md`.
- Work: Add the pinned grammar dependencies, SCALA numeric ID 9, analyzer and type parser as one language boundary.
  - Extract case-class constructor fields and supported class vals/vars, local aliases, simple enums and named references. Do not turn methods or unrelated object members into JSON fields.
  - Normalize Option and supported collection names, including qualified names and Scala 3 nullable unions. Preserve the distinction between absent and nullable fields where source syntax provides it.
  - Support the emitted alias scope and nested references; diagnose opaque types, match types, refinements and unresolved implicit/type-level computation.
  - Create `tree-sitter-wasm/queries/scala/type-declarations.scm` and `src/main/resources/tree-sitter/queries/scala/type-declarations.scm` with matching captures against the selected grammar. Queries are required even though the main Kotlin service calls analyze_source directly.
  - Keep all language algorithms out of the FFI wrapper and memory layer. Preserve partial-result diagnostics and existing Unknown serialization.
  - Run existing host Rust tests and build/copy WASM. Update only the existing full-descriptor expectation for newly added IDs; leave original fixture baselines intact.
  - After that build, perform bounded in-process inspection of the actual bundled module using RuntimeHandle.instance.export: within one withTransaction, allocate the generated source/query with WasmMemoryBridge, call parser_create(languageId), tree_parse(parserHandle, sourcePtr, sourceLen), then tree_query(treeHandle, sourcePtr, sourceLen, queryPtr, queryLen). Decode the result JSON and compare captures to the Stage 1 contract. Release result/query/source buffers and destroy tree/parser handles in finally blocks. Record unavailable runtime evaluation as unverified, not a pass.
- Deliverable: Rust implementation, synchronized queries, rebuilt plugin WASM and dependency/build observations in `docs/briefs/type-languages/07-scala-handoff.md`.
- Verify: `./gradlew buildTreeSitterWasm copyWasmToResources`; Inputs: repository root and tree-sitter-wasm target wasm32-wasip1 with the selected grammar dependencies; Expected: exit 0 and the generated module is copied to src/main/resources/wasm/tree-sitter/tree-sitter.wasm
- Ends when:
  - [ ] The selected grammar compiles for the actual WASI target.
  - [ ] Crate and plugin queries agree, and original IDs remain unchanged.
  - [ ] Actual bundled tree_query execution returns the declared nonempty captures for the generated source with no query error or unexpected syntax diagnostics.
  - [ ] The rebuilt module includes the appended language and the recorded generated declaration subset.
- Handoff: Stage 4 receives executable bundled analysis and source/grammar metadata for UI exposure.
- Replan when: A grammar scanner, runtime ABI or generated-helper interpretation fails. Keep this entry out of completed UI exposure, correct the language-local mapping/build, and return to the parent if a shared runtime change is required.

### Stage 4 — Complete metadata and the user-visible conversion route

- Starts when: Renderer and bundled analysis support the same language mapping and Stage 3 build evidence is available.
- Work: Complete SCALA metadata with extension `scala`, resource key `scala`, explicit ID 9, naming choices and supported annotation defaults.
  - Reuse the shared immutable generation-result, preview transport and warning display described in `docs/briefs/type-languages/02-c-handoff.md`. Enable its metadata capability for this language and preserve existing generate overloads; add no duplicate presentation logic. Verify the warning and displayed JSON belong to the same request.
  - Atomically add this Kotlin enum entry, registry binding and exhaustive inference/metadata/expectation branches now that renderer, analyzer and resources exist. Do not register any future sibling entry.
  - Add language and placeholder strings to LocalizationBundle.properties, LocalizationBundle_en.properties, LocalizationBundle_ko.properties, LocalizationBundle_ja.properties and LocalizationBundle_zh_CN.properties. Use the existing scala icon pair and old-pack fallback.
  - Keep options valid when switching languages and loading old settings. Preserve caller selections where supported and use existing per-language defaults only for unsupported choices.
  - Bind `ScalaCodeHighlighting` to the shared route for `.scala` only after this language's two conversion directions are complete. Apply it to CodeInputPanel and the type-output CodePreviewPanel; keep JSON/JSON5 output on its original highlighter and do not change copied/inserted content.
  - Start the existing sandbox with `./gradlew runIde` from the repository root (IC 2024.3), or use that already-running sandbox after rebuilding. Trace preview, copy, insertToNewTab and insertToEditor end to end. Verify the extension reaches the final consumer and the bundled fallback visibly highlights both conversion code editors when optional language support is absent. Check native-provider preference separately where that provider is available; report unavailable native checks without claiming success.
  - Maintain existing exhaustive Kotlin expectation branches and Rust descriptor assertions for the new entry only. Do not add test files/functions or weaken old assertions.
  - In the editable CodeInputPanel, use the recorded supported declaration text and bounded manual edits to cover the required token categories and Stage 1 special forms, including non-ASCII text and unfinished multiline delimiters. Verify restart recovery and language switching there. In the read-only CodePreviewPanel, inspect actual generated output and only the token categories it contains; do not make the viewer editable or alter generated code to fabricate color evidence. Reopen/close the dialog to check recreation and disposal.
  - In a sandbox color scheme, check light/dark UI themes, independent editor-scheme switching and same-scheme Apply while the input and preview fields stay open. Change attributes for token categories actually present in each surface: Language Defaults for fallback tokens, the provider's own language keys for native tokens, and documentation tag/value keys for JSDoc. Compare resolved attributes and background with the active scheme; preserve content, caret, selection, scroll and input undo. Mark categories absent from generated output as not applicable to that preview sample and cover them in editable input instead. Record the exact provider/build/scheme and an actual live-update observation; closing/reopening the dialog alone does not prove live updates.
- Deliverable: Usable two-tab Scala conversion route and UI/settings observations in `docs/briefs/type-languages/07-scala-handoff.md`. Include the Highlighting provider/profile, token keys, live color changes, restart/disposal observations and unverified checks.
- Verify: `bounded dialog and result-consumer inspection`; Inputs: Scala in both conversion tabs, all existing icon packs/themes, all five message bundles and final .scala output; Expected: the language can be selected and persisted, resources resolve, and preview/copy/insert use the expected text and extension; also inspect the native/fallback token keys and visible colors in both code editors before/after live scheme changes, expecting the current user attributes with unchanged content
- Ends when:
  - [ ] The requested language reaches a usable native highlighter or its bundled fallback in both input and type preview; a plain-text recovery is not recorded as success.
  - [ ] Light/dark theme, independent scheme switching and same-scheme user attribute changes update open editors, with lexical restart and disposal observations recorded in Highlighting.
  - [ ] Both tabs expose the same completed language implementation.
  - [ ] Icons, labels, placeholders and option defaults resolve through existing UI wiring.
  - [ ] Final output consumers receive the correct extension and current preview only.
- Handoff: Stage 5 receives the complete feature and all exact files needed for regression and integrated checks.
- Replan when: The UI would expose unsupported controls, a one-way entry or a mandatory external language plugin. Correct within the existing metadata route before completing this wave.

### Stage 5 — Verify the integrated slice and publish evidence

- Starts when: All production wiring for this language is complete and earlier waves remain integrated.
- Work: From the repository root run `./gradlew compileKotlin`, `./gradlew test --tests 'com.livteam.jsoninja.services.typeConversion.TypeConversionWasmIntegration*Test'` and `cargo test --manifest-path tree-sitter-wasm/Cargo.toml`; record command, input population, executed count and result.
  - Use the existing TS_CONFIG_JSON_TEXT and COMPLEX_WORKSPACE_REPORT_JSON_TEXT datasets through the running dialog, then analyze the generated source using the bundled module. Check supported shapes/keys, not equality of synthetic sample values to original JSON.
  - Exercise supported handwritten declaration forms from the cited language sources and explicitly inspect unsupported/invalid inputs through the same UI. Do not create new automated test cases or fixture assets.
  - Check optional/null generation settings, root selection, outputCount bounds, commented optional output, rapid language changes and dialog disposal through the existing service/UI route.
  - Compare the built module and packaged resource, inspect the complete new language module population for coupling/duplication, and complete the evidence record. Report missing compiler/UI access or coverage honestly rather than marking it passed.
- Deliverable: Final `docs/briefs/type-languages/07-scala-handoff.md` as Markdown with Revision, Language (Scala), Sources (URLs and pinned versions), Mapping, Highlighting, Owned files, Checks (command/cwd/input/result), Limitations and Readiness fields. Set Readiness to ready only when every acceptance criterion and side-effect checkpoint is evidenced.
- Verify: `./gradlew test --tests 'com.livteam.jsoninja.services.typeConversion.TypeConversionWasmIntegration*Test'`; Inputs: three existing integration suites, their existing datasets and the rebuilt WASM with SCALA; Expected: exit 0 with nonzero executed checks and preserved original-language assertions, plus recorded manual evidence for behavior outside the suites
- Ends when:
  - [ ] Compilation and existing relevant regression suites have actual recorded results.
  - [ ] Bundled-WASM and visible end-to-end behavior agree with the mapping matrix.
  - [ ] The evidence names uncovered behavior and contains a valid readiness decision.
- Handoff: Parent and `docs/briefs/2026-09-26-feat-type-languages-08-jsdoc.md` receive `docs/briefs/type-languages/07-scala-handoff.md` with the integrated source and readiness evidence.
- Replan when: Any proof fails or current evidence contradicts the matrix. Stop successors, return to the parent, perform bounded correction plus re-verification, and recalculate topology/handoffs before resuming.

## Side Effect Checkpoints

- [ ] Native provider selection and user-defined colors remain authoritative; fallback uses Language Defaults, old language/JSON editors and external file associations are unaffected, and language/scheme changes do not leak listeners or rewrite document content.
- [ ] Scala selection reaches renderer, WASM numeric ID, result preview, clipboard and final inserted tab/editor content with the correct extension.
- [ ] Both conversion tabs synchronize language once, preserve caller options and reload persisted selections without resetting old settings.
- [ ] Optional, nullable, inherited/referenced and recursive types use the existing JSON generation policies and depth guards without leaking language-specific helpers as JSON fields.
- [ ] Keyword/collision handling, literal escaping, original JSON keys and warning comment syntax agree between renderer and analyzer.
- [ ] No stale preview or warning is applied after input/language changes or disposal, and cancellation does not become a displayed conversion failure.
- [ ] A non-error loss diagnostic reaches the warning display while copied/inserted strict JSON contains only the generated JSON text.
- [ ] Default/en/ko/ja/zh_CN messages and v1/v2/v3 icon selection resolve for both themes.
- [ ] Query copies match, WASM resources match the rebuilt module, and the bundled runtime can analyze the new language without an installed language plugin.
- [ ] Existing regression assertions for Kotlin, Java, TypeScript and Go remain intact; no unsupported behavior is hidden by disabling a check or excluding enum entries.

## Acceptance Criteria

- [ ] Scala is visibly highlighted in editable type input and generated type preview on IC 2024.3 without its optional language plugin; `.scala` reaches the correct native/fallback profile, and the Stage 1 lexical forms recover after incremental edits.
- [ ] Both open editors follow the active scheme and user attributes for their displayed token categories after Apply and theme/scheme changes, preserving text, caret, selection, scroll and input undo. The editable input covers every required lexical category; the read-only generated preview supplies evidence for categories actually emitted. Record native language-specific and fallback Language Defaults observations separately.
- [ ] The Highlighting handoff records actual provider/key/color and disposal evidence. The bundled fallback adds no parser/PSI/WASM/LSP dependency, full-document regex pass per keystroke, per-token markup objects or custom palette; missing UI/provider checks remain unverified.
- [ ] Scala works in both JSON-to-type and type-to-JSON flows with SCALA / 9 / `scala` / `.scala` consistently reaching every consumer.
- [ ] Scala output follows its declared 2.13/3-compatible baseline, including legal root alias scoping.
- [ ] Case-class fields and Option/collection semantics reach the JSON builder without executing Scala code.
- [ ] Supported Scala 2 and Scala 3 input forms are enumerated in the evidence record and checked against the pinned grammar.
- [ ] The actual shipped query compiles and captures the declared nonempty source elements in bundled WASM execution; file equality or old-language tests alone do not satisfy this criterion.
- [ ] The existing focused Kotlin tests and host Rust tests pass for the current integrated population, and the actual WASM build/copy succeeds.
- [ ] Generated source and supported input forms satisfy the recorded mapping, including explicit unsupported/loss diagnostics; synthetic JSON is not presented as a lossless value round-trip.
- [ ] No existing language loses its selection, naming behavior, source-name handling, saved settings, preview cancellation or result extension.
- [ ] The coupling audit finds no language syntax in common inference/UI orchestration and no target-language code copied into siblings.
- [ ] `docs/briefs/type-languages/07-scala-handoff.md` contains pinned-source, mapping, build, UI and coverage evidence sufficient for the successor to continue without re-interviewing the requester.

## Open Questions

- None — both directions and this language are in the requested scope; syntax representation and grammar selection are bounded worker decisions, while compatibility breaks return to the parent.
