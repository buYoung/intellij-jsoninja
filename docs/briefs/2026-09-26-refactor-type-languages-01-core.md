# [refactor] Isolate type conversion language policies

실행 상태: 구현과 자동 검증을 완료했다. [전체 검증 기록](type-languages/09-verification.md)에 메인 에이전트의 검토·골든·단위·통합 검증 및 남은 수동 확인을 기록했다. 최신 사용자의 골든·단위 테스트 추가 지시가 이 문서의 기존 테스트 추가 제한에 우선한다.

## Work Type

refactor

## Current State (As-Is)

- [confirmed] Highlighting review on 2026-09-27 at revision `519470b` found `CodeInputPanel.rebuildEditor` and `CodePreviewPanel.ensureViewer` using `src/main/kotlin/com/livteam/jsoninja/ui/component/editor/EditorTextFieldFactory.kt:createCodeField`; `resolveCodeFileType` falls back to PlainTextFileType and `applyEditorAppearance` selects the IDE highlighter and global scheme. There is no bundled per-language fallback in this route.
- [confirmed] The original snapshot was 2026-09-26 at revision `1077537` on `codex/dependabot-194`. Main-agent document validation on 2026-09-27 inspected the current detached revision `519470b`, including gradle/libs.versions.toml (Kotlin 2.4.0), without running plugin builds or tests. Recheck the execution checkout rather than restoring the historical branch/toolchain. Evidence: `git rev-parse --short HEAD`, `git branch --show-current` and the version catalog.
- [confirmed] `JsonToTypeConversionService.convertDetailed` performs parse → inference → render, while `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/JsonToTypeRenderer.kt` contains four language renderers, import rules and an unconditional `// Warning:` header.
- [confirmed] `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/JsonToTypeNamingSupport.kt` owns a language-keyed reserved-word map and Java accessor exception; `JsonToTypeInferenceContext.inferObjectType` selects declaration kind using SupportedLanguage.
- [confirmed] `src/main/kotlin/com/livteam/jsoninja/model/SupportedLanguage.kt` owns the four enum names, explicit WASM IDs, extensions, naming/annotation options and persisted-value fallback. `LanguageSelectorComponent` uses all entries.
- [confirmed] `src/main/kotlin/com/livteam/jsoninja/ui/dialog/convertType/TypeToJsonDialogView.kt:updateInputLanguage` has a four-language placeholder switch; `src/main/kotlin/com/livteam/jsoninja/icons/JsoninjaIcons.kt:getLanguageIcon` chooses base or v3 paths from the icon-pack setting.
- [confirmed] `tree-sitter-wasm/src/analyzer/mod.rs` and `type_parser/mod.rs` already dispatch into per-language modules. `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/TypeDeclarationAnalyzerService.kt` loads a query asset, enters a cancellable WASM transaction and calls analyzeSource with the explicit numeric ID.
- [confirmed] `src/main/kotlin/com/livteam/jsoninja/ui/dialog/convertType/ConvertTypeDialogPresenter.kt:syncLanguage/consumeCurrentPreview` connects both tabs to the final consumer, and `src/main/kotlin/com/livteam/jsoninja/utils/ConvertResultUtils.kt` forwards the file extension or applies an undoable editor write.
- [confirmed] Existing verification anchors are `src/test/kotlin/com/livteam/jsoninja/services/typeConversion/TypeConversionWasmIntegrationTest.kt`, `TypeConversionWasmIntegrationV2Test.kt`, `TypeConversionWasmIntegrationV3Test.kt` and `tree-sitter-wasm/src/tests.rs`; the Rust descriptor test explicitly expects four languages.
- [inferred] Extending the current switches for seven more entries would spread language rules across inference, rendering, naming and UI. Confirm the complete branch inventory during Stage 1 before moving responsibilities.

## Behavior Contract

