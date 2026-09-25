# [feat] Add safe JSON diff sorting controls

## Work Type
feat

## Current State (As-Is)
- [confirmed] Use main at `7c5392e`, inspected on 2026-09-26, as the authoring baseline. Evidence: `git log -1`.
- [confirmed] `src/main/kotlin/com/livteam/jsoninja/services/JsonDiffService.kt`, `ActiveDiffContext` and `getOrCreateContext()`, retain both Documents while an existing host is open and replace only the left input when a new JSON is sent.
- [confirmed] `JsonDiffService.createDiffRequest()` attaches `SortJsonDiffKeysOnceAction` through `DiffUserDataKeys.CONTEXT_ACTIONS` and marks requests through `JsonDiffKeys`.
- [confirmed] `src/main/kotlin/com/livteam/jsoninja/diff/JsonDiffExtension.kt` captures a Boolean sort setting at viewer creation, schedules formatting after a 300ms delay, and rejects stale document/sequence results.
- [confirmed] `src/main/kotlin/com/livteam/jsoninja/actions/SortJsonDiffKeysOnceAction.kt` checks both document stamps and writes both results through one WriteCommandAction; it has no dedicated pre-sort restoration surface.
- [confirmed] `src/main/kotlin/com/livteam/jsoninja/settings/JsoninjaSettingsState.kt` defines `diffSortKeys=false` and routes persisted settings through synced/local stores plus legacy migration.
- [confirmed] `JsonDiffVirtualFile` and `JsonDiffRequestChain` currently propagate a Boolean sort value to request creation; this boundary must carry the session's new choices through to the final formatter.
- [confirmed] `src/test/kotlin/com/livteam/jsoninja/actions/ShowJsonDiffActionTest.kt` does not actually open the host in its display-mode checks; those checks do not prove the new toolbar or host behavior.
- [inferred] Retaining the current captured Boolean while adding a mode picker would allow UI state and later formatting to disagree. Confirm every option consumer and request-refresh path in Stage 1.

## Desired Outcome (To-Be)
- Expose "키 사전순" and "왼쪽 기준" directly in each JSONinja diff toolbar and visibly identify the selected mode.
- Allow automatic sorting to be enabled/disabled in that diff without a trip to Settings.
- Expose "배열 순서 무시" as a separate, unchecked-by-default option.
- Provide "정렬 되돌리기" for the latest eligible manual sorting operation without overwriting later user edits.
- Apply the selected options through the final two-document consumer in both editor-tab and separate-window hosts.
- Preserve existing host reuse, right-side content retention, 300ms scheduling, background computation, and stale-result protection.

## Scope
### In Scope
- Integrate child 01's alignment service into a single session-owned sorting route.
- Carry live choices through service, request data, request chain, virtual file, toolbar actions, and automatic formatting.
- Add one safe paired snapshot/restore path for manual sorting.
- Localize the new controls and their unavailable/disabled explanations in the existing message bundles.
- Verify approved items 1–4 and preserve items 7–8.
### Out of Scope
- [hard] Do not implement editor-selection entry or editable side titles here; children 03 and 04 own them.
- [hard] Do not add or modify automated test files, cases, fixtures, lint rules, or formatter configuration.
- [hard] Do not replace IntelliJ's diff renderer or change unrelated editor/clipboard formatting.
- [deferred] Cross-restart persistence of new mode/array choices, a persistent comparison history, multi-session management, and choosing identity keys for arrays.

