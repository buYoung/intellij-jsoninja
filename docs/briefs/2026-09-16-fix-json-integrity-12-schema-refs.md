# [fix] Resolve schema references in their source context

## Work Type

fix

## Current State (As-Is)

- [confirmed] Audit anchor: HEAD `9eed77ed863f82843f9aadca0af479cf6efa0afd` inspected on 2026-09-16 with an untracked Undo/Redo test; treat the current checkout as authoritative. Evidence: repository HEAD and named source symbols below.
- [confirmed] `JsonSchemaNormalizer.resolveReferences()` recursively visits object values and interprets textual $ref/$dynamicRef independent of whether a value is schema or instance data. Evidence: recursive object handling.
- [confirmed] `GenerateSchemaJsonTabPresenter.loadSchemaFromUrl()` keeps fetched text but `getConfig()` passes schemaText without retrieval URI; `JsonSchemaDataGenerationService.prepareSchema()` calls normalize with project-local base context. Evidence: those methods and JsonGenerationConfig fields.

## Reproduction

- Environment: generate-from-schema dialog with pasted schema and a schema fetched from a controlled HTTP endpoint. No recorded end-to-end result exists yet.
- Generate from `{"type":"object","const":{"$ref":"#/missing"}}`, plus enum/default/examples containing literal `$ref` and `$dynamicRef` properties. Expected: those instance data objects are preserved and never trigger reference loading.
- Inspect anchor collection and generation for `{"$defs":{"target":{"$anchor":"real","type":"string"}},"type":"object","properties":{"value":{"$ref":"#real"}},"required":["value"],"examples":[{"$anchor":"fake"},{"$dynamicAnchor":"fakeDynamic"}]}`. Expected: `real` is registered and resolves to the string schema, while `fake` and `fakeDynamic` are absent from the collected anchor map; repeat with the examples field before $defs to exclude traversal-order dependence.
- Compare `{"type":"object","const":{"minimum":5,"maximum":1}}` with `{"type":"number","minimum":5,"maximum":1}` through normalization and generation. Expected: the first preserves and emits the literal const object without a contradiction error, while the actual numeric schema reports a recoverable contradiction with its JSON pointer.
- Serve `/schemas/root.json` containing a relative `$ref` to `defs.json`, with `/schemas/defs.json` available. Expected: resolution uses the retrieval URI and applicable `$id`, not the project filesystem.
- Repeat with nested `$id`, fragments/anchors, local pasted schemas, and edits after a URL load. Expected: an explicit source-context rule survives the full presenter/config/service/normalizer chain.

## Desired Outcome (To-Be)

- Only schema-valued keywords participate in reference traversal, and remote relative references retain the correct retrieval/effective base URI.

## Scope

### In Scope

- Resolve assigned audit items R1.10, R1.11 through the named end-to-end entry points.
- Classify schema-valued locations according to the supported schema dialect/keywords. Traverse real schemas while treating const, enum, default, and examples payloads as instance data; apply the same boundary to anchors and contradiction checks where they share the traversal.
- Propagate retrieval URI from URL loading through configuration and service into normalization. Apply nested `$id` changes and resolve fragments/relative resources with the correct effective base.
- Use source provenance as an explicit bounded rule: editing the loaded document retains its retrieval base until a new load/paste/source replacement deliberately resets provenance. Make all such replacement paths explicit; typed schemas without provenance retain existing project-relative behavior.
- Keep reference caches, SchemaStore fallback behavior, JSON pointers, localized generation errors, and cycle/unsupported-reference handling; do not silently weaken a failed reference into an unconstrained schema.

### Out of Scope

- [hard] Treating arbitrary instance data as a schema or making network requests from data-only `$ref` values.
- [deferred] Numeric range generation belongs to child 13.
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

- `src/main/kotlin/com/livteam/jsoninja/services/schema/JsonSchemaNormalizer.kt` — Start at normalize/resolveReferences, anchor collection, and contradiction traversal.
- `src/main/kotlin/com/livteam/jsoninja/ui/dialog/generateJson/schema/GenerateSchemaJsonTabPresenter.kt` — Capture URL source metadata and propagate it with schema text.
- `src/main/kotlin/com/livteam/jsoninja/ui/dialog/generateJson/model/JsonGenerationConfig.kt` — Carry source context only as required, preserving other generation options.
- `src/main/kotlin/com/livteam/jsoninja/services/schema/JsonSchemaDataGenerationService.kt` — Pass retrieval context to normalization/compilation.
- `src/test/kotlin/com/livteam/jsoninja/services/schema/JsonSchemaDataGenerationServiceTest.kt` — Existing schema behavior target.
- `docs/briefs/evidence/json-integrity-12.md` (proposed) — Durable Baseline, Implementation, and Acceptance handoff record for this child.
- `docs/briefs/2026-09-16-briefset-json-integrity.md` (proposed) — Parent owns cross-child order, conflict handling, and global acceptance.
- `docs/coroutine-threading-standard.md` — Apply the canonical threading and cancellation contract.

