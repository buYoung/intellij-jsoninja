# [refactor] Audit and normalize coroutine usage and threading model

## Execution Status (2026-05-26)
- 완료: `GenerateSchemaJsonTabPresenter` 계열의 `Project?` 경로를 제거하고 `JsoninjaCoroutineScopeService.createChildScope()`만 사용하도록 정리함
- 완료: `OnboardingService.startTutorial`의 중첩 `launch`를 제거하고 단일 `Dispatchers.EDT` launch로 단순화함
- 완료: 감사 문서 `docs/coroutine-threading-audit.md` 추가
- 검증: `build/test-results/test` 아래 13개 XML 결과 파일이 모두 `failures="0"` 및 `errors="0"` 상태임
- 미수행: 브리프에 적힌 수동 smoke 검증과 IDE 로그 확인

## Work Type
refactor

## Current State (As-Is)
- Commit `3542b67` migrated IntelliJ async flows (`invokeLater` / `executeOnPooledThread` / `ReadAction`) to coroutines across ~17 files, introducing `JsoninjaCoroutineScopeService` with two acquisition styles that are now used inconsistently.
- **Shared application scope, fire-and-forget** via `project.service<JsoninjaCoroutineScopeService>().launch { ... }` with no stored `Job` and no disposal tie: `FoldingAwareEditorTextField.kt:67`, `actions/OpenJsonFileAction.kt:46`, `actions/GenerateRandomJsonAction.kt:38`. `JsonEditorTooltipListener.kt:79` uses the shared scope but stores `tooltipJob`.
- **Per-component child scope** via `createChildScope()` stored and cancelled on dispose: `JsonEditorTextPresenter`, `JsonQueryPresenter`, `JsonTabsPresenter`, `JsonToTypeDialogPresenter`, `TypeToJsonDialogPresenter`, `LoadJsonFromApiDialogPresenter`, `OnboardingStep8DiffTooltipController`, `JsonDiffExtension` (line 279).
- `GenerateSchemaJsonTabPresenter.kt:42-43` falls back to a raw `CoroutineScope(SupervisorJob() + Dispatchers.Default)` when `project` is null — an orphan scope with no cancellation owner.
- EDT switches are inconsistent about modality: some sites use `withContext(Dispatchers.EDT + ModalityState.any().asContextElement())` (`ConvertPreviewExecutor`, `GenerateSchemaJsonTabPresenter`, `LoadJsonFromApiDialogPresenter`, `JsonToTypeDialogPresenter`), others use bare `withContext(Dispatchers.EDT)` (`JsonEditorTextPresenter:78`, `FoldingAwareEditorTextField:82`, `JsonTabsPresenter:118`, `GenerateRandomJsonAction`, `JsonQueryPresenter`, `OnboardingStep8DiffTooltipController`).
- `FoldingAwareEditorTextField.kt:98` calls `PsiDocumentManager.commitDocument` from inside a background `readAction`, which is inconsistent with the platform's EDT contract for commit (the specific symptom is owned by the fix brief, but the pattern is audited here).
- `OnboardingService.kt:50-57` nests a `coroutineScope.launch` inside another `launch` on the shared scope.
- Older threading primitives still coexist (legitimately or not): `WriteCommandAction`/`runWriteCommandAction` in `JsonDiffService`, `ConvertResultUtils`, `BaseEditorJsonAction`, `SortJsonDiffKeysOnceAction`, `GenerateSchemaJsonTabPresenter`, `JsonEditorTextPresenter`; `Alarm` in `JsonDiffExtension`; `SwingUtilities.invokeLater` in `OnboardingTutorialDialogView`; `ApplicationManager.runReadAction` in `JsonDocumentFactory`.

