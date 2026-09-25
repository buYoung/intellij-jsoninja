# [feat] Edit JSON diff side titles

## Work Type
feat

## Current State (As-Is)
- [confirmed] Use main at `7c5392e`, inspected on 2026-09-26, as the authoring baseline. Evidence: `git log -1`.
- [confirmed] `src/main/kotlin/com/livteam/jsoninja/services/JsonDiffService.kt`, `createDiffRequest()`, supplies fixed localized side labels through `dialog.json.diff.left` and `dialog.json.diff.right`.
- [confirmed] `src/main/kotlin/com/livteam/jsoninja/ui/diff/JsonDiffRequestChain.kt` constructs the editor-tab request through the service; `JsonDiffWindowDialog` owns the separate window's DiffRequestPanel.
- [confirmed] `JsonDiffService.ActiveDiffContext` owns both Documents and active hosts, providing the session lifetime to which custom labels can belong.
- [confirmed] `src/test/kotlin/com/livteam/jsoninja/services/JsonDiffServiceTest.kt`, `testCreateDiffRequestWithValidJson()`, checks a caller-supplied overall request title; side-label changes must preserve that separate contract.
- [inferred] Refreshing side labels by recreating unrelated editor state could discard the input pair or pending operation guards. Confirm supported request refresh and document retention in Stage 1.

## Desired Outcome (To-Be)
- Let the user rename both sides from the diff toolbar, such as "개발 서버" and "운영 서버".
- Retain labels for the active comparison session across request/host recreation and repeated left-input replacement.
- Keep labels distinct from the overall diff title and leave JSON text, selected sort options, and restoration eligibility unchanged.
- Preserve existing default labels when a custom label is cleared.

## Scope
### In Scope
- Add one request/session-scoped rename action and a small two-field editing surface.
- Store custom side labels in the active diff session and pass them into every new/refreshed request.
- Localize action/dialog labels and retain Unicode user-entered names.
- Complete integration observations across the approved eight items after all children have landed.
### Out of Scope
- [hard] Do not rename source files or change the caller-supplied overall diff request title.
- [hard] Do not use reflection, internal component traversal, or private UI fields to alter labels.
- [hard] Do not add or modify automated test files, cases, fixtures, lint rules, formatter configuration, or new verification tooling.
- [deferred] Persisting names across IDE restarts, comparison history, source-based title inference, and multi-session management.

## Constraints
- Consume child 02's session ownership and compatible request APIs; do not create a second owner for Documents or sorting state.
- Keep `dialog.json.diff.left` and `dialog.json.diff.right` as the default-label contracts and keep the optional overall `title` argument unchanged.
- Trim surrounding whitespace from entered labels; use the existing localized default for an empty value rather than blocking completion.
- Treat custom labels as session-local display data. New sessions begin with existing defaults and the feature does not change synced/local settings storage.
- Apply both names together; canceling the dialog changes neither name.
- Retain names when the same comparison is reopened or its left JSON is replaced while the host remains open. End their lifetime when the comparison session ends.
- Refresh only what the supported API requires, retaining both Document instances, selected mode/auto/array options, pending-result guards, and the valid restore snapshot.
- Inspect the exact API and containing type on the minimum supported build 243 before implementation. Prefer supported request replacement with existing Documents if in-place side-label mutation is unavailable; do not bypass the public API boundary.
- Keep required modal updates on the appropriate EDT/modality context and tie any owned UI resources to the dialog/session lifecycle.
- Perform new message edits only after child 03's message changes have integrated; retain all keys from child 02.

## Related Files / Entry Points
- `docs/briefs/2026-09-26-briefset-diff-ux.md` — use Global Acceptance Criteria as the exact eight-item input/observation list for final integration, while this child retains ownership only of title behavior and the final join.
- `src/main/kotlin/com/livteam/jsoninja/services/JsonDiffService.kt` — add session-local labels and feed both createDiffRequest paths without changing the overall title argument.
- `src/main/kotlin/com/livteam/jsoninja/actions/RenameJsonDiffTitlesAction.kt` (proposed) — open the two-field editing surface through the current diff session.
- `src/main/kotlin/com/livteam/jsoninja/ui/diff/JsonDiffTitlesDialog.kt` (proposed) — edit both side names and apply/cancel atomically using existing dialog conventions.
- `src/main/kotlin/com/livteam/jsoninja/ui/diff/JsonDiffRequestChain.kt` — preserve session labels when constructing or refreshing editor-tab requests.
- `src/main/kotlin/com/livteam/jsoninja/ui/diff/JsonDiffVirtualFile.kt` — preserve the Documents and live session through editor-tab host reuse.
- `src/main/kotlin/com/livteam/jsoninja/ui/diff/JsonDiffWindowDialog.kt` — inspect supported request refresh on the separate-window request panel.
- `src/main/kotlin/com/livteam/jsoninja/diff/JsonDiffKeys.kt` — reuse the established typed session route rather than inspecting internal UI components.
- `src/main/resources/messages` — add rename labels in every existing LocalizationBundle variant and retain existing side-default keys.
- `src/test/kotlin/com/livteam/jsoninja/services/JsonDiffServiceTest.kt` — retain the public request-title and two-content contracts.
- `gradle.properties` — check the minimum supported platform build.
- `build.gradle.kts` — use existing compilation, targeted tests, and sandbox tasks.

