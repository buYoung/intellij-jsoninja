# Research: Marketplace Verification 경고 제거 및 코루틴 리팩터링

**Date**: 2026-04-10  
**Feature**: 002-verification-coroutine-refactor

## R1: 현재 비동기 패턴 전수 조사

### Decision
기존 비동기 패턴을 3가지 카테고리로 분류하여 전환 전략을 결정한다.

### Findings

#### executeOnPooledThread (7곳)

| # | File | Line | Context |
|---|------|------|---------|
| 1 | `ui/component/jsonQuery/JsonQueryPresenter.kt` | 104 | 쿼리 실행 + invokeLater 결과 반환 |
| 2 | `ui/dialog/convertType/ConvertPreviewExecutor.kt` | 29 | Alarm 디바운스 후 프리뷰 계산 |
| 3 | `ui/dialog/loadJson/LoadJsonFromApiDialogPresenter.kt` | 56 | HTTP API 호출 + invokeLater 결과 반환 |
| 4 | `ui/dialog/generateJson/schema/GenerateSchemaJsonTabPresenter.kt` | 126 | SchemaStore 카탈로그 로드 |
| 5 | `ui/dialog/generateJson/schema/GenerateSchemaJsonTabPresenter.kt` | 379 | 스키마 URL 로드 |
| 6 | `diff/JsonDiffExtension.kt` | 343 | Diff auto-format |
| 7 | `actions/GenerateRandomJsonAction.kt` | 35 | 랜덤 JSON 생성 |

#### invokeLater (14개 파일, ~30회)

| # | File | Uses | Notes |
|---|------|------|-------|
| 1 | `JsonQueryPresenter.kt` | 3 | executeOnPooledThread 콜백 |
| 2 | `ConvertPreviewExecutor.kt` | 1 | executeOnPooledThread 콜백 |
| 3 | `LoadJsonFromApiDialogPresenter.kt` | 1 | executeOnPooledThread 콜백 |
| 4 | `GenerateSchemaJsonTabPresenter.kt` | 6 | executeOnPooledThread 콜백 |
| 5 | `GenerateRandomJsonAction.kt` | 4 | executeOnPooledThread 콜백 |
| 6 | `JsonDiffExtension.kt` | 1 | executeOnPooledThread 콜백 |
| 7 | `JsonTabsPresenter.kt` | 3 | 독립 EDT 전환 |
| 8 | `JsonEditorTextPresenter.kt` | 1 | 독립 EDT 전환 |
| 9 | `FoldingAwareEditorTextField.kt` | 1 | ApplicationManager.invokeLater |
| 10 | `JsonToTypeDialogPresenter.kt` | 1 | 독립 EDT 전환 |
| 11 | `JsoninjaOnboardingStartupActivity.kt` | 1 | suspend fun execute 내부 |
| 12 | `OnboardingService.kt` | 3 | 중첩 invokeLater |
| 13 | `OnboardingStep8DiffTooltipController.kt` | 1 | 독립 EDT 전환 |
| 14 | `OnboardingTutorialDialogView.kt` | 1 | SwingUtilities.invokeLater (포커스용, 유지) |

#### Alarm (3곳)

| # | File | Line | Purpose |
|---|------|------|---------|
| 1 | `JsonEditorTooltipListener.kt` | 33 | hover 툴팁 지연 (SWING_THREAD) |
| 2 | `ConvertPreviewExecutor.kt` | 10 | 프리뷰 디바운스 (SWING_THREAD) |
| 3 | `JsonDiffExtension.kt` | 269 | 문서 변경 디바운스 (SWING_THREAD, viewer 연결) |

### Rationale
- `executeOnPooledThread` + `invokeLater` 쌍은 코루틴 `launch { ... withContext(Dispatchers.EDT) }` 패턴으로 1:1 전환 가능
- `Alarm` 기반 디바운스는 로컬 Job + `delay` 패턴으로 전환
- `SwingUtilities.invokeLater` (OnboardingTutorialDialogView)는 Swing 포커스 용도로 유지

## R2: 기존 서비스 구조 및 CoroutineScope 주입 가능성

### Decision
기존 서비스 중 비동기 작업 소유가 필요한 서비스에 `CoroutineScope` 생성자 파라미터를 추가한다. 새 서비스 생성은 최소화한다.

### Findings

현재 프로젝트 서비스 (CoroutineScope 주입 없음):
- `JsonHelperService` (PROJECT)
- `OnboardingService` (PROJECT) — invokeLater 3회 사용
- `JsonQueryService` (PROJECT)
- `JsonHelperProjectService` (PROJECT)
- `JsonDiffService` (PROJECT)
- `JsonFormatterService` (PROJECT)
- `JsoninjaSettingsState` (PROJECT)

