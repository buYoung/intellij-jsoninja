# [fix] Close only diff UI owned by the tutorial

## Work Type

fix

## Current State (As-Is)

- [confirmed] Audit anchor: HEAD `9eed77ed863f82843f9aadca0af479cf6efa0afd` inspected on 2026-09-16 with an untracked Undo/Redo test; treat the current checkout as authoritative. Evidence: repository HEAD and named source symbols below.
- [confirmed] `OnboardingStep8DiffTooltipController.closeDiffWindow()` falls back to finding a sort action button across visible windows, then disposes its Window ancestor. Evidence: closeDiffWindow/findSortKeysActionButton/findWindowAncestor.
- [confirmed] The tutorial calls this cleanup on step changes and disposal; sort actions also occur in ordinary JSON diffs. Evidence: maybeCloseDiffWindow/dispose and JsonDiffService.createDiffRequest.

## Reproduction

- Environment: authorized IDE session with an unrelated JSON diff already open and the onboarding tutorial. The review established a dangerous source path but did not execute window disposal.
- Advance past the tutorial diff step or close the tutorial before it owns a diff window. Expected: unrelated diff windows and the main IDE frame remain open.
- Create a tutorial-owned diff, advance/close, then repeat cleanup. Expected: only the owned disposable closes once; borrowing an existing host does not grant ownership.

## Desired Outcome (To-Be)

- Tutorial cleanup operates only on the resource it created, with explicit disposal ownership.

## Scope

### In Scope

- Resolve assigned audit items R1.3 through the named end-to-end entry points.
- Replace global sort-button/window searches as a closure mechanism with an explicitly owned tutorial resource/handle.
- Distinguish created resources from borrowed or absent hosts. Idempotent disposal may close only the former and may never dispose the main IDE Window.
- Use supported platform disposal/close APIs on EDT; verify dialog and editor-tab diff host behavior on the minimum platform before choosing the ownership mechanism.

### Out of Scope

- [hard] Closing unrelated IDE windows or changing onboarding completion state as a workaround.
- [hard] Implementing sibling-owned findings or changing parent dependencies without first replanning the parent.

## Constraints

- Evidence stamps identify the tested revision and changed contract files; they do not require whole-worktree equality after unrelated siblings land. A successor records which predecessor file/contract hashes are unchanged, or which later correction re-verified the changed contract. Re-run only affected proof after new changes; final integration records that chain instead of rewriting historical results.
- Use `/Users/buyong/workspace/private/json-helper2` as the working directory for root commands. Before each verification command, announce the exact command and working directory. Baseline after Kotlin changes is `./gradlew compileKotlin`.
- Follow `docs/coroutine-threading-standard.md`: lifecycle child scopes, Default for CPU, IO for network/files, EDT for UI, modal context where needed, EDT WriteCommandAction for undoable mutations, and read actions for off-EDT platform reads. Preserve cancellation and caller-supplied options.
- Verify any new platform API against the minimum IC 2024.3 / build 243 environment and JVM 17 before using it. Preserve `JSONINJA_EDITOR_KEY`, settings enum/persistence keys, localization, registration, large-file thresholds, and disposal ownership where touched.
- Preserve unrelated dirty work, including `.codemap`, `.antigravitycli`, existing briefs, and the untracked Undo/Redo test. Do not stage, commit, publish, or run external mutations as part of implementation verification.
- Except child 01's explicitly authorized Undo/Redo tests, do not add or modify test cases/files, lint rules, formatter configuration, or test automation without a further explicit request. Use existing tests and the concrete bounded manual inspections/scenarios below. A passing existing suite does not substitute for a missing behavior observation.
- Record limitations honestly. Native UI control was not authorized in the audit session; no task may bypass that denial. Missing UI, compiler, network, or CI evidence stays pending, and the affected behavior is not accepted until an authorized observation or appropriate existing proof supplies it.
- Native IntelliJ control was denied in the audit session. Do not retry or bypass that denial; retain native window-lifetime checks as pending until an authorized IDE session/user observation supplies evidence.

## Related Files / Entry Points

