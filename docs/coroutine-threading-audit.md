# Coroutine / Threading Audit

## 목적
- 이 문서는 현재 작업 트리 기준으로 코루틴 시작 지점, `CoroutineScope` 소유권, 그리고 남아 있는 레거시 스레딩 primitive를 감사한 결과를 정리합니다.
- 판단 기준은 `AGENTS.md`의 `Threading Rules`, `Threading and Disposal Boundaries`, 그리고 IntelliJ 플랫폼의 `readAction` / `WriteCommandAction` / `Dispatchers.EDT` 경계입니다.
- `FoldingAwareEditorTextField`는 현재 트리에 이미 `commitDocument`가 EDT에서 실행되도록 반영된 상태를 기준으로 분류했습니다. 접기 동작 자체의 추가 회귀 수정은 별도 브리프 범위입니다.

## 규칙 매핑

| 규칙 의도 | 현재 기준 패턴 |
| --- | --- |
| 백그라운드 계산 | `withContext(Dispatchers.IO)` 또는 `withContext(Dispatchers.Default)` |
| UI 갱신 | `withContext(Dispatchers.EDT)` |
| 모달 대화상자 안 UI 갱신 | `withContext(Dispatchers.EDT + ModalityState.any().asContextElement())` |
| 쓰기 + Undo 보존 | `WriteCommandAction.runWriteCommandAction(...)` |
| PSI / 문서 읽기 | `readAction { ... }` 또는 `runReadAction(...)` |
| 수명 주기 결합 비동기 작업 | `JsoninjaCoroutineScopeService.createChildScope()` 후 `dispose` 시 `cancel()` |
| 일회성 프로젝트 작업 | `JsoninjaCoroutineScopeService.launch { ... }` |

## 코루틴 및 scope 감사

| 위치 | 종류 | 분류 | 단계 매핑 | 조치 |
| --- | --- | --- | --- | --- |
| `services/JsoninjaCoroutineScopeService.launch` | shared scope 진입점 | 정상 | 프로젝트 수명 주기 공유 작업 | 유지 |
| `services/JsoninjaCoroutineScopeService.createChildScope` | child scope factory | 정상 | 부모 `Job` 하위로 분기, 호출자가 `cancel()` 책임 | 유지 |
| `actions/OpenJsonFileAction.actionPerformed` | shared scope 일회성 launch | 정상 | 파일 읽기 `IO` → 탭 추가 `EDT` | 유지. 파일 선택 이후 비모달 흐름이라 bare `EDT`가 의도에 맞음 |
| `actions/GenerateRandomJsonAction.actionPerformed` | shared scope 일회성 launch | 정상 | 생성 `Default` → 결과 반영 및 오류 표시 `EDT` | 유지. 대화상자 종료 뒤 실행되므로 bare `EDT` 유지 |
| `ui/component/editor/JsonEditorTooltipListener.mouseMoved` | shared scope + `tooltipJob` 취소 | 정상 | 지연 대기 → `readAction` → 툴팁 반영 `EDT` | 유지. staleness가 중요하므로 `tooltipJob` 취소가 충분함 |
| `ui/component/editor/JsonEditorTextPresenter.<init>` | child scope 소유 | 정상 | presenter 수명 주기 결합 | 유지. `dispose`에서 `coroutineScope.cancel()` 수행 |
| `ui/component/editor/JsonEditorTextPresenter.schedulePlaceholderNormalization` | EDT 반영 launch | 정상 | 문서 상태 확인 후 `EDT`에서 텍스트 반영 | 유지 |
| `ui/component/editor/FoldingAwareEditorTextField.ensureRefreshInfrastructure` | child scope + `Alarm` 소유 | 정상 | editor disposable에 결합된 debounce 인프라 | 유지 |
| `ui/component/editor/FoldingAwareEditorTextField.refreshFoldRegions` | debounce launch | 정상 | `commitDocument` `EDT` → fold 수집 `readAction` → 적용 `EDT` | 유지. 현재 트리 기준으로 위상 배치가 맞음 |
| `ui/component/jsonQuery/JsonQueryPresenter.<init>` | child scope 소유 | 정상 | presenter 수명 주기 결합 | 유지 |
| `ui/component/jsonQuery/JsonQueryPresenter.setupKeyListener` | 빈 쿼리 초기화 launch | 정상 | 원본 결과 복원 `EDT` | 유지 |
| `ui/component/jsonQuery/JsonQueryPresenter.performSearch` | 검색 launch | 정상 | 표현식 실행 `Default` → 결과 반영 `EDT` | 유지 |
| `ui/component/tab/JsonTabsPresenter.<init>` | child scope 소유 | 정상 | parent disposable 결합 | 유지 |
| `ui/component/tab/JsonTabsPresenter.launchOnEdt` | UI helper launch | 정상 | 탭 조작을 `EDT`에서 직렬화 | 유지 |
| `ui/dialog/convertType/JsonToTypeDialogPresenter.<init>` | child scope 소유 | 정상 | dialog presenter 수명 주기 결합 | 유지 |
| `ui/dialog/convertType/JsonToTypeDialogPresenter.scheduleInitialPreview` | 모달 초기화 launch | 정상 | 미리보기 시작 요청을 `EDT + ModalityState.any()`로 전달 | 유지 |
| `ui/dialog/convertType/TypeToJsonDialogPresenter.<init>` | child scope 소유 | 정상 | dialog presenter 수명 주기 결합 | 유지 |
| `ui/dialog/convertType/ConvertPreviewExecutor.submit` | debounce preview launch | 정상 | loading 표시 `EDT + ModalityState.any()` → 계산 `Default` → 결과 반영 `EDT + ModalityState.any()` | 유지 |
| `ui/dialog/loadJson/LoadJsonFromApiDialogPresenter.<init>` | child scope 소유 | 정상 | dialog presenter 수명 주기 결합 | 유지 |
| `ui/dialog/loadJson/LoadJsonFromApiDialogPresenter.handleSendRequested` | API 요청 launch | 정상 | 요청 `IO` → 결과/오류 반영 `EDT + ModalityState.any()` | 유지 |
| `ui/dialog/generateJson/schema/GenerateSchemaJsonTabPresenter.<init>` | child scope 소유 | 수정 필요 → 수정 완료 | 이전에는 `project == null` 경로에서 고아 `CoroutineScope` 생성 | `Project`를 비-null로 고정하고 `createChildScope()`만 사용하도록 정리 완료 |
| `ui/dialog/generateJson/schema/GenerateSchemaJsonTabPresenter.loadSchemaStoreCatalog` | catalog load launch | 정상 | 카탈로그 다운로드 `IO` + 파싱 `Default` → 제안 목록 반영 `EDT + ModalityState.any()` | 유지 |
| `ui/dialog/generateJson/schema/GenerateSchemaJsonTabPresenter.loadSchemaFromUrl` | schema fetch launch | 정상 | 다운로드 `IO` → 에디터/오류/UI 상태 반영 `EDT + ModalityState.any()` | 유지 |
| `ui/onboarding/OnboardingStep8DiffTooltipController.<init>` | child scope 소유 | 정상 | tooltip parent disposable 결합 | 유지 |
| `ui/onboarding/OnboardingStep8DiffTooltipController.maybeOpenDiff` | UI launch | 정상 | diff 창 열기와 tooltip 표시를 `EDT`에서 수행 | 유지 |
| `diff/JsonDiffExtension.installAutoFormatter` | child scope + `Alarm` 소유 | 정상 | viewer 수명 주기 결합 debounce 인프라 | 유지 |
| `diff/JsonDiffExtension.scheduleJsonFormatting` | formatting launch | 정상 | 텍스트 읽기 `readAction` + 포맷 `Default` → 문서 적용 `EDT` | 유지 |
| `services/OnboardingService.startTutorial` | 프로젝트 서비스 scope launch | 수정 필요 → 수정 완료 | 이전에는 `EDT` 안에서 다시 `launch`를 중첩해 동일 작업을 재스케줄링 | 단일 `Dispatchers.EDT` launch와 `toolWindow.show` 콜백으로 단순화 완료 |
| `listeners/JsoninjaOnboardingStartupActivity.execute` | suspend 진입점의 EDT 전환 | 정상 | 시작 시 welcome dialog 판단을 `EDT`에서 수행 | 유지 |