- Preserve the existing four-language highlighter selection and JSON/JSON5 editor behavior while exposing the extension seam. New fallback colors and scheme-refresh behavior belong to the C feature child; this refactor must not make a future language selectable.
- Keep the four existing language selections, default options, explicit IDs, labels, extensions, generated syntax/import order, naming results, warnings and JSON samples unchanged.
- Keep the service method signatures, IR model shapes, persisted setting names, query resource names, sourceName mapping and optional/null semantics unchanged.
- Keep request cancellation, modality, stale-result rejection, resource disposal, WASM transaction ownership and undo behavior unchanged.
- Use the three existing TypeConversionWasmIntegration test classes and their existing datasets as the regression contract. Execute `./gradlew test --tests 'com.livteam.jsoninja.services.typeConversion.TypeConversionWasmIntegration*Test'` before and after extraction; do not edit expected output to accommodate a refactor.
- Record any behavior not covered by existing assertions and compare the same existing input/output through the running conversion dialog. An unchanged test result alone does not prove byte-for-byte output preservation.

## Desired Outcome (To-Be)

- Successors receive one editor-local native/fallback resolver contract that preserves the original requested extension and active IDE color scheme, without duplicating logic in the two panels.
- Existing conversion behavior is preserved while syntax rendering, language naming policy and generic shape inference have separate responsibilities.
- A new language can supply a cohesive implementation plus explicit metadata/registry entries without adding its syntax to generic renderer, inference or presenter methods.
- Existing Rust per-language boundaries remain intact; reuse their pattern instead of replacing them with a generic plugin framework.
- Successors receive a concrete extension contract, preserved behavior evidence and the shared-file ownership order.

## Scope

### In Scope

- Extract only the narrow highlighting selection seam needed by the successor languages; keep its current provider behavior unchanged and specify fallback/theme-refresh responsibilities for the C feature child.
- Extract existing Kotlin, Java, TypeScript and Go renderers and per-language naming/literal policy into colocated language modules while preserving public facades.
- Introduce only the small renderer/policy contracts and explicit composition registry required by the seven successor languages. Keep request-scoped inference state outside the registry.
- Move object declaration-kind selection behind a small policy and localize import, annotation and warning-comment decisions without moving generic JSON traversal.
- Replace UI placeholder switching with metadata-driven lookup and provide a resource fallback seam that preserves old icon choices.
- Record the Kotlin/Rust ID allocation, query-copy ownership, existing test limitations and target/runtime requirements as successor inputs.

### Out of Scope

- [hard] Adding any new selectable language, changing existing generated output or introducing an IR/ABI migration in this refactor.
- [hard] Rewriting tree-sitter memory, parser handle management, coroutine scheduling or type-to-JSON sampling algorithms.
- [deferred] Repository-wide package cleanup, generic extension discovery, dependency injection frameworks and automatic grammar/query generation.

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
- Keep existing actions and registration IDs. Convert through the existing dialog and result consumers; do not create seven new actions or require each target language IDE plugin. Preserve existing highlighting behavior in this refactor. The C feature child supplies the shared lightweight fallback and scheme-refresh route; each later feature must provide its own profile without requiring its IDE language plugin.
- Preserve old icon behavior and use an existing v3 asset fallback for a newly added language when v1/v2 has no asset. Keep both theme variants, all five LocalizationBundle files, placeholder keys and output extensions aligned.
- Keep the crate query and plugin query for each language synchronized. Rebuild and copy WASM through existing Gradle tasks after Rust changes; `-PskipWasmBuild=true` is not evidence that new language analysis is shipped.
- Treat reverse conversion as sample generation from types, not restoration of original JSON values. Preserve supported shape/key semantics and expose lossy representations honestly.
- Announce the exact verification command and working directory before executing it. Record failed, unavailable and unrun checks separately; never mark readiness from compilation alone when UI or WASM proof is missing.

- The highlighting contract uses native providers first and existing DefaultLanguageHighlighterColors keys plus the active EditorColorsScheme for fallbacks. Do not prescribe fixed colors, a custom color settings page, a full parser/PSI model, WASM-based coloring or mandatory language-plugin dependencies.

## Related Files / Entry Points

