# [fix] Preserve enum literals and optional-field modes

## Work Type

fix

## Current State (As-Is)

- [confirmed] Audit anchor: HEAD `9eed77ed863f82843f9aadca0af479cf6efa0afd` inspected on 2026-09-16 with an untracked Undo/Redo test; treat the current checkout as authoritative. Evidence: repository HEAD and named source symbols below.
- [confirmed] The Rust TypeScript enum analyzer supplies `name` and `value_text`, while Kotlin TreeSitterQueryResult decoding/domain mapping retains only the name. Evidence: `parse_enum_declaration()` and `parseDeclaration()`/`toDomainModel()`.
- [confirmed] `TypeToJsonNodeGenerator.generateNamedNode()` serializes the enum name, and `shouldIncludeField()` treats COMMENTED like ALL. Evidence: generator branches and the view's SchemaPropertyGenerationMode entries.

## Reproduction

- Environment: TypeScript source through bundled WASM → Kotlin decoder → type-to-JSON preview. Existing integration fixtures with name equal to value do not prove literal fidelity.
- Convert `enum Status { ACTIVE = "active" } interface Response { status: Status }`. Expected: `{"status":"active"}`, not the member identifier.
- Exercise an explicit numeric enum, implicit numeric members, and Java/Kotlin enums separately. Expected: supported literal semantics and unchanged name-based behavior where that language uses enum names.
- Convert a declaration with one required and one optional field using REQUIRED, ALL, and COMMENTED. Expected: the COMMENTED field is actually commented, with valid active required content.

## Desired Outcome (To-Be)

- Type-to-JSON respects declared enum values and the user-selected optional-field mode through the final preview/copy/insert text.

## Scope

### In Scope

- Resolve assigned audit items R2.5, R2.6 through the named end-to-end entry points.
- Carry enum literal kind/value from analyzer output through Kotlin decoding and generation as one atomic contract change. Preserve name behavior for Java/Kotlin enums; support literal/implicit TypeScript values that the analyzer can resolve.
- Reject or report unsupported computed enum values through the existing recoverable path instead of silently substituting the member name. Verify exact decoder field names against bundled WASM output.
- Render COMMENTED optional fields as comments consistent with schema generation, while REQUIRED omits them and ALL emits them actively. Keep the final formatter from stripping or activating those comments.
- Reuse nearby rendering logic only without expanding ownership into shared schema implementation; if extraction is necessary, stop the affected successor and update the parent's file ownership/order first.

### Out of Scope

- [hard] Removing the existing COMMENTED choice or inventing enum values for unsupported expressions.
- [hard] Changing WASM exported function names or buffer ownership.
- [hard] Implementing sibling-owned findings or changing parent dependencies without first replanning the parent.

## Constraints

- Evidence stamps identify the tested revision and changed contract files; they do not require whole-worktree equality after unrelated siblings land. A successor records which predecessor file/contract hashes are unchanged, or which later correction re-verified the changed contract. Re-run only affected proof after new changes; final integration records that chain instead of rewriting historical results.
- Mode names in this brief are shorthand only: REQUIRED means `SchemaPropertyGenerationMode.REQUIRED_ONLY`, ALL means `REQUIRED_AND_OPTIONAL`, and COMMENTED means `REQUIRED_AND_OPTIONAL_COMMENTED`. Preserve those exact enum/persistence values.
- Use `/Users/buyong/workspace/private/json-helper2` as the working directory for root commands. Before each verification command, announce the exact command and working directory. Baseline after Kotlin changes is `./gradlew compileKotlin`.
- Follow `docs/coroutine-threading-standard.md`: lifecycle child scopes, Default for CPU, IO for network/files, EDT for UI, modal context where needed, EDT WriteCommandAction for undoable mutations, and read actions for off-EDT platform reads. Preserve cancellation and caller-supplied options.
- Verify any new platform API against the minimum IC 2024.3 / build 243 environment and JVM 17 before using it. Preserve `JSONINJA_EDITOR_KEY`, settings enum/persistence keys, localization, registration, large-file thresholds, and disposal ownership where touched.
- Preserve unrelated dirty work, including `.codemap`, `.antigravitycli`, existing briefs, and the untracked Undo/Redo test. Do not stage, commit, publish, or run external mutations as part of implementation verification.
- Except child 01's explicitly authorized Undo/Redo tests, do not add or modify test cases/files, lint rules, formatter configuration, or test automation without a further explicit request. Use existing tests and the concrete bounded manual inspections/scenarios below. A passing existing suite does not substitute for a missing behavior observation.
- Record limitations honestly. Native UI control was not authorized in the audit session; no task may bypass that denial. Missing UI, compiler, network, or CI evidence stays pending, and the affected behavior is not accepted until an authorized observation or appropriate existing proof supplies it.
- Run `cargo test` from `tree-sitter-wasm` if Rust is changed, then rebuild the actual wasm32-wasip1 resource through the existing Gradle pipeline; host tests alone do not verify bundled WASM.

