# [fix] Apply only previews for the current input

## Work Type

fix

## Current State (As-Is)

- [confirmed] Audit anchor: HEAD `9eed77ed863f82843f9aadca0af479cf6efa0afd` inspected on 2026-09-16 with an untracked Undo/Redo test; treat the current checkout as authoritative. Evidence: repository HEAD and named source symbols below.
- [confirmed] `JsonToTypeDialogPresenter.schedulePreview()` and `TypeToJsonDialogPresenter.schedulePreview()` keep an earlier currentPreviewText during pending work; invalid-input branches do not consistently cancel earlier requests. Evidence: the validation/early-return and submission branches.
- [confirmed] `ConvertTypeDialog.doOKAction()` checks a nonblank preview rather than whether it belongs to the current input/configuration. Evidence: insertion gating.
- [confirmed] `JsonToTypeDialogValidator` parses JSON before debounced compute. Evidence: validator readTree called from the presenter change route; EDT cost is not yet measured.

## Reproduction

- Environment: both conversion directions in the modal Convert Type dialog, current debounce 300ms for JSON→type and 500ms for type→JSON.
- Obtain a valid preview, change the source or target language, and immediately press Insert/Copy before the next preview completes. Expected: stale output is unavailable.
- Schedule valid input, then make it blank/invalid before completion. Expected: pending work is invalidated and cannot restore an earlier preview.
- Use approximately 1 MiB valid and malformed JSON to capture validation call stacks and EDT timings on the same IDE/machine; no latency threshold is presumed.

## Desired Outcome (To-Be)

- Insert/Copy become available only for a successful preview matching the current source, direction, language, and generation options.
- Parsing/validation work follows the background preview pipeline while immediate UI state invalidation remains on EDT.

## Scope

### In Scope

- Resolve assigned audit items R1.6, M1b through the named end-to-end entry points.
- Model Pending/Ready/Invalid state with source/configuration identity. Immediately invalidate readiness and cancel obsolete work on every relevant change, including blank input and language synchronization.
- Gate every preview consumption endpoint, including both copy buttons and final insertion, on the matching Ready state; re-check at the final consumer rather than trusting a previously enabled button.
- Place expensive JSON validation inside cancellable background work, preserving the 300ms/500ms debounce values and modal EDT context for UI application. Capture before/after threading evidence without inventing a speed SLA.
- Preserve the transaction isolation from child 08 and discard out-of-order completions even when a canceled synchronous computation returns.

### Out of Scope

- [hard] Changing debounce values, generation settings, or the meaning of Copy/Insert.
- [deferred] A numeric UI latency acceptance threshold requires a separate measured product target.
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

- `src/main/kotlin/com/livteam/jsoninja/ui/dialog/convertType/ConvertTypeDialog.kt` — Start at doOKAction/validation and final insert.
- `src/main/kotlin/com/livteam/jsoninja/ui/dialog/convertType/ConvertPreviewExecutor.kt` — Use current request ownership and cancellation.
- `src/main/kotlin/com/livteam/jsoninja/ui/dialog/convertType/JsonToTypeDialogPresenter.kt` — Trace validation, debounce, and preview assignment.
- `src/main/kotlin/com/livteam/jsoninja/ui/dialog/convertType/TypeToJsonDialogPresenter.kt` — Apply the same current-input readiness contract.
- `src/main/kotlin/com/livteam/jsoninja/ui/dialog/convertType/JsonToTypeDialogValidator.kt` — Locate expensive pre-debounce parsing.
- `src/main/kotlin/com/livteam/jsoninja/ui/dialog/convertType/ConvertTypeDialogPresenter.kt` — Preserve tab/language synchronization and disposal.
- `docs/briefs/evidence/json-integrity-09.md` (proposed) — Durable Baseline, Implementation, and Acceptance handoff record for this child.
- `docs/briefs/2026-09-16-briefset-json-integrity.md` (proposed) — Parent owns cross-child order, conflict handling, and global acceptance.
- `docs/coroutine-threading-standard.md` — Apply the canonical threading and cancellation contract.

## Execution Plan

### Stage 1 — Pin the behavior and contract

- Starts when: Read `docs/briefs/evidence/json-integrity-08.md` (proposed). Each predecessor must have an Acceptance section with ready/no-change status, source stamp matching the integrated code, completed required cases, and no unresolved prerequisite. Stop and return to the parent if any is missing, stale, or not-ready.
- Work: Trace callers through intermediate layers to the final consumer. Enumerate the Reproduction cases, record observed versus expected behavior, and separate source-confirmed structure from unexecuted runtime predictions. Inventory relevant public APIs/options and existing verification coverage before editing.
- No-op when: Every whole-child Acceptance Criterion and Side Effect Checkpoint is already supported by current, reproducible evidence on the integrated checkout; source appearance or passing unrelated tests alone is insufficient.
- No-op handoff: Write the complete Acceptance evidence to `docs/briefs/evidence/json-integrity-09.md` (proposed) with status no-change, exact source stamp, all case results and limitations. The parent checks whole-child acceptance and lets child 10, child 11, child 16 continue; skip implementation stages only when every required criterion is met. A failed proof instead follows Replan when.
- Deliverable: `docs/briefs/evidence/json-integrity-09.md` (proposed), Baseline section with source stamp (HEAD plus dirty-file/diff identity), case IDs, inputs, expected/actual values, methods, current command results, contracts, limitations, and route: implement/no-change/replan.
- Verify: `Bounded comparison of named source symbols and recorded baseline cases`; Inputs: the Reproduction cases and each existing entry-point file above; Expected: a non-empty case inventory, one concrete result or explicit unverified label per case, and a justified route tied to the actual checkout.
- Ends when:
  - [ ] All assigned audit IDs have a traced final consumer and a specific reproduction/inspection result.
  - [ ] Existing behavior to preserve and any unavailable evidence are explicitly recorded before implementation.
