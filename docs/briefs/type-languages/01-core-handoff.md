# Core language extension handoff

## Revision

Base: detached `519470b`; implementation is the uncommitted working tree in `/Users/buyong/.codex/worktrees/c8e7/json-helper2`.

## Language

Kotlin, Java, TypeScript, Go only. Reserved next IDs: C=4, CPP=5, CSHARP=6, PYTHON=7, RUST=8, SCALA=9, JSDOC=10. IDs never derive from enum ordinal.

## Sources

- Current catalog: Kotlin 2.4.0, IntelliJ Platform Gradle Plugin 2.11.0, Chicory 1.5.1; Java 17 compilation toolchain; IC 2024.3 build 243.21565.193.
- Local `ideaIC-2024.3-sources.jar`: public `HighlighterFactory.createHighlighter(FileType, EditorColorsScheme, Project)` delegates to the public `EditorHighlighterFactory` service overload; no factory construction or implementation-class dependency.
- [IntelliJ lexer contract](https://plugins.jetbrains.com/docs/intellij/implementing-lexer.html), [syntax highlighting](https://plugins.jetbrains.com/docs/intellij/syntax-highlighting-and-error-highlighting.html).

## Mapping

`JsonToTypeConversionService` → generic inference → `TypeLanguageRegistry` → language-local renderer and policy. The renderer facade and service signatures are unchanged. Naming tokenization/collision detection and JSON traversal remain shared. Language modules own declarations, imports, reserved names and literal exceptions. UI placeholder lookup uses metadata, icons preserve existing base/v3 choices with a v3 fallback.

Reverse: dialog presenter → cancellable preview executor → generation service → analyzer → runtime transaction → `analyze_source` → shared JSON builder → current preview → clipboard/tab/editor (undoable writes). C owns an additive detailed result carrying text and diagnostics plus typed preview transport. Original String entry points must remain compatible.

## Highlighting

`CodeInputPanel` and `CodePreviewPanel` pass the original extension to `TypeCodeHighlighterResolver` through the factory's optional highlighter callback. The callback runs only when the caller enables highlighting, after the caller's selected color scheme is applied. The native FileType route is retained; JSON/JSON5 and original languages retain native providers. C owns native usability checks, bundled fallback dispatch, UTF-16/integer-state lexer correctness, same-scheme Apply updates and actual disposal cleanup. No global file associations are added.

## Owned files

- `services/typeConversion/languages/{kotlin,java,typescript,go}`: original renderers/policies and Go codec helper.
- `TypeLanguageRenderer`, `TypeLanguagePolicy`, `TypeLanguageRegistry`: small immutable composition boundary.
- Facades, inference kind, metadata, placeholder/icon lookup; embedded editor provider seam.
- Renderer golden tests and fixture files (authorized by the latest user request).

## Checks

All commands run from `/Users/buyong/.codex/worktrees/c8e7/json-helper2`.

- Before extraction: `./gradlew test --tests 'com.livteam.jsoninja.services.typeConversion.TypeConversionWasmIntegration*Test'`: PASS, 12 tests in three classes, zero failures/errors/skips. Real WASI module rebuilt; tree-sitter resolved 0.25.10.
- Before extraction: renderer golden test PASS, four exact source files for nested object, renamed key, primitives and arrays. Existing singularization `address` → `RootAddres` intentionally preserved. Initial hand-authored expected name was corrected before extraction, not after a behavior change.
- After extraction: `./gradlew compileKotlin test --tests 'com.livteam.jsoninja.services.typeConversion.TypeConversionWasmIntegration*Test' --tests 'com.livteam.jsoninja.services.typeConversion.TypeLanguageRendererGoldenTest'`: PASS, 13 tests, zero failures/errors/skips. Original golden files unchanged after extraction.

## Limitations

Existing Gradle configuration-cache incompatibility warnings occur at baseline; execution exits 0 and discards that cache. The local Cargo.lock remains ignored. Runtime UI and live scheme evidence are not yet collected. Source inspection is not a Plugin Verifier or UI pass.

## 최종 검증

[전체 검증 기록](09-verification.md): 전체 Kotlin 178개, Rust 37개 통과. 마지막 선언명 정책 보완 후 정책 테스트 5개·컴파일·배포 ZIP 재빌드 통과. 일곱 언어의 열린 입력/미리보기 색상 갱신과 실제 편집기 증분 복구, 비동기 취소/경고 분리, 기본 루트 선택을 포함한다. 쿼리 사본·아이콘·번들·패키지 리소스 일치를 확인했다. 이전 Checks의 수치는 각 단계 당시 결과다.

## Readiness

구현 및 자동 검증 완료. 실제 샌드박스 화면·복사/삽입 버튼·동적 언로드 관찰은 미확인이므로, 전체 수동 인수까지 완료했다는 `ready` 표시는 하지 않는다. 상세 한계와 Plugin Verifier 결과는 전체 검증 기록을 따른다.
