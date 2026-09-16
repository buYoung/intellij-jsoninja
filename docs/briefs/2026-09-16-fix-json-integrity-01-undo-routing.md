# [fix] Route Undo and Redo to the JSON editor

## Work Type

fix

## Current State (As-Is)

- [confirmed] Audit anchor: HEAD `9eed77ed863f82843f9aadca0af479cf6efa0afd` inspected on 2026-09-16 with an untracked Undo/Redo test; treat the current checkout as authoritative. Evidence: repository HEAD and named source symbols below.
- [confirmed] The existing `JsonEditorUndoRedoTest` has eight cases: four direct UndoManager controls pass, while three action-dispatch cases report `FILE_EDITOR=null` and one dispatch undoes the parent editor. Evidence: `testTypingUndoRedoThroughEditorActions`, `testReplacementUndoRedoThroughEditorActions`, `testRedoThroughEditorActionAfterDirectUndo`, and `testUndoThroughEditorActionDoesNotChangeParentEditor` in the test entry point, plus the recorded 8/4/0/0 tests/failures/errors/skipped XML result.
- [confirmed] `EditorTextFieldFactory.createConfiguredField()` marks editors supplementary. IntelliJ 2024.3 `BasicUiDataRule` only synthesizes FILE_EDITOR for a non-supplementary editor when FILE_EDITOR is absent; `UndoRedoAction` consumes FILE_EDITOR. Evidence: factory implementation and the locally cached 2024.3 source JAR, `com/intellij/ide/impl/dataRules/BasicUiDataRule.kt` and `UndoRedoAction.java`.
- [confirmed] The test builds the production UI data snapshot using `IdeUiService.createUiDataContext(editor.contentComponent)`, because the headless fixture DataManager ignores components. Evidence: `JsonEditorUndoRedoTest.performEditorAction()`; changing this back would invalidate the reproduction.

## Reproduction

- Environment: IC 2024.3 platform fixture, inspected checkout, JVM toolchain 17. Run the targeted command below from the repository root. Observed once in the recorded final run: eight tests, four failures, zero errors/skips; rerun to pin the current baseline.
- Type JSON in an embedded editor, dispatch `$Undo` then `$Redo`; repeat after `JsonEditorView.setText()`. Expected: the focused JSON document alone follows its history. Observed: normal actions disabled without a FILE_EDITOR, or a supplied parent FILE_EDITOR consumes Undo.
- Control: direct UndoManager restores typing/replacement, a new edit clears redo, and placeholder normalization joins the typing history. These passing controls must remain passing.

## Desired Outcome (To-Be)

- Standard IDE Undo/Redo actions resolve the focused JSON document, including when an enclosing context supplies another file editor.

## Scope

### In Scope

- Resolve assigned audit items U1 through the named end-to-end entry points.
- Provide the owned editor as FILE_EDITOR at the narrow component data boundary; verify the minimum-platform UiDataProvider/API behavior before choosing the exact implementation. Preserve supplementary behavior where unrelated editor integrations need it.
- Do not rely only on clearing SUPPLEMENTARY_KEY: an inherited FILE_EDITOR may still win. Prove focus routing with both absent and conflicting parent contexts.
- Keep write-command grouping, placeholder normalization, editor ownership, disposal, viewer behavior, and host document isolation intact. Add or adjust only necessary Undo/Redo cases under the user's explicit test request.

### Out of Scope

- [hard] Replacing platform UndoManager with custom history or changing ordinary file-editor history.
- [hard] Treating `isEmbeddedIntoDialogWrapper` alone as the proven Undo cause.
- [hard] Implementing sibling-owned findings or changing parent dependencies without first replanning the parent.

## Constraints

- Evidence stamps identify the tested revision and changed contract files; they do not require whole-worktree equality after unrelated siblings land. A successor records which predecessor file/contract hashes are unchanged, or which later correction re-verified the changed contract. Re-run only affected proof after new changes; final integration records that chain instead of rewriting historical results.
- Use `/Users/buyong/workspace/private/json-helper2` as the working directory for root commands. Before each verification command, announce the exact command and working directory. Baseline after Kotlin changes is `./gradlew compileKotlin`.
- Follow `docs/coroutine-threading-standard.md`: lifecycle child scopes, Default for CPU, IO for network/files, EDT for UI, modal context where needed, EDT WriteCommandAction for undoable mutations, and read actions for off-EDT platform reads. Preserve cancellation and caller-supplied options.
- Verify any new platform API against the minimum IC 2024.3 / build 243 environment and JVM 17 before using it. Preserve `JSONINJA_EDITOR_KEY`, settings enum/persistence keys, localization, registration, large-file thresholds, and disposal ownership where touched.
- Preserve unrelated dirty work, including `.codemap`, `.antigravitycli`, existing briefs, and the untracked Undo/Redo test. Do not stage, commit, publish, or run external mutations as part of implementation verification.
- Except child 01's explicitly authorized Undo/Redo tests, do not add or modify test cases/files, lint rules, formatter configuration, or test automation without a further explicit request. Use existing tests and the concrete bounded manual inspections/scenarios below. A passing existing suite does not substitute for a missing behavior observation.
- Record limitations honestly. Native UI control was not authorized in the audit session; no task may bypass that denial. Missing UI, compiler, network, or CI evidence stays pending, and the affected behavior is not accepted until an authorized observation or appropriate existing proof supplies it.
- Test creation/modification is explicitly authorized for Undo/Redo; preserve the currently untracked test file rather than replacing the user's work. Native IntelliJ control was denied in the audit session: do not retry or bypass that denial. Record native UI checks as pending until an authorized session or user observation is available.

