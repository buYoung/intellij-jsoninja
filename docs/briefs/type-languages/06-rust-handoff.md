# Rust language handoff

## Revision

Base `519470b` plus prior language slices; original ABI, settings and services retained.

## Language

RUST / 8 / rust / .rs. Rust 2021 owned structs, no generated Cargo/Serde dependency. NONE annotation option.

## Sources

- [tree-sitter-rust 0.24.2](https://docs.rs/tree-sitter-rust/0.24.2/tree_sitter_rust/), pinned crate inspected locally: ABI 15, C11 parser + scanner, tree-sitter-language 0.1 / cc 1.1. Runtime remains tree-sitter 0.25.x; host and WASI verification passed.
- [Rust token rules](https://doc.rust-lang.org/reference/tokens.html), [Serde container metadata](https://serde.rs/container-attrs.html), [Serde field metadata](https://serde.rs/field-attrs.html).
- MIT copyright 2017 Maxim Sokolov; upstream notice copied to bundled resources.

## Mapping

| Rust form | IR / output policy |
| --- | --- |
| named structs | Struct, owned String/i64/f64/bool fields; includes private instance fields like Serde derives |
| Option / Vec / arrays and slices / HashMap, BTreeMap | Nullable / List / Map; fixed lengths and non-string map keys warn |
| references / Box, Rc, Arc | unwrap for shape with ownership/reference warning |
| newtype tuple struct | TypeAlias; multi-field tuple and unit structs explicitly unknown |
| unit enum | string member sample, literal rename/rename_all supported; discriminants do not become JSON numeric values |
| enum with payload / unsupported metadata | Unknown or warning, never fabricated unit values |
| named local aliases / generics | named references and declared type parameters; unresolved external/associated types warn |
| optional field | exact generated comment and supported Serde default/skip_serializing_if; independently nullable |
| Serde names | literal rename + eight rename_all cases; skip/skip_serializing omit fields; no macro execution |
| Any / heterogeneous union | complete collision-safe JsoninjaValue helper with finite-size recursive collections; exact generated helper normalizes to Unknown and warns |

Renamed generated keys warn because NONE has no serializer mapping. Raw identifiers preserve legal Rust keywords; self/Self/super/crate use suffixes. Collection helper recognition requires the marker and exact variant/type shape, not the type name alone.

## Highlighting

Rust keyword/number/string/comment defaults, hash-delimited raw/byte strings, lifetimes versus chars, raw identifiers, nested comments and attribute markers. Whole multiline tokens restart at the delimiter; scans remain bounded/cancellable. Native provider retains its own color keys, fallback uses Language Defaults.

## Owned files

Rust renderer/policy, analyzer/type parser/metadata helper, query pair, grammar registration/license, profile, golden and edge tests, metadata/localization bindings.

## Checks

Host Rust suite: 29 passed. `./gradlew compileKotlin buildTreeSitterWasm copyWasmToResources` passed. Kotlin conversion/highlighting suites: 38 tests, zero failures/errors/skips, including exact bundled Rust query, helper isolation, Serde metadata and lexical restarts. Rust 2021 `rustc --crate-type=lib --emit=metadata` compiled the source golden. Networked Cargo initially failed DNS; cached pinned dependencies succeeded with --offline.

## Limitations

No trait solving, macro expansion, Cargo discovery or execution. Serde serialization-direction metadata only; unsupported nested/dynamic metadata warns. Actual manual UI verification remains pending separately from editor integration tests.

## 최종 검증

[전체 검증 기록](09-verification.md): 전체 Kotlin 178개, Rust 37개 통과. 마지막 선언명 정책 보완 후 정책 테스트 5개·컴파일·배포 ZIP 재빌드 통과. 일곱 언어의 열린 입력/미리보기 색상 갱신과 실제 편집기 증분 복구, 비동기 취소/경고 분리, 기본 루트 선택을 포함한다. 쿼리 사본·아이콘·번들·패키지 리소스 일치를 확인했다. 이전 Checks의 수치는 각 단계 당시 결과다.

## Readiness

구현 및 자동 검증 완료. 실제 샌드박스 화면·복사/삽입 버튼·동적 언로드 관찰은 미확인이므로, 전체 수동 인수까지 완료했다는 `ready` 표시는 하지 않는다. 상세 한계와 Plugin Verifier 결과는 전체 검증 기록을 따른다.