## Behavior Contract
- **Locked behavior:** every migrated flow keeps its observable result and ordering — Open file inserts file content into a tab; Generate random JSON produces and inserts JSON; JSON query updates results; convert-type preview debounces and shows the latest preview; schema generation fetches/parses/inserts; diff updates; onboarding tooltips appear. No user-visible behavior change is intended by this refactor.
- **Contract artifacts:** existing tests under `src/test/kotlin/com/livteam/jsoninja` (action, formatter, query, schema, UI-component, diff, tree-sitter). `./gradlew test` is the regression net; `./gradlew compileKotlin` is the type-safety baseline (AGENTS.md).
- **Verification method:** full test suite passes unchanged, plus manual smoke of each audited flow in the test IDE (build 261). Where tests do not cover a flow, note it in Open Questions rather than asserting coverage.
- **Threading rules of record:** AGENTS.md §"Threading Rules" table (Background→UI, Background→Write+Undo, Write+Undo EDT, Write-only EDT) states the intent — which read/write/EDT phase each kind of work belongs in. Note the table currently expresses these in legacy primitives (`executeOnPooledThread`/`invokeLater`); map each coroutine site to the *phase intent* (background compute off-EDT, UI/write back on EDT via `WriteCommandAction`/`Dispatchers.EDT`), and propose extending the table with coroutine equivalents as part of this work.

## Desired Outcome (To-Be)
- A documented audit of every coroutine launch site and every remaining legacy threading primitive, classifying each as correct / needs-fix, with rationale tied to the AGENTS.md threading rules. The audit is delivered as a written Markdown document under `docs/` (e.g. `docs/coroutine-threading-audit.md`) — a table keyed by file:site → classification → mapped phase intent → action — so the result is reviewable independent of the code changes.
- Scope acquisition is consistent and intentional: lifecycle-bound work uses a `createChildScope()` cancelled on dispose; truly transient one-shot work on the shared scope is justified and, where staleness matters, tracks/cancels its `Job`.
- EDT modality handling is consistent — sites that update UI from dialogs/modal contexts use the correct `ModalityState`, and the choice (with vs without `asContextElement()`) is deliberate rather than incidental.
- No orphan scopes without a cancellation owner (resolve `GenerateSchemaJsonTabPresenter` null-project fallback).
- Platform-phase-sensitive calls (e.g. `commitDocument`, write actions, PSI access) run in the correct read/write/EDT phase per platform contract.

## Scope
### In Scope
- Audit all coroutine usage: `FoldingAwareEditorTextField`, `JsonEditorTextPresenter`, `JsonEditorTooltipListener`, `JsonQueryPresenter`, `JsonTabsPresenter`, `ConvertPreviewExecutor`, `JsonToTypeDialogPresenter`, `TypeToJsonDialogPresenter`, `GenerateSchemaJsonTabPresenter`, `LoadJsonFromApiDialogPresenter`, `OnboardingService`, `OnboardingStep8DiffTooltipController`, `JsonDiffExtension`, `OpenJsonFileAction`, `GenerateRandomJsonAction`, `JsoninjaOnboardingStartupActivity`, `JsoninjaCoroutineScopeService`.
- Audit remaining legacy threading sites for correctness/consistency: `JsonDiffService`, `ConvertResultUtils`, `BaseEditorJsonAction`, `SortJsonDiffKeysOnceAction`, `JsonDocumentFactory`, `OnboardingTutorialDialogView`.
- Normalize scope ownership, modality handling, and read/write/EDT phase placement to match AGENTS.md threading rules.
- Resolve the orphan-scope fallback in `GenerateSchemaJsonTabPresenter`.
### Out of Scope
- [hard] The folding/editor regression fix (debounce, `commitDocument` threading in `FoldingAwareEditorTextField`, undo/redo) — owned by `docs/briefs/2026-05-26-fix-folding-editor-regression.md`. Audit may flag the folding site, but the change there is made by the fix brief first; this brief consumes that result.
- [hard] Behavior changes to any feature (this is behavior-preserving refactor only).
- [deferred] Introducing new abstractions over `JsoninjaCoroutineScopeService` beyond what consistency requires.

## Constraints
- Behavior-preserving: no externally observable change to any flow (see Behavior Contract).
- Conform to AGENTS.md §"Threading Rules" and §"Threading and Disposal Boundaries"; do not reintroduce raw `Thread`/`ExecutorService`.
- Run this brief **after** the fix brief lands, to avoid editing `FoldingAwareEditorTextField` folding logic concurrently (shared hotspot).
- Keep `JsoninjaCoroutineScopeService` public shape unless a change is required for correct disposal.