## Related Files / Entry Points

- `src/main/kotlin/com/livteam/jsoninja/ui/component/editor/EditorTextFieldFactory.kt` — Start at `createConfiguredField()` and trace every JSON/code/plain-text field caller.
- `src/main/kotlin/com/livteam/jsoninja/ui/component/editor/JsonEditorView.kt` — Trace `editor`, `setText()`, and the component data-provider/lifecycle boundary.
- `src/main/kotlin/com/livteam/jsoninja/ui/component/editor/JsonEditorTextView.kt` — Trace `createJsonEditor()` and its `customizeEditor` callback between JsonEditorView and EditorTextFieldFactory.
- `src/main/kotlin/com/livteam/jsoninja/ui/component/editor/JsonEditorTextPresenter.kt` — Trace write-command replacement and placeholder normalization ownership.
- `src/main/kotlin/com/livteam/jsoninja/ui/component/editor/FoldingAwareEditorTextField.kt` — Preserve folding and editor creation/disposal behavior.
- `src/test/kotlin/com/livteam/jsoninja/ui/component/editor/JsonEditorUndoRedoTest.kt` — Retain all eight cases and the real UI data snapshot helper.
- `src/test/kotlin/com/livteam/jsoninja/ui/component/editor/FoldingAwareEditorTextFieldTest.kt` — Existing folding regression target.
- `docs/briefs/evidence/json-integrity-01.md` (proposed) — Durable Baseline, Implementation, and Acceptance handoff record for this child.
- `docs/briefs/2026-09-16-briefset-json-integrity.md` (proposed) — Parent owns cross-child order, conflict handling, and global acceptance.
- `docs/coroutine-threading-standard.md` — Apply the canonical threading and cancellation contract.

## Execution Plan

### Stage 1 — Pin the behavior and contract

- Starts when: The current checkout and named entry points are available.
- Work: Trace callers through intermediate layers to the final consumer. Enumerate the Reproduction cases, record observed versus expected behavior, and separate source-confirmed structure from unexecuted runtime predictions. Inventory relevant public APIs/options and existing verification coverage before editing.
- No-op when: Every whole-child Acceptance Criterion and Side Effect Checkpoint is already supported by current, reproducible evidence on the integrated checkout; source appearance or passing unrelated tests alone is insufficient.
- No-op handoff: Write the complete Acceptance evidence to `docs/briefs/evidence/json-integrity-01.md` (proposed) with status no-change, exact source stamp, all case results and limitations. The parent checks whole-child acceptance and lets child 03, child 04, child 07, child 16 continue; skip implementation stages only when every required criterion is met. A failed proof instead follows Replan when.
- Deliverable: `docs/briefs/evidence/json-integrity-01.md` (proposed), Baseline section with source stamp (HEAD plus dirty-file/diff identity), case IDs, inputs, expected/actual values, methods, current command results, contracts, limitations, and route: implement/no-change/replan.
- Verify: `Bounded comparison of named source symbols and recorded baseline cases`; Inputs: the Reproduction cases and each existing entry-point file above; Expected: a non-empty case inventory, one concrete result or explicit unverified label per case, and a justified route tied to the actual checkout.
- Ends when:
  - [ ] All assigned audit IDs have a traced final consumer and a specific reproduction/inspection result.
  - [ ] Existing behavior to preserve and any unavailable evidence are explicitly recorded before implementation.
- Handoff: Stage 2 receives the Baseline section of `docs/briefs/evidence/json-integrity-01.md` (proposed), including the failing cases, caller contract, and selected bounded correction.
- Replan when: A prerequisite is stale, the predicted defect cannot be established, or a public/ownership contract must expand. Stop affected successors, return evidence to the parent owner, choose a bounded correction or evidence-only route, update topology/handoffs, and re-verify before resuming; never force an edit to satisfy the plan.

### Stage 2 — Implement the bounded correction

- Starts when: Stage 1 records route implement with confirmed failing behavior or a source-proven contract defect, and all listed prerequisites are accepted.
- Work: Apply the following focused changes:
  - Provide the owned editor as FILE_EDITOR at the narrow component data boundary; verify the minimum-platform UiDataProvider/API behavior before choosing the exact implementation. Preserve supplementary behavior where unrelated editor integrations need it.
  - Do not rely only on clearing SUPPLEMENTARY_KEY: an inherited FILE_EDITOR may still win. Prove focus routing with both absent and conflicting parent contexts.
  - Keep write-command grouping, placeholder normalization, editor ownership, disposal, viewer behavior, and host document isolation intact. Add or adjust only necessary Undo/Redo cases under the user's explicit test request.
