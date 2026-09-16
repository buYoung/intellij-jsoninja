# [fix] Isolate complete WASM analysis transactions

## Work Type

fix

## Current State (As-Is)

- [confirmed] Audit anchor: HEAD `9eed77ed863f82843f9aadca0af479cf6efa0afd` inspected on 2026-09-16 with an untracked Undo/Redo test; treat the current checkout as authoritative. Evidence: repository HEAD and named source symbols below.
- [confirmed] `TreeSitterWasmRuntime.getOrCreate()` synchronizes initialization, then shares a RuntimeHandle. Evidence: the singleton runtime accessor.
- [confirmed] `TypeDeclarationAnalyzerService.analyzeSource()` performs allocate/write/analyze/read/free on that shared handle without one enclosing isolation boundary. Evidence: the request body and finally cleanup.
- [confirmed] `ConvertPreviewExecutor.submit()` cancels a previous Job without waiting for synchronous computation to stop. Evidence: cancellation plus computePreview inside Default; the inspected Chicory 1.5.1 InterpreterMachine stores mutable stack/callStack on the instance.
- [inferred] Two overlapping previews/projects can enter the same interpreter/memory transaction. Confirm by tracing overlapping analysis with distinguishable source payloads and observing ownership through allocation, error access, and free.

## Reproduction

- Environment: bundled tree-sitter WASM, Chicory 1.5.1, concurrent conversion dialogs/projects. Concurrency corruption is an inferred risk, not a reproduced crash.
- Submit a large TypeScript interface then immediately another distinct declaration, and repeat from a second dialog/project while canceling the first. Expected: each live request gets its own output, canceled results stay discarded, and buffers/errors cannot cross requests.
- Trigger malformed input and an analyzer error during contention. Expected: all owned buffers are freed and subsequent requests still work.

## Desired Outcome (To-Be)

- No two transactions concurrently mutate one WASM instance, including allocation, result/error reads, and cleanup.

## Scope

### In Scope

- Resolve assigned audit items R1.4 through the named end-to-end entry points.
- Choose whole-transaction serialization or isolated interpreter instances based on supported runtime APIs. The protected span must include allocate, source write, call, error/result read, and every free; a lock around only the exported function is insufficient.
- Keep waiting and compute off EDT. Preserve cancellation of queued requests, suppress obsolete results, and let finally cleanup finish for an already-started synchronous transaction.
- Preserve the exported WASM ABI and buffer ownership. Record the minimum-platform/runtime API evidence and any contention tradeoff; do not claim concurrency safety from canceled coroutine state alone.

### Out of Scope

- [hard] Changing the exported WASM ABI or upgrading Chicory as an unproven shortcut.
- [deferred] Conversion preview Ready/Pending UI semantics belong to child 09.
- [hard] Implementing sibling-owned findings or changing parent dependencies without first replanning the parent.

## Constraints

- Evidence stamps identify the tested revision and changed contract files; they do not require whole-worktree equality after unrelated siblings land. A successor records which predecessor file/contract hashes are unchanged, or which later correction re-verified the changed contract. Re-run only affected proof after new changes; final integration records that chain instead of rewriting historical results.
- Use `/Users/buyong/workspace/private/json-helper2` as the working directory for root commands. Before each verification command, announce the exact command and working directory. Baseline after Kotlin changes is `./gradlew compileKotlin`.
- Follow `docs/coroutine-threading-standard.md`: lifecycle child scopes, Default for CPU, IO for network/files, EDT for UI, modal context where needed, EDT WriteCommandAction for undoable mutations, and read actions for off-EDT platform reads. Preserve cancellation and caller-supplied options.
- Verify any new platform API against the minimum IC 2024.3 / build 243 environment and JVM 17 before using it. Preserve `JSONINJA_EDITOR_KEY`, settings enum/persistence keys, localization, registration, large-file thresholds, and disposal ownership where touched.
- Preserve unrelated dirty work, including `.codemap`, `.antigravitycli`, existing briefs, and the untracked Undo/Redo test. Do not stage, commit, publish, or run external mutations as part of implementation verification.
- Except child 01's explicitly authorized Undo/Redo tests, do not add or modify test cases/files, lint rules, formatter configuration, or test automation without a further explicit request. Use existing tests and the concrete bounded manual inspections/scenarios below. A passing existing suite does not substitute for a missing behavior observation.
- Record limitations honestly. Native UI control was not authorized in the audit session; no task may bypass that denial. Missing UI, compiler, network, or CI evidence stays pending, and the affected behavior is not accepted until an authorized observation or appropriate existing proof supplies it.
- If Rust source or ABI must change to establish ownership, stop successors and replan the parent with explicit artifact rebuilding and compatibility verification before expanding scope.

