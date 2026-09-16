# [fix] Preserve JSON numbers and JSON5 input

## Work Type

fix

## Current State (As-Is)

- [confirmed] Audit anchor: HEAD `9eed77ed863f82843f9aadca0af479cf6efa0afd` inspected on 2026-09-16 with an untracked Undo/Redo test; treat the current checkout as authoritative. Evidence: repository HEAD and named source symbols below.
- [confirmed] `JsonObjectMapperService.objectMapper` enables comments, single quotes, unquoted names, and trailing commas, but not exact decimal deserialization. Evidence: the mapper initialization and its configured JsonReadFeature/DeserializationFeature flags.
- [confirmed] `JsonFormatterService.formatJson()` reads a tree and its sorted branch converts that tree to an untyped Object. Evidence: `readTree` and `treeToValue(..., Object::class.java)`; both conversions must preserve numeric meaning.
- [inferred] Other JSON5 forms and high-precision decimals can be rejected or rounded by the shared parse/format pipeline. Confirm by the finite JSON5 and decimal fixtures in Reproduction across the actual formatter and query consumers.

## Reproduction

- Environment: current shared Jackson pipeline on IC 2024.3/JVM 17. This is source-backed risk, not a recorded passing/failing runtime matrix; establish observed results before editing.
- Parse and format `{"n":0.12345678901234567890}` with sorting off and on; compare exact numeric value with arbitrary-precision arithmetic, not Double. Expected: no significant decimal digit loss.
- Exercise JSON5 inputs `{n:+1}`, `{n:0x10}`, `{n:.5}`, `{n:1.}`, a single-quoted string continued by backslash plus newline, Unicode identifier keys, comments, and trailing commas. Expected: finite values reach all shared consumers with equivalent JSON meaning.
- Attempt strict-JSON conversion of `Infinity`, `-Infinity`, and `NaN` as root values and inside objects/arrays. Expected: reject the entire conversion through the existing recoverable error path and retain the exact original text; never substitute null or strings or apply partial output. Quoted strings such as `"Infinity"` must still convert normally.

## Desired Outcome (To-Be)

- The shared input boundary accepts finite JSON5 syntax and preserves exact representable numeric values through tree, untyped conversion, formatting, and query serialization.
- Strict-JSON conversion rejects non-finite numeric values and preserves the original text unchanged, as confirmed by the user; no further policy confirmation is required.

## Scope

### In Scope

- Resolve assigned audit items R1.5, R2.10 through the named end-to-end entry points.
- Trace raw input to the shared parser and final serialized consumer, including sorted tree-to-Object conversion. Preserve arbitrary-precision numeric value; lexical spelling, indentation, trailing-zero spelling, and hexadecimal spelling need not survive JSON serialization.
- Use supported mapper options where sufficient. If full finite JSON5 requires a preprocessing/parser boundary, make it syntax-aware and shared; regex substitution inside string data is unacceptable. Preserve the exposed ObjectMapper contract or plan an atomic migration of every proven call site before changing it.
- Check existing dependencies first. Add a parsing dependency only if necessary, document why current libraries cannot meet the fixture matrix, verify JVM 17/IC 243 support, and include final packaged behavior in the handoff.
- Reject strict-JSON conversion when any numeric value is `Infinity`, `-Infinity`, or `NaN`. Propagate the recoverable failure to the final consumer before any output is applied, preserving the exact original text without partial replacement or null/string coercion. Distinguish numeric tokens from ordinary string contents.

### Out of Scope

- [hard] Preserving original whitespace, comments, or numeric spelling in successful output as a new round-trip formatting feature. Rejected conversions must still preserve the exact original text.
- [hard] Silent numeric coercion to achieve parse success.
- [deferred] Query source lifecycle and successful null-result presentation belong to child 05.
- [hard] Implementing sibling-owned findings or changing parent dependencies without first replanning the parent.

## Constraints