- `src/main/kotlin/com/livteam/jsoninja/ui/onboarding/OnboardingStep8DiffTooltipController.kt` — Start at cleanup ownership and global-window fallback.
- `src/main/kotlin/com/livteam/jsoninja/ui/onboarding/OnboardingTutorialDialogPresenter.kt` — Trace step transitions and dispose into controller cleanup.
- `src/main/kotlin/com/livteam/jsoninja/services/JsonDiffService.kt` — Identify supported diff request/host ownership APIs.
- `src/main/kotlin/com/livteam/jsoninja/diff/JsonDiffKeys.kt` — Preserve existing JSON diff markers and sort semantics.
- `docs/briefs/evidence/json-integrity-14.md` (proposed) — Durable Baseline, Implementation, and Acceptance handoff record for this child.
- `docs/briefs/2026-09-16-briefset-json-integrity.md` (proposed) — Parent owns cross-child order, conflict handling, and global acceptance.
- `docs/coroutine-threading-standard.md` — Apply the canonical threading and cancellation contract.

## Execution Plan

### Stage 1 — Pin the behavior and contract

- Starts when: The current checkout and named entry points are available.
- Work: Trace callers through intermediate layers to the final consumer. Enumerate the Reproduction cases, record observed versus expected behavior, and separate source-confirmed structure from unexecuted runtime predictions. Inventory relevant public APIs/options and existing verification coverage before editing.
- No-op when: Every whole-child Acceptance Criterion and Side Effect Checkpoint is already supported by current, reproducible evidence on the integrated checkout; source appearance or passing unrelated tests alone is insufficient.
- No-op handoff: Write the complete Acceptance evidence to `docs/briefs/evidence/json-integrity-14.md` (proposed) with status no-change, exact source stamp, all case results and limitations. The parent checks whole-child acceptance and lets child 16 continue; skip implementation stages only when every required criterion is met. A failed proof instead follows Replan when.
- Deliverable: `docs/briefs/evidence/json-integrity-14.md` (proposed), Baseline section with source stamp (HEAD plus dirty-file/diff identity), case IDs, inputs, expected/actual values, methods, current command results, contracts, limitations, and route: implement/no-change/replan.
- Verify: `Bounded comparison of named source symbols and recorded baseline cases`; Inputs: the Reproduction cases and each existing entry-point file above; Expected: a non-empty case inventory, one concrete result or explicit unverified label per case, and a justified route tied to the actual checkout.
- Ends when:
  - [x] All assigned audit IDs have a traced final consumer and a specific reproduction/inspection result.
  - [x] Existing behavior to preserve and any unavailable evidence are explicitly recorded before implementation.
- Handoff: Stage 2 receives the Baseline section of `docs/briefs/evidence/json-integrity-14.md` (proposed), including the failing cases, caller contract, and selected bounded correction.
- Replan when: A prerequisite is stale, the predicted defect cannot be established, or a public/ownership contract must expand. Stop affected successors, return evidence to the parent owner, choose a bounded correction or evidence-only route, update topology/handoffs, and re-verify before resuming; never force an edit to satisfy the plan.

### Stage 2 — Implement the bounded correction

- Starts when: Stage 1 records route implement with confirmed failing behavior or a source-proven contract defect, and all listed prerequisites are accepted.
- Work: Apply the following focused changes:
  - Replace global sort-button/window searches as a closure mechanism with an explicitly owned tutorial resource/handle.
  - Distinguish created resources from borrowed or absent hosts. Idempotent disposal may close only the former and may never dispose the main IDE Window.
  - Use supported platform disposal/close APIs on EDT; verify dialog and editor-tab diff host behavior on the minimum platform before choosing the ownership mechanism.
- Deliverable: `docs/briefs/evidence/json-integrity-14.md` (proposed), Implementation section with edited files/symbols, source stamp, chosen API/contract decisions, final-consumer trace, and compile result; corresponding focused source changes remain reviewable in the working tree.
- Verify: `./gradlew compileKotlin`; Inputs: the edited Kotlin consumers and unchanged public callers in the repository root; Expected: exit 0 with no new compilation errors. Also inspect every changed value/option at its final consumer against the Stage 1 contract.
- Ends when:
  - [x] The focused correction reaches every assigned final consumer and preserves the named contracts.
  - [x] Compile succeeds and no source change has crossed sibling ownership or an unanswered question milestone.