## Constraints
- Start new sessions in KEY_ASCENDING mode with shouldIgnoreArrayOrder=false and shouldAutoSort initialized from the existing `diffSortKeys` value. Its existing default false remains false.
- Keep new toolbar overrides in the active comparison session; do not rewrite synced/local settings from transient diff controls. Existing Settings continues to provide the default for new sessions.
- Preserve `openDiff(displayMode, currentJson, defaultSortKeys)`, both `createDiffRequest` overloads, and the old Boolean semantic meaning for existing callers.
- Preserve the request-marker, sort-key, and change-guard keys in `JsonDiffKeys`; add a typed session route without repurposing those identifiers or applying the extension to unmarked IDE diffs.
- Store modes, automatic sorting, array option, generation, and restoration eligibility under one session owner. Keep the owner alive across host recreation and dispose its jobs/listeners when the session ends.
- Selecting a key mode applies it once; the existing one-shot action applies the currently selected mode. Enabling automatic sorting applies the current choices and then follows future edits. Disabling it stops future sorting without reconstructing original text.
- Toggling array alignment on applies the selected mode once. Toggling it off stops future array reordering; use the dedicated restore action for the preceding manual transformation when still eligible.
- Capture a pre-operation pair only for a successful, text-changing manual sort; automatic reformatting must not replace this snapshot. A later successful manual sort replaces it.
- Permit restoration only while both current Documents still match the captured post-sort stamps/text and session identity. Invalidate it after intervening user edits, automatic text changes, or replacement of the input pair; mode/label presentation changes alone do not corrupt the stored pair.
- Restore both pre-sort texts in one named WriteCommandAction and invalidate pending formatting generations. Suppress self-triggered re-sorting so the restore visibly sticks; later genuine user edits may follow the still-selected automatic policy.
- Preserve each invalid side's existing formatting fallback. Run pair-dependent alignment only when its required inputs are valid; show a localized non-destructive explanation for an unavailable manual operation and skip it quietly during typing.
- In LEFT_ORDER, the alignment operation must not rewrite the left text. Ordinary pretty formatting on genuine left-side edits may continue, but it must not alphabetize the reference document in that mode.
- When automatic LEFT_ORDER sorting is enabled, a genuine edit to either side schedules alignment from the latest pair after the existing debounce. A new left key order must propagate to the right even when the right Document itself has not changed; do not retain two independent one-input sort listeners.
- Changing either source Document or any relevant option invalidates pending paired work. Guard both document identities/stamps, the option generation, host/session lifetime, and cancellation before one atomic write.
- Keep parsing/alignment off the EDT, model reads under readAction when off-EDT, and writes on EDT. Retain the 300ms delay as a scheduling interval rather than promising completion within 300ms.
- Keep existing large-file warnings and cancellation choices on the route that performs work; do not bypass them with a new toolbar action.
- Keep state, capture, guarded application, and restore together as one atomic implementation unit; splitting them would temporarily allow stale or unrecoverable writes.
- Use only externally supported IntelliJ APIs on build 243+; inspect update/disposal support before wiring new controls. Do not copy reflection or internal-component access from Json Assistant.

## Related Files / Entry Points
- `src/main/kotlin/com/livteam/jsoninja/services/JsonDiffAlignmentService.kt` (proposed) — consume child 01's align/options/outcome contract.
- `src/main/kotlin/com/livteam/jsoninja/model/JsonDiffAlignment.kt` (proposed) — consume the two key modes and false-by-default array option.
- `src/main/kotlin/com/livteam/jsoninja/services/JsonDiffService.kt` — start at ActiveDiffContext and createDiffRequest to own session state and preserve the legacy facade.
- `src/main/kotlin/com/livteam/jsoninja/diff/JsonDiffKeys.kt` — add the typed session access route while preserving existing marker/guard contracts.
- `src/main/kotlin/com/livteam/jsoninja/diff/JsonDiffExtension.kt` — replace stale captured choices with guarded current-session consumption.
- `src/main/kotlin/com/livteam/jsoninja/actions/SortJsonDiffKeysOnceAction.kt` — delegate manual sorting to the common session route.
- `src/main/kotlin/com/livteam/jsoninja/actions` — colocate mode, automatic-sort, array-option, and restore controls with existing actions.
- `src/main/kotlin/com/livteam/jsoninja/ui/diff/JsonDiffRequestChain.kt` — carry the same session through request creation.
- `src/main/kotlin/com/livteam/jsoninja/ui/diff/JsonDiffVirtualFile.kt` — retain session/document identity in editor tabs.
- `src/main/kotlin/com/livteam/jsoninja/ui/diff/JsonDiffWindowDialog.kt` — retain session/document identity and disposal behavior in the separate window.
- `src/main/kotlin/com/livteam/jsoninja/settings/JsoninjaSettingsState.kt` — inspect diffSortKeys defaults, activeSettings, copySettingsFrom, and synced/local/legacy contracts; avoid unnecessary schema edits.
- `src/main/resources/messages` — update every existing LocalizationBundle properties variant for the new controls.
- `src/test/kotlin/com/livteam/jsoninja/services/JsonDiffServiceTest.kt` — run the existing request and semantic-sorting checks.
- `src/test/kotlin/com/livteam/jsoninja/services/JsonFormatterServiceTest.kt` — run existing formatting regressions after integrating the shared formatting route.
- `build.gradle.kts` — use existing compileKotlin, test, runIde, and skipWasmBuild support.
- `docs/coroutine-threading-standard.md` — follow lifecycle and paired stale-write protection.