- The user confirmed the non-finite strict-JSON policy: abort conversion and preserve the original text. Apply it in Stage 2 without another policy approval; finite JSON5 handling and other existing contracts remain unchanged.
- Evidence stamps identify the tested revision and changed contract files; they do not require whole-worktree equality after unrelated siblings land. A successor records which predecessor file/contract hashes are unchanged, or which later correction re-verified the changed contract. Re-run only affected proof after new changes; final integration records that chain instead of rewriting historical results.
- Use `/Users/buyong/workspace/private/json-helper2` as the working directory for root commands. Before each verification command, announce the exact command and working directory. Baseline after Kotlin changes is `./gradlew compileKotlin`.
- Follow `docs/coroutine-threading-standard.md`: lifecycle child scopes, Default for CPU, IO for network/files, EDT for UI, modal context where needed, EDT WriteCommandAction for undoable mutations, and read actions for off-EDT platform reads. Preserve cancellation and caller-supplied options.
- Verify any new platform API against the minimum IC 2024.3 / build 243 environment and JVM 17 before using it. Preserve `JSONINJA_EDITOR_KEY`, settings enum/persistence keys, localization, registration, large-file thresholds, and disposal ownership where touched.
- Preserve unrelated dirty work, including `.codemap`, `.antigravitycli`, existing briefs, and the untracked Undo/Redo test. Do not stage, commit, publish, or run external mutations as part of implementation verification.
- Except child 01's explicitly authorized Undo/Redo tests, do not add or modify test cases/files, lint rules, formatter configuration, or test automation without a further explicit request. Use existing tests and the concrete bounded manual inspections/scenarios below. A passing existing suite does not substitute for a missing behavior observation.
- Record limitations honestly. Native UI control was not authorized in the audit session; no task may bypass that denial. Missing UI, compiler, network, or CI evidence stays pending, and the affected behavior is not accepted until an authorized observation or appropriate existing proof supplies it.

## Related Files / Entry Points

- `src/main/kotlin/com/livteam/jsoninja/services/JsonObjectMapperService.kt` — Start at the shared mapper and enumerate readTree/readValue/treeToValue consumers.
- `src/main/kotlin/com/livteam/jsoninja/services/JsonFormatterService.kt` — Audit precision through formatting and sorted untyped conversion; coordinate subsequent formatting work.
- `src/main/kotlin/com/livteam/jsoninja/services/JsonQueryService.kt` — Check each engine's parse/serialize boundary as a consumer, without taking ownership of query result-state changes.
- `src/main/kotlin/com/livteam/jsoninja/model/typeConversion/TypeConversionModels.kt` — Read numeric inference/output contracts; do not change their shape here.
- `build.gradle.kts` — Inspect existing parsing dependencies and runtime constraints before any justified dependency change.
- `src/main/resources/messages/LocalizationBundle.properties` — Use existing invalid-input feedback where possible; own any new parser diagnostic keys in every sibling locale.
- `src/test/kotlin/com/livteam/jsoninja/services/JsonFormatterServiceTest.kt` — Run the existing formatter suite.
- `src/test/kotlin/com/livteam/jsoninja/services/JMESPathServiceTest.kt` — Run the existing shared-query regression suite.
- `docs/briefs/evidence/json-integrity-02.md` (proposed) — Durable Baseline, Implementation, and Acceptance handoff record for this child.
- `docs/briefs/2026-09-16-briefset-json-integrity.md` (proposed) — Parent owns cross-child order, conflict handling, and global acceptance.
- `docs/coroutine-threading-standard.md` — Apply the canonical threading and cancellation contract.

## Execution Plan

### Stage 1 — Pin the behavior and contract

