# [ci] Verify pull requests and preserve release artifacts

## Work Type

ci

## Current State (As-Is)

- [confirmed] Audit anchor: HEAD `9eed77ed863f82843f9aadca0af479cf6efa0afd` inspected on 2026-09-16 with an untracked Undo/Redo test; treat the current checkout as authoritative. Evidence: repository HEAD and named source symbols below.
- [confirmed] `.github/workflows/build.yml` declares tag-oriented triggers despite a pull-request build comment. Evidence: the `on` block and build/check/verifyPlugin steps.
- [confirmed] The build workflow enables WASM construction for verification, while release.yml runs publishPlugin with skipWasmBuild=true from a separate checkout. Evidence: workflow environment/commands and Gradle task dependencies.
- [inferred] The verified plugin archive and subsequently published archive may differ. Confirm by tracing artifact upload/download/provenance and comparing archive/WASM hashes in a controlled workflow run; no production mismatch was observed.

## Desired Outcome (To-Be)

- Pull requests receive the existing relevant validation, and release publication consumes the exact verified plugin artifact with recorded provenance.

## Scope

### In Scope

- Resolve assigned audit items M3a, M3b through the named end-to-end entry points.
- Add a PR validation route using the existing compile/test/check/package/verifier tasks appropriate to the repository; preserve tag release behavior and avoid credentials in untrusted PR jobs.
- Make build output identity explicit: commit/tag, toolchain versions, WASM hash, plugin archive hash, and verifier result. A publisher must consume the verified archive rather than silently rebuild a different one.
- Verify the installed IntelliJ Gradle plugin's supported archive/publication API locally before editing workflow wiring; do not invent a task parameter. Keep runtime/SDK installation requirements explicit.
- Validate workflow syntax, event/job/artifact wiring, and local package identity without dispatching, uploading, releasing, tagging, publishing, or accessing credentials. Record actual CI/publication evidence as pending if external execution is not authorized.

### Out of Scope

- [hard] Running publishPlugin, dispatching external workflows, pushing commits/tags, or reading signing/publishing credentials under this brief alone.
- [hard] Upgrading the platform compatibility range or release version as part of CI wiring.
- [hard] Implementing sibling-owned findings or changing parent dependencies without first replanning the parent.

## Constraints

- Evidence stamps identify the tested revision and changed contract files; they do not require whole-worktree equality after unrelated siblings land. A successor records which predecessor file/contract hashes are unchanged, or which later correction re-verified the changed contract. Re-run only affected proof after new changes; final integration records that chain instead of rewriting historical results.
- Use `/Users/buyong/workspace/private/json-helper2` as the working directory for root commands. Before each verification command, announce the exact command and working directory. Baseline after Kotlin changes is `./gradlew compileKotlin`.
- Follow `docs/coroutine-threading-standard.md`: lifecycle child scopes, Default for CPU, IO for network/files, EDT for UI, modal context where needed, EDT WriteCommandAction for undoable mutations, and read actions for off-EDT platform reads. Preserve cancellation and caller-supplied options.
- Verify any new platform API against the minimum IC 2024.3 / build 243 environment and JVM 17 before using it. Preserve `JSONINJA_EDITOR_KEY`, settings enum/persistence keys, localization, registration, large-file thresholds, and disposal ownership where touched.
- Preserve unrelated dirty work, including `.codemap`, `.antigravitycli`, existing briefs, and the untracked Undo/Redo test. Do not stage, commit, publish, or run external mutations as part of implementation verification.
- Except child 01's explicitly authorized Undo/Redo tests, do not add or modify test cases/files, lint rules, formatter configuration, or test automation without a further explicit request. Use existing tests and the concrete bounded manual inspections/scenarios below. A passing existing suite does not substitute for a missing behavior observation.
- Record limitations honestly. Native UI control was not authorized in the audit session; no task may bypass that denial. Missing UI, compiler, network, or CI evidence stays pending, and the affected behavior is not accepted until an authorized observation or appropriate existing proof supplies it.
- Local workflow implementation can be accepted with explicit static/local evidence, but externally executed PR and publication behavior stays unverified until authorized. The parent global criteria must retain that distinction.

## Related Files / Entry Points

- `.github/workflows/build.yml` — Start at triggers, toolchains, WASM build, validation, and artifact upload.
- `.github/workflows/release.yml` — Trace artifact selection to publishPlugin without executing publication.
- `build.gradle.kts` — Inspect supported task inputs and archive-selection hooks.
- `gradle.properties` — Preserve plugin version, compatibility range, and configured platform.
- `tree-sitter-wasm/Cargo.toml` — Read required WASM target/toolchain and release behavior.
- `docs/briefs/evidence/json-integrity-16.md` (proposed) — Durable Baseline, Implementation, and Acceptance handoff record for this child.
- `docs/briefs/2026-09-16-briefset-json-integrity.md` (proposed) — Parent owns cross-child order, conflict handling, and global acceptance.
- `docs/coroutine-threading-standard.md` — Apply the canonical threading and cancellation contract.

