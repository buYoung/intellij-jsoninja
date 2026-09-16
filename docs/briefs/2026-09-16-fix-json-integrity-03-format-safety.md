# [fix] Preserve strings and combined formatting options

## Work Type

fix

## Current State (As-Is)

- [confirmed] Audit anchor: HEAD `9eed77ed863f82843f9aadca0af479cf6efa0afd` inspected on 2026-09-16 with an untracked Undo/Redo test; treat the current checkout as authoritative. Evidence: repository HEAD and named source symbols below.
- [confirmed] `JsoninjaPanelPresenter.formatJson()` and `BaseEditorJsonAction` can fully unescape input before formatting. Evidence: `containsEscapeCharacters` and `unescapeBeforeTransform` branches.
- [confirmed] `JsonFormatterService.unescapeBeautifiedJson()` replaces escaped slash/quote sequences based on `isBeautifiedJson()` layout heuristics. Evidence: those methods and `fullyUnescapeJson()`.
- [confirmed] `JsonFormatterService.formatJson()` substitutes PRETTIFY_SORTED when sorting, while `JsonFormatState.usesCompactArrays()` only recognizes PRETTIFY_COMPACT. Evidence: the state override and enum helper.
- [confirmed] `BaseEditorJsonAction` performs its pre-unescape before the background transform. Evidence: action execution order; responsiveness impact has not been measured.

## Reproduction

- Environment: tool-window formatter and selection-aware editor actions on IC 2024.3. Failures are source-backed predictions; record the actual before/after values for each route.
- Pretty-print valid multiline JSON containing `"path": "C:\\temp"` and `"quote": "a\"b"`. Expected: decoded string values remain the original path and quote, never tabs or malformed strings.
- Set compact arrays together with key sorting on `{"z":[1,2],"a":0}`. Expected: keys sorted and array compact at the same time; repeat with sorting override and UGLIFY.
- Use a generated approximately 1 MiB JSON string document and capture EDT samples around transform submission. Record size, machine, IDE build, and timing; no speed threshold is assumed.

## Desired Outcome (To-Be)

- Formatting preserves valid JSON string values, unwraps only genuinely encoded input, and combines sort/compact choices without changing persisted settings.
- CPU-heavy unescape/validation work in the transform path follows the coroutine threading standard rather than running before background dispatch.

## Scope

### In Scope

- Resolve assigned audit items R1.1, R2.7, M1a through the named end-to-end entry points.
- Validate the original input before any unwrapping. Valid JSON/JSON5 string escape sequences are data, including paths and quotes. Decode an outer encoding layer only when it is actually required, and stop once valid input is reached.
- Preserve the original source on any invalid transformation or formatter failure, including panel and editor-action callers; do not use already-corrupted intermediate text as fallback.
- Make effective sorting and compact-array behavior composable without renaming persisted JsonFormatState values or overriding caller-supplied sort options. Preserve intentional UGLIFY behavior.
- Move expensive pre-unescape to the existing background transform flow. Retain EDT writes, selected-range identity, document stamp checks, cancellation propagation, and one Undo command; record before/after EDT observations without an invented performance SLA.

### Out of Scope

- [hard] Changing enum persistence names or adding a new formatting preference.
- [deferred] A quantitative responsiveness SLA and broader profiling campaign require measured baselines and a separate product target.
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

- `src/main/kotlin/com/livteam/jsoninja/services/JsonFormatterService.kt` — Start at `fullyUnescapeJson`, `unescapeBeautifiedJson`, and `formatJson` state handling.
- `src/main/kotlin/com/livteam/jsoninja/ui/component/main/JsoninjaPanelPresenter.kt` — Trace tool-window input and replacement fallback.
- `src/main/kotlin/com/livteam/jsoninja/actions/editor/BaseEditorJsonAction.kt` — Trace selections, pre-unescape, background work, and stale-write protection.
- `src/main/kotlin/com/livteam/jsoninja/model/JsonFormatState.kt` — Preserve enum names and existing UGLIFY override semantics.
- `src/test/kotlin/com/livteam/jsoninja/services/JsonFormatterServiceTest.kt` — Existing formatter regression suite.
- `docs/briefs/evidence/json-integrity-03.md` (proposed) — Durable Baseline, Implementation, and Acceptance handoff record for this child.
- `docs/briefs/2026-09-16-briefset-json-integrity.md` (proposed) — Parent owns cross-child order, conflict handling, and global acceptance.
- `docs/coroutine-threading-standard.md` — Apply the canonical threading and cancellation contract.