현재 앱 서비스:
- `JsonObjectMapperService` (APP)
- `BundledResourceService` (APP)

현재 코루틴 사용: **0건** (프로젝트에 코루틴 코드가 전혀 없음)

### Rationale
- 기존 서비스에 CoroutineScope를 추가하는 것이 새 서비스를 만드는 것보다 자연스러움
- `OnboardingService`는 이미 invokeLater 로직을 포함하므로 CoroutineScope 추가 대상
- Presenter에서 서비스로 비동기 로직을 이동할 때, 기존 서비스 확장을 우선하고 불가능한 경우만 새 서비스 생성
- `GenerateRandomJsonAction`의 비동기 로직은 기존 서비스가 없으므로 Action에서 직접 서비스 스코프를 얻거나 새 서비스로 이동

### Alternatives Considered
1. **모든 Presenter에 CoroutineScope 주입**: Presenter는 서비스가 아니므로 IntelliJ 플랫폼이 자동 주입하지 않음. 별도 팩토리 필요 → 복잡도 증가로 기각
2. **GlobalScope / project.coroutineScope 사용**: deprecated이며 누수 위험 → 기각

## R3: Verification 경고 직접 교체 항목 확인

### Decision
가이드 문서의 교체 항목이 현재 소스와 일치함을 확인. 그대로 진행.

### Findings

#### ReadAction.compute / ReadAction.run (3곳 — 일치)
- `JsonDocumentFactory.kt:62,76` — `ReadAction.compute` 2회
- `JsonEditorTooltipListener.kt:60` — `ReadAction.compute` 1회
- `FoldingAwareEditorTextField.kt:50` — `ReadAction.run` 1회

#### Document.addDocumentListener without parentDisposable (verification 대상 2건)
- `JsonDiffExtension.kt:314` — `document.addDocumentListener(documentListener)` (수동 removeDocumentListener at 318)
- `CodeInputPanel.kt:79` — `createdEditorField.document.addDocumentListener(...)` (parentDisposable 없음)

> Note: 그 외 addDocumentListener 호출 3곳(`JsonEditorTextPresenter.kt:40`, `JsonToTypeDialogView.kt:71`, `LoadJsonFromApiDialogView.kt:273`, `GenerateSchemaJsonTabView.kt:340`)은 `com.intellij.openapi.editor.event.DocumentListener`가 아닌 `javax.swing.event.DocumentListener`이거나, verification 경고에 포함되지 않은 호출임

#### ObjectNode.fields() (1곳)
- `JsonToTypeInferenceContext.kt:157` — `objectNode.fields().forEachRemaining`
- 테스트 파일 `RandomJsonDataCreatorTest.kt:84`에도 있으나 테스트 코드는 verification 대상 외

#### URL(String) (1곳)
- `JsonQueryView.kt:72` — `URL(link)` (deprecated constructor)
- 다른 파일들(`LoadJsonFromApiDialogPresenter.kt:148`, `GenerateSchemaJsonTabPresenter.kt:409`, `JsonSchemaNormalizer.kt:395`)은 이미 `URI(...).toURL()` 패턴 사용 중

#### DynamicPluginListener (1곳)
- `JsonHelperActivationListener.kt:8` — implements `DynamicPluginListener` (메서드 오버라이드 없음)
- `plugin.xml:40-41` — listener 등록 존재

#### ToolWindowFactory (1곳)
- `JsoninjaToolWindowFactory.kt` — `createToolWindowContent`, `shouldBeAvailable` 만 오버라이드
- `plugin.xml:16-17` — `icon` 속성만 선언됨. `anchor`, `doNotActivateOnStart` 미선언

## R4: plugin.xml 선언형 속성 전략

### Decision
바이트코드 브릿지 경고 해결을 위해 `plugin.xml`에 `anchor="right"` 추가하고, `doNotActivateOnStart` 속성은 기본 동작(false)이 의도와 맞으므로 추가하지 않되 필요시 추가. `isApplicable` → `shouldBeAvailable` 이미 오버라이드됨. `manage`, `getIcon` 브릿지는 Plugin Verifier 재실행으로 검증 후 필요시 빈 오버라이드 추가.

### Rationale
- `anchor` 선언 추가로 `getAnchor()` 브릿지 호출 방지 가능
- `icon`은 이미 plugin.xml에 선언됨
- Kotlin 바이트코드 브릿지는 컴파일러 버전에 따라 달라지므로 최종 검증 필수

### Alternatives Considered
1. **모든 default method에 빈 오버라이드 추가**: 코드 노이즈 증가. plugin.xml 속성으로 해결 가능한 것부터 시도 → 기각 (1차 시도)
2. **Kotlin jvmTarget 변경**: 전체 빌드에 영향 → 기각