- `src/main/kotlin/com/livteam/jsoninja/ui/component/editor/EditorTextFieldFactory.kt` — trace createCodeField, requested-extension preservation, resolveCodeFileType and applyEditorAppearance before extracting provider selection.
- `src/main/kotlin/com/livteam/jsoninja/ui/component/convertType/CodeInputPanel.kt` — preserve editable input, language-switch and disposal ownership.
- `src/main/kotlin/com/livteam/jsoninja/ui/component/convertType/CodePreviewPanel.kt` — preserve the shared type/JSON preview, file-extension selection and viewer lifetime.
- `src/main/kotlin/com/livteam/jsoninja/ui/component/convertType/highlighting/TypeCodeHighlighterResolver.kt` (proposed) — minimal editor-local provider seam; preserve existing behavior here and reserve bundled fallback implementation for the C feature child.
- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/JsonToTypeRenderer.kt` — start at render, renderDeclaration, collectImports and language-specific render methods.
- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/JsonToTypeNamingSupport.kt` — separate language policy from shared tokenization and collision handling.
- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/JsonToTypeLiteralSupport.kt` — trace quote and Go tag escaping before relocating only language-specific rules.
- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/JsonToTypeInferenceContext.kt` — preserve infer/inferObjectType traversal and inject the narrow policy.
- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/JsonToTypeConversionService.kt` — preserve the public conversion facade and composition.
- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/languages/TypeLanguageRegistry.kt` (proposed) — explicit composition of immutable language renderer/policy implementations.
- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/languages/TypeLanguageRenderer.kt` (proposed) — focused generated-source contract.
- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/languages/TypeLanguagePolicy.kt` (proposed) — focused naming/declaration policy without Swing or WASM ownership.
- `src/main/kotlin/com/livteam/jsoninja/model/SupportedLanguage.kt` — retain persistence identities and language metadata.
- `src/main/kotlin/com/livteam/jsoninja/ui/dialog/convertType/TypeToJsonDialogView.kt` — remove placeholder policy from the view without changing text.
- `src/main/kotlin/com/livteam/jsoninja/icons/JsoninjaIcons.kt` — preserve old lookup and isolate fallback for absent language assets.
- `src/main/kotlin/com/livteam/jsoninja/ui/dialog/convertType/ConvertPreviewExecutor.kt` — confirm cancellation and UI contracts are not moved into renderers.
- `src/test/kotlin/com/livteam/jsoninja/services/typeConversion` — use all three existing integration suites as preservation checks.
- `tree-sitter-wasm/src/language.rs` — preserve explicit IDs and document append-only allocation.
- `tree-sitter-wasm/src/analyzer/mod.rs` — reuse the existing per-language ownership pattern.
- `build.gradle.kts` — use compileKotlin, test, buildTreeSitterWasm and copyWasmToResources as existing tasks.
- `gradle.properties` — preserve IDE compatibility settings.
- `docs/coroutine-threading-standard.md` — apply the existing lifecycle/threading contract.

## Execution Plan

### Stage 1 — Capture behavior and lock the extension contract

- Starts when: The current checkout is available and this brief is selected by `docs/briefs/2026-09-26-briefset-type-languages.md`. Recheck revision and local edits before any source mutation.
- Work: Trace dialog → services → inference/renderer and dialog → analyzer → WASM → JSON builder → result consumer, then inventory every SupportedLanguage branch in the affected areas.
  - Run the existing focused integration suites and capture generated results for their existing datasets before extraction. Identify failures already present without adjusting their expectations.
  - Trace successful reverse-conversion diagnostics: generate currently returns only text and the preview executor is String-based. Record the required additive result/preview boundary for the C feature child, which owns visible new-language warning delivery; keep this refactor’s old preview behavior unchanged.
  - Record renderer/policy responsibilities, metadata fields, seven appended IDs, query ownership, source-name limitations and the exact files each successor may edit.
  - Audit the proposed split for cohesion: one language owns its syntax and naming, shared code owns data traversal and orchestration, and UI depends on metadata rather than concrete renderers.
  - Trace selected language → original file extension → CodeInputPanel/CodePreviewPanel → EditorTextFieldFactory → final editor/highlighter/scheme. Record what occurs with and without native language support, and whether the platform updates an open field when the same color scheme is edited.
  - Add Highlighting to the handoff: specify native-provider priority and preservation of its file-type/layered highlighter route, standard fallback color keys, UTF-16 offsets and integer-state restart contracts, actual listener disposal and C-child ownership of implementation. Distinguish editable input checks from read-only generated-preview checks. Review the parent Lightweight syntax highlighting contract and the jetbrains-plugin-development syntax-highlighting reference; confirm chosen APIs on IC 2024.3 before introducing the seam.