## Execution Plan

### Stage 1 — Pin the behavior and contract

- Starts when: Read `docs/briefs/evidence/json-integrity-01.md` (proposed), `docs/briefs/evidence/json-integrity-02.md` (proposed). Each predecessor must have an Acceptance section with ready/no-change status, source stamp matching the integrated code, completed required cases, and no unresolved prerequisite. Stop and return to the parent if any is missing, stale, or not-ready.
- Work: Trace callers through intermediate layers to the final consumer. Enumerate the Reproduction cases, record observed versus expected behavior, and separate source-confirmed structure from unexecuted runtime predictions. Inventory relevant public APIs/options and existing verification coverage before editing.
- No-op when: Every whole-child Acceptance Criterion and Side Effect Checkpoint is already supported by current, reproducible evidence on the integrated checkout; source appearance or passing unrelated tests alone is insufficient.
- No-op handoff: Write the complete Acceptance evidence to `docs/briefs/evidence/json-integrity-03.md` (proposed) with status no-change, exact source stamp, all case results and limitations. The parent checks whole-child acceptance and lets child 04, child 16 continue; skip implementation stages only when every required criterion is met. A failed proof instead follows Replan when.
- Deliverable: `docs/briefs/evidence/json-integrity-03.md` (proposed), Baseline section with source stamp (HEAD plus dirty-file/diff identity), case IDs, inputs, expected/actual values, methods, current command results, contracts, limitations, and route: implement/no-change/replan.
- Verify: `Bounded comparison of named source symbols and recorded baseline cases`; Inputs: the Reproduction cases and each existing entry-point file above; Expected: a non-empty case inventory, one concrete result or explicit unverified label per case, and a justified route tied to the actual checkout.
- Ends when:
  - [ ] All assigned audit IDs have a traced final consumer and a specific reproduction/inspection result.
  - [ ] Existing behavior to preserve and any unavailable evidence are explicitly recorded before implementation.
- Handoff: Stage 2 receives the Baseline section of `docs/briefs/evidence/json-integrity-03.md` (proposed), including the failing cases, caller contract, and selected bounded correction.
- Replan when: A prerequisite is stale, the predicted defect cannot be established, or a public/ownership contract must expand. Stop affected successors, return evidence to the parent owner, choose a bounded correction or evidence-only route, update topology/handoffs, and re-verify before resuming; never force an edit to satisfy the plan.

### Stage 2 — Implement the bounded correction

- Starts when: Stage 1 records route implement with confirmed failing behavior or a source-proven contract defect, and all listed prerequisites are accepted.
- Work: Apply the following focused changes:
  - Validate the original input before any unwrapping. Valid JSON/JSON5 string escape sequences are data, including paths and quotes. Decode an outer encoding layer only when it is actually required, and stop once valid input is reached.
  - Preserve the original source on any invalid transformation or formatter failure, including panel and editor-action callers; do not use already-corrupted intermediate text as fallback.
  - Make effective sorting and compact-array behavior composable without renaming persisted JsonFormatState values or overriding caller-supplied sort options. Preserve intentional UGLIFY behavior.
  - Move expensive pre-unescape to the existing background transform flow. Retain EDT writes, selected-range identity, document stamp checks, cancellation propagation, and one Undo command; record before/after EDT observations without an invented performance SLA.
- Deliverable: `docs/briefs/evidence/json-integrity-03.md` (proposed), Implementation section with edited files/symbols, source stamp, chosen API/contract decisions, final-consumer trace, and compile result; corresponding focused source changes remain reviewable in the working tree.
- Verify: `./gradlew compileKotlin`; Inputs: the edited Kotlin consumers and unchanged public callers in the repository root; Expected: exit 0 with no new compilation errors. Also inspect every changed value/option at its final consumer against the Stage 1 contract.
- Ends when:
  - [ ] The focused correction reaches every assigned final consumer and preserves the named contracts.
  - [ ] Compile succeeds and no source change has crossed sibling ownership or an unanswered question milestone.