## Execution Plan

### Stage 1 — Pin the behavior and contract

- Starts when: Read `docs/briefs/evidence/json-integrity-02.md` (proposed). Each predecessor must have an Acceptance section with ready/no-change status, source stamp matching the integrated code, completed required cases, and no unresolved prerequisite. Stop and return to the parent if any is missing, stale, or not-ready.
- Work: Trace callers through intermediate layers to the final consumer. Enumerate the Reproduction cases, record observed versus expected behavior, and separate source-confirmed structure from unexecuted runtime predictions. Inventory relevant public APIs/options and existing verification coverage before editing.
- No-op when: Every whole-child Acceptance Criterion and Side Effect Checkpoint is already supported by current, reproducible evidence on the integrated checkout; source appearance or passing unrelated tests alone is insufficient.
- No-op handoff: Write the complete Acceptance evidence to `docs/briefs/evidence/json-integrity-12.md` (proposed) with status no-change, exact source stamp, all case results and limitations. The parent checks whole-child acceptance and lets child 13, child 15, child 16 continue; skip implementation stages only when every required criterion is met. A failed proof instead follows Replan when.
- Deliverable: `docs/briefs/evidence/json-integrity-12.md` (proposed), Baseline section with source stamp (HEAD plus dirty-file/diff identity), case IDs, inputs, expected/actual values, methods, current command results, contracts, limitations, and route: implement/no-change/replan.
- Verify: `Bounded comparison of named source symbols and recorded baseline cases`; Inputs: the Reproduction cases and each existing entry-point file above; Expected: a non-empty case inventory, one concrete result or explicit unverified label per case, and a justified route tied to the actual checkout.
- Ends when:
  - [ ] All assigned audit IDs have a traced final consumer and a specific reproduction/inspection result.
  - [ ] Existing behavior to preserve and any unavailable evidence are explicitly recorded before implementation.
- Handoff: Stage 2 receives the Baseline section of `docs/briefs/evidence/json-integrity-12.md` (proposed), including the failing cases, caller contract, and selected bounded correction.
- Replan when: A prerequisite is stale, the predicted defect cannot be established, or a public/ownership contract must expand. Stop affected successors, return evidence to the parent owner, choose a bounded correction or evidence-only route, update topology/handoffs, and re-verify before resuming; never force an edit to satisfy the plan.

### Stage 2 — Implement the bounded correction

- Starts when: Stage 1 records route implement with confirmed failing behavior or a source-proven contract defect, and all listed prerequisites are accepted.
- Work: Apply the following focused changes:
  - Classify schema-valued locations according to the supported schema dialect/keywords. Traverse real schemas while treating const, enum, default, and examples payloads as instance data; apply the same boundary to anchors and contradiction checks where they share the traversal.
  - Propagate retrieval URI from URL loading through configuration and service into normalization. Apply nested `$id` changes and resolve fragments/relative resources with the correct effective base.
  - Use source provenance as an explicit bounded rule: editing the loaded document retains its retrieval base until a new load/paste/source replacement deliberately resets provenance. Make all such replacement paths explicit; typed schemas without provenance retain existing project-relative behavior.
  - Keep reference caches, SchemaStore fallback behavior, JSON pointers, localized generation errors, and cycle/unsupported-reference handling; do not silently weaken a failed reference into an unconstrained schema.
- Deliverable: `docs/briefs/evidence/json-integrity-12.md` (proposed), Implementation section with edited files/symbols, source stamp, chosen API/contract decisions, final-consumer trace, and compile result; corresponding focused source changes remain reviewable in the working tree.
- Verify: `./gradlew compileKotlin`; Inputs: the edited Kotlin consumers and unchanged public callers in the repository root; Expected: exit 0 with no new compilation errors. Also inspect every changed value/option at its final consumer against the Stage 1 contract.
- Ends when:
  - [ ] The focused correction reaches every assigned final consumer and preserves the named contracts.
  - [ ] Compile succeeds and no source change has crossed sibling ownership or an unanswered question milestone.