## Execution Plan
### Stage 1 — Confirm supported side-label refresh
- Starts when: `src/main/kotlin/com/livteam/jsoninja/services/JsonDiffService.kt` from child 02 provides the shared session, retained Documents, sorting/restoration state, and compatible request creation; child 03's serialized registration/message edits have integrated.
- Work: Determine the supported side-label refresh path for both hosts and pin label lifetime, defaults, and cancel behavior.
- No-op when: None — the reviewed baseline exposes only fixed localized side titles.
- No-op handoff: None — if pickup discovers complete verified title editing, return that evidence to the parent and recalculate active work before continuing.
- Deliverable: The supported request-refresh and session-label contract in `src/main/kotlin/com/livteam/jsoninja/services/JsonDiffService.kt`.
- Verify: `Inspect the exact supported diff request/panel APIs for build 243 and both host consumers`; Inputs: gradle.properties, the resolved platform API declarations, JsonDiffService.createDiffRequest, JsonDiffRequestChain, and JsonDiffWindowDialog; Expected: a public route to refresh both labels while retaining Documents and session state, with its actual API/version evidence recorded.
- Ends when:
  - [ ] Both host types have a confirmed refresh route without internal/private API access.
  - [ ] The overall-title argument and side-default keys remain separate contracts.
- Handoff: Stage 2 receives the supported refresh route and label lifetime/default rules.
- Replan when: No supported API can update labels while preserving the session contract; stop this child and return the limitation to the parent with a supported alternative.

### Stage 2 — Add atomic title editing
- Starts when: Stage 1 confirms the public refresh route and predecessor message edits are present.
- Work: Add the localized rename action/dialog, session-local custom names, and both-host label refresh while preserving content and sorting state.
- Deliverable: `src/main/kotlin/com/livteam/jsoninja/services/JsonDiffService.kt` with session label storage and request construction that consumes the current pair of names.
- Verify: `./gradlew compileKotlin`; Inputs: the new action/dialog and changed service/request/host classes from the repository root; Expected: exit 0 with existing request creation overloads and the overall-title argument unchanged.
- Ends when:
  - [ ] Apply changes both labels and cancel changes neither.
  - [ ] Empty labels use existing localized defaults and Unicode input is retained.
  - [ ] Refresh retains both Documents and the existing session controls.
- Handoff: Stage 3 receives integrated label editing in both hosts.
- Replan when: Label refresh recreates data or invalidates unrelated restoration state; stop and correct the owning service/host route before verification.

### Stage 3 — Verify titles and the integrated workflow
- Starts when: Stage 2 is integrated with children 01–03.
- Work: Run the existing request regression checks, exercise rename/apply/cancel in both hosts, and complete the parent's eight-item integration observations. Reuse earlier evidence only while its affected paths/contracts are unchanged.
- Deliverable: `src/main/kotlin/com/livteam/jsoninja/services/JsonDiffService.kt` with verified label-aware request construction preserving the shared session and the approved end-to-end diff workflow.
- Verify: `./gradlew test -PskipWasmBuild=true --tests 'com.livteam.jsoninja.services.JsonDiffServiceTest'`; Inputs: the existing suite and `./gradlew runIde -PskipWasmBuild=true` with "개발 서버"/"운영 서버" labels plus Global Acceptance Criteria in `docs/briefs/2026-09-26-briefset-diff-ux.md`; Expected: test exit 0, both visible names, preserved content/options, and correctly distinguished executed versus unavailable observations.
- Ends when:
  - [ ] Title behavior and all affected side-effect checkpoints have recorded observations on both hosts.
  - [ ] The parent has current evidence for all eight approved concerns; missing mandatory observations remain incomplete.
- Handoff: Parent whole-set acceptance receives `src/main/kotlin/com/livteam/jsoninja/services/JsonDiffService.kt` and the integrated label/session contract; tick child status only after its criteria pass.
- Replan when: An integration proof fails; stop whole-set completion, return the defect to its owning child, perform bounded correction and re-verification, and recalculate affected handoffs before resuming.

## Side Effect Checkpoints
- [ ] Custom names do not replace or rename the caller-provided overall request title.
- [ ] JSON text, Document identities, selected mode, auto/array choices, and valid pre-sort snapshot survive a label refresh.
- [ ] Existing right-side content retention still works when another left input is sent.
- [ ] Both editor-tab and separate-window rendering show the same session names.
- [ ] Closed sessions release dialog/listener resources and do not leak custom names into new sessions.
- [ ] Existing default/English/Korean/Japanese/Chinese bundle keys remain consistent after serialized edits.
- [ ] No reflection or internal component-field manipulation enters the implementation.

## Acceptance Criteria
- [ ] Rename an open comparison to "개발 서버" / "운영 서버"; both visible side headers change in both host modes.
- [ ] Rename to "배포 전" / "배포 후" without changing either JSON or the overall request title.
- [ ] Cancel preserves the previous names; clearing a custom name restores that side's localized default.
- [ ] Reusing the open comparison retains the custom names and right baseline while accepting a new left input.
- [ ] Label updates do not cause another sort, invalidate a still-valid manual restore, or overwrite a newer input.
- [ ] All stage checks and affected parent integration criteria have current evidence before whole-set completion.

## Open Questions
- None — labels are reversible session-local display data and the existing request contracts define their lifetime.