- Handoff: Stage 3 receives the integrated source changes plus Implementation section of `docs/briefs/evidence/json-integrity-14.md` (proposed).
- Replan when: The correction needs a new dependency/API, shared writer, unsupported platform behavior, or unresolved user-owned policy beyond the bounded choices. Stop affected successors and return the concrete evidence/change proposal to the parent owner to revise scope/order and re-verify. Keep unrelated ready children available.

### Stage 3 — Verify behavior and publish the handoff record

- Starts when: Stage 2 provides the compiling correction, or Stage 1 proves the complete no-change route and only evidence finalization remains.
- Work: Run the existing commands below and exercise every whole-child acceptance case and side-effect checkpoint. Record exact inputs, expected/actual results, tool/IDE versions, cwd, exit status, and limitations. Native/manual checks require an authorized execution context; do not substitute source inspection for an unobserved user interaction.
- Deliverable: `docs/briefs/evidence/json-integrity-14.md` (proposed), Acceptance section with source stamp, assigned audit IDs, per-case evidence, command/cwd/exit results, artifact paths/hashes where relevant, side-effect results, residuals, and status ready/no-change/not-ready. Readiness requires all mandatory behavior criteria; CI external observations are explicitly separated as stated in that child's criteria.
- Verify: `Bounded inspection of the command and behavior evidence matrix`; Inputs: root commands `./gradlew compileKotlin`; then `./gradlew test -PskipWasmBuild=true --tests 'com.livteam.jsoninja.services.JsonDiffServiceTest' --tests 'com.livteam.jsoninja.actions.ShowJsonDiffActionTest'`, the integrated checkout, and every concrete Acceptance Criterion; Expected: exit 0 for each required command plus the stated per-case outcomes. Record missing capabilities as pending and use not-ready for unmet mandatory behavior instead of fabricating success.
- Ends when:
  - [x] Every required command and case has a recorded result or an explicit unresolved prerequisite with an owner.
  - [x] The final source stamp matches the code that produced the evidence, and the status accurately reflects whole-child acceptance.
- Handoff: The parent and child 16 receive `docs/briefs/evidence/json-integrity-14.md` (proposed) with the complete minimum format above. Only ready/no-change records with no unmet prerequisites authorize dependent work; the parent alone updates its Child Briefs checklist.
- Replan when: Verification fails or required proof is unavailable. Mark not-ready, stop dependent starts, return the exact failed input/result to the parent owner, activate bounded correction plus re-verification, and recalculate affected waves/handoffs before resuming. Never call a failing or unobserved behavior complete.

## Side Effect Checkpoints

- [x] Welcome/tutorial persistence and step numbering remain unchanged.
- [x] Listeners, tooltips, alarms, and tutorial-owned resources are released without global UI scans for disposal.

## Acceptance Criteria

- [x] Closing or advancing the tutorial without an owned diff leaves unrelated diffs and the main IDE frame alive.
- [x] A tutorial-created diff is cleaned up exactly once, and repeated dispose/step changes are safe.
- [x] Dialog and editor-tab hosts preserve normal sort and JSON diff behavior; ownership is recorded rather than inferred from a button label or reflection.
- [x] All stages and side-effect checkpoints have evidence at `docs/briefs/evidence/json-integrity-14.md` (proposed); required commands pass on the final source state, unresolved mandatory checks are absent, and the parent can consume the handoff without reconstructing context.

## 후속 수용 기록

- 사용자가 후속 대화에서 “온보딩 확인됐어”라고 확인 완료를 알렸다. [수용 기록](evidence/json-integrity-14.md)의 기존 컴파일/테스트/소스 확인과 사용자 확인을 근거로 위 체크리스트를 완료 처리한다. 네이티브 UI를 에이전트가 직접 검증한 것으로 보고하지 않는다.

## Open Questions

- None — scope and behavior follow the reviewed defects; bounded implementation choices are assigned to the worker.