## Related Files / Entry Points
- `src/main/kotlin/com/livteam/jsoninja/services/JsoninjaCoroutineScopeService.kt` — start here: defines `launch` (shared) vs `createChildScope`; the audit's central abstraction.
- `src/main/kotlin/com/livteam/jsoninja/ui/dialog/generateJson/schema/GenerateSchemaJsonTabPresenter.kt:42-43` — orphan `CoroutineScope` fallback to resolve.
- `src/main/kotlin/com/livteam/jsoninja/services/OnboardingService.kt:50-57` — nested launch on shared scope to review.
- `src/main/kotlin/com/livteam/jsoninja/diff/JsonDiffExtension.kt` — reference implementation of debounce + child scope + readAction/EDT split; the pattern to standardize toward.
- `src/main/kotlin/com/livteam/jsoninja/ui/dialog/convertType/ConvertPreviewExecutor.kt` — reference for `previewJob` cancellation + `ModalityState.any().asContextElement()`.
- Commit `3542b67` — the migration introducing all the above; baseline for the audit diff.
- `AGENTS.md` §"Threading Rules" / §"Threading and Disposal Boundaries" — the contract each site is checked against.

## Side Effect Checkpoints
- [ ] All migrated flows still produce identical observable results (open file, generate, query, convert preview, schema generation, diff, onboarding, tooltips).
- [ ] No coroutine scope outlives its owner: closing tabs/dialogs/editors cancels their jobs (no leaks, no callbacks after dispose).
- [ ] EDT/modality changes do not cause UI updates to be deferred until a modal closes, or run in the wrong modality.
- [ ] Write/undo semantics unchanged for all `WriteCommandAction` sites touched.
- [ ] `commitDocument`/PSI/read-write phase changes do not introduce threading assertions or "read access not allowed" errors in the IDE log.
- [ ] The folding fix from the fix brief remains intact (audit does not re-touch its hotspot logic).
- [ ] Undo/redo in the JsonEditor still works after this audit's changes — the fix brief's repair is assumed complete and must not regress (this audit re-verifies, it does not re-fix).

## Acceptance Criteria
- [x] A written classification exists for every launch site and legacy threading site listed in In Scope: correct vs needs-fix, with the AGENTS.md rule it maps to.
- [x] Lifecycle-bound launches use a disposal-cancelled child scope; transient shared-scope launches are explicitly justified and track/cancel their `Job` where staleness matters.
- [x] `GenerateSchemaJsonTabPresenter` no longer creates an uncancelled orphan scope.
- [x] EDT modality usage is consistent and intentional across audited sites.
- [x] `./gradlew compileKotlin` and `./gradlew test` both pass with no behavior change.
- [ ] No raw `Thread`/`ExecutorService` reintroduced; no new threading assertions in the IDE log during manual smoke.

## Open Questions
- What is the decision criterion for "keep legacy primitive vs convert to coroutine"? The surviving `WriteCommandAction`/`Alarm`/`invokeLater` sites (`JsonDiffService`, `ConvertResultUtils`, `BaseEditorJsonAction`, `SortJsonDiffKeysOnceAction`, `JsonDocumentFactory`, `OnboardingTutorialDialogView`) need an explicit rule — e.g. "convert only async/background flows; leave synchronous EDT write actions as `WriteCommandAction`." Confirm before classifying these as correct vs needs-fix. (Default recommendation: `WriteCommandAction`/`runWriteAction` stay as-is since they are the platform write API, not a threading mechanism to migrate.)
- Is the shared-scope `launch` in transient actions (`OpenJsonFileAction`, `GenerateRandomJsonAction`) acceptable as-is, or should action work also be cancellable/scoped? (Actions are short-lived; default recommendation is to keep shared scope but confirm.)
- For `GenerateSchemaJsonTabPresenter` null-project case, what is the correct owning scope — should the dialog always have a project, making the fallback dead code?
- Which audited flows currently lack test coverage in `src/test/kotlin`? Those gaps may need coverage added before refactoring them (per Behavior Contract escape hatch).
- Should EDT modality be standardized to always use `ModalityState.any().asContextElement()` for dialog-originated UI updates, or decided per call site?
