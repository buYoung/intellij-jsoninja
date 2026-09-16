# [fix] Generate types that admit the source JSON

## Work Type

fix

## Current State (As-Is)

- [confirmed] Audit anchor: HEAD `9eed77ed863f82843f9aadca0af479cf6efa0afd` inspected on 2026-09-16 with an untracked Undo/Redo test; treat the current checkout as authoritative. Evidence: repository HEAD and named source symbols below.
- [confirmed] `JsonToTypeSupport.mergePrimitiveKinds()` falls back to STRING for mixed primitive kinds. Evidence: fallback branch used by `JsonToTypeInferenceContext.inferArrayType()`.
- [confirmed] `JsonToTypeRenderer.renderTypescriptType()` appends [] without grouping nullable/union elements. Evidence: list, nullable, and union branches.
- [confirmed] `renderKotlinDeclaration()` emits a data class constructor for an empty object; Java/Kotlin annotations and Go tags interpolate source names. Evidence: declaration and annotation/tag renderer methods.
- [confirmed] `JsonToTypeNamingSupport.reservedWordsByLanguage` contains partial keyword lists. Evidence: reserved-word map and `toFieldName()`/`escapeReservedWord()`; `return` is a concrete missing collision.

## Reproduction

- Environment: JSON→type conversion for every currently supported language; pin emitted output and syntax validity before editing.
- Convert `{"items":[1,true]}`. Expected: element type admits both number and boolean, rather than string-only.
- Convert `{"items":[1,null]}` to TypeScript. Expected: array of a nullable element, such as `(number | null)[]`, not `number | null[]`.
- Convert `{}` to Kotlin. Expected: valid empty declaration rather than an illegal zero-parameter data class.
- Convert keys `return`, `class`, a quote, a backslash, a newline, dollar, backtick, Unicode, and colliding sanitized names. Expected: valid identifiers and string/tag syntax that preserve the original source key mapping.

## Desired Outcome (To-Be)

- Generated declarations are syntactically valid and accurately represent observed primitive, union, nullable, and empty-object values while preserving source field names.

## Scope

### In Scope

- Resolve assigned audit items R1.7, R1.8, R1.9a, R1.9b, R2.9 through the named end-to-end entry points.
- Preserve mixed primitive information through inference; choose a union where supported and a correct general type where the language lacks one. Preserve numeric promotion, nullability options, and existing experimental Go-union opt-in behavior.
- Group union/nullable element types before appending array notation, including nested arrays. Keep precedence correct through renderer composition.
- Emit a valid Kotlin declaration for empty objects without losing the root type name.
- Escape source names for the actual Java/Kotlin string literal or Go struct-tag context. Handle quotes, slash, controls, dollar interpolation, and backtick restrictions without silently renaming the serialized field.
- Apply complete relevant reserved-keyword handling and deterministic collision resolution for supported languages; retain existing naming conventions for non-conflicting identifiers.

### Out of Scope

- [hard] Adding a new output language or enabling experimental Go union behavior by default.
- [hard] Implementing sibling-owned findings or changing parent dependencies without first replanning the parent.

## Constraints

- Evidence stamps identify the tested revision and changed contract files; they do not require whole-worktree equality after unrelated siblings land. A successor records which predecessor file/contract hashes are unchanged, or which later correction re-verified the changed contract. Re-run only affected proof after new changes; final integration records that chain instead of rewriting historical results.
- Use `/Users/buyong/workspace/private/json-helper2` as the working directory for root commands. Before each verification command, announce the exact command and working directory. Baseline after Kotlin changes is `./gradlew compileKotlin`.
- Follow `docs/coroutine-threading-standard.md`: lifecycle child scopes, Default for CPU, IO for network/files, EDT for UI, modal context where needed, EDT WriteCommandAction for undoable mutations, and read actions for off-EDT platform reads. Preserve cancellation and caller-supplied options.
- Verify any new platform API against the minimum IC 2024.3 / build 243 environment and JVM 17 before using it. Preserve `JSONINJA_EDITOR_KEY`, settings enum/persistence keys, localization, registration, large-file thresholds, and disposal ownership where touched.
- Preserve unrelated dirty work, including `.codemap`, `.antigravitycli`, existing briefs, and the untracked Undo/Redo test. Do not stage, commit, publish, or run external mutations as part of implementation verification.
- Except child 01's explicitly authorized Undo/Redo tests, do not add or modify test cases/files, lint rules, formatter configuration, or test automation without a further explicit request. Use existing tests and the concrete bounded manual inspections/scenarios below. A passing existing suite does not substitute for a missing behavior observation.
- Record limitations honestly. Native UI control was not authorized in the audit session; no task may bypass that denial. Missing UI, compiler, network, or CI evidence stays pending, and the affected behavior is not accepted until an authorized observation or appropriate existing proof supplies it.

