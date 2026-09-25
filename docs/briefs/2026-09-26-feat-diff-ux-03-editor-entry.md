# [feat] Open JSON diff from editor selections

## Work Type
feat

## Current State (As-Is)
- [confirmed] Use main at `7c5392e`, inspected on 2026-09-26, as the authoring baseline. Evidence: `git log -1`.
- [confirmed] `src/main/kotlin/com/livteam/jsoninja/actions/ShowJsonDiffAction.kt`, `openDiffForCurrentJson()`, obtains its left input from `JsonHelperUtils.getCurrentJsonFromToolWindow()` rather than the invoking editor selection.
- [confirmed] `src/main/kotlin/com/livteam/jsoninja/services/JsonDiffService.kt`, `openDiff()`, already accepts an explicit currentJson string and preserves the right Document when reusing an open host.
- [confirmed] `src/main/kotlin/com/livteam/jsoninja/actions/editor/BaseEditorJsonAction.kt` captures selectedText or document text and checks document/selection staleness, but its transform route writes into the source editor.
- [confirmed] `src/main/resources/META-INF/plugin.xml` registers the existing editor transformations in `com.livteam.jsoninja.action.group.EditorContextMenu` under `EditorPopupMenu`.
- [confirmed] `src/test/kotlin/com/livteam/jsoninja/actions/ShowJsonDiffActionTest.kt` covers existing action availability/settings but does not exercise actual display-mode opening.
- [inferred] Reusing BaseEditorJsonAction by inheritance would route a compare-only feature through source mutation. Confirm the action boundary in Stage 1 and reuse its capture/validation pattern without its document-write behavior.

## Desired Outcome (To-Be)
- Start a comparison directly from valid JSON selected in the current editor.
- When there is no selection, use the entire current editor only if its content is valid JSON under the existing validation rules.
- Put the captured input into the left diff Document without changing the source document or requiring a tool-window copy.
- Reuse the session controls and display behavior delivered by child 02.

## Scope
### In Scope
- Add one editor context action with selection-first input capture.
- Validate the captured input and route it to the compatible explicit-input diff service facade.
- Register the action in the existing JSONinja editor context group and localize its text/error feedback.
- Preserve current tool-window comparison actions and host reuse.
### Out of Scope
- [hard] Do not change BaseEditorJsonAction into a general compare/transform abstraction or mutate the source editor.
- [hard] Do not silently fall back from an invalid selection to the whole file or to unrelated tool-window content.
- [hard] Do not add or modify automated test files, cases, fixtures, lint rules, formatter configuration, or keyboard shortcuts.
- [deferred] Comparing multiple selections, batch file comparison, clipboard auto-import, and opening multiple independent diff sessions.

## Constraints
- Keep existing action IDs, Tools menu routes, and the editor transformation group functional.
- Prefer the existing explicit-input `JsonDiffService.openDiff(displayMode, currentJson, defaultSortKeys)` facade; do not introduce a second host-management implementation.
- Use the existing display-mode setting and current-session policy without resetting the chosen mode, array option, or right baseline when a host is reused.
- Resolve input once from the invoking editor: selected text when present, otherwise the complete document. Validate that exact captured text using the existing JSON/JSON5 boundary.
- Treat a blank or invalid selection as unavailable input and use localized feedback; do not compare a different input instead.
- Keep update() inexpensive; do not parse a whole editor repeatedly during action presentation updates.
- Read editor/selection state under the appropriate platform access context. Perform expensive validation off the EDT and discard stale completion if the source editor is disposed or its captured document/selection no longer matches.
- Keep the source document content, modification stamp, and selection unchanged throughout a successful comparison opening.
- Use the existing primary-selection behavior; do not invent multi-caret merging.
- Place the new label "선택 JSON 비교" in localization resources and use an appropriate whole-document label when no selection exists.
- Retain build 243+ compatibility and the existing Kotlin/JVM toolchain; use externally supported action/data-context APIs only.

## Related Files / Entry Points
- `src/main/kotlin/com/livteam/jsoninja/actions/editor/EditorShowJsonDiffAction.kt` (proposed) — implement the selection-first compare-only action.
- `src/main/kotlin/com/livteam/jsoninja/actions/editor/BaseEditorJsonAction.kt` — read its source-capture, validation, and staleness patterns without inheriting source mutation.
- `src/main/kotlin/com/livteam/jsoninja/actions/ShowJsonDiffAction.kt` — preserve existing tool-window entry and display-mode fallback.
- `src/main/kotlin/com/livteam/jsoninja/services/JsonDiffService.kt` — consume child 02's compatible explicit-input facade and session reuse.
- `src/main/kotlin/com/livteam/jsoninja/services/JsonFormatterService.kt` — reuse isValidJson without introducing a competing parser.
- `src/main/kotlin/com/livteam/jsoninja/settings/JsoninjaSettingsState.kt` — use current diffDisplayMode and diffSortKeys defaults.
- `src/main/resources/META-INF/plugin.xml` — add the action to the existing EditorContextMenu group.
- `src/main/resources/messages` — localize the new context label and reuse existing invalid-JSON feedback where suitable.
- `src/test/kotlin/com/livteam/jsoninja/actions/ShowJsonDiffActionTest.kt` — run the existing comparison action regression checks.
- `build.gradle.kts` — use the established compileKotlin, test, runIde, and skipWasmBuild paths.