## Related Files / Entry Points

- `tree-sitter-wasm/src/analyzer/typescript.rs` — Start at enum value extraction; retain the existing ABI.
- `src/main/kotlin/com/livteam/jsoninja/services/treesitter/TreeSitterQueryResult.kt` — Trace enum decoding into domain types.
- `src/main/kotlin/com/livteam/jsoninja/model/typeConversion/TypeConversionModels.kt` — Carry required enum semantics atomically with decoder/generator changes.
- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/TypeToJsonNodeGenerator.kt` — Select actual enum literal values and classify optional fields.
- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/TypeToJsonGenerationService.kt` — Keep final document rendering from discarding comments.
- `src/main/kotlin/com/livteam/jsoninja/ui/dialog/generateJson/model/SchemaPropertyGenerationMode.kt` — Preserve the actual three generation mode constants while correcting emitted text.
- `src/main/kotlin/com/livteam/jsoninja/ui/dialog/convertType/TypeToJsonDialogView.kt` — Preserve the advertised generation mode choices.
- `src/main/kotlin/com/livteam/jsoninja/services/schema/JsonSchemaDataGenerationService.kt` — Read existing COMMENTED semantics for consistency; no edits here in this child.
- `docs/briefs/evidence/json-integrity-11.md` (proposed) — Durable Baseline, Implementation, and Acceptance handoff record for this child.
- `docs/briefs/2026-09-16-briefset-json-integrity.md` (proposed) — Parent owns cross-child order, conflict handling, and global acceptance.
- `docs/coroutine-threading-standard.md` — Apply the canonical threading and cancellation contract.

## Execution Plan

- 부모의 2026-09-16 순서 보정에 따라 08/09의 수용된 계약에서 시작한다. 10의 원본 키 정책은 이 흐름과 독립이며 기존 enumValues/필드 구조를 보존한다. 전체 10 수용을 완료로 간주하지 않는다.

### Stage 1 — Pin the behavior and contract

- Starts when: Read `docs/briefs/evidence/json-integrity-08.md` (proposed), `docs/briefs/evidence/json-integrity-09.md` (proposed). Each predecessor must have an Acceptance section with ready/no-change status, source stamp matching the integrated code, completed required cases, and no unresolved prerequisite. Stop and return to the parent if any is missing, stale, or not-ready.
- Work: Trace callers through intermediate layers to the final consumer. Enumerate the Reproduction cases, record observed versus expected behavior, and separate source-confirmed structure from unexecuted runtime predictions. Inventory relevant public APIs/options and existing verification coverage before editing.
- No-op when: Every whole-child Acceptance Criterion and Side Effect Checkpoint is already supported by current, reproducible evidence on the integrated checkout; source appearance or passing unrelated tests alone is insufficient.
- No-op handoff: Write the complete Acceptance evidence to `docs/briefs/evidence/json-integrity-11.md` (proposed) with status no-change, exact source stamp, all case results and limitations. The parent checks whole-child acceptance and lets child 16 continue; skip implementation stages only when every required criterion is met. A failed proof instead follows Replan when.
- Deliverable: `docs/briefs/evidence/json-integrity-11.md` (proposed), Baseline section with source stamp (HEAD plus dirty-file/diff identity), case IDs, inputs, expected/actual values, methods, current command results, contracts, limitations, and route: implement/no-change/replan.
- Verify: `Bounded comparison of named source symbols and recorded baseline cases`; Inputs: the Reproduction cases and each existing entry-point file above; Expected: a non-empty case inventory, one concrete result or explicit unverified label per case, and a justified route tied to the actual checkout.
- Ends when:
  - [x] All assigned audit IDs have a traced final consumer and a specific reproduction/inspection result.
  - [x] Existing behavior to preserve and any unavailable evidence are explicitly recorded before implementation.
- Handoff: Stage 2 receives the Baseline section of `docs/briefs/evidence/json-integrity-11.md` (proposed), including the failing cases, caller contract, and selected bounded correction.
- Replan when: A prerequisite is stale, the predicted defect cannot be established, or a public/ownership contract must expand. Stop affected successors, return evidence to the parent owner, choose a bounded correction or evidence-only route, update topology/handoffs, and re-verify before resuming; never force an edit to satisfy the plan.

### Stage 2 — Implement the bounded correction

