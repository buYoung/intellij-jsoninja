# C language handoff

## Revision

Base `519470b` plus the uncommitted core extraction. Core regression and golden preservation passed; visible UI proof remains pending and is not promoted to ready.

## Language

C11, C / 4 / c / .c. Both directions; sample JSON is not original-value recovery.

## Sources

- [tree-sitter-c 0.24.2](https://docs.rs/tree-sitter-c/0.24.2/tree_sitter_c/); exact local registry source `tree-sitter-c-0.24.2`, parser LANGUAGE_VERSION=15, Rust edition 2021, tree-sitter-language 0.1, build with cc and C11. No external scanner. Pin `=0.24.2`; tree-sitter runtime resolves 0.25.10. Actual WASI execution passed.
- [C11 N1570](https://www.open-std.org/jtc1/sc22/wg14/www/docs/n1570.pdf). No preprocessing or user code execution.
- MIT, copyright 2014 Max Brunsfeld: bundle the upstream notice with grammar resources.

## Mapping

| Shape | Generated / accepted form | IR / loss policy |
| --- | --- | --- |
| boolean/integer/decimal | bool, int64_t, double with standard headers; ordinary C integer and floating spellings accepted | Primitive; width/range not represented |
| string | char pointer | String by documented convention; handwritten pointer assumption warns |
| object/reference | typedef struct Name { fields } Name; struct tags and anonymous typedef structs | Struct and Named; each declarator extracted independently |
| array | exact marked `JsoninjaArray*` struct with size_t length and typed pointer data | List only when marker, prefix and structural layout match; ordinary C arrays become List with a length-loss warning |
| map | marked entry struct with key/value and marked map length/data helper | Map; helper references expand into List/Map before helper declarations are removed, so automatic root selection cannot select bookkeeping |
| nullable | extra pointer layer, including pointers to collection helper types | Nullable; arbitrary handwritten pointers warn about assumed nullability and unknown length |
| optional | marked generated field, independently recorded optional flag | Missing-member semantics cannot be expressed by C alone; warn, preserve generator marker in reverse conversion |
| root alias | typedef Type Root | TypeAlias, primitive and collection roots |
| empty object | exact marked struct with an unsigned-char sentinel | Empty Struct; only exact marker/layout removes sentinel |
| AnyValue/union | void pointer | Unknown and visible loss warning; never guess dynamic shape |
| enum | enum names and literal values | Enum; unresolved expressions remain unresolved and warn |
| unsupported | function pointer, bit field, anonymous union, conditional/macro-dependent type | Unknown/partial declarations plus warnings; syntax errors remain errors |
| keys | keyword-safe deterministic names with collisions resolved | Renamed source keys warn; no invented serializer contract |

Helper names are allocated per render request against all declaration names and other helpers. Nested dependencies precede their consumers. Query captures: declaration nodes, type names and field declarators; the exact generated nonempty source is executed through tree_query in bundled WASM tests.

## Highlighting

C11 keywords/types, numbers, ordinary/prefixed string/character literals, non-nested comments, preprocessor directives and include headers. Standard DefaultLanguageHighlighterColors keys only. New lexer per highlighter, UTF-16 offsets, integer restart state, all-input progress, cancellable scans. Preserve native FileType highlighters and user keys. C owns the common lexer/provider and live-scheme/disposal route; no optional IDE plugin is required.

## Owned files

C renderer/policy; Rust analyzer and type parser; exact grammar dependency; language dispatch; matching query copies; C metadata/localization; common detailed-result/warning and highlighting route; focused golden/unit/integration tests.

## Checks

All commands ran from `/Users/buyong/.codex/worktrees/c8e7/json-helper2`.

- `cargo test --manifest-path tree-sitter-wasm/Cargo.toml`: 18 passed, none ignored/failed. Includes semantic/capture golden fixtures, declarator precedence, strict helper recognition and unresolved external types.
- `./gradlew compileKotlin buildTreeSitterWasm copyWasmToResources`: PASS, no skipped WASM build. C grammar compiles for wasm32-wasip1; query invoked through Chicory from bundled resource.
- Focused Kotlin run: 26 conversion/renderer-golden/lexer cases passed. Five new bundled-WASM tests cover generated shape/query captures, root/empty/nested collection shapes, warning isolation, optional versus nullable, malformed input, cancellation and enum numeric values. Original 12 integration assertions remain intact.
- Editor integration: native-provider priority confirmed on IC 243.21565.193, which ships basic C custom-file-type highlighting. Its keys differ from Language Defaults. Native monochrome user override passed. Forced absent-language fallback passed live same-scheme attribute updates in both editor surfaces, document/caret/selection preservation and incremental comment recovery. Focused editor integration rerun: all 4 cases passed, including deferred disposal and undo/redo after color changes.
- Earlier test failures were separately investigated: original golden singularization baseline, missing closing brace during extraction, test setter syntax, incorrect test assumption about absent native C provider, and deferred editor disposal. No original-language expected output was weakened.

## Limitations

No header resolution, macro execution, memory ownership, allocation or serialization runtime. Native UI binding pending as recorded in the core handoff.

## 최종 검증

[전체 검증 기록](09-verification.md): 전체 Kotlin 178개, Rust 37개 통과. 마지막 선언명 정책 보완 후 정책 테스트 5개·컴파일·배포 ZIP 재빌드 통과. 일곱 언어의 열린 입력/미리보기 색상 갱신과 실제 편집기 증분 복구, 비동기 취소/경고 분리, 기본 루트 선택을 포함한다. 쿼리 사본·아이콘·번들·패키지 리소스 일치를 확인했다. 이전 Checks의 수치는 각 단계 당시 결과다.

## Readiness

구현 및 자동 검증 완료. 실제 샌드박스 화면·복사/삽입 버튼·동적 언로드 관찰은 미확인이므로, 전체 수동 인수까지 완료했다는 `ready` 표시는 하지 않는다. 상세 한계와 Plugin Verifier 결과는 전체 검증 기록을 따른다.