- Deliverable: `docs/briefs/evidence/json-integrity-01.md` (proposed), Implementation section with edited files/symbols, source stamp, chosen API/contract decisions, final-consumer trace, and compile result; corresponding focused source changes remain reviewable in the working tree.
- Verify: `./gradlew compileKotlin`; Inputs: the edited Kotlin consumers and unchanged public callers in the repository root; Expected: exit 0 with no new compilation errors. Also inspect every changed value/option at its final consumer against the Stage 1 contract.
- Ends when:
  - [ ] The focused correction reaches every assigned final consumer and preserves the named contracts.
  - [ ] Compile succeeds and no source change has crossed sibling ownership or an unanswered question milestone.
- Handoff: Stage 3 receives the integrated source changes plus Implementation section of `docs/briefs/evidence/json-integrity-01.md` (proposed).
- Replan when: The correction needs a new dependency/API, shared writer, unsupported platform behavior, or unresolved user-owned policy beyond the bounded choices. Stop affected successors and return the concrete evidence/change proposal to the parent owner to revise scope/order and re-verify. Keep unrelated ready children available.

### Stage 3 — Verify behavior and publish the handoff record

- Starts when: Stage 2 provides the compiling correction, or Stage 1 proves the complete no-change route and only evidence finalization remains.
- Work: Run the existing commands below and exercise every whole-child acceptance case and side-effect checkpoint. Record exact inputs, expected/actual results, tool/IDE versions, cwd, exit status, and limitations. Native/manual checks require an authorized execution context; do not substitute source inspection for an unobserved user interaction.
- Deliverable: `docs/briefs/evidence/json-integrity-01.md` (proposed), Acceptance section with source stamp, assigned audit IDs, per-case evidence, command/cwd/exit results, artifact paths/hashes where relevant, side-effect results, residuals, and status ready/no-change/not-ready. Readiness requires all mandatory behavior criteria; CI external observations are explicitly separated as stated in that child's criteria.
- Verify: `Bounded inspection of the command and behavior evidence matrix`; Inputs: root commands `./gradlew compileKotlin`; then `./gradlew test -PskipWasmBuild=true --tests 'com.livteam.jsoninja.ui.component.editor.JsonEditorUndoRedoTest' --tests 'com.livteam.jsoninja.ui.component.editor.FoldingAwareEditorTextFieldTest'`, the integrated checkout, and every concrete Acceptance Criterion; Expected: exit 0 for each required command plus the stated per-case outcomes. Record missing capabilities as pending and use not-ready for unmet mandatory behavior instead of fabricating success.
- Ends when:
  - [ ] Every required command and case has a recorded result or an explicit unresolved prerequisite with an owner.
  - [ ] The final source stamp matches the code that produced the evidence, and the status accurately reflects whole-child acceptance.
- Handoff: The parent and child 03, child 04, child 07, child 16 receive `docs/briefs/evidence/json-integrity-01.md` (proposed) with the complete minimum format above. Only ready/no-change records with no unmet prerequisites authorize dependent work; the parent alone updates its Child Briefs checklist.
- Replan when: Verification fails or required proof is unavailable. Mark not-ready, stop dependent starts, return the exact failed input/result to the parent owner, activate bounded correction plus re-verification, and recalculate affected waves/handoffs before resuming. Never call a failing or unobserved behavior complete.

## Side Effect Checkpoints

- [ ] Existing `FoldingAwareEditorTextFieldTest` passes and collapse/expand action ownership is unchanged.
- [ ] All factory callers retain their supplied viewer, one-line, file-type, dialog-focus, and disposal options.

## Acceptance Criteria

- [ ] The targeted eight-case class reports eight tests, zero failures/errors/skips; all original assertions remain meaningful.
- [ ] Typing and presenter replacement both undo and redo through IDE actions; a direct undo followed by IDE Redo restores the JSON text.
- [ ] With a parent editor containing `{"host":0} `, JSON Undo/Redo never removes the parent's trailing space.
- [ ] A new edit after Undo invalidates Redo, and `{"value":{{ name }}}` normalization undoes/redoes with typing within the existing 5000ms wait bound.
- [ ] Record keyboard/menu observations separately from the headless action tests. If an authorized IDE session is unavailable, explicitly leave native keyboard/dialog interaction unverified; the proven action-routing contract can still be handed to dependent code work without claiming a native UI pass.
- [ ] All stages and side-effect checkpoints have evidence at `docs/briefs/evidence/json-integrity-01.md` (proposed); required commands pass on the final source state, unresolved mandatory checks are absent, and the parent can consume the handoff without reconstructing context.

## Open Questions

- None — scope and behavior follow the reviewed defects; bounded implementation choices are assigned to the worker.