- Handoff: Stage 2 receives the Baseline section of `docs/briefs/evidence/json-integrity-09.md` (proposed), including the failing cases, caller contract, and selected bounded correction.
- Replan when: A prerequisite is stale, the predicted defect cannot be established, or a public/ownership contract must expand. Stop affected successors, return evidence to the parent owner, choose a bounded correction or evidence-only route, update topology/handoffs, and re-verify before resuming; never force an edit to satisfy the plan.

### Stage 2 — Implement the bounded correction

- Starts when: Stage 1 records route implement with confirmed failing behavior or a source-proven contract defect, and all listed prerequisites are accepted.
- Work: Apply the following focused changes:
  - Model Pending/Ready/Invalid state with source/configuration identity. Immediately invalidate readiness and cancel obsolete work on every relevant change, including blank input and language synchronization.
  - Gate every preview consumption endpoint, including both copy buttons and final insertion, on the matching Ready state; re-check at the final consumer rather than trusting a previously enabled button.
  - Place expensive JSON validation inside cancellable background work, preserving the 300ms/500ms debounce values and modal EDT context for UI application. Capture before/after threading evidence without inventing a speed SLA.
  - Preserve the transaction isolation from child 08 and discard out-of-order completions even when a canceled synchronous computation returns.
- Deliverable: `docs/briefs/evidence/json-integrity-09.md` (proposed), Implementation section with edited files/symbols, source stamp, chosen API/contract decisions, final-consumer trace, and compile result; corresponding focused source changes remain reviewable in the working tree.
- Verify: `./gradlew compileKotlin`; Inputs: the edited Kotlin consumers and unchanged public callers in the repository root; Expected: exit 0 with no new compilation errors. Also inspect every changed value/option at its final consumer against the Stage 1 contract.
- Ends when:
  - [ ] The focused correction reaches every assigned final consumer and preserves the named contracts.
  - [ ] Compile succeeds and no source change has crossed sibling ownership or an unanswered question milestone.
- Handoff: Stage 3 receives the integrated source changes plus Implementation section of `docs/briefs/evidence/json-integrity-09.md` (proposed).
- Replan when: The correction needs a new dependency/API, shared writer, unsupported platform behavior, or unresolved user-owned policy beyond the bounded choices. Stop affected successors and return the concrete evidence/change proposal to the parent owner to revise scope/order and re-verify. Keep unrelated ready children available.

### Stage 3 — Verify behavior and publish the handoff record

- Starts when: Stage 2 provides the compiling correction, or Stage 1 proves the complete no-change route and only evidence finalization remains.
- Work: Run the existing commands below and exercise every whole-child acceptance case and side-effect checkpoint. Record exact inputs, expected/actual results, tool/IDE versions, cwd, exit status, and limitations. Native/manual checks require an authorized execution context; do not substitute source inspection for an unobserved user interaction.
- Deliverable: `docs/briefs/evidence/json-integrity-09.md` (proposed), Acceptance section with source stamp, assigned audit IDs, per-case evidence, command/cwd/exit results, artifact paths/hashes where relevant, side-effect results, residuals, and status ready/no-change/not-ready. Readiness requires all mandatory behavior criteria; CI external observations are explicitly separated as stated in that child's criteria.
- Verify: `Bounded inspection of the command and behavior evidence matrix`; Inputs: root commands `./gradlew compileKotlin`; then `./gradlew test -PskipWasmBuild=true --tests 'com.livteam.jsoninja.services.typeConversion.TypeConversionWasmIntegration*'`, the integrated checkout, and every concrete Acceptance Criterion; Expected: exit 0 for each required command plus the stated per-case outcomes. Record missing capabilities as pending and use not-ready for unmet mandatory behavior instead of fabricating success.
- Ends when:
  - [ ] Every required command and case has a recorded result or an explicit unresolved prerequisite with an owner.
  - [ ] The final source stamp matches the code that produced the evidence, and the status accurately reflects whole-child acceptance.
- Handoff: The parent and child 10, child 11, child 16 receive `docs/briefs/evidence/json-integrity-09.md` (proposed) with the complete minimum format above. Only ready/no-change records with no unmet prerequisites authorize dependent work; the parent alone updates its Child Briefs checklist.
- Replan when: Verification fails or required proof is unavailable. Mark not-ready, stop dependent starts, return the exact failed input/result to the parent owner, activate bounded correction plus re-verification, and recalculate affected waves/handoffs before resuming. Never call a failing or unobserved behavior complete.

## Side Effect Checkpoints

- [ ] Language synchronization avoids recursive submissions, and all child scopes/listeners are disposed.
- [ ] The existing conversion results for valid settled input are unchanged.

## Acceptance Criteria

- [ ] The previous preview cannot be inserted or copied during pending, invalid, blank, or error states in either conversion direction.
- [ ] Only a result matching source, direction, language, and options enables copy/insert; final consumption verifies that identity again.
- [ ] Invalidating input while a request is running cannot resurrect old text, including closing/reopening the dialog and switching tabs.
- [ ] JSON→type remains debounced at 300ms and type→JSON at 500ms; large-input validation executes off EDT with recorded measurements and preserved validation messages.
- [ ] All stages and side-effect checkpoints have evidence at `docs/briefs/evidence/json-integrity-09.md` (proposed); required commands pass on the final source state, unresolved mandatory checks are absent, and the parent can consume the handoff without reconstructing context.

## Open Questions

- None — scope and behavior follow the reviewed defects; bounded implementation choices are assigned to the worker.
