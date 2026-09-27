# Scala language handoff

## Revision

Base `519470b` plus preceding language work. Existing wire format, IDs and public services retained.

## Language

SCALA / 9 / scala / .scala. Scala 2.13/3-compatible case-class output; NONE annotation style, no serializer dependency.

## Sources

- [tree-sitter-scala 0.26.2](https://docs.rs/tree-sitter-scala/0.26.2/tree_sitter_scala/): exact published crate inspected; ABI 15, C11 parser + external scanner, tree-sitter-language 0.1, cc 1.1. Upstream dev dependency 0.26 is not a runtime dependency. Existing tree-sitter 0.25 line retained; host and WASI compatibility passed.
- [Scala 2.13 lexical syntax](https://www.scala-lang.org/files/archive/spec/2.13/01-lexical-syntax.html), [case classes](https://docs.scala-lang.org/tour/case-classes.html), [Scala 3 union types](https://docs.scala-lang.org/scala3/reference/new-types/union-types.html).
- MIT copyright 2018 Max Brunsfeld and GitHub; exact release notice bundled.

## Mapping

| Form | Rule / IR |
| --- | --- |
| object | case class, String/Long/Double/Boolean fields |
| array / map | List[T] / Map[String,T]; List/Seq/Vector/Array/Set and qualified standard names accepted |
| nullability | Option[T], Scala 3 `T \| Null` -> Nullable |
| optional member | exact JSONinja comment; default constructor expressions do not imply optional JSON keys |
| root array / scalar | generated marker + collision-safe JsoninjaTypes object scope enclosing declarations and alias; reverse unscopes only the emitted declaration-only form |
| Any / heterogeneous union | Any with warning for 2.13 compatibility; handwritten Scala 3 union retains alternatives and sample-first warning |
| handwritten class / trait | first case-class parameter list or explicit val/var parameters, public typed vals/vars; methods, private/protected members and contextual parameters excluded |
| aliases / nesting | local named references with object/package scopes; ordinary object values are not fields |
| enum | simple Scala 3 cases -> string samples; payload cases -> Unknown with warning |
| unsupported | opaque/match/refinement/function types, unresolved names, untyped/computed fields -> explicit diagnostics; no evaluation |

Generated field renaming reports key loss because there is no serializer mapping. Keyword fields get safe suffixes. Imported predefined type names are reserved for declaration names to avoid shadowing.

## Highlighting

Reserved Scala 2.13/3 keywords, neutral soft-keyword identifiers, nested comments, triple/interpolated strings, backtick identifiers, annotations and numbers. Whole string tokens keep variable delimiters restartable; no PSI/compiler/WASM used for highlighting.

## Owned files

Scala renderer/policy, analyzer/type parser, grammar/dispatch/license, query pair, profile, metadata/localization and semantic/source/lexer goldens.

## Checks

Host Rust suite: 32 passed. Actual wasm32-wasip1 build/copy and Kotlin compilation passed. Kotlin conversion/highlighting suite: 40 tests passed, no failures/errors/skips; includes bundled Scala query, root scoped aliases, default-vs-optional-vs-nullable, triple interpolation and restart bounds.

## Limitations

No Scala compiler is installed locally. No implicit search, macro expansion, framework inference or compiler installation. Live sandbox UI remains separately unverified.

## 최종 검증

[전체 검증 기록](09-verification.md): 전체 Kotlin 178개, Rust 37개 통과. 마지막 선언명 정책 보완 후 정책 테스트 5개·컴파일·배포 ZIP 재빌드 통과. 일곱 언어의 열린 입력/미리보기 색상 갱신과 실제 편집기 증분 복구, 비동기 취소/경고 분리, 기본 루트 선택을 포함한다. 쿼리 사본·아이콘·번들·패키지 리소스 일치를 확인했다. 이전 Checks의 수치는 각 단계 당시 결과다.

## Readiness

구현 및 자동 검증 완료. 실제 샌드박스 화면·복사/삽입 버튼·동적 언로드 관찰은 미확인이므로, 전체 수동 인수까지 완료했다는 `ready` 표시는 하지 않는다. 상세 한계와 Plugin Verifier 결과는 전체 검증 기록을 따른다.
