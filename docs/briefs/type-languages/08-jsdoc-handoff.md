# JSDoc language handoff

## Revision

Base `519470b` plus preceding language work. Original settings, services and analysis wire format retained.

## Language

JSDOC / 10 / jsdoc / .js. Exactly one JSDoc selector; JavaScript source grammar externally, bounded JSDoc tag/type parsing internally.

## Sources

- [tree-sitter-javascript 0.25.0](https://docs.rs/tree-sitter-javascript/0.25.0/tree_sitter_javascript/): pinned release inspected locally, ABI 15, C11 parser + external scanner, tree-sitter-language 0.1, cc 1.2. No separate JSDoc grammar dependency or runtime.
- [typedef](https://jsdoc.app/tags-typedef), [property](https://jsdoc.app/tags-property), [type expressions](https://jsdoc.app/tags-type), [quoted namepaths](https://jsdoc.app/about-namepaths).
- MIT copyright 2014 Max Brunsfeld; upstream release notice bundled.

## Mapping

| Form | Rule / IR |
| --- | --- |
| object | `@typedef {Object} Name` + property tags -> Class fields |
| scalar/list root | typedef of string/number/boolean, Array<T> or T[] -> TypeAlias |
| nested JSON object | separate named typedefs; references preserve shapes |
| map | Object<string,T> / Object.<string,T> -> Map |
| nullable / nonnullable | ?T or union with null -> Nullable; !T removes nullable wrapper |
| optional | [name] / [name=literal] / type= -> optional independently of nullability; default values never evaluated |
| nested property paths | a.b and a[].b merge into InlineObject / List; quoted segments stay exact keys |
| source keys | unquoted safe identifiers, otherwise quoted namepath segments; escaped comment terminators/control characters cannot terminate generated comments |
| union / record types | balanced recursive parser, bounded depth; first sample alternative warning; record fields normalize inline |
| Any / unknown | * / ? / unsupported expression -> Unknown with visible diagnostics |
| multiple blocks / source | JavaScript comment AST nodes only; no string/template lookalikes, no non-doc comments, no evaluation; original tag byte spans retained |
| unsupported | callbacks, enums/runtime constants and Closure function/type-level expressions produce explicit unsupported diagnostics |

Block tags start on documentation lines (after optional leading star); continuation lines extend the current tag. Generated comments always use one tag per line. Bare comment sequences and full JavaScript share parser_create(10) and the exact JavaScript-comment query; only genuine documentation captures qualify.

## Highlighting

Real documentation block text -> DOC_COMMENT, tags -> DOC_COMMENT_TAG, type/name payload -> DOC_COMMENT_TAG_VALUE, using existing Language Defaults keys. Restartable integer document states split tags/payload without retaining a document copy. JavaScript quoted/template strings mask comment-like text. Native providers qualify only when their documentation tags and payload are distinguishable from comment text by key identity.

## Owned files

JSDoc renderer/policy, JavaScript AST block extractor, tag parser and independent bounded expression parser, query pair, grammar/dispatch/license, lexer profile, metadata/localization and source/semantic/lexer goldens.

## Checks

Actual host analysis/query goldens and bundled WASI query/generation passed. The latest host suite has 37 tests; the full Kotlin suite has 178 tests. JSDoc covers quoted/comment-sensitive keys, optional/nullable separation, nested paths, actual doc-tag keys and incremental editor recovery.

## Limitations

No JavaScript/Node/JSDoc CLI execution or full Closure checker. No inferred runtime object fields. Visible sandbox UI remains separately unverified.

## 최종 검증

[전체 검증 기록](09-verification.md): 전체 Kotlin 178개, Rust 37개 통과. 마지막 선언명 정책 보완 후 정책 테스트 5개·컴파일·배포 ZIP 재빌드 통과. 일곱 언어의 열린 입력/미리보기 색상 갱신과 실제 편집기 증분 복구, 비동기 취소/경고 분리, 기본 루트 선택을 포함한다. 쿼리 사본·아이콘·번들·패키지 리소스 일치를 확인했다. 이전 Checks의 수치는 각 단계 당시 결과다.

## Readiness

구현 및 자동 검증 완료. 실제 샌드박스 화면·복사/삽입 버튼·동적 언로드 관찰은 미확인이므로, 전체 수동 인수까지 완료했다는 `ready` 표시는 하지 않는다. 상세 한계와 Plugin Verifier 결과는 전체 검증 기록을 따른다.
