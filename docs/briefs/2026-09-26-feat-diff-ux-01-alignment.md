# [feat] Add reference-based JSON diff alignment

## Work Type
feat

## Current State (As-Is)
- [confirmed] Use main at `7c5392e`, inspected on 2026-09-26, as the authoring baseline; recheck changed target files at pickup. Evidence: `git log -1` and `gradle.properties` declare JSONinja 1.14.0.
- [confirmed] `src/main/kotlin/com/livteam/jsoninja/services/JsonFormatterService.kt`, `sortedMapper` and `formatJson()`, implements recursive object-key ordering through Jackson's `ORDER_MAP_ENTRIES_BY_KEYS`; array element order is retained.
- [confirmed] `src/main/kotlin/com/livteam/jsoninja/actions/SortJsonDiffKeysOnceAction.kt`, `actionPerformed()`, formats the two documents independently with the sort override enabled.
- [confirmed] `src/main/kotlin/com/livteam/jsoninja/services/JsonObjectMapperService.kt`, `objectMapper`, owns JSON5 parsing and BigDecimal configuration; `JsonFormatterService.formatJson()` also owns placeholder restoration and original-input fallback.
- [confirmed] `src/test/kotlin/com/livteam/jsoninja/services/JsonDiffServiceTest.kt`, `testValidateAndFormatWithSemanticSorting()`, establishes the existing alphabetical behavior of the Boolean semantic flag.
- [inferred] Reference-based alignment needs both inputs while the current formatter accepts one. Confirm the call boundary in Stage 1 before choosing how to reuse formatting without changing non-diff consumers.

## Desired Outcome (To-Be)
- Provide one diff-specific computation boundary for alphabetical ordering and left-reference ordering.
- In left-reference mode, preserve the left input text and reorder only corresponding right object keys, including nested objects.
- Offer array order alignment as an explicit option that is false by default.
- Match array elements by complete JSON content while ignoring object-key order; retain every value, duplicate, missing key, and unmatched element.
- Supply the controls brief with complete output texts and an explicit outcome that cannot be mistaken for a successful alignment after invalid input.

## Scope
### In Scope
- Add a computation service adjacent to the existing JSON services.
- Define the small shared mode/options/result model consumed by the service and the next brief.
- Reuse the established parser and formatter boundaries for JSON/JSON5 input and output.
- Preserve the existing alphabetical mode when the new array option is disabled.
### Out of Scope
- [hard] Do not change query, schema, type conversion, or general editor formatting semantics.
- [hard] Do not mutate IDE Documents, source files, or diff sessions from the computation service.
- [hard] Do not add or modify automated test files, cases, fixtures, lint rules, or formatter configuration.
- [deferred] Identity-key matching such as matching changed records by `id`, fuzzy matching, natural-order sorting, and an independent structural diff viewer.
- [deferred] New parser capabilities or general numeric/JSON5 fidelity repairs not caused by this work.

## Constraints
- Keep `JsonFormatterService.formatJson(json, formatState, sortOverride)` and `JsonDiffService.validateAndFormat(json, semantic)` compatible; `semantic=true` continues to mean alphabetical key sorting.
- Treat `JsonDiffSortMode.KEY_ASCENDING` and `JsonDiffSortMode.LEFT_ORDER` as the cross-brief mode vocabulary; carry `shouldIgnoreArrayOrder` separately with default false.
- Expose an `align` operation in `JsonDiffAlignmentService` that accepts captured left/right text and immutable options, returning left/right result text plus explicit applied/no-change/invalid-input information. Keep this boundary free of editor and session ownership.
- In LEFT_ORDER, return the left text byte-for-byte unchanged. In each paired right object, emit shared keys in left order and then right-only keys in their original relative order; never invent missing keys.
- With array alignment off, retain element positions and recurse only through corresponding positions/paths. With it on, consume each equal right element at most once in left order, then append unmatched right elements in their original relative order.
- For equality, ignore object field order, preserve nested-array order during an individual equality check, and use existing parsed JSON value/type semantics without string/number/null coercion. Equal IDs alone do not constitute a match.
- Recurse through known paired objects or matched array elements; do not infer record identity for unmatched elements.
- Keep pair-dependent operations atomic on invalid/blank input: return the original pair and an explicit unavailable result. Retain the formatter's existing independent-side fallback for KEY_ASCENDING with array alignment off.
- Preserve parsed numeric values, nulls, escaping, and supported placeholder behavior. If the existing placeholder pipeline cannot support the new mode without changing a shared contract, stop that integration and replan with the parent.
- Keep computation cancellable and suitable for Dispatchers.Default. Bound the matching strategy to avoid blindly copying an uninterruptible quadratic UI-thread loop; choose indexing/equality details within the same deterministic semantics.
- Use existing dependencies only. Preserve the current minimum IDE build 243 and JVM toolchain; do not raise them for this feature.