## Execution Plan
### Stage 1 — Route live options through one comparison session
- Starts when: `src/main/kotlin/com/livteam/jsoninja/services/JsonDiffAlignmentService.kt` from child 01 exposes the verified align operation, immutable two-mode/array options, and explicit output/failure outcomes.
- Work: Trace all service-to-host-to-extension consumers and establish one session state route while retaining Boolean compatibility and new-session defaults.
- No-op when: None — the reviewed baseline has only captured Boolean sorting and no session-visible mode/array/restore controls.
- No-op handoff: None — if the complete requested state route is already present at pickup, return evidence to the parent and re-evaluate active children before proceeding.
- Deliverable: `src/main/kotlin/com/livteam/jsoninja/services/JsonDiffService.kt` with one live session contract, legacy facade compatibility, mode/auto/array choices, generation, and shared Document identity.
- Verify: `Inspect service/request/host/extension option flow`; Inputs: JsonDiffService, JsonDiffKeys, JsonDiffRequestChain, JsonDiffVirtualFile, JsonDiffWindowDialog, and JsonDiffExtension; Expected: every request reaches the same mutable session and Boolean-only callers retain alphabetical semantics.
- Ends when:
  - [ ] No final consumer uses an obsolete captured mode after a session option changes.
  - [ ] Both host types share the same Documents and session owner.
  - [ ] Defaults and persisted settings remain compatible.
- Handoff: Stage 2 receives the session contract and verified request/host propagation route.
- Replan when: Supported platform APIs cannot carry/update the session without losing Documents or existing callers; stop successors and return the incompatibility to the parent.

### Stage 2 — Expose controls with guarded sorting and restoration
- Starts when: Stage 1 supplies the live session route and child 01 supplies the computation boundary.
- Work: Add localized controls, integrate manual/automatic pair alignment, and implement one guarded paired snapshot/restore transaction.
- Deliverable: `src/main/kotlin/com/livteam/jsoninja/services/JsonDiffService.kt` with the session commands consumed by toolbar actions and automatic formatting, including snapshot eligibility and change-generation rules.
- Verify: `./gradlew compileKotlin`; Inputs: all changed controls, service/request/host classes, and existing Kotlin callers from the repository root; Expected: exit 0 with unchanged legacy signatures.
- Ends when:
  - [ ] Manual and automatic actions use the same options and computation boundary.
  - [ ] Restoration cannot overwrite later input or immediately trigger the same sorting again.
  - [ ] Invalid input, superseding options, host closure, and cancellation have non-destructive exits.
- Handoff: Stage 3 receives integrated controls on both hosts and the safe restoration route.
- Replan when: Capture and restoration require an unapproved destructive overwrite or a storage-format change; stop that route and return to the parent for bounded correction.