- Starts when: The current checkout and named entry points are available.
- Work: Trace callers through intermediate layers to the final consumer. Enumerate the Reproduction cases, record observed versus expected behavior, and separate source-confirmed structure from unexecuted runtime predictions. Inventory relevant public APIs/options and existing verification coverage before editing.
- No-op when: Every whole-child Acceptance Criterion and Side Effect Checkpoint is already supported by current, reproducible evidence on the integrated checkout; source appearance or passing unrelated tests alone is insufficient.
- No-op handoff: Write the complete Acceptance evidence to `docs/briefs/evidence/json-integrity-02.md` (proposed) with status no-change, exact source stamp, all case results and limitations. The parent checks whole-child acceptance and lets child 03, child 10, child 12, child 16 continue; skip implementation stages only when every required criterion is met. A failed proof instead follows Replan when.
- Deliverable: `docs/briefs/evidence/json-integrity-02.md` (proposed), Baseline section with source stamp (HEAD plus dirty-file/diff identity), case IDs, inputs, expected/actual values, methods, current command results, contracts, limitations, and route: implement/no-change/replan.
- Verify: `Bounded comparison of named source symbols and recorded baseline cases`; Inputs: the Reproduction cases and each existing entry-point file above; Expected: a non-empty case inventory, one concrete result or explicit unverified label per case, and a justified route tied to the actual checkout.
- Ends when:
  - [ ] All assigned audit IDs have a traced final consumer and a specific reproduction/inspection result.
  - [ ] Existing behavior to preserve and any unavailable evidence are explicitly recorded before implementation.
- Handoff: Stage 2 receives the Baseline section of `docs/briefs/evidence/json-integrity-02.md` (proposed), including the failing cases, caller contract, and selected bounded correction.
- Replan when: A prerequisite is stale, the predicted defect cannot be established, or a public/ownership contract must expand. Stop affected successors, return evidence to the parent owner, choose a bounded correction or evidence-only route, update topology/handoffs, and re-verify before resuming; never force an edit to satisfy the plan.

### Stage 2 — Implement the bounded correction

- Starts when: Stage 1 records route implement with confirmed failing behavior or a source-proven contract defect, and all listed prerequisites are accepted.
- Work: Apply the following focused changes:
  - Trace raw input to the shared parser and final serialized consumer, including sorted tree-to-Object conversion. Preserve arbitrary-precision numeric value; lexical spelling, indentation, trailing-zero spelling, and hexadecimal spelling need not survive JSON serialization.
  - Use supported mapper options where sufficient. If full finite JSON5 requires a preprocessing/parser boundary, make it syntax-aware and shared; regex substitution inside string data is unacceptable. Preserve the exposed ObjectMapper contract or plan an atomic migration of every proven call site before changing it.
  - Check existing dependencies first. Add a parsing dependency only if necessary, document why current libraries cannot meet the fixture matrix, verify JVM 17/IC 243 support, and include final packaged behavior in the handoff.
  - Reject strict-JSON conversion when any numeric value is `Infinity`, `-Infinity`, or `NaN`. Propagate the recoverable failure to the final consumer before any output is applied, preserving the exact original text without partial replacement or null/string coercion. Distinguish numeric tokens from ordinary string contents.
- Deliverable: `docs/briefs/evidence/json-integrity-02.md` (proposed), Implementation section with edited files/symbols, source stamp, chosen API/contract decisions, final-consumer trace, and compile result; corresponding focused source changes remain reviewable in the working tree.
- Verify: `./gradlew compileKotlin`; Inputs: the edited Kotlin consumers and unchanged public callers in the repository root; Expected: exit 0 with no new compilation errors. Also inspect every changed value/option at its final consumer against the Stage 1 contract.
- Ends when:
  - [ ] The focused correction reaches every assigned final consumer and preserves the named contracts.
  - [ ] Compile succeeds and no source change has crossed sibling ownership or an unanswered question milestone.