## Related Files / Entry Points
- `src/main/kotlin/com/livteam/jsoninja/services/JsonDiffAlignmentService.kt` (proposed) — own the new two-input alignment computation and the handoff consumed by child 02.
- `src/main/kotlin/com/livteam/jsoninja/model/JsonDiffAlignment.kt` (proposed) — colocate the small mode, immutable options, and outcome model.
- `src/main/kotlin/com/livteam/jsoninja/services/JsonFormatterService.kt` — inspect `formatJson()` and reuse formatting, sorting, placeholder, and fallback behavior without changing other consumers.
- `src/main/kotlin/com/livteam/jsoninja/services/JsonObjectMapperService.kt` — reuse the shared JSON/JSON5 parser configuration.
- `src/main/kotlin/com/livteam/jsoninja/services/TemplatePlaceholderSupport.kt` — inspect supported placeholder extraction/restoration before parsing new diff modes.
- `src/main/kotlin/com/livteam/jsoninja/actions/SortJsonDiffKeysOnceAction.kt` — read the current independent-side call shape that child 02 will replace for session-aware requests.
- `src/test/kotlin/com/livteam/jsoninja/services/JsonDiffServiceTest.kt` — preserve the existing Boolean semantic sorting contract.
- `src/test/kotlin/com/livteam/jsoninja/services/JsonFormatterServiceTest.kt` — identify existing formatter regression checks if shared code must change.
- `gradle.properties` — retain the declared platform range and version.
- `docs/coroutine-threading-standard.md` — use its computation/cancellation boundaries when the service is integrated.

## Execution Plan
### Stage 1 — Stabilize the alignment contract
- Starts when: The authoring baseline and the existing one-input formatter are available in the current checkout.
- Work: Confirm parser/placeholder reuse and define the two modes, array option, equality rules, invalid-input outcome, and no-change outcome without altering existing callers.
- No-op when: None — the reviewed baseline lacks the requested left-reference and array-alignment boundary.
- No-op handoff: None — if pickup discovers the complete feature already exists, return current evidence to the parent and recalculate the topology before continuing.
- Deliverable: `src/main/kotlin/com/livteam/jsoninja/model/JsonDiffAlignment.kt` (proposed), defining the mode/options/outcome contract needed by Stage 2.
- Verify: `Inspect the new declarations and the existing formatter call chain`; Inputs: the proposed model, JsonFormatterService.formatJson, JsonObjectMapperService.objectMapper, and TemplatePlaceholderSupport; Expected: exactly two explicit key modes, a false-by-default array option, preservation/failure rules, and no Document dependency.
- Ends when:
  - [ ] Every option and outcome has an unambiguous consumer meaning.
  - [ ] Reuse of JSON5 and placeholder handling has a bounded implementation route.
- Handoff: Stage 2 receives the immutable options and explicit output/failure contract.
- Replan when: Supporting the requested inputs would require changing shared parser semantics or losing values; stop dependent work and return the conflict to the parent for bounded correction.