### Stage 3 — Verify the visible sorting workflow
- Starts when: Stage 2 provides both host integrations and all localized controls.
- Work: Run the existing scoped checks and exercise the already-approved examples in the sandbox. Record actual observations separately from code inspection.
- Deliverable: `src/main/kotlin/com/livteam/jsoninja/services/JsonDiffService.kt` with a verified session-command contract for mode selection, auto sorting, optional array alignment, guarded restoration, host reuse, and explicit-input opening.
- Verify: `./gradlew test -PskipWasmBuild=true --tests 'com.livteam.jsoninja.services.JsonDiffServiceTest' --tests 'com.livteam.jsoninja.services.JsonFormatterServiceTest'`; Inputs: unchanged existing suites on the integrated checkout, followed by `./gradlew runIde -PskipWasmBuild=true` and the whole-child Acceptance Criteria; Expected: exit 0 for the test command and the specified visible results in both hosts.
- Ends when:
  - [ ] Required command results and visible behavior observations are recorded with the IDE build and affected revision.
  - [ ] The full side-effect checklist has an actual result; unavailable UI execution is recorded as pending rather than passed.
- Handoff: Children 03 and 04 receive `src/main/kotlin/com/livteam/jsoninja/services/JsonDiffService.kt` with the compatible explicit-input facade and shared session/document contract.
- Replan when: A required proof fails or cannot establish the final behavior; stop successors, correct this child's affected route, re-verify it, and update parent handoffs before resuming.

## Side Effect Checkpoints
- [ ] A new session created with legacy diffSortKeys=false remains unsorted automatically; true still starts with alphabetical automatic sorting.
- [ ] Existing jsoninja.xml, jsoninja-local.xml, sync preference, and legacy project migration keep the same fields and meanings.
- [ ] Query, tool-window formatting, and unrelated IDE diffs do not acquire the new session sorting behavior.
- [ ] Sending another tool-window JSON to an open comparison replaces the left side and retains the right side; it clears obsolete restoration eligibility.
- [ ] Both editor-tab and separate-window paths retain large-file warnings and cancel work on disposal.
- [ ] Pending results cannot overwrite either newer text or newer choices.
- [ ] Undo grouping remains available through named WriteCommandAction operations; a dedicated restore does not discard intervening edits.
- [ ] Default/English/Korean/Japanese/Chinese message bundles expose corresponding keys without raw missing-key labels.

## Acceptance Criteria
- [ ] On both host types, left {"id":7,"status":"active","name":"Alice"} and right {"name":"Alice","id":7,"status":"inactive"} keep the status difference: LEFT_ORDER preserves left text and produces right id/status/name, while KEY_ASCENDING produces id/name/status on both sides.
- [ ] The toolbar offers "키 사전순", "왼쪽 기준", automatic sorting, "배열 순서 무시", and "정렬 되돌리기" without opening Settings.
- [ ] With left [{"id":1},{"id":2}] and right [{"id":2},{"id":1}], the unchecked option preserves the order difference and checking it aligns equal records only.
- [ ] Immediately after manually sorting {"z":1,"a":2}, restore returns the pre-sort pair; if a user edits either side first, the dedicated restore is unavailable and the edit remains.
- [ ] With automatic sorting active, restoring does not instantly re-sort its own write; a later genuine edit uses the selected policy.
- [ ] Rapidly replacing {"count":1} with {"count":2} leaves count 2 after pending work settles; changing the selected mode similarly rejects results from the previous mode.
- [ ] With automatic LEFT_ORDER active on the same id/status/name pair, reordering the left keys to status/id/name propagates that order to the unchanged right input without changing either side's values.
- [ ] Opening a second tool-window input reuses the open comparison, retains the right baseline, and works in both supported display modes.
- [ ] Treat 300ms as the debounce interval only and retain background parsing/alignment; do not report an unmeasured performance improvement.

## Open Questions
- None — preserve existing defaults and use reversible session-local choices; later-edit protection follows the approved latest-input safety requirement.