- Handoff: Stage 3 receives the integrated source changes plus Implementation section of `docs/briefs/evidence/json-integrity-02.md` (proposed).
- Replan when: The correction needs a new dependency/API, shared writer, unsupported platform behavior, or unresolved user-owned policy beyond the bounded choices. Stop affected successors and return the concrete evidence/change proposal to the parent owner to revise scope/order and re-verify. Keep unrelated ready children available.

### Stage 3 — Verify behavior and publish the handoff record

- Starts when: Stage 2 provides the compiling correction, or Stage 1 proves the complete no-change route and only evidence finalization remains.
- Work: Run the existing commands below and exercise every whole-child acceptance case and side-effect checkpoint. Record exact inputs, expected/actual results, tool/IDE versions, cwd, exit status, and limitations. Native/manual checks require an authorized execution context; do not substitute source inspection for an unobserved user interaction.
- Deliverable: `docs/briefs/evidence/json-integrity-02.md` (proposed), Acceptance section with source stamp, assigned audit IDs, per-case evidence, command/cwd/exit results, artifact paths/hashes where relevant, side-effect results, residuals, and status ready/no-change/not-ready. Readiness requires all mandatory behavior criteria; CI external observations are explicitly separated as stated in that child's criteria.
- Verify: `Bounded inspection of the command and behavior evidence matrix`; Inputs: root commands `./gradlew compileKotlin`; then `./gradlew test -PskipWasmBuild=true --tests 'com.livteam.jsoninja.services.JsonFormatterServiceTest' --tests 'com.livteam.jsoninja.services.JMESPathServiceTest' --tests 'com.livteam.jsoninja.services.JacksonJqServiceTest'`, the integrated checkout, and every concrete Acceptance Criterion; Expected: exit 0 for each required command plus the stated per-case outcomes. Record missing capabilities as pending and use not-ready for unmet mandatory behavior instead of fabricating success.
- Ends when:
  - [ ] Every required command and case has a recorded result or an explicit unresolved prerequisite with an owner.
  - [ ] The final source stamp matches the code that produced the evidence, and the status accurately reflects whole-child acceptance.
- Handoff: The parent and child 03, child 10, child 12, child 16 receive `docs/briefs/evidence/json-integrity-02.md` (proposed) with the complete minimum format above. Only ready/no-change records with no unmet prerequisites authorize dependent work; the parent alone updates its Child Briefs checklist.
- Replan when: Verification fails or required proof is unavailable. Mark not-ready, stop dependent starts, return the exact failed input/result to the parent owner, activate bounded correction plus re-verification, and recalculate affected waves/handoffs before resuming. Never call a failing or unobserved behavior complete.

## Side Effect Checkpoints

- [ ] Query, schema, diff, tooltip, and type-inference consumers are enumerated and checked for assumptions about DoubleNode/DecimalNode and ObjectMapper return types.
- [ ] Existing parser errors and locale keys remain recoverable and do not expose raw secrets or document contents in logs.

## Acceptance Criteria

- [ ] Every finite JSON5 fixture in Reproduction reaches the formatter, query input, and type-inference input with equivalent value; malformed input remains a recoverable error and does not replace a document.
- [ ] The high-precision decimal retains significant digits after pretty, sorted, and compact output and after shared untyped conversion; comparison uses exact decimal values.
- [ ] Existing strict JSON, comments, single quotes, unquoted names, trailing commas, Unicode keys, and placeholder handling retain their established behavior.
- [ ] For each non-finite numeric fixture in Reproduction, every applicable strict-JSON output path rejects the entire conversion recoverably and leaves the exact original text unchanged. No final consumer applies partial output or substitutes null or a string; quoted strings such as `"Infinity"` retain normal behavior.
- [ ] All stages and side-effect checkpoints have evidence at `docs/briefs/evidence/json-integrity-02.md` (proposed); required commands pass on the final source state, unresolved mandatory checks are absent, and the parent can consume the handoff without reconstructing context.

## Open Questions

- None — the user confirmed rejection of non-finite strict-JSON conversion with unchanged source text; no policy reconfirmation is required.