## Execution Plan

부모의 2026-09-16 시작 조건 보정에 따라 14의 네이티브 관찰 대기와 독립적인 로컬 작업을 진행한다. 이 예외는 14나 전체 동작 수용을 완료로 간주하지 않는다.

### Stage 1 — Map validation and artifact provenance

- Starts when: Read `docs/briefs/evidence/json-integrity-01.md` (proposed), `docs/briefs/evidence/json-integrity-02.md` (proposed), `docs/briefs/evidence/json-integrity-03.md` (proposed), `docs/briefs/evidence/json-integrity-04.md` (proposed), `docs/briefs/evidence/json-integrity-05.md` (proposed), `docs/briefs/evidence/json-integrity-06.md` (proposed), `docs/briefs/evidence/json-integrity-07.md` (proposed), `docs/briefs/evidence/json-integrity-08.md` (proposed), `docs/briefs/evidence/json-integrity-09.md` (proposed), `docs/briefs/evidence/json-integrity-10.md` (proposed), `docs/briefs/evidence/json-integrity-11.md` (proposed), `docs/briefs/evidence/json-integrity-12.md` (proposed), `docs/briefs/evidence/json-integrity-13.md` (proposed), `docs/briefs/evidence/json-integrity-14.md` (proposed), `docs/briefs/evidence/json-integrity-15.md` (proposed). Each predecessor must have an Acceptance section with ready/no-change status, source stamp matching the integrated code, completed required cases, and no unresolved prerequisite. Stop and return to the parent if any is missing, stale, or not-ready.
- Work: Trace callers through intermediate layers to the final consumer. Enumerate the pull_request/tag triggers, every build/check/test/verify/upload/download/publish path, and the local archive identity, record observed versus expected behavior, and separate source-confirmed structure from unexecuted runtime predictions. Inventory relevant public APIs/options and existing verification coverage before editing.
- No-op when: Every whole-child Acceptance Criterion and Side Effect Checkpoint is already supported by current, reproducible evidence on the integrated checkout; source appearance or passing unrelated tests alone is insufficient.
- No-op handoff: Write the complete Acceptance evidence to `docs/briefs/evidence/json-integrity-16.md` (proposed) with status no-change, exact source stamp, all case results and limitations. The parent checks whole-child acceptance and lets the parent integration owner continue; skip implementation stages only when every required criterion is met. A failed proof instead follows Replan when.
- Deliverable: `docs/briefs/evidence/json-integrity-16.md` (proposed), Baseline section with source stamp (HEAD plus dirty-file/diff identity), case IDs, inputs, expected/actual values, methods, current command results, contracts, limitations, and route: implement/no-change/replan.
- Verify: `Bounded comparison of named source symbols and recorded baseline cases`; Inputs: the pull_request/tag triggers, every build/check/test/verify/upload/download/publish path, and the local archive identity and each existing entry-point file above; Expected: a non-empty case inventory, one concrete result or explicit unverified label per case, and a justified route tied to the actual checkout.
- Ends when:
  - [x] All assigned audit IDs have a traced final consumer and a specific reproduction/inspection result.
  - [x] Existing behavior to preserve and any unavailable evidence are explicitly recorded before implementation.
- Handoff: Stage 2 receives the Baseline section of `docs/briefs/evidence/json-integrity-16.md` (proposed), including the failing cases, caller contract, and selected bounded correction.
- Replan when: A prerequisite is stale, the predicted defect cannot be established, or a public/ownership contract must expand. Stop affected successors, return evidence to the parent owner, choose a bounded correction or evidence-only route, update topology/handoffs, and re-verify before resuming; never force an edit to satisfy the plan.

### Stage 2 — Implement the bounded correction

- Starts when: Stage 1 records route implement with confirmed failing behavior or a source-proven contract defect, and all listed prerequisites are accepted.
- Work: Apply the following focused changes:
  - Add a PR validation route using the existing compile/test/check/package/verifier tasks appropriate to the repository; preserve tag release behavior and avoid credentials in untrusted PR jobs.
  - Make build output identity explicit: commit/tag, toolchain versions, WASM hash, plugin archive hash, and verifier result. A publisher must consume the verified archive rather than silently rebuild a different one.
  - Verify the installed IntelliJ Gradle plugin's supported archive/publication API locally before editing workflow wiring; do not invent a task parameter. Keep runtime/SDK installation requirements explicit.
  - Validate workflow syntax, event/job/artifact wiring, and local package identity without dispatching, uploading, releasing, tagging, publishing, or accessing credentials. Record actual CI/publication evidence as pending if external execution is not authorized.