- Handoff: Stage 3 receives the integrated source changes plus Implementation section of `docs/briefs/evidence/json-integrity-03.md` (proposed).
- Replan when: The correction needs a new dependency/API, shared writer, unsupported platform behavior, or unresolved user-owned policy beyond the bounded choices. Stop affected successors and return the concrete evidence/change proposal to the parent owner to revise scope/order and re-verify. Keep unrelated ready children available.

### Stage 3 — Verify behavior and publish the handoff record

- Starts when: Stage 2 provides the compiling correction, or Stage 1 proves the complete no-change route and only evidence finalization remains.
- Work: Run the existing commands below and exercise every whole-child acceptance case and side-effect checkpoint. Record exact inputs, expected/actual results, tool/IDE versions, cwd, exit status, and limitations. Native/manual checks require an authorized execution context; do not substitute source inspection for an unobserved user interaction.
- Deliverable: `docs/briefs/evidence/json-integrity-03.md` (proposed), Acceptance section with source stamp, assigned audit IDs, per-case evidence, command/cwd/exit results, artifact paths/hashes where relevant, side-effect results, residuals, and status ready/no-change/not-ready. Readiness requires all mandatory behavior criteria; CI external observations are explicitly separated as stated in that child's criteria.
- Verify: `Bounded inspection of the command and behavior evidence matrix`; Inputs: root commands `./gradlew compileKotlin`; then `./gradlew test -PskipWasmBuild=true --tests 'com.livteam.jsoninja.services.JsonFormatterServiceTest' --tests 'com.livteam.jsoninja.ui.component.editor.JsonEditorUndoRedoTest'`, the integrated checkout, and every concrete Acceptance Criterion; Expected: exit 0 for each required command plus the stated per-case outcomes. Record missing capabilities as pending and use not-ready for unmet mandatory behavior instead of fabricating success.
- Ends when:
  - [ ] Every required command and case has a recorded result or an explicit unresolved prerequisite with an owner.
  - [ ] The final source stamp matches the code that produced the evidence, and the status accurately reflects whole-child acceptance.
- Handoff: The parent and child 04, child 16 receive `docs/briefs/evidence/json-integrity-03.md` (proposed) with the complete minimum format above. Only ready/no-change records with no unmet prerequisites authorize dependent work; the parent alone updates its Child Briefs checklist.
- Replan when: Verification fails or required proof is unavailable. Mark not-ready, stop dependent starts, return the exact failed input/result to the parent owner, activate bounded correction plus re-verification, and recalculate affected waves/handoffs before resuming. Never call a failing or unobserved behavior complete.

## Side Effect Checkpoints

- [ ] Placeholder restoration, selection-only updates, cache-key behavior, and invalid/blank input handling still work.
- [ ] The input-fidelity evidence from child 02 remains valid after formatter changes.

## Acceptance Criteria

- [ ] Valid escaped path, quote, backslash, newline, and Unicode string values survive tool-window and selection-aware editor formatting unchanged in meaning.
- [ ] Encoded outer JSON is decoded only to the first valid document; repeated formatting is idempotent in value, and failure leaves the initial source intact.
- [ ] Compact arrays and sorted keys both apply to the sample object, including caller sorting overrides; UGLIFY and ordinary pretty modes retain documented existing behavior.
- [ ] The approximately 1 MiB case records pre/post EDT evidence and a source trace placing CPU transform work on Default; no numeric performance claim is made without measurements.
- [ ] Undo/Redo reverses and reapplies the complete formatter result once, and concurrent edits cause the stale result to be discarded.
- [ ] All stages and side-effect checkpoints have evidence at `docs/briefs/evidence/json-integrity-03.md` (proposed); required commands pass on the final source state, unresolved mandatory checks are absent, and the parent can consume the handoff without reconstructing context.

## Open Questions

- None — scope and behavior follow the reviewed defects; bounded implementation choices are assigned to the worker.
