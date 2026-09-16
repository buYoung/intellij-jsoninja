# [fix] Reject stale background document replacements

## Work Type

fix

## Current State (As-Is)

- [confirmed] Audit anchor: HEAD `9eed77ed863f82843f9aadca0af479cf6efa0afd` inspected on 2026-09-16 with an untracked Undo/Redo test; treat the current checkout as authoritative. Evidence: repository HEAD and named source symbols below.
- [confirmed] `JsonDiffExtension.formatJsonInBackground()` returns formatted text, and `applyJsonFormatting()` writes without comparing the captured source stamp. Evidence: those methods and `scheduleJsonFormatting()`.
- [confirmed] `JsonTabContextFactory.setupJmesPathPresenter()` formats a query callback asynchronously before editor.setText without final document/request validation. Evidence: the on-search callback.
- [confirmed] `GenerateRandomJsonAction` later calls `JsoninjaPanelPresenter.setRandomJsonData()`, which resolves the current editor on completion. Evidence: action coroutine and presenter setter.

## Reproduction

- Environment: two JSON tabs and a JSON diff viewer on IC 2024.3. Races are inferred from source; record a controlled delayed-completion reproduction rather than assuming deterministic frequency.
- Start diff reformatting, then type before completion. Expected: later typing survives and an old formatted result is discarded.
- Submit query A, then query B or manually edit while A's formatting is pending. Expected: only the current authorized request may replace its original target.
- Start generation in tab A, edit A or switch to tab B before completion. Expected: B is never targeted by completion; stale generation cannot replace edits in A.

## Desired Outcome (To-Be)

- Every async write validates the original target identity, content revision, request ownership, and disposal state immediately before the write.

## Scope

### In Scope

- Resolve assigned audit items R1.2a, R1.2b, R1.2c through the named end-to-end entry points.
- Carry immutable editor/document identity and modification stamp across compute and EDT apply in the diff path. Preserve the debounce and self-update loop guards.
- Carry query request ownership through post-query formatting and final write, not just query evaluation. Reject old requests after a newer search, a manual edit, or disposal.
- Capture the generation destination at launch. Switching tabs may leave a valid unchanged original target eligible, but can never redirect output to the newly active tab; edits/disposal invalidate the original target.
- Preserve lifecycle cancellation and write-command grouping. Handle stale results as discarded work without reporting success or writing fallback output.

### Out of Scope

- [hard] Redirecting completed work to whichever tab happens to be selected.
- [deferred] Query original-source and null-result semantics are owned by child 05.
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

- `src/main/kotlin/com/livteam/jsoninja/diff/JsonDiffExtension.kt` — Start at schedule/compute/apply formatting, retaining self-update flags.
- `src/main/kotlin/com/livteam/jsoninja/ui/component/tab/JsonTabContextFactory.kt` — Trace query compute/format/apply callbacks.
- `src/main/kotlin/com/livteam/jsoninja/actions/GenerateRandomJsonAction.kt` — Capture the generation target and configuration before dispatch.
- `src/main/kotlin/com/livteam/jsoninja/ui/component/main/JsoninjaPanelPresenter.kt` — Avoid resolving a replacement target from current selection at completion.
- `src/main/kotlin/com/livteam/jsoninja/services/JsoninjaCoroutineScopeService.kt` — Reuse project and child lifetime scopes.
- `src/test/kotlin/com/livteam/jsoninja/services/JsonDiffServiceTest.kt` — Existing diff regression suite.
- `docs/briefs/evidence/json-integrity-04.md` (proposed) — Durable Baseline, Implementation, and Acceptance handoff record for this child.
- `docs/briefs/2026-09-16-briefset-json-integrity.md` (proposed) — Parent owns cross-child order, conflict handling, and global acceptance.
- `docs/coroutine-threading-standard.md` — Apply the canonical threading and cancellation contract.

## Execution Plan

### Stage 1 — Pin the behavior and contract

- Starts when: Read `docs/briefs/evidence/json-integrity-01.md` (proposed), `docs/briefs/evidence/json-integrity-03.md` (proposed). Each predecessor must have an Acceptance section with ready/no-change status, source stamp matching the integrated code, completed required cases, and no unresolved prerequisite. Stop and return to the parent if any is missing, stale, or not-ready.
- Work: Trace callers through intermediate layers to the final consumer. Enumerate the Reproduction cases, record observed versus expected behavior, and separate source-confirmed structure from unexecuted runtime predictions. Inventory relevant public APIs/options and existing verification coverage before editing.
- No-op when: Every whole-child Acceptance Criterion and Side Effect Checkpoint is already supported by current, reproducible evidence on the integrated checkout; source appearance or passing unrelated tests alone is insufficient.
- No-op handoff: Write the complete Acceptance evidence to `docs/briefs/evidence/json-integrity-04.md` (proposed) with status no-change, exact source stamp, all case results and limitations. The parent checks whole-child acceptance and lets child 05, child 16 continue; skip implementation stages only when every required criterion is met. A failed proof instead follows Replan when.
- Deliverable: `docs/briefs/evidence/json-integrity-04.md` (proposed), Baseline section with source stamp (HEAD plus dirty-file/diff identity), case IDs, inputs, expected/actual values, methods, current command results, contracts, limitations, and route: implement/no-change/replan.
- Verify: `Bounded comparison of named source symbols and recorded baseline cases`; Inputs: the Reproduction cases and each existing entry-point file above; Expected: a non-empty case inventory, one concrete result or explicit unverified label per case, and a justified route tied to the actual checkout.
- Ends when:
  - [ ] All assigned audit IDs have a traced final consumer and a specific reproduction/inspection result.
  - [ ] Existing behavior to preserve and any unavailable evidence are explicitly recorded before implementation.