### Stage 2 — Implement deterministic pair alignment
- Starts when: Stage 1 provides the model and a confirmed parser/formatter reuse route.
- Work: Implement key ordering and optional array alignment, preserving original input on unavailable pair-dependent operations and propagating cancellation.
- Deliverable: `src/main/kotlin/com/livteam/jsoninja/services/JsonDiffAlignmentService.kt` (proposed), with an align operation taking two texts/options and returning two texts plus applied/no-change/invalid-input status.
- Verify: `./gradlew compileKotlin`; Inputs: the new model/service and all existing Kotlin consumers from the repository root; Expected: exit 0 with the old formatter and diff Boolean signatures still callable.
- Ends when:
  - [ ] The service performs no UI or Document mutation.
  - [ ] Key and array traversal preserve values and multiplicity.
  - [ ] Invalid-input and cancellation paths cannot report a successful partial pair transformation.
- Handoff: Stage 3 receives the compilable service and its stable options/outcome model.
- Replan when: The implementation requires changing general formatter output or a new dependency; stop and return the dependency/contract choice to the parent.

### Stage 3 — Establish the computation handoff
- Starts when: Stage 2 provides the compilable service and its model.
- Work: Trace the accepted key and array examples through the final result construction; distinguish code inspection from any executed observation. If shared formatter/parser code changed, run `./gradlew test -PskipWasmBuild=true --tests 'com.livteam.jsoninja.services.JsonFormatterServiceTest'` from the repository root and require exit 0; otherwise retain the existing boundary without an unrelated rerun.
- Deliverable: `src/main/kotlin/com/livteam/jsoninja/services/JsonDiffAlignmentService.kt`, with the defined align/options/outcome boundary verified against the examples and ready for UI integration.
- Verify: `Bounded trace of JsonDiffAlignmentService.align`; Inputs: left {"id":7,"status":"active","name":"Alice"} and right {"name":"Alice","id":7,"status":"inactive"} in both modes, plus left [{"id":1},{"id":2}] and right [{"id":2},{"id":1}] with the array option off/on; Expected: unchanged left text in LEFT_ORDER, the stated corresponding key order, and right array reordering only when opted in.
- Ends when:
  - [ ] The accepted examples and value-preservation paths have recorded, correctly labeled evidence.
  - [ ] The computation handoff states explicitly that final visible behavior remains to be exercised in child 02.
- Handoff: `docs/briefs/2026-09-26-feat-diff-ux-02-controls.md` receives `src/main/kotlin/com/livteam/jsoninja/services/JsonDiffAlignmentService.kt` and its mode/options/outcome contract.
- Replan when: Any trace contradicts the accepted examples; stop child 02, correct this service, re-verify the affected evidence, and update the parent handoff before continuing.

## Side Effect Checkpoints
- [ ] Existing KEY_ASCENDING behavior still orders object keys recursively without reordering arrays when the option is false.
- [ ] No new mode changes the parsing/output behavior of non-diff formatter consumers.
- [ ] Right-only keys and unmatched array elements remain present; missing keys remain missing and duplicate element counts are preserved.
- [ ] Supported JSON5, precise numeric values, and placeholders follow the existing parser/formatter contracts.
- [ ] A blank/invalid paired input never causes the other side to be silently replaced by an empty or partial structure.
- [ ] Cancellation reaches callers instead of becoming a normal alignment result.

## Acceptance Criteria
- [ ] For the accepted id/status/name example, LEFT_ORDER preserves left text and produces right key order id, status, name with status still inactive.
- [ ] For the same example, KEY_ASCENDING yields id, name, status on both sides while retaining the differing status values.
- [ ] For the accepted two-record array example, disabled alignment preserves [2,1] on the right and enabled alignment yields [1,2] without changing record contents.
- [ ] The service contract exposes success/no-change/unavailable distinctions for child 02 and preserves the old alphabetical Boolean contract.
- [ ] Complete the stage checks and side-effect checkpoints before handing the service to child 02; do not claim UI execution from source traces.

## Open Questions
- None — the approved examples define the behavior; implementation choices are bounded above.