- Starts when: Stage 1 records route implement with confirmed failing behavior or a source-proven contract defect, and all listed prerequisites are accepted.
- Work: Apply the following focused changes:
  - Carry enum literal kind/value from analyzer output through Kotlin decoding and generation as one atomic contract change. Preserve name behavior for Java/Kotlin enums; support literal/implicit TypeScript values that the analyzer can resolve.
  - Reject or report unsupported computed enum values through the existing recoverable path instead of silently substituting the member name. Verify exact decoder field names against bundled WASM output.
  - Render COMMENTED optional fields as comments consistent with schema generation, while REQUIRED omits them and ALL emits them actively. Keep the final formatter from stripping or activating those comments.
  - Reuse nearby rendering logic only without expanding ownership into shared schema implementation; if extraction is necessary, stop the affected successor and update the parent's file ownership/order first.
- Deliverable: `docs/briefs/evidence/json-integrity-11.md` (proposed), Implementation section with edited files/symbols, source stamp, chosen API/contract decisions, final-consumer trace, and compile result; corresponding focused source changes remain reviewable in the working tree.
- Verify: `./gradlew compileKotlin`; Inputs: the edited Kotlin consumers and unchanged public callers in the repository root; Expected: exit 0 with no new compilation errors. Also inspect every changed value/option at its final consumer against the Stage 1 contract.
- Ends when:
  - [x] The focused correction reaches every assigned final consumer and preserves the named contracts.
  - [x] Compile succeeds and no source change has crossed sibling ownership or an unanswered question milestone.
- Handoff: Stage 3 receives the integrated source changes plus Implementation section of `docs/briefs/evidence/json-integrity-11.md` (proposed).
- Replan when: The correction needs a new dependency/API, shared writer, unsupported platform behavior, or unresolved user-owned policy beyond the bounded choices. Stop affected successors and return the concrete evidence/change proposal to the parent owner to revise scope/order and re-verify. Keep unrelated ready children available.

### Stage 3 — Verify behavior and publish the handoff record

- Starts when: Stage 2 provides the compiling correction, or Stage 1 proves the complete no-change route and only evidence finalization remains.
- Work: Run the existing commands below and exercise every whole-child acceptance case and side-effect checkpoint. Record exact inputs, expected/actual results, tool/IDE versions, cwd, exit status, and limitations. Native/manual checks require an authorized execution context; do not substitute source inspection for an unobserved user interaction.
- Deliverable: `docs/briefs/evidence/json-integrity-11.md` (proposed), Acceptance section with source stamp, assigned audit IDs, per-case evidence, command/cwd/exit results, artifact paths/hashes where relevant, side-effect results, residuals, and status ready/no-change/not-ready. Readiness requires all mandatory behavior criteria; CI external observations are explicitly separated as stated in that child's criteria.
- Verify: `Bounded inspection of the command and behavior evidence matrix`; Inputs: root commands `./gradlew compileKotlin`; then `./gradlew test --tests 'com.livteam.jsoninja.services.typeConversion.TypeConversionWasmIntegration*'`, the integrated checkout, and every concrete Acceptance Criterion; Expected: exit 0 for each required command plus the stated per-case outcomes. Record missing capabilities as pending and use not-ready for unmet mandatory behavior instead of fabricating success.
- Ends when:
  - [x] Every required command and case has a recorded result or an explicit unresolved prerequisite with an owner.
  - [x] The final source stamp matches the code that produced the evidence, and the status accurately reflects whole-child acceptance.
- Handoff: The parent and child 16 receive `docs/briefs/evidence/json-integrity-11.md` (proposed) with the complete minimum format above. Only ready/no-change records with no unmet prerequisites authorize dependent work; the parent alone updates its Child Briefs checklist.
- Replan when: Verification fails or required proof is unavailable. Mark not-ready, stop dependent starts, return the exact failed input/result to the parent owner, activate bounded correction plus re-verification, and recalculate affected waves/handoffs before resuming. Never call a failing or unobserved behavior complete.

## Side Effect Checkpoints

- [x] Child 08 transaction isolation and child 09 current-preview gating remain intact.
- [x] All supported language decoders remain compatible with unchanged fields and enum declarations lacking explicit values.

## Acceptance Criteria

- [x] The ACTIVE/active fixture emits the literal `active`; numeric and implicit TypeScript enum cases produce matching value kinds, while Java/Kotlin enum behavior remains valid.
- [x] REQUIRED, ALL, and COMMENTED yield three distinct outputs for the same required/optional declaration; commented optional fields are inactive and the remaining JSON/JSON5 parses.
- [x] Preview, copied text, and inserted text preserve the chosen enum literals and optional-field mode through final formatting.
- [x] Existing type-conversion integration suites run against a rebuilt WASM artifact if Rust changed, and recorded artifact identity agrees with the packaged resource.
- [x] All stages and side-effect checkpoints have evidence at `docs/briefs/evidence/json-integrity-11.md` (proposed); required commands pass on the final source state, unresolved mandatory checks are absent, and the parent can consume the handoff without reconstructing context.

## Open Questions

- None — scope and behavior follow the reviewed defects; bounded implementation choices are assigned to the worker.