## Execution Plan
### Stage 1 — Confirm a compare-only editor route
- Starts when: `src/main/kotlin/com/livteam/jsoninja/services/JsonDiffService.kt` from child 02 provides the verified explicit-input facade, shared session choices, retained right Document, and both host modes.
- Work: Pin the selection/no-selection precedence, source-staleness boundary, localized feedback, and context-menu placement without changing the source document.
- No-op when: None — the reviewed baseline comparison action reads tool-window content and lacks this selection-first route.
- No-op handoff: None — if pickup finds the entire approved editor workflow already implemented and verified, return that evidence to the parent for topology recalculation.
- Deliverable: The input-capture and service-call contract for `src/main/kotlin/com/livteam/jsoninja/actions/editor/EditorShowJsonDiffAction.kt` (proposed).
- Verify: `Inspect editor action capture and plugin group wiring`; Inputs: BaseEditorJsonAction.actionPerformed, ShowJsonDiffAction.openDiffForCurrentJson, JsonDiffService.openDiff, and plugin.xml EditorContextMenu; Expected: one selection-first input route with no source write and no tool-window fallback.
- Ends when:
  - [ ] The action has a concrete source input, destination, and stale-result rule.
  - [ ] Its platform API and registration route are confirmed for the minimum supported IDE.
- Handoff: Stage 2 receives the capture/validation contract and confirmed menu location.
- Replan when: The requested editor surface needs unsupported APIs or input ownership differs from the verified selection contract; stop this child and return the bounded compatibility problem to the parent.

### Stage 2 — Implement and register editor comparison
- Starts when: Stage 1 has confirmed the input contract and child 02's facade is unchanged.
- Work: Implement the compare-only action, localized selection/whole-document labels, and registration in the existing editor group.
- Deliverable: `src/main/kotlin/com/livteam/jsoninja/actions/editor/EditorShowJsonDiffAction.kt` (proposed), wired through plugin.xml to the existing diff service.
- Verify: `./gradlew compileKotlin`; Inputs: the new action and all existing comparison/editor action callers from the repository root; Expected: exit 0 without modifying the legacy tool-window route.
- Ends when:
  - [ ] The new action calls the shared service with the exact validated input.
  - [ ] Source mutation is absent from the action.
  - [ ] Message keys exist in all current localization variants.
- Handoff: Stage 3 receives the registered action on the integrated session implementation.
- Replan when: Reusing the service resets the right baseline or selected options; stop this child, return the facade defect to child 02, and re-verify that handoff before continuing.

### Stage 3 — Verify selection-first behavior
- Starts when: Stage 2 provides the compiled and registered editor action.
- Work: Run existing action checks and exercise the approved editor example in the sandbox, including the no-selection and invalid-input boundaries.
- Deliverable: `src/main/kotlin/com/livteam/jsoninja/actions/editor/EditorShowJsonDiffAction.kt`, registered and verified to open the correct left input without source mutation.
- Verify: `./gradlew test -PskipWasmBuild=true --tests 'com.livteam.jsoninja.actions.ShowJsonDiffActionTest'`; Inputs: the existing suite, then `./gradlew runIde -PskipWasmBuild=true` with a selected {"error":"E01"} and tool-window {"userId":7}; Expected: test exit 0 and the new action opens error E01 on the left without a tool-window copy or source edit.
- Ends when:
  - [ ] The accepted selection example, no-selection route, and source-preservation checks have actual observations.
  - [ ] The evidence distinguishes existing test results from the new menu's sandbox behavior.
- Handoff: Parent integration receives `src/main/kotlin/com/livteam/jsoninja/actions/editor/EditorShowJsonDiffAction.kt` and its registration/input contract before child 04's serialized message edits begin.
- Replan when: Any required observation fails or cannot be made; keep this child incomplete, correct its owning action or return a service failure to child 02, and re-verify before global completion.

## Side Effect Checkpoints
- [ ] Existing tool-window comparison still reads the active tool-window JSON rather than being silently redirected to the current file.
- [ ] Existing prettify, uglify, escape, unescape, and Copy JSON Query context actions retain their registration and behavior.
- [ ] The source file's text and modification stamp remain unchanged after comparison opens.
- [ ] Reusing an open diff retains its right baseline and session choices while replacing only the intended left input.
- [ ] Missing project/editor, disposed input, blank selection, and invalid JSON exit without opening misleading content.
- [ ] The new action does not parse large documents in update(), bypass existing large-file protections, or apply stale validation results.

## Acceptance Criteria
- [ ] With {"error":"E01"} selected and {"userId":7} in the tool window, invoking "선택 JSON 비교" opens error E01 on the left and leaves the tool-window data unchanged.
- [ ] A whole valid JSON document opens when there is no selection; an invalid selected fragment does not trigger whole-document or tool-window fallback.
- [ ] The action works with the existing editor-tab and separate-window preference and reuses the right baseline of an already-open comparison.
- [ ] No source editor text or selection changes occur as a side effect of comparison opening.
- [ ] Complete every stage and side-effect checkpoint with actual evidence before the parent treats this entry route as integrated.

## Open Questions
- None — selection precedence and source preservation are defined by the approved examples and existing action patterns.