- No-op when: All described seams already exist and every preservation check passes on current code.
- No-op handoff: Write no-change proof to `docs/briefs/type-languages/01-core-handoff.md` and let the parent continue to the C brief only when the same contract and readiness fields are complete.
- Deliverable: Initial contract and baseline in `docs/briefs/type-languages/01-core-handoff.md` (proposed), using Markdown with Revision, Language (existing four), Sources (URLs and pinned versions), Mapping, Highlighting, Owned files, Checks (command/cwd/input/result), Limitations and Readiness fields.
- Verify: `bounded source and baseline-result inspection`; Inputs: JsonToTypeRenderer, NamingSupport, InferenceContext, SupportedLanguage, both dialog presenters and the existing three integration suites; Expected: every affected branch is assigned an owner and original output/check results are recorded before edits
- Ends when:
  - [ ] The Highlighting handoff identifies both final editor consumers, the original-extension route and the public-API/native-provider baseline; it leaves actual fallback implementation to the C feature child.
  - [ ] The existing four-language contract and source-to-final-consumer flow are explicit.
  - [ ] The baseline distinguishes passing, failing and unavailable checks.
  - [ ] The split preserves signatures and has no request state or UI lifecycle in language policy objects.
  - [ ] The handoff identifies the C child as owner of shared warning delivery and distinguishes query availability from actual tree_query execution.
- Handoff: Stage 2 consumes the extension contract and baseline in `docs/briefs/type-languages/01-core-handoff.md`.
- Replan when: A source/API change outside the documented type-conversion and embedded-editor highlighting entry points is required, existing results cannot be preserved, or the baseline reveals an unrelated blocker. Return to the parent with a bounded correction route instead of mixing a behavior change into this refactor.
- Worker decision: Keep facade class names and call signatures. Choose package-private/internal collaborators and prefer two focused interfaces over a universal language framework.

### Stage 2 — Extract language responsibilities without changing output

- Starts when: Stage 1 contract and baseline are recorded in `docs/briefs/type-languages/01-core-handoff.md`.
- Work: Extract the existing four language renderers and naming/literal policies, then route the existing renderer facade through the explicit registry.
  - Keep JSON traversal, type merging, depth guards and declaration deduplication in shared inference. Replace only the language-sensitive kind/naming decisions with policy calls.
  - Keep import collection, annotation syntax, warning comment formatting and language-only helpers beside each renderer. Preserve the old facade API and deterministic output order.
  - Use metadata for placeholder lookup and an existing-v3 fallback only when a base icon asset is missing. Preserve current labels, icon paths and setting defaults for the original four.
  - Delete superseded branches/helpers only after all callers use the new route. Do not keep an unused compatibility path or duplicate renderer implementation.
  - Extract a small editor-local resolver only if needed by the recorded contract. Forward existing caller flags, requested extension, project and effective scheme without enabling fallback profiles yet; keep the JSON/JSON5 path and existing four-language providers unchanged.
- Deliverable: Extracted source modules and an updated ownership/API map in `docs/briefs/type-languages/01-core-handoff.md`.
- Verify: `./gradlew compileKotlin`; Inputs: repository root and all changed Kotlin production sources; Expected: exit 0 with the original four entries and public signatures unchanged
- Ends when:
  - [ ] Generic inference/render facades do not contain target-language syntax.
  - [ ] Each original language is routed to exactly one cohesive renderer/policy implementation.
  - [ ] Existing consumers compile and old metadata and resources still resolve.
- Handoff: Stage 3 receives the compiled extraction plus the unchanged baseline inputs.
- Replan when: The extraction requires changing persisted values, wire schema, Go experimental-option behavior or original generated text. Restore the affected behavior and revise the split before proceeding.

### Stage 3 — Prove preservation and publish the handoff

