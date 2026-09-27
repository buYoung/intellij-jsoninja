# C# language handoff

## Revision

Base `519470b` plus the serialized core, C and C++ working tree. No ABI change.

## Language

C# 8 generated classes/nullable annotations (System.Text.Json available); supported handwritten records use C# 9 forms. C# 11 raw strings are a lexical highlighting subset. CSHARP / 6 / csharp / .cs.

## Sources

- [tree-sitter-c-sharp 0.23.5](https://docs.rs/tree-sitter-c-sharp/0.23.5/tree_sitter_c_sharp/): exact published local source, LANGUAGE_VERSION=15, parser.c and external scanner.c, tree-sitter-language 0.1. Pin =0.23.5. Actual WASI build and execution passed.
- [System.Text.Json property naming](https://learn.microsoft.com/en-us/dotnet/standard/serialization/system-text-json/customize-properties): JsonPropertyName is bidirectional; default enum representation is numeric.
- [C# special tokens](https://learn.microsoft.com/en-us/dotnet/csharp/language-reference/tokens/): verbatim/interpolated/raw strings and escaped identifiers.
- Upstream MIT LICENSE from the published crate is bundled unchanged.
- [C# using-alias binding](https://learn.microsoft.com/en-us/dotnet/csharp/language-reference/language-specification/namespaces#1462-using-alias-directives): nested framework types in an alias use fully qualified names; sibling using directives do not apply there.

## Mapping

| Shape | Generated / accepted form | IR and limitations |
| --- | --- | --- |
| scalar | string, long, double, bool; common CLR/built-in names accepted | Primitive, widths/ranges not represented |
| object | public class with public auto-properties | Class; public instance fields/properties/record positional parameters; methods, static members and indexers excluded |
| array/map | List<T>, Dictionary<string,T>; ordinary arrays and supported collection interfaces accepted | List/Map; multidimensional ranks and non-string keys warn |
| nullable | T? for both value and reference types, #nullable enable | Nullable; member absence independently marked |
| optional | generated JSONinja optional marker | Field.optional; no claim that nullable alone means omittable |
| root alias | using Root = fully-qualified type; all aliases before classes | TypeAlias; collection, primitive and object roots remain JSON shapes without wrapper properties |
| AnyValue/union | object / object? | Unknown plus warning, no fabricated union semantics |
| keys | default System.Text.Json JsonPropertyName; NONE selectable | literal source-name attributes survive reverse conversion; NONE warns on rename |
| names | keyword-safe deterministic names; property equal to enclosing class name gets a collision-safe suffix | annotations preserve original keys |
| enum | integer literals/implicit successors | numeric sample; expressions or unsupported values rejected |
| attributes | literal JsonPropertyName / JsonPropertyNameAttribute including property-target record parameters | decoded literal only, no evaluation; unresolved expressions/attribute semantics warn |
| unsupported | external bases/aliases/generics, pointers, tuples, conditional declarations | partial analysis with visible diagnostics |

Non-null reference properties use `= default!;` to avoid requiring constructors/newer required-member syntax. Initialization is declaration scaffolding, not generated JSON values. Query contract: type names, property names, field declarators, positional parameters and named aliases.

## Highlighting

Own keyword profile; @identifier is IDENTIFIER. Ordinary, verbatim, interpolated and quote-count-delimited raw strings stay bounded; interpolation contents may be one STRING span. /// docs, attribute brackets, standard keys; native priority conditioned on usable lexical support. Use the inherited component-tree scheme callbacks and actual panel disposal path, with no global listeners or palette.

## Owned files

CSharpTypeRenderer/policy/literals; Rust analyzer/type parser; exact grammar dependency and dispatch; query pair; metadata/annotation option/localization; highlighting profile; focused golden and unit/integration tests.

## Checks

Implementation complete. `cargo test --manifest-path tree-sitter-wasm/Cargo.toml`: 23 passed after the C++ typedef boundary addition; C# record/property/attribute golden and unsupported-value cases passed. `./gradlew compileKotlin buildTreeSitterWasm copyWasmToResources`: PASS. Integrated Kotlin command `./gradlew compileKotlin test --tests 'com.livteam.jsoninja.services.typeConversion.*Test' --tests 'com.livteam.jsoninja.ui.component.convertType.highlighting.*Test'`: 34 passed, zero failures/skips. Generated C# root aliases, exact keys, class/property collisions, real bundled-query captures and lexical restart forms passed. No dotnet/csc/mcs compiler is installed, so external C# compilation is unverified. UI screenshots remain unverified; final compatibility results are recorded in the aggregate verification record.

## Limitations

No Roslyn, NuGet, external code execution, arbitrary attribute evaluation or Newtonsoft generation. UI access limitation remains separately recorded from automated editor assertions.

## 최종 검증

[전체 검증 기록](09-verification.md): 전체 Kotlin 178개, Rust 37개 통과. 마지막 선언명 정책 보완 후 정책 테스트 5개·컴파일·배포 ZIP 재빌드 통과. 일곱 언어의 열린 입력/미리보기 색상 갱신과 실제 편집기 증분 복구, 비동기 취소/경고 분리, 기본 루트 선택을 포함한다. 쿼리 사본·아이콘·번들·패키지 리소스 일치를 확인했다. 이전 Checks의 수치는 각 단계 당시 결과다.

## Readiness

구현 및 자동 검증 완료. 실제 샌드박스 화면·복사/삽입 버튼·동적 언로드 관찰은 미확인이므로, 전체 수동 인수까지 완료했다는 `ready` 표시는 하지 않는다. 상세 한계와 Plugin Verifier 결과는 전체 검증 기록을 따른다.