## Related Files / Entry Points

- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/JsonToTypeSupport.kt` — Start at primitive merge semantics.
- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/JsonToTypeInferenceContext.kt` — Trace array and field union inference.
- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/JsonToTypeRenderer.kt` — Apply language-specific precedence, empty-declaration, annotation, and tag rules.
- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/JsonToTypeNamingSupport.kt` — Complete relevant keyword escaping and collision handling.
- `src/main/kotlin/com/livteam/jsoninja/model/typeConversion/TypeConversionModels.kt` — Preserve the shared type model or update producers/consumers atomically if a type is required.
- `src/test/kotlin/com/livteam/jsoninja/services/typeConversion/TypeConversionWasmIntegrationV3Test.kt` — Inspect and run existing generated-type integration coverage.
- `docs/briefs/evidence/json-integrity-10.md` (proposed) — Durable Baseline, Implementation, and Acceptance handoff record for this child.
- `docs/briefs/2026-09-16-briefset-json-integrity.md` (proposed) — Parent owns cross-child order, conflict handling, and global acceptance.
- `docs/coroutine-threading-standard.md` — Apply the canonical threading and cancellation contract.

## Execution Plan

### Stage 1 — Pin the behavior and contract

- Starts when: Read `docs/briefs/evidence/json-integrity-02.md` (proposed), `docs/briefs/evidence/json-integrity-09.md` (proposed). Each predecessor must have an Acceptance section with ready/no-change status, source stamp matching the integrated code, completed required cases, and no unresolved prerequisite. Stop and return to the parent if any is missing, stale, or not-ready.
- Work: Trace callers through intermediate layers to the final consumer. Enumerate the Reproduction cases, record observed versus expected behavior, and separate source-confirmed structure from unexecuted runtime predictions. Inventory relevant public APIs/options and existing verification coverage before editing.
- No-op when: Every whole-child Acceptance Criterion and Side Effect Checkpoint is already supported by current, reproducible evidence on the integrated checkout; source appearance or passing unrelated tests alone is insufficient.
- No-op handoff: Write the complete Acceptance evidence to `docs/briefs/evidence/json-integrity-10.md` (proposed) with status no-change, exact source stamp, all case results and limitations. The parent checks whole-child acceptance and lets child 11, child 16 continue; skip implementation stages only when every required criterion is met. A failed proof instead follows Replan when.
- Deliverable: `docs/briefs/evidence/json-integrity-10.md` (proposed), Baseline section with source stamp (HEAD plus dirty-file/diff identity), case IDs, inputs, expected/actual values, methods, current command results, contracts, limitations, and route: implement/no-change/replan.
- Verify: `Bounded comparison of named source symbols and recorded baseline cases`; Inputs: the Reproduction cases and each existing entry-point file above; Expected: a non-empty case inventory, one concrete result or explicit unverified label per case, and a justified route tied to the actual checkout.
- Ends when:
  - [x] All assigned audit IDs have a traced final consumer and a specific reproduction/inspection result.
  - [x] Existing behavior to preserve and any unavailable evidence are explicitly recorded before implementation.
- Handoff: Stage 2 receives the Baseline section of `docs/briefs/evidence/json-integrity-10.md` (proposed), including the failing cases, caller contract, and selected bounded correction.
- Replan when: A prerequisite is stale, the predicted defect cannot be established, or a public/ownership contract must expand. Stop affected successors, return evidence to the parent owner, choose a bounded correction or evidence-only route, update topology/handoffs, and re-verify before resuming; never force an edit to satisfy the plan.

### Stage 2 — Implement the bounded correction

- Starts when: Stage 1 records route implement with confirmed failing behavior or a source-proven contract defect, and all listed prerequisites are accepted.
- Work: Apply the following focused changes:
  - Preserve mixed primitive information through inference; choose a union where supported and a correct general type where the language lacks one. Preserve numeric promotion, nullability options, and existing experimental Go-union opt-in behavior.
  - Group union/nullable element types before appending array notation, including nested arrays. Keep precedence correct through renderer composition.
  - Emit a valid Kotlin declaration for empty objects without losing the root type name.
  - Escape source names for the actual Java/Kotlin string literal or Go struct-tag context. Handle quotes, slash, controls, dollar interpolation, and backtick restrictions without silently renaming the serialized field.
  - Apply complete relevant reserved-keyword handling and deterministic collision resolution for supported languages; retain existing naming conventions for non-conflicting identifiers.