- Handoff: Stage 2 receives the Baseline section of `docs/briefs/evidence/json-integrity-04.md` (proposed), including the failing cases, caller contract, and selected bounded correction.
- Replan when: A prerequisite is stale, the predicted defect cannot be established, or a public/ownership contract must expand. Stop affected successors, return evidence to the parent owner, choose a bounded correction or evidence-only route, update topology/handoffs, and re-verify before resuming; never force an edit to satisfy the plan.

### Stage 2 — Implement the bounded correction

- Starts when: Stage 1 records route implement with confirmed failing behavior or a source-proven contract defect, and all listed prerequisites are accepted.
- Work: Apply the following focused changes:
  - Carry immutable editor/document identity and modification stamp across compute and EDT apply in the diff path. Preserve the debounce and self-update loop guards.
  - Carry query request ownership through post-query formatting and final write, not just query evaluation. Reject old requests after a newer search, a manual edit, or disposal.
  - Capture the generation destination at launch. Switching tabs may leave a valid unchanged original target eligible, but can never redirect output to the newly active tab; edits/disposal invalidate the original target.
  - Preserve lifecycle cancellation and write-command grouping. Handle stale results as discarded work without reporting success or writing fallback output.
- Deliverable: `docs/briefs/evidence/json-integrity-04.md` (proposed), Implementation section with edited files/symbols, source stamp, chosen API/contract decisions, final-consumer trace, and compile result; corresponding focused source changes remain reviewable in the working tree.
- Verify: `./gradlew compileKotlin`; Inputs: the edited Kotlin consumers and unchanged public callers in the repository root; Expected: exit 0 with no new compilation errors. Also inspect every changed value/option at its final consumer against the Stage 1 contract.
- Ends when:
  - [ ] The focused correction reaches every assigned final consumer and preserves the named contracts.
  - [ ] Compile succeeds and no source change has crossed sibling ownership or an unanswered question milestone.
- Handoff: Stage 3 receives the integrated source changes plus Implementation section of `docs/briefs/evidence/json-integrity-04.md` (proposed).
- Replan when: The correction needs a new dependency/API, shared writer, unsupported platform behavior, or unresolved user-owned policy beyond the bounded choices. Stop affected successors and return the concrete evidence/change proposal to the parent owner to revise scope/order and re-verify. Keep unrelated ready children available.

### Stage 3 — Verify behavior and publish the handoff record

- Starts when: Stage 2 provides the compiling correction, or Stage 1 proves the complete no-change route and only evidence finalization remains.
- Work: Run the existing commands below and exercise every whole-child acceptance case and side-effect checkpoint. Record exact inputs, expected/actual results, tool/IDE versions, cwd, exit status, and limitations. Native/manual checks require an authorized execution context; do not substitute source inspection for an unobserved user interaction.
- Deliverable: `docs/briefs/evidence/json-integrity-04.md` (proposed), Acceptance section with source stamp, assigned audit IDs, per-case evidence, command/cwd/exit results, artifact paths/hashes where relevant, side-effect results, residuals, and status ready/no-change/not-ready. Readiness requires all mandatory behavior criteria; CI external observations are explicitly separated as stated in that child's criteria.
- Verify: `Bounded inspection of the command and behavior evidence matrix`; Inputs: root commands `./gradlew compileKotlin`; then `./gradlew test -PskipWasmBuild=true --tests 'com.livteam.jsoninja.services.JsonDiffServiceTest' --tests 'com.livteam.jsoninja.actions.ShowJsonDiffActionTest' --tests 'com.livteam.jsoninja.ui.component.editor.JsonEditorUndoRedoTest'`, the integrated checkout, and every concrete Acceptance Criterion; Expected: exit 0 for each required command plus the stated per-case outcomes. Record missing capabilities as pending and use not-ready for unmet mandatory behavior instead of fabricating success.
- Ends when:
  - [ ] Every required command and case has a recorded result or an explicit unresolved prerequisite with an owner.
  - [ ] The final source stamp matches the code that produced the evidence, and the status accurately reflects whole-child acceptance.
- Handoff: The parent and child 05, child 16 receive `docs/briefs/evidence/json-integrity-04.md` (proposed) with the complete minimum format above. Only ready/no-change records with no unmet prerequisites authorize dependent work; the parent alone updates its Child Briefs checklist.
- Replan when: Verification fails or required proof is unavailable. Mark not-ready, stop dependent starts, return the exact failed input/result to the parent owner, activate bounded correction plus re-verification, and recalculate affected waves/handoffs before resuming. Never call a failing or unobserved behavior complete.

## Side Effect Checkpoints

- [ ] The query input/source bookkeeping contract is handed explicitly to child 05, including whether a write is a query result, source edit, or discarded operation.
- [ ] No caller-provided cancellation signal or sort/configuration override is replaced.

## Acceptance Criteria

- [ ] For each of the three routes, the recorded delayed-completion scenario preserves newer document text and never writes to another tab/editor.
- [ ] A non-stale request still applies once to its captured target and is reversible through normal Undo/Redo.
- [ ] Rapid repeated requests, closing the original tab, and disposing the project cause no post-disposal writes; canceled work cannot re-enable an obsolete completion.
- [ ] Diff self-update flags are cleared on every terminal path, and no formatter recursion is introduced.
- [ ] All stages and side-effect checkpoints have evidence at `docs/briefs/evidence/json-integrity-04.md` (proposed); required commands pass on the final source state, unresolved mandatory checks are absent, and the parent can consume the handoff without reconstructing context.

## Open Questions

- None — scope and behavior follow the reviewed defects; bounded implementation choices are assigned to the worker.