## Related Files / Entry Points

- `src/main/kotlin/com/livteam/jsoninja/services/treesitter/TreeSitterWasmRuntime.kt` — Start at RuntimeHandle sharing and synchronization ownership.
- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/TypeDeclarationAnalyzerService.kt` — Isolate the complete request, including finally release.
- `src/main/kotlin/com/livteam/jsoninja/services/treesitter/WasmMemoryBridge.kt` — Verify all memory reads/writes and allocation ownership.
- `src/main/kotlin/com/livteam/jsoninja/ui/dialog/convertType/ConvertPreviewExecutor.kt` — Coordinate canceled/queued previews without changing debounce contracts.
- `tree-sitter-wasm/src` — Inspect ABI, runtime state, and error ownership; no unrelated parser migration.
- `src/test/kotlin/com/livteam/jsoninja/services/typeConversion/TypeConversionWasmIntegrationTest.kt` — Existing end-to-end bundled WASM target.
- `docs/briefs/evidence/json-integrity-08.md` (proposed) — Durable Baseline, Implementation, and Acceptance handoff record for this child.
- `docs/briefs/2026-09-16-briefset-json-integrity.md` (proposed) — Parent owns cross-child order, conflict handling, and global acceptance.
- `docs/coroutine-threading-standard.md` — Apply the canonical threading and cancellation contract.

## Execution Plan

### Stage 1 — Pin the behavior and contract

- Starts when: The current checkout and named entry points are available.
- Work: Trace callers through intermediate layers to the final consumer. Enumerate the Reproduction cases, record observed versus expected behavior, and separate source-confirmed structure from unexecuted runtime predictions. Inventory relevant public APIs/options and existing verification coverage before editing.
- No-op when: Every whole-child Acceptance Criterion and Side Effect Checkpoint is already supported by current, reproducible evidence on the integrated checkout; source appearance or passing unrelated tests alone is insufficient.
- No-op handoff: Write the complete Acceptance evidence to `docs/briefs/evidence/json-integrity-08.md` (proposed) with status no-change, exact source stamp, all case results and limitations. The parent checks whole-child acceptance and lets child 09, child 11, child 16 continue; skip implementation stages only when every required criterion is met. A failed proof instead follows Replan when.
- Deliverable: `docs/briefs/evidence/json-integrity-08.md` (proposed), Baseline section with source stamp (HEAD plus dirty-file/diff identity), case IDs, inputs, expected/actual values, methods, current command results, contracts, limitations, and route: implement/no-change/replan.
- Verify: `Bounded comparison of named source symbols and recorded baseline cases`; Inputs: the Reproduction cases and each existing entry-point file above; Expected: a non-empty case inventory, one concrete result or explicit unverified label per case, and a justified route tied to the actual checkout.
- Ends when:
  - [ ] All assigned audit IDs have a traced final consumer and a specific reproduction/inspection result.
  - [ ] Existing behavior to preserve and any unavailable evidence are explicitly recorded before implementation.
- Handoff: Stage 2 receives the Baseline section of `docs/briefs/evidence/json-integrity-08.md` (proposed), including the failing cases, caller contract, and selected bounded correction.
- Replan when: A prerequisite is stale, the predicted defect cannot be established, or a public/ownership contract must expand. Stop affected successors, return evidence to the parent owner, choose a bounded correction or evidence-only route, update topology/handoffs, and re-verify before resuming; never force an edit to satisfy the plan.

### Stage 2 — Implement the bounded correction

- Starts when: Stage 1 records route implement with confirmed failing behavior or a source-proven contract defect, and all listed prerequisites are accepted.
- Work: Apply the following focused changes:
  - Choose whole-transaction serialization or isolated interpreter instances based on supported runtime APIs. The protected span must include allocate, source write, call, error/result read, and every free; a lock around only the exported function is insufficient.
  - Keep waiting and compute off EDT. Preserve cancellation of queued requests, suppress obsolete results, and let finally cleanup finish for an already-started synchronous transaction.
  - Preserve the exported WASM ABI and buffer ownership. Record the minimum-platform/runtime API evidence and any contention tradeoff; do not claim concurrency safety from canceled coroutine state alone.
- Deliverable: `docs/briefs/evidence/json-integrity-08.md` (proposed), Implementation section with edited files/symbols, source stamp, chosen API/contract decisions, final-consumer trace, and compile result; corresponding focused source changes remain reviewable in the working tree.
- Verify: `./gradlew compileKotlin`; Inputs: the edited Kotlin consumers and unchanged public callers in the repository root; Expected: exit 0 with no new compilation errors. Also inspect every changed value/option at its final consumer against the Stage 1 contract.
- Ends when:
  - [ ] The focused correction reaches every assigned final consumer and preserves the named contracts.
  - [ ] Compile succeeds and no source change has crossed sibling ownership or an unanswered question milestone.
- Handoff: Stage 3 receives the integrated source changes plus Implementation section of `docs/briefs/evidence/json-integrity-08.md` (proposed).
- Replan when: The correction needs a new dependency/API, shared writer, unsupported platform behavior, or unresolved user-owned policy beyond the bounded choices. Stop affected successors and return the concrete evidence/change proposal to the parent owner to revise scope/order and re-verify. Keep unrelated ready children available.

### Stage 3 — Verify behavior and publish the handoff record

- Starts when: Stage 2 provides the compiling correction, or Stage 1 proves the complete no-change route and only evidence finalization remains.
- Work: Run the existing commands below and exercise every whole-child acceptance case and side-effect checkpoint. Record exact inputs, expected/actual results, tool/IDE versions, cwd, exit status, and limitations. Native/manual checks require an authorized execution context; do not substitute source inspection for an unobserved user interaction.
- Deliverable: `docs/briefs/evidence/json-integrity-08.md` (proposed), Acceptance section with source stamp, assigned audit IDs, per-case evidence, command/cwd/exit results, artifact paths/hashes where relevant, side-effect results, residuals, and status ready/no-change/not-ready. Readiness requires all mandatory behavior criteria; CI external observations are explicitly separated as stated in that child's criteria.
- Verify: `Bounded inspection of the command and behavior evidence matrix`; Inputs: root commands `./gradlew compileKotlin`; then `./gradlew test -PskipWasmBuild=true --tests 'com.livteam.jsoninja.services.typeConversion.TypeConversionWasmIntegration*'`, the integrated checkout, and every concrete Acceptance Criterion; Expected: exit 0 for each required command plus the stated per-case outcomes. Record missing capabilities as pending and use not-ready for unmet mandatory behavior instead of fabricating success.
- Ends when:
  - [ ] Every required command and case has a recorded result or an explicit unresolved prerequisite with an owner.
  - [ ] The final source stamp matches the code that produced the evidence, and the status accurately reflects whole-child acceptance.
- Handoff: The parent and child 09, child 11, child 16 receive `docs/briefs/evidence/json-integrity-08.md` (proposed) with the complete minimum format above. Only ready/no-change records with no unmet prerequisites authorize dependent work; the parent alone updates its Child Briefs checklist.
- Replan when: Verification fails or required proof is unavailable. Mark not-ready, stop dependent starts, return the exact failed input/result to the parent owner, activate bounded correction plus re-verification, and recalculate affected waves/handoffs before resuming. Never call a failing or unobserved behavior complete.

## Side Effect Checkpoints

- [ ] No UI thread waits for a runtime lock or performs WASM compute.
- [ ] Project/dialog disposal cancels queued work while preserving runtime cleanup and the original exception/cancellation semantics.

## Acceptance Criteria

- [ ] A controlled overlapping-request trace shows at most one active transaction per shared instance, or independent instances with separate memory ownership.
- [ ] Distinct concurrent inputs return matching outputs without cross-request text/error contamination, including cancellation and malformed input.
- [ ] Failure and cancellation paths free all owned buffers and leave the runtime usable for the next request.
- [ ] The existing three TypeConversionWasmIntegration suites pass against the intended bundled artifact; Rust host tests are reported separately from WASM execution.
- [ ] All stages and side-effect checkpoints have evidence at `docs/briefs/evidence/json-integrity-08.md` (proposed); required commands pass on the final source state, unresolved mandatory checks are absent, and the parent can consume the handoff without reconstructing context.

## Open Questions

- None — scope and behavior follow the reviewed defects; bounded implementation choices are assigned to the worker.