- Deliverable: `docs/briefs/evidence/json-integrity-16.md` (proposed), Implementation section with edited files/symbols, source stamp, chosen API/contract decisions, final-consumer trace, and compile result; corresponding focused source changes remain reviewable in the working tree.
- Verify: `./gradlew compileKotlin`; Inputs: the edited Kotlin consumers and unchanged public callers in the repository root; Expected: exit 0 with no new compilation errors. Also inspect every changed value/option at its final consumer against the Stage 1 contract.
- Ends when:
  - [x] The focused correction reaches every assigned final consumer and preserves the named contracts.
  - [x] Compile succeeds and no source change has crossed sibling ownership or an unanswered question milestone.
- Handoff: Stage 3 receives the integrated source changes plus Implementation section of `docs/briefs/evidence/json-integrity-16.md` (proposed).
- Replan when: The correction needs a new dependency/API, shared writer, unsupported platform behavior, or unresolved user-owned policy beyond the bounded choices. Stop affected successors and return the concrete evidence/change proposal to the parent owner to revise scope/order and re-verify. Keep unrelated ready children available.

### Stage 3 — Verify behavior and publish the handoff record

- Starts when: Stage 2 provides the compiling correction, or Stage 1 proves the complete no-change route and only evidence finalization remains.
- Work: Run the existing commands below and exercise every whole-child acceptance case and side-effect checkpoint. Record exact inputs, expected/actual results, tool/IDE versions, cwd, exit status, and limitations. Native/manual checks require an authorized execution context; do not substitute source inspection for an unobserved user interaction.
- Deliverable: `docs/briefs/evidence/json-integrity-16.md` (proposed), Acceptance section with source stamp, assigned audit IDs, per-case evidence, command/cwd/exit results, artifact paths/hashes where relevant, side-effect results, residuals, and status ready/no-change/not-ready. Readiness requires all mandatory behavior criteria; CI external observations are explicitly separated as stated in that child's criteria.
- Verify: `Bounded inspection of the command and behavior evidence matrix`; Inputs: root commands `./gradlew compileKotlin`; then `./gradlew test`; then `./gradlew buildPlugin`; then `./gradlew verifyPlugin`, the integrated checkout, and every concrete Acceptance Criterion; Expected: exit 0 for each required command plus the stated per-case outcomes. Record missing capabilities as pending and use not-ready for unmet mandatory behavior instead of fabricating success.
- Ends when:
  - [x] Every required command and case has a recorded result or an explicit unresolved prerequisite with an owner.
  - [x] The final source stamp matches the code that produced the evidence, and the status accurately reflects whole-child acceptance.
- Handoff: The parent and the parent integration owner receive `docs/briefs/evidence/json-integrity-16.md` (proposed) with the complete minimum format above. Only ready/no-change records with no unmet prerequisites authorize dependent work; the parent alone updates its Child Briefs checklist.
- Replan when: Verification fails or required proof is unavailable. Mark not-ready, stop dependent starts, return the exact failed input/result to the parent owner, activate bounded correction plus re-verification, and recalculate affected waves/handoffs before resuming. Never call a failing or unobserved behavior complete.

## Side Effect Checkpoints

- [x] Declared compatibility range 243 through 263.* and JVM/toolchain assumptions are not silently changed to make verification pass.
- [x] The final artifact includes all accepted source/WASM fixes and no unrelated working-tree artifacts.

## Acceptance Criteria

- [x] Static workflow inspection shows pull_request triggers validation and tag behavior remains intact, with explicit trust/secret boundaries.
- [x] The consumer archive path resolves to the producer's verified artifact, with matching commit/tag and SHA-256 records; a local packaged resource matches the WASM used by integration tests.
- [x] `./gradlew compileKotlin`, `./gradlew test`, `./gradlew buildPlugin`, and `./gradlew verifyPlugin` run from the root on the integrated source and report their actual exit/results. `cargo test` runs in tree-sitter-wasm; unavailable platform downloads/toolchains are recorded as gaps, not passes.
- [x] An actual PR/job and release artifact-consumption observation remains separately pending until authorized external execution supplies it; local/static proof is never labeled a successful publication.
- [x] All stages and side-effect checkpoints have evidence at `docs/briefs/evidence/json-integrity-16.md` (proposed); required commands pass on the final source state, unresolved mandatory checks are absent, and the parent can consume the handoff without reconstructing context.

## Open Questions

- None — scope and behavior follow the reviewed defects; bounded implementation choices are assigned to the worker.