- Deliverable: `docs/briefs/evidence/json-integrity-10.md` (proposed), Implementation section with edited files/symbols, source stamp, chosen API/contract decisions, final-consumer trace, and compile result; corresponding focused source changes remain reviewable in the working tree.
- Verify: `./gradlew compileKotlin`; Inputs: the edited Kotlin consumers and unchanged public callers in the repository root; Expected: exit 0 with no new compilation errors. Also inspect every changed value/option at its final consumer against the Stage 1 contract.
- Ends when:
  - [x] The focused correction reaches every assigned final consumer and preserves the named contracts.
  - [x] Compile succeeds and no source change has crossed sibling ownership or an unanswered question milestone.
- Handoff: Stage 3 receives the integrated source changes plus Implementation section of `docs/briefs/evidence/json-integrity-10.md` (proposed).
- Replan when: The correction needs a new dependency/API, shared writer, unsupported platform behavior, or unresolved user-owned policy beyond the bounded choices. Stop affected successors and return the concrete evidence/change proposal to the parent owner to revise scope/order and re-verify. Keep unrelated ready children available.

### Stage 3 — Verify behavior and publish the handoff record

- Starts when: Stage 2 provides the compiling correction, or Stage 1 proves the complete no-change route and only evidence finalization remains.
- Work: Run the existing commands below and exercise every whole-child acceptance case and side-effect checkpoint. Record exact inputs, expected/actual results, tool/IDE versions, cwd, exit status, and limitations. Native/manual checks require an authorized execution context; do not substitute source inspection for an unobserved user interaction.
- Deliverable: `docs/briefs/evidence/json-integrity-10.md` (proposed), Acceptance section with source stamp, assigned audit IDs, per-case evidence, command/cwd/exit results, artifact paths/hashes where relevant, side-effect results, residuals, and status ready/no-change/not-ready. Readiness requires all mandatory behavior criteria; CI external observations are explicitly separated as stated in that child's criteria.
- Verify: `Bounded inspection of the command and behavior evidence matrix`; Inputs: root commands `./gradlew compileKotlin`; then `./gradlew test -PskipWasmBuild=true --tests 'com.livteam.jsoninja.services.typeConversion.TypeConversionWasmIntegration*'`, the integrated checkout, and every concrete Acceptance Criterion; Expected: exit 0 for each required command plus the stated per-case outcomes. Record missing capabilities as pending and use not-ready for unmet mandatory behavior instead of fabricating success.
- Ends when:
  - [x] Every required command and case has a recorded result or an explicit unresolved prerequisite with an owner.
  - [x] The final source stamp matches the code that produced the evidence, and the status accurately reflects whole-child acceptance.
- Handoff: The parent and child 11, child 16 receive `docs/briefs/evidence/json-integrity-10.md` (proposed) with the complete minimum format above. Only ready/no-change records with no unmet prerequisites authorize dependent work; the parent alone updates its Child Briefs checklist.
- Replan when: Verification fails or required proof is unavailable. Mark not-ready, stop dependent starts, return the exact failed input/result to the parent owner, activate bounded correction plus re-verification, and recalculate affected waves/handoffs before resuming. Never call a failing or unobserved behavior complete.

## Side Effect Checkpoints

- [x] Nullable/optional modes, naming casing, root names, annotation switches, and experimental Go settings retain their meanings.
- [x] 부모의 순서 보정에 따라 먼저 수용된 11의 enum 확장을 보존하고 양방향 통합 검사를 다시 수행한다.

## Acceptance Criteria

- [x] Mixed `[1,true]` arrays generate types admitting both observed values in every supported language; numeric-only promotion and nullable fields remain compatible.
- [x] TypeScript nullable/union array and nested-array output has the correct grouping, established by the language parser/compiler when available and explicitly recorded otherwise.
- [x] Kotlin output for `{}` is syntactically valid and retains the requested class name.
- [x] Every special/reserved/colliding key in Reproduction produces valid identifiers plus recoverable original source-name mapping; language-specific annotations/tags are escaped correctly.
- [x] Existing integration suites pass; generated-output syntax checks record the language tool and version, and unavailable compilers are not reported as successful compilation.
- [x] All stages and side-effect checkpoints have evidence at `docs/briefs/evidence/json-integrity-10.md` (proposed); required commands pass on the final source state, unresolved mandatory checks are absent, and the parent can consume the handoff without reconstructing context.

## 확정된 정책

- 사용자는 원본 키 보존을 우선하도록 확정했다. TypeScript 속성은 원래 키를 사용하고 Go는 기본 태그로 보존할 수 없는 경우 JSON 변환 메서드를 생성한다. 어노테이션 NONE은 태그를 추가하지 않으며 Go의 키 보존은 메서드로 처리한다. 기존 Java/Kotlin 어노테이션 선택은 유지한다.