## 레거시 primitive 감사

| 위치 | primitive | 분류 | 단계 매핑 | 조치 |
| --- | --- | --- | --- | --- |
| `services/JsonDiffService.replaceDocumentText` | `WriteCommandAction.runWriteCommandAction` | 정상 | 문서 쓰기 + Undo 보존 | 유지 |
| `utils/ConvertResultUtils.insertToEditor` | `WriteCommandAction.runWriteCommandAction` | 정상 | 에디터 쓰기 + Undo 보존 | 유지 |
| `actions/editor/BaseEditorJsonAction.actionPerformed` | `WriteCommandAction.runWriteCommandAction` | 정상 | 선택 범위 또는 전체 문서 쓰기 + Undo 보존 | 유지 |
| `actions/SortJsonDiffKeysOnceAction.actionPerformed` | `WriteCommandAction.runWriteCommandAction` | 정상 | diff 양쪽 문서 동시 쓰기 + Undo 보존 | 유지 |
| `ui/component/editor/JsonDocumentFactory.createJsonDocument` | `runReadAction(...)` | 정상 | PSI 파일 생성과 `Document` 조회를 읽기 위상에 배치 | 유지 |
| `ui/onboarding/OnboardingTutorialDialogView.focusNextButtonIfEnabled` | `SwingUtilities.invokeLater` | 정상 | 포커스 요청을 다음 UI tick으로 미룸 | 유지. 단일 포커스 보정 용도라 코루틴 전환 이점이 없음 |
| `ui/component/editor/FoldingAwareEditorTextField.ensureRefreshInfrastructure` | `Alarm` | 정상 | editor 수명 주기에 묶인 Swing debounce | 유지 |
| `diff/JsonDiffExtension.installAutoFormatter` | `Alarm` | 정상 | viewer 수명 주기에 묶인 diff debounce | 유지 |

## 이번 작업에서 반영한 변경

1. `GenerateJsonDialog`, `GenerateJsonDialogPresenter`, `GenerateSchemaJsonTabPresenter`, `GenerateSchemaJsonTabView`의 `Project`를 비-null로 고정했습니다.
2. `GenerateSchemaJsonTabPresenter`의 고아 `CoroutineScope(SupervisorJob() + Dispatchers.Default)` fallback을 제거하고 `JsoninjaCoroutineScopeService.createChildScope()`만 사용하도록 정리했습니다.
3. `OnboardingService.startTutorial`의 중첩 `launch`를 제거하고 단일 `Dispatchers.EDT` launch로 단순화했습니다.

## 결론

- 이번 작업 후 `needs-fix`로 남는 코루틴 스레딩 지점은 없습니다.
- `WriteCommandAction`, `readAction`, `runReadAction`, `Alarm`, `SwingUtilities.invokeLater`는 모두 현재 역할이 명확하며, 코루틴으로 기계적으로 치환할 대상이 아닙니다.
- 추가 정리가 필요하다면 새 추상화 도입보다 현재 감사 문서를 기준으로 "언제 shared scope를 허용하고 언제 child scope를 강제하는지"를 코드 리뷰 규칙으로 굳히는 편이 더 적절합니다.