- Starts when: Stage 2 extraction compiles and every call site uses the intended owner.
- Work: Run `./gradlew test --tests 'com.livteam.jsoninja.services.typeConversion.TypeConversionWasmIntegration*Test'` from the repository root against the original datasets and compare with the Stage 1 baseline.
  - Inspect the complete changed typeConversion/language and convertType UI populations for unused old branches, duplicate policy and reverse dependencies into UI.
  - Start the existing IC 2024.3 sandbox with `./gradlew runIde` from the repository root, then use the existing dataset content to compare preview, copy/insert, language synchronization and settings reload behavior. Record unexercised routes explicitly.
  - Compare existing input/preview highlighting with the Stage 1 baseline using the same language, IDE provider and scheme. Include the verified resolver seam and the C-child implementation responsibilities in the Highlighting handoff; record any unverified runtime behavior separately.
  - Complete `docs/briefs/type-languages/01-core-handoff.md` with source paths, chosen contracts and proof. Set Readiness to ready only after the behavior contract and acceptance criteria are satisfied.
- Deliverable: Final `docs/briefs/type-languages/01-core-handoff.md` as Markdown with Revision, Language (existing four), Sources (URLs and pinned versions), Mapping, Highlighting, Owned files, Checks (command/cwd/input/result), Limitations and Readiness fields.
- Verify: `./gradlew test --tests 'com.livteam.jsoninja.services.typeConversion.TypeConversionWasmIntegration*Test'`; Inputs: the three existing TypeConversionWasmIntegration classes and their current datasets; Expected: exit 0 with nonzero executed checks and no changed original-language expectations
- Ends when:
  - [ ] Existing automated results and observed outputs match the recorded baseline.
  - [ ] The ownership audit accounts for all old rendering/naming branches.
  - [ ] The handoff has concrete commands, outputs, limitations and ready state or a truthful blocked verification result.
- Handoff: Parent and `docs/briefs/2026-09-26-feat-type-languages-02-c.md` receive `docs/briefs/type-languages/01-core-handoff.md` as the verified prerequisite for starting language additions.
- Replan when: Preservation proof fails. Stop successor work, return to the parent, perform bounded correction and re-verification, and recalculate topology/handoffs before resuming.

## Side Effect Checkpoints

- [ ] Highlighting extraction preserves caller flags, the original extension, existing providers, JSON/JSON5 handling and panel disposal; it does not register a global language/file type or create new listeners prematurely.
- [ ] Each existing language selection reaches renderer, WASM numeric ID, result preview, clipboard and final inserted tab/editor content with the correct extension.
- [ ] Both conversion tabs synchronize language once, preserve caller options and reload persisted selections without resetting old settings.
- [ ] Optional, nullable, inherited/referenced and recursive types use the existing JSON generation policies and depth guards without leaking language-specific helpers as JSON fields.
- [ ] Keyword/collision handling, literal escaping, original JSON keys and warning comment syntax agree between renderer and analyzer.
- [ ] No stale preview is applied after input/language changes or disposal, and cancellation does not become a displayed conversion failure.
- [ ] Default/en/ko/ja/zh_CN messages and v1/v2/v3 icon selection resolve for both themes.
- [ ] The existing four language query/analysis routes still work through the bundled WASM. This Kotlin extraction does not require new grammar assets or expose a new language.
- [ ] Existing regression assertions for Kotlin, Java, TypeScript and Go remain intact; no unsupported behavior is hidden by disabling a check or excluding enum entries.

## Acceptance Criteria

- [ ] The Highlighting handoff supplies the C child with the exact resolver seam, native-provider baseline, standard color-key policy and theme-refresh ownership while the current four-language behavior remains unchanged.
- [ ] All original language outputs, settings, IDs and service contracts remain unchanged after all stages and side-effect checks.
- [ ] Adding language syntax no longer requires editing the shared renderer, generic JSON traversal or UI presenter.
- [ ] Language renderer/policy implementations do not import UI classes or own Project, coroutine scopes, parser handles or mutable inference state.
- [ ] The existing focused integration suite passes without weakened or changed original expectations, and compileKotlin succeeds.
- [ ] `docs/briefs/type-languages/01-core-handoff.md` records the exact extension contract, preservation evidence and seven reserved IDs so successors can start without re-interviewing the requester.

## Open Questions

- None — the user requested code separation with high cohesion and low coupling; the preserved contract and reversible implementation boundaries are fixed here.

