# Python language handoff

## Revision

Base `519470b` plus preceding serialized language work. Original service and WASM contracts remain unchanged.

## Language

Python 3.11 TypedDict output; PYTHON / 7 / python / .py. No Python 3.12 type statement.

## Sources

- [tree-sitter-python 0.25.0](https://docs.rs/tree-sitter-python/0.25.0/tree_sitter_python/): pinned published crate, LANGUAGE_VERSION=15, parser.c + external scanner.c, tree-sitter-language 0.1 and cc 1.2; actual WASI build passed.
- [Python 3.11 typing](https://docs.python.org/3.11/library/typing.html), [lexical rules](https://docs.python.org/3.11/reference/lexical_analysis.html).
- MIT copyright 2016 Max Brunsfeld; retain the upstream license in bundled resources.

## Mapping

| Shape | Output / supported input | IR and limitations |
| --- | --- | --- |
| scalar | str, int, float, bool | Primitive |
| object | class TypedDict for safe keys, functional TypedDict for other keys | named object, exact source keys; naming UI shows original-key preservation |
| list/map | list[T], dict[str,T]; List/Dict/Optional/Union typing forms also accepted | List/Map; non-string key coercion warns |
| nullable | `T \| None` / Optional[T] | Nullable, independently of key requirement |
| optional | NotRequired[T]; total=False and Required[T] input | Field.optional without losing nullability |
| aliases | Name = static annotation; explicit TypeAlias also accepted | TypeAlias; quoted annotations use a depth-bounded static parser, never eval/import |
| Any/union | Any / union annotation | Unknown or Union; unknown and first-alternative sample limitations warn |
| classes | annotated class/dataclass fields, local bases | fields; methods and explicit ClassVar omitted |
| enum/dynamic | Enum classes, dynamic annotations/calls, external types | explicit unsupported/unknown diagnostics, no user code execution |
| empty object | TypedDict class with pass | empty object, not a fake property |

Generated nested declarations precede aliases/consumers. Functional dictionaries quote raw keys without renaming. Static type parsing is limited to identifiers, supported generic wrappers, unions and quoted forward references, with a bounded depth.

## Highlighting

Python 3.11 reserved keywords only (soft match/case remain neutral), # comments, valid prefixes and triple strings, decorator markers, numeric literals. TypedDict/Required/NotRequired stay identifiers. F-string contents can remain one STRING token. No semantic execution or dependency for coloring.

## Owned files

Python renderer/policy; metadata for preserving original keys; analyzer/static type parser; query pair; grammar, dispatch, localization; profile; golden and edge tests.

## Checks

Host Rust suite: 26 passed. Kotlin conversion/highlighting suite: 36 passed, zero failures/errors/skips, including actual bundled query per added language, key preservation and optional/nullable integration. `./gradlew compileKotlin buildTreeSitterWasm copyWasmToResources` passed. Python 3.11 grammar parse and local compile passed. Installed Python is used only to parse/compile generated source, never execute submitted user declarations.

## Limitations

No Pydantic or runtime validation promise, no interpreter installation, no eval or user imports. Unavailable live sandbox UI checks stay separate from automated editor tests.

## 최종 검증

[전체 검증 기록](09-verification.md): 전체 Kotlin 178개, Rust 37개 통과. 마지막 선언명 정책 보완 후 정책 테스트 5개·컴파일·배포 ZIP 재빌드 통과. 일곱 언어의 열린 입력/미리보기 색상 갱신과 실제 편집기 증분 복구, 비동기 취소/경고 분리, 기본 루트 선택을 포함한다. 쿼리 사본·아이콘·번들·패키지 리소스 일치를 확인했다. 이전 Checks의 수치는 각 단계 당시 결과다.

## Readiness

구현 및 자동 검증 완료. 실제 샌드박스 화면·복사/삽입 버튼·동적 언로드 관찰은 미확인이므로, 전체 수동 인수까지 완료했다는 `ready` 표시는 하지 않는다. 상세 한계와 Plugin Verifier 결과는 전체 검증 기록을 따른다.
