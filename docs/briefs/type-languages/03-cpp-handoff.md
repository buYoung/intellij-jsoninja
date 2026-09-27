# C++ language handoff

## Revision

Base `519470b` plus current core/C working tree. Language edits are serialized.

## Language

C++17; CPP / 5 / cpp / .cpp.

## Sources

- [tree-sitter-cpp 0.23.4](https://docs.rs/tree-sitter-cpp/0.23.4/tree_sitter_cpp/): exact local published crate, LANGUAGE_VERSION=14, tree-sitter-language 0.1, parser.c + scanner.c compiled by cc. Runtime remains tree-sitter 0.25.10. Actual WASI build and execution passed.
- [Raw strings](https://eel.is/c%2B%2Bdraft/lex.string), [optional](https://eel.is/c%2B%2Bdraft/optional). Current working draft used only for the established C++17 forms selected here.
- [Pinned MIT license](https://raw.githubusercontent.com/tree-sitter/tree-sitter-cpp/v0.23.4/LICENSE), copyright 2014 Max Brunsfeld; copied into bundled grammar notices (the published crate omitted this file).

## Mapping

| Shape | Output / input | IR and limitations |
| --- | --- | --- |
| scalar | bool, std::int64_t, double, std::string | primitive; integer width not retained |
| object | struct with public data members | Struct; class defaults private, static members and methods excluded |
| array | std::vector<T>; std::array<T,N> and C-style arrays accepted | List; fixed sizes warn |
| map | std::map<std::string,T>, std::unordered_map<K,V> accepted | Map; non-string JSON keys warn |
| nullable | std::optional<T> | Nullable |
| optional | generated optional marker, optional storage as appropriate | Field.optional independently retained; C++ alone cannot distinguish absent JSON member from null |
| alias | using Root = Type; typedef accepted | TypeAlias |
| unconstrained/union | std::any / std::variant<...> | Unknown or Union; visible loss/sample-selection warnings |
| enum | named enum/enum class | numeric literals and implicit successors; unsupported expressions rejected by result adapter |
| references/pointers | handwritten T& / T* | retained referent / nullable referent plus ownership/length warning |
| names | deterministic keyword-safe fields; namespace-qualified declaration names | renamed JSON keys warn; local named references resolve only against declarations in this source |
| unsupported | dependent templates, preprocessor conditionals, unions | explicit diagnostics; no compiler/header/template execution |

Query contract: named structs/classes/enums, aliases and field declarators captured from actual generated source. The C++ analyzer/type parser remain separate from C. Shared AST utilities are limited to node text/children/span operations.

## Highlighting

C++17 keyword profile; C-equivalent comment/escape/punctuation scanning shared. Raw strings consume through their exact delimiter (maximum 16 delimiter characters), including multiline content. UTF-16 offsets and token-boundary restart tests; no semantic template resolution. Native provider and user keys remain first; bundled fallback uses Language Defaults. Shared component-tree scheme notification and warning transport are inherited from C.

## Owned files

C++ renderer/policy, analyzer/type parser, grammar dependency, dispatch/metadata, query pair, localized labels/placeholder, highlighting profile, golden and unit/integration cases.

## Checks

Implemented and integrated. Rust host tests, Kotlin compile, real wasm32-wasip1 build/copy, bundled analysis/query execution, source golden and raw-string restart checks passed. `clang++ -std=c++17 -fsyntax-only src/test/resources/typeConversion/golden/object.cpp` exited 0. Original C golden also passed `clang -std=c11 -fsyntax-only .../object.c`. C++ anonymous typedef regression added and passed in the next host run. Latest combined Kotlin run through C# passed 34 cases; visible UI checks remain unverified.

## Limitations

No preprocessor/header/compiler execution, serializer framework or template instantiation. Visible sandbox UI access remains pending; tests do not claim observed screenshots.

## 최종 검증

[전체 검증 기록](09-verification.md): 전체 Kotlin 178개, Rust 37개 통과. 마지막 선언명 정책 보완 후 정책 테스트 5개·컴파일·배포 ZIP 재빌드 통과. 일곱 언어의 열린 입력/미리보기 색상 갱신과 실제 편집기 증분 복구, 비동기 취소/경고 분리, 기본 루트 선택을 포함한다. 쿼리 사본·아이콘·번들·패키지 리소스 일치를 확인했다. 이전 Checks의 수치는 각 단계 당시 결과다.

## Readiness

구현 및 자동 검증 완료. 실제 샌드박스 화면·복사/삽입 버튼·동적 언로드 관찰은 미확인이므로, 전체 수동 인수까지 완료했다는 `ready` 표시는 하지 않는다. 상세 한계와 Plugin Verifier 결과는 전체 검증 기록을 따른다.