- Handoff: Stage 3 receives the integrated source changes plus Implementation section of `docs/briefs/evidence/json-integrity-12.md` (proposed).
- Replan when: The correction needs a new dependency/API, shared writer, unsupported platform behavior, or unresolved user-owned policy beyond the bounded choices. Stop affected successors and return the concrete evidence/change proposal to the parent owner to revise scope/order and re-verify. Keep unrelated ready children available.

### Stage 3 — Verify behavior and publish the handoff record

- Starts when: Stage 2 provides the compiling correction, or Stage 1 proves the complete no-change route and only evidence finalization remains.
- Work: Run the existing commands below and exercise every whole-child acceptance case and side-effect checkpoint. Record exact inputs, expected/actual results, tool/IDE versions, cwd, exit status, and limitations. Native/manual checks require an authorized execution context; do not substitute source inspection for an unobserved user interaction.
- Deliverable: `docs/briefs/evidence/json-integrity-12.md` (proposed), Acceptance section with source stamp, assigned audit IDs, per-case evidence, command/cwd/exit results, artifact paths/hashes where relevant, side-effect results, residuals, and status ready/no-change/not-ready. Readiness requires all mandatory behavior criteria; CI external observations are explicitly separated as stated in that child's criteria.
- Verify: `Bounded inspection of the command and behavior evidence matrix`; Inputs: root commands `./gradlew compileKotlin`; then `./gradlew test -PskipWasmBuild=true --tests 'com.livteam.jsoninja.services.schema.JsonSchemaDataGenerationServiceTest'`, the integrated checkout, and every concrete Acceptance Criterion; Expected: exit 0 for each required command plus the stated per-case outcomes. Record missing capabilities as pending and use not-ready for unmet mandatory behavior instead of fabricating success.
- Ends when:
  - [ ] Every required command and case has a recorded result or an explicit unresolved prerequisite with an owner.
  - [ ] The final source stamp matches the code that produced the evidence, and the status accurately reflects whole-child acceptance.
- Handoff: The parent and child 13, child 15, child 16 receive `docs/briefs/evidence/json-integrity-12.md` (proposed) with the complete minimum format above. Only ready/no-change records with no unmet prerequisites authorize dependent work; the parent alone updates its Child Briefs checklist.
- Replan when: Verification fails or required proof is unavailable. Mark not-ready, stop dependent starts, return the exact failed input/result to the parent owner, activate bounded correction plus re-verification, and recalculate affected waves/handoffs before resuming. Never call a failing or unobserved behavior complete.

## Side Effect Checkpoints

- [ ] Schema generation mode, caches, fallback ownership, and cancellation/threading behavior remain compatible.
- [ ] Remote fetch behavior is left available for child 15 to consolidate without losing source URI metadata.

## Acceptance Criteria

- [ ] Literal `$ref`/`$dynamicRef` inside const, enum, default, and examples remains data and triggers no network or filesystem reference lookup.
- [ ] The anchor fixture registers and resolves the actual `$defs` anchor while excluding both data-only anchor names, regardless of root field order. Record the collected-map inspection and final generated value type; supported schema anchors must remain active.
- [ ] The const fixture containing minimum 5 and maximum 1 passes without interpreting those data properties as constraints; the corresponding actual numeric schema still fails with a pointed contradiction error. Record both inputs and outcomes so skipping all contradiction checks cannot satisfy this criterion.
- [ ] The controlled remote root resolves sibling `defs.json`, nested `$id`, and fragment/anchor cases from the correct source URI; the produced instance validates against the intended resolved schema.
- [ ] Pasted/local schemas preserve their existing base behavior, while edited URL-loaded schemas follow the documented provenance rule through final generation.
- [ ] Missing/cyclic/unsupported references use explicit recoverable errors with pointers rather than silently generating from the wrong resource.
- [ ] All stages and side-effect checkpoints have evidence at `docs/briefs/evidence/json-integrity-12.md` (proposed); required commands pass on the final source state, unresolved mandatory checks are absent, and the parent can consume the handoff without reconstructing context.

## Open Questions

- None — scope and behavior follow the reviewed defects; bounded implementation choices are assigned to the worker.
