# JSONinja 마켓플레이스 verification 코루틴 우선 리팩터링 가이드

이 문서는 JetBrains Marketplace가 `JSONinja 1.11.2`에 대해 보고한 verification 경고를 바탕으로, 현재 `/Users/buyong/workspace/private/json-helper2` 소스를 어떤 방향으로 정리해야 하는지 설명하는 가이드입니다.

문서의 목적은 두 가지입니다.

1. verification 경고를 없애기 위한 직접 교체 항목을 정리한다.
2. 현재 소스 기준 `sinceBuild=243`, `platformVersion=2024.3`에 맞춰 비동기 흐름을 코루틴 중심 구조로 재정렬한다.

이 문서는 아래 두 근거를 함께 사용합니다.

- IntelliJ SDK `domain-knowledge`
- 현재 `intellij-community` 공개 API 및 플랫폼 소스 확인

## 1. 전제와 확인된 사실

- verification 대상: IntelliJ IDEA `2026.1`, Plugin Verifier `1.402`
- 마켓플레이스 verification 결과
  - deprecated API 사용 `13건` (deprecated method 12 + deprecated constructor 1)
  - experimental API 사용 `6건`
  - 총 경고 `19건`
- 현재 워크스페이스 기준 빌드 설정
  - `pluginVersion=1.11.2`
  - `pluginSinceBuild=243`
  - `pluginUntilBuild=263.*`
  - `platformVersion=2024.3`
- 즉, 현재 소스 기준으로는 `2024.1+` 코루틴 API를 기본 가정으로 사용할 수 있습니다.
  - `Dispatchers.EDT`
  - `readAction { }`
  - `readActionBlocking { }`
  - `smartReadAction(project) { }`
  - `withBackgroundProgress(...)`
- **v1.11.2 태그와 현재 HEAD 소스는 동일합니다** (`git diff v1.11.2..HEAD`에 변경 없음).
  - 따라서 모든 경고는 현재 소스에서 직접 수정 가능합니다.

### 1.1 바이트코드 브릿지 메서드에 의한 경고

verification 결과 중 `ToolWindowFactory`와 `DynamicPluginListener` 관련 경고 12건은 소스에 명시적 오버라이드가 없습니다.

이는 **Kotlin 컴파일러가 인터페이스의 default method에 대해 바이트코드 수준에서 bridge 메서드를 생성**하기 때문입니다. 소스에는 `isApplicable()`, `getAnchor()` 등의 오버라이드가 없지만, 컴파일된 `.class` 파일에는 default 구현을 호출하는 bridge가 포함되며 Plugin Verifier가 이를 "오버라이드 + 호출"로 감지합니다.

해결 방법은 두 가지입니다.

1. 해당 인터페이스 구현 자체를 제거 (불필요한 경우)
2. `plugin.xml` 선언형 속성으로 이동하여 코드 오버라이드 필요성을 없앰

## 2. 코루틴 우선 원칙

현재 소스는 `executeOnPooledThread`, `invokeLater`, `Alarm`에 의존한 비동기 흐름이 넓게 퍼져 있습니다. 현재 지원 버전 기준에서는 아래 원칙으로 정리하는 편이 더 자연스럽습니다.

### 2.1 비동기 소유권

- 장수명 비동기 작업은 `@Service` + `CoroutineScope` 주입으로 소유합니다.
- 액션, 프레젠터, 스타트업 진입점은 비동기 작업을 직접 스레드에 던지지 않고 서비스 호출만 담당합니다.
- 서비스는 Swing 컴포넌트를 직접 받지 않습니다.
  - 대신 요청 모델, 문자열, 단순 콜백만 받습니다.

참고: `domain-knowledge/05-services.md`

```kotlin
@Service(Service.Level.PROJECT)
class MyProjectService(
  private val project: Project,
  private val cs: CoroutineScope,
) {
  fun doWork() {
    cs.launch {
      val result = withContext(Dispatchers.IO) { /* 네트워크/IO */ }
      val parsed = readAction { /* PSI 읽기 */ }
      withContext(Dispatchers.EDT) { /* UI 업데이트 */ }
    }
  }
}
```

### 2.2 읽기와 쓰기

- 기본 읽기: `readAction { }`
  - 새 비동기 코드의 기본값입니다.
  - Write Allowing Read Action (WARA) — Write 도착 시 자동 취소 후 재시도
  - 블록은 멱등적이고 짧아야 합니다.
- 쓰기를 막고 끝까지 완주해야 하는 아주 짧은 읽기: `readActionBlocking { }`
  - 이미 동기 콜백 내부에 있고 suspend 경계로 올리기 어려운 경우에만 씁니다.
- 인덱스 의존 읽기: `smartReadAction(project) { }`
- UI 복귀: `withContext(Dispatchers.EDT)`
  - `Dispatchers.Main`은 사용하지 않습니다 (ModalityState 무시 위험).
- 진행률이 필요한 장시간 작업: `withBackgroundProgress(project, title) { ... }`

참고: `domain-knowledge/09-threading.md`, `domain-knowledge/11-coroutines.md`

### 2.3 수명주기

- 서비스 스코프는 프로젝트 close, 플러그인 언로드, IDE 종료 시 자동 취소됩니다.
  - `Disposable`을 수동 구현하거나 `Disposer.register`를 쓸 필요가 없습니다.
- 문서 리스너, 에디터 이벤트, hover 지연처럼 컴포넌트에 강하게 묶인 짧은 흐름만 로컬 `Job`으로 유지합니다.
- `Document.addDocumentListener(listener, parentDisposable)` 패턴은 계속 유지합니다.
  - `parentDisposable` 기반 등록 뒤에는 수동 `removeDocumentListener(...)`를 다시 호출하지 않는 편이 안전합니다.

참고: `domain-knowledge/08-disposer-and-lifecycle.md`

## 3. verification 경고 전체 분류

### 3.1 Deprecated API (13건)

| # | API | 경고 수 | 분류 | 위치 | 원인 |
|---|-----|--------|------|------|------|
| 1 | `ReadAction.compute(ThrowableComputable)` | 2 | deprecated method | `JsonEditorTooltipListener.mouseMoved(...)`, `JsonDocumentFactory.createJsonDocument(...)` | 소스에 명시적 호출 |
| 2 | `ReadAction.run(ThrowableRunnable)` | 1 | deprecated method | `FoldingAwareEditorTextField.refreshFoldRegions(...)` | 소스에 명시적 호출 |
| 3 | `Document.addDocumentListener(DocumentListener)` | 2 | deprecated method | `JsonDiffExtension.installAutoFormatter(...)`, `CodeInputPanel.rebuildEditor(...)` | 소스에 명시적 호출 |
| 4 | `ObjectNode.fields()` | 1 | deprecated method | `JsonToTypeInferenceContext.inferObjectType(...)` | 소스에 명시적 호출 |
| 5 | `URL.<init>(String)` | 1 | deprecated constructor | `JsonQueryView.updateHelpTooltip(...)` | 소스에 명시적 호출 |
| 6 | `DynamicPluginListener.checkUnloadPlugin(...)` | 2 | deprecated method | `JsonHelperActivationListener` (오버라이드 + 호출) | **Kotlin 바이트코드 브릿지** |
| 7 | `ToolWindowFactory.isApplicable(Project)` | 2 | deprecated method | `JsoninjaToolWindowFactory` (오버라이드 + 호출) | **Kotlin 바이트코드 브릿지** |
| 8 | `ToolWindowFactory.isDoNotActivateOnStart()` | 2 | deprecated method | `JsoninjaToolWindowFactory` (오버라이드 + 호출) | **Kotlin 바이트코드 브릿지** |

### 3.2 Experimental API (6건)

| # | API | 경고 수 | 분류 | 위치 | 원인 |
|---|-----|--------|------|------|------|
| 9 | `ToolWindowFactory.getAnchor()` | 2 | experimental method | `JsoninjaToolWindowFactory` (오버라이드 + 호출) | **Kotlin 바이트코드 브릿지** |
| 10 | `ToolWindowFactory.getIcon()` | 2 | experimental method | `JsoninjaToolWindowFactory` (오버라이드 + 호출) | **Kotlin 바이트코드 브릿지** |
| 11 | `ToolWindowFactory.manage(...)` | 2 | experimental method | `JsoninjaToolWindowFactory` (오버라이드 + 호출) | **Kotlin 바이트코드 브릿지** |

## 4. 수정 항목 상세

### 4.1 `ReadAction.compute(...)` / `ReadAction.run(...)` — 3건

대상 파일과 위치

| 파일 | 메서드 | 라인 | API |
|------|--------|------|-----|
| `src/.../editor/JsonDocumentFactory.kt` | `createJsonDocument(...)` | 62, 76 | `ReadAction.compute` |
| `src/.../editor/JsonEditorTooltipListener.kt` | `mouseMoved(...)` | 60 | `ReadAction.compute` |
| `src/.../editor/FoldingAwareEditorTextField.kt` | `refreshFoldRegions(...)` | 50 | `ReadAction.run` |

교체 기준

- 이미 서비스 코루틴이나 로컬 `Job` 안에 들어갈 수 있는 흐름 → `readAction { }` 우선
- 기존 동기 콜백 안에서 아주 짧게 끝나는 읽기 → `readActionBlocking { }`
- 인덱스를 직접 건드리는 읽기 → `smartReadAction(project) { }`

즉, "`무조건 blocking 치환`"이 아니라 "`코루틴으로 올릴 수 있으면 올리고, 동기 경계만 blocking bridge를 쓴다`"가 원칙입니다.

### 4.2 `Document.addDocumentListener(listener)` → `addDocumentListener(listener, parentDisposable)` — 2건

대상 파일과 위치

| 파일 | 메서드 | 라인 |
|------|--------|------|
| `src/.../diff/JsonDiffExtension.kt` | `installAutoFormatter(...)` | 314 |
| `src/.../convertType/CodeInputPanel.kt` | `rebuildEditor(...)` | 79 |

수정 방향

- `viewer`, 전용 `Disposable`, 패널 자체처럼 플러그인이 통제하는 parent에 리스너를 연결합니다.
- 자동 해제에 맡긴 뒤 수동 `removeDocumentListener(...)`는 제거합니다.
- 에디터 재생성 흐름은 에디터마다 별도 `Disposable`을 만들어 묶습니다.

현재 수동 제거 코드

| 파일 | 라인 | 내용 |
|------|------|------|
| `src/.../diff/JsonDiffExtension.kt` | 318 | `document.removeDocumentListener(documentListener)` in `Disposer.register(viewer)` |

`parentDisposable` 기반으로 전환 시 이 수동 제거 코드는 불필요합니다.

### 4.3 일반 라이브러리 / JDK 교체 — 2건

#### `ObjectNode.fields()` → `ObjectNode.properties()` — 1건

| 파일 | 메서드 | 라인 |
|------|--------|------|
| `src/.../typeConversion/JsonToTypeInferenceContext.kt` | `inferObjectType(...)` | 157 |

```kotlin
// Before
objectNode.fields().forEachRemaining { (fieldName, fieldValue) ->

// After
for ((fieldName, fieldValue) in objectNode.properties()) {
    fieldSourceNames.getOrPut(fieldName) { mutableListOf() }.add(fieldValue)
}
```

#### `URL(String)` → `URI.create(...).toURL()` — 1건

| 파일 | 메서드 | 라인 |
|------|--------|------|
| `src/.../jsonQuery/JsonQueryView.kt` | `updateHelpTooltip(...)` | 72 |

```kotlin
// Before
URL(link)

// After
URI.create(link).toURL()
```

### 4.4 `ToolWindowFactory` 바이트코드 브릿지 경고 — 10건 (deprecated 4 + experimental 6)

현재 소스 확인 결과

- `JsoninjaToolWindowFactory.kt`는 `createToolWindowContent(...)` 와 `shouldBeAvailable(...)`만 구현합니다.
- `plugin.xml`에는 이미 `icon` 속성이 선언되어 있습니다.

```xml
<toolWindow factoryClass="com.livteam.jsoninja.ui.toolWindow.JsoninjaToolWindowFactory"
            id="JSONinja"
            icon="com.livteam.jsoninja.icons.JsoninjaIcons.ToolWindowIcon"/>
```

소스에 명시적 오버라이드가 없지만, Kotlin 컴파일러가 `ToolWindowFactory` 인터페이스의 default method에 대해 바이트코드 브릿지를 생성하여 Plugin Verifier가 감지합니다.

수정 방향

Kotlin 컴파일러의 bridge 생성을 억제하려면, `plugin.xml`에 선언형 속성을 추가하여 플랫폼이 해당 default method를 호출하지 않도록 합니다. 또한 필요시 명시적으로 비-deprecated 메서드를 오버라이드하여 bridge를 덮어씁니다.

| 경고 API | 건수 | 분류 | 수정 방법 |
|---------|------|------|----------|
| `isApplicable(Project)` | 2 | deprecated | 이미 `shouldBeAvailable(project)`을 오버라이드하고 있으므로 추가 조치 불필요할 수 있음. bridge 방지를 위해 `@Suppress` 또는 명시적 `isApplicableAsync` 오버라이드 검토 |
| `isDoNotActivateOnStart()` | 2 | deprecated | `plugin.xml`에 `doNotActivateOnStart="true"` 속성 추가 |
| `getAnchor()` | 2 | experimental | `plugin.xml`에 `anchor` 속성 추가 (이미 기본값이면 명시적 선언으로 bridge 호출 방지) |
| `getIcon()` | 2 | experimental | `plugin.xml`에 이미 `icon` 속성이 있음. bridge 여전히 생성되는지 확인 필요 |
| `manage(...)` | 2 | experimental | 명시적으로 빈 오버라이드를 추가하여 super 호출 방지, 또는 플랫폼 버전에 따른 처리 검토 |

> **주의**: 바이트코드 브릿지 경고는 Kotlin 컴파일러 버전이나 `jvmTarget` 설정에 따라 달라질 수 있습니다. `plugin.xml` 속성 추가만으로 해결되지 않는 경우, 빈 오버라이드를 추가하여 deprecated/experimental super 호출을 제거하는 방식으로 대응합니다. 최종적으로 Plugin Verifier 재실행으로 검증해야 합니다.

### 4.5 `DynamicPluginListener.checkUnloadPlugin(...)` 바이트코드 브릿지 경고 — 2건

현재 소스 확인 결과

- `JsonHelperActivationListener.kt`는 `ApplicationActivationListener`와 `DynamicPluginListener`를 함께 구현합니다.
- `DynamicPluginListener`에서 사용하는 메서드는 없습니다 (오직 `ApplicationActivationListener.applicationActivated()`만 오버라이드).
- `plugin.xml`에 `DynamicPluginListener` 토픽으로 별도 등록되어 있습니다.

```xml
<listener class="com.livteam.jsoninja.listeners.JsonHelperActivationListener"
          topic="com.intellij.ide.plugins.DynamicPluginListener"/>
```

수정 방향: `DynamicPluginListener` 구현과 등록 제거

`DynamicPluginListener`의 메서드를 하나도 사용하지 않으므로, 가장 단순한 해결은 구현 자체를 제거하는 것입니다.

1. `JsonHelperActivationListener`에서 `DynamicPluginListener` 인터페이스 구현 제거
2. `plugin.xml`에서 `topic="com.intellij.ide.plugins.DynamicPluginListener"` 리스너 등록 제거
3. `DynamicPluginListener` import 제거

만약 향후 동적 언로드를 막아야 할 이유가 생기면, 그때 `DynamicPluginVetoer`를 별도로 구현합니다.

## 5. 코루틴 중심으로 같이 정리할 주요 비동기 흐름

verification 경고를 없애는 작업과 함께, 현재 소스의 주요 비동기 흐름은 아래처럼 재구성합니다.

### 5.1 `executeOnPooledThread` 전수 목록 (7곳)

| # | 파일 | 라인 | 현재 패턴 | 권장 전환 |
|---|------|------|----------|----------|
| 1 | `src/.../jsonQuery/JsonQueryPresenter.kt` | 104 | `executeOnPooledThread` + `invokeLater` | 서비스 스코프 |
| 2 | `src/.../convertType/ConvertPreviewExecutor.kt` | 29 | `executeOnPooledThread` + `invokeLater` | 로컬 Job |
| 3 | `src/.../loadJson/LoadJsonFromApiDialogPresenter.kt` | 56 | `executeOnPooledThread` + `invokeLater` | 서비스 스코프 |
| 4 | `src/.../generateJson/schema/GenerateSchemaJsonTabPresenter.kt` | 126 | `executeOnPooledThread` + `invokeLater` (카탈로그 로드) | 서비스 스코프 |
| 5 | `src/.../generateJson/schema/GenerateSchemaJsonTabPresenter.kt` | 379 | `executeOnPooledThread` + `invokeLater` (스키마 URL 로드) | 서비스 스코프 |
| 6 | `src/.../diff/JsonDiffExtension.kt` | 343 | `executeOnPooledThread` + `invokeLater` | 서비스 스코프 또는 로컬 Job |
| 7 | `src/.../actions/GenerateRandomJsonAction.kt` | 35 | `executeOnPooledThread` + `invokeLater` | 서비스 스코프 |

### 5.2 `invokeLater` 전수 목록 (12개 파일)

서비스 스코프로 올릴 때 `invokeLater`는 `withContext(Dispatchers.EDT)`로 교체됩니다.

| # | 파일 | 사용 횟수 | 권장 전환 |
|---|------|----------|----------|
| 1 | `src/.../jsonQuery/JsonQueryPresenter.kt` | 3 | 서비스 스코프 + `Dispatchers.EDT` |
| 2 | `src/.../convertType/ConvertPreviewExecutor.kt` | 1 | 로컬 Job + `Dispatchers.EDT` |
| 3 | `src/.../loadJson/LoadJsonFromApiDialogPresenter.kt` | 1 | 서비스 스코프 + `Dispatchers.EDT` |
| 4 | `src/.../generateJson/schema/GenerateSchemaJsonTabPresenter.kt` | 6 | 서비스 스코프 + `Dispatchers.EDT` |
| 5 | `src/.../actions/GenerateRandomJsonAction.kt` | 4 | 서비스 스코프 + `Dispatchers.EDT` |
| 6 | `src/.../diff/JsonDiffExtension.kt` | 1 | 서비스 스코프 + `Dispatchers.EDT` |
| 7 | `src/.../tab/JsonTabsPresenter.kt` | 3 | `Dispatchers.EDT` (이미 EDT 문맥일 가능성 확인 필요) |
| 8 | `src/.../editor/JsonEditorTextPresenter.kt` | 1 | `Dispatchers.EDT` |
| 9 | `src/.../editor/FoldingAwareEditorTextField.kt` | 1 | `Dispatchers.EDT` |
| 10 | `src/.../convertType/JsonToTypeDialogPresenter.kt` | 1 | `Dispatchers.EDT` |
| 11 | `src/.../listeners/JsoninjaOnboardingStartupActivity.kt` | 1 | `Dispatchers.EDT` (이미 `suspend fun execute` 내부) |
| 12 | `src/.../services/OnboardingService.kt` | 3 | 서비스 스코프 + `Dispatchers.EDT` |
| 13 | `src/.../onboarding/OnboardingStep8DiffTooltipController.kt` | 1 | `Dispatchers.EDT` |
| 14 | `src/.../onboarding/OnboardingTutorialDialogView.kt` | 1 | `SwingUtilities.invokeLater` — Swing 컴포넌트 포커스용이므로 유지 가능 |

### 5.3 `Alarm` 전수 목록 (3곳)

| # | 파일 | 라인 | 현재 용도 | 권장 전환 |
|---|------|------|----------|----------|
| 1 | `src/.../editor/JsonEditorTooltipListener.kt` | 33 | hover 툴팁 지연 | 로컬 Job + `delay` |
| 2 | `src/.../convertType/ConvertPreviewExecutor.kt` | 10 | 프리뷰 디바운스 | 로컬 Job + `delay` |
| 3 | `src/.../diff/JsonDiffExtension.kt` | 269 | 문서 변경 디바운스 | 로컬 Job + `delay` |

### 5.4 서비스 스코프로 올릴 흐름 요약

- **JSON query 실행** (`JsonQueryPresenter`)
  - `executeOnPooledThread` + `invokeLater` → `JsonQueryService`가 `CoroutineScope`를 받아 비동기 실행 소유
- **랜덤 JSON / 스키마 기반 JSON 생성** (`GenerateRandomJsonAction`)
  - `executeOnPooledThread` + `invokeLater` → 별도 프로젝트 서비스로 이동
- **SchemaStore 카탈로그 로드와 스키마 URL 로드** (`GenerateSchemaJsonTabPresenter`)
  - `executeOnPooledThread` + HTTP 호출 + `invokeLater` → 별도 서비스로 이동
- **외부 API JSON 로드** (`LoadJsonFromApiDialogPresenter`)
  - `executeOnPooledThread` + HTTP 호출 + `invokeLater` → 별도 서비스로 이동
- **온보딩 시작 및 다이얼로그 표시** (`OnboardingService`, `JsoninjaOnboardingStartupActivity`)
  - 중첩된 `invokeLater` → 서비스 스코프 + `Dispatchers.EDT`로 단순화
- **JSON Diff auto-format** (`JsonDiffExtension`)
  - `executeOnPooledThread` + `invokeLater` → 서비스 스코프 또는 로컬 Job

### 5.5 로컬 `Job`으로 유지할 흐름 요약

- **hover 툴팁 지연** (`JsonEditorTooltipListener.Alarm`)
- **프리뷰 디바운스** (`ConvertPreviewExecutor.Alarm` + `executeOnPooledThread`)
- **Diff auto-format 디바운스** (`JsonDiffExtension.Alarm`)
- **편집기 폴드 갱신** (`FoldingAwareEditorTextField.invokeLater`)
- **편집기 텍스트 업데이트** (`JsonEditorTextPresenter.invokeLater`)

이 묶음은 서비스까지 올리기보다, `Disposable` 또는 자체 `dispose()`에 연결된 로컬 코루틴 스코프로 유지하는 편이 더 자연스럽습니다.

## 6. 실제 수정 순서 권장안

### 1단계: verification deprecated/experimental 경고 직접 제거 (19건)

단순 교체로 해결되는 항목부터 처리합니다.

1. `Document.addDocumentListener(listener, parentDisposable)` 전환 (2건)
   - `JsonDiffExtension.kt` — `viewer`를 parentDisposable로
   - `CodeInputPanel.kt` — 패널 또는 에디터 `Disposable`을 parent로
   - 수동 `removeDocumentListener(...)` 제거
2. `ObjectNode.properties()` 전환 (1건)
3. `URI.create(...).toURL()` 전환 (1건)
4. `ReadAction.compute/run` → `readActionBlocking { }` 또는 `readAction { }` (3건)
   - `JsonDocumentFactory.kt` — 동기 팩토리이므로 `readActionBlocking { }` 사용
   - `JsonEditorTooltipListener.kt` — `Alarm` 콜백 내부이므로 코루틴 전환 시 `readAction { }` 가능
   - `FoldingAwareEditorTextField.kt` — `invokeLater` 콜백 내부이므로 `readActionBlocking { }`
5. `DynamicPluginListener` 구현/등록 제거 (2건)
6. `ToolWindowFactory` 바이트코드 브릿지 해결 (10건)
   - `plugin.xml`에 `doNotActivateOnStart`, `anchor` 등 선언형 속성 추가
   - 필요시 빈 오버라이드 추가로 deprecated/experimental super 호출 방지
   - Plugin Verifier로 검증

### 2단계: 주요 비동기 흐름 코루틴 전환

1. `@Service` + `CoroutineScope` 주입으로 장수명 비동기 작업 이동
   - `JsonQueryPresenter` → `JsonQueryService` 또는 기존 서비스 확장
   - `GenerateRandomJsonAction` → 별도 서비스
   - `GenerateSchemaJsonTabPresenter` → 스키마 로드 서비스
   - `LoadJsonFromApiDialogPresenter` → API 로드 서비스
   - `OnboardingService` 내부 정리
2. 프레젠터, 액션에서 `executeOnPooledThread` + `invokeLater` 패턴 제거 (7곳)
3. `Alarm` 기반 디바운스를 로컬 `Job` + `delay`로 교체 (3곳)
4. 단순 `invokeLater`를 `withContext(Dispatchers.EDT)` 또는 `Dispatchers.EDT` 기반으로 교체

### 3단계: verification 재실행 및 검증

- Plugin Verifier 또는 마켓플레이스 verification 재실행
- deprecated / experimental 경고 0건 확인
- tool window 위치, 아이콘, 시작 시 활성화 여부 수동 확인
- 동적 언로드 정상 동작 확인

## 7. 주의할 점

- 현재 소스 기준 최소 지원 버전은 `243` (2024.3)입니다.
- 새 비동기 코드는 `GlobalScope`, `Application.getCoroutineScope()`, `project.coroutineScope`를 사용하지 않습니다.
  - 이들은 deprecated이며 누수 위험이 있습니다.
  - 반드시 서비스에 주입받은 `CoroutineScope`를 사용합니다.
- `Dispatchers.Main` 대신 `Dispatchers.EDT`를 사용합니다.
  - `Dispatchers.Main`은 ModalityState를 인식하지 못합니다.
- `CancellationException` 또는 `ProcessCanceledException`을 catch-all로 삼켜서는 안 됩니다.
  - 코루틴 취소 신호가 무효화됩니다.
- `readAction { }` 안에서는 긴 작업을 하지 않습니다.
  - Write 도착 시 재시작되어 비용이 증가합니다.
- `Document` / 에디터 리스너는 parent disposable 기반 자동 해제를 우선합니다.
- 서비스 생성자에서 다른 서비스를 파라미터로 주입받지 않습니다 (deprecated).
  - 필요한 서비스는 호출 시점에 `getService()`로 얻습니다.
- `ToolWindowFactory` 바이트코드 브릿지 경고는 `plugin.xml` 속성 추가만으로 해결되지 않을 수 있습니다.
  - 반드시 Plugin Verifier 재실행으로 검증합니다.

## 8. 참고 기준

### 8.1 `domain-knowledge`

- `/Users/buyong/tmp/intellij-sdk-docs/domain-knowledge/05-services.md`
- `/Users/buyong/tmp/intellij-sdk-docs/domain-knowledge/08-disposer-and-lifecycle.md`
- `/Users/buyong/tmp/intellij-sdk-docs/domain-knowledge/09-threading.md`
- `/Users/buyong/tmp/intellij-sdk-docs/domain-knowledge/10-background-and-progress.md`
- `/Users/buyong/tmp/intellij-sdk-docs/domain-knowledge/11-coroutines.md`
- `/Users/buyong/tmp/intellij-sdk-docs/domain-knowledge/13-documents.md`
- `/Users/buyong/tmp/intellij-sdk-docs/domain-knowledge/23-ui-components.md`
- `/Users/buyong/tmp/intellij-sdk-docs/domain-knowledge/25-productivity-and-ops.md`

### 8.2 verification 원문

- 대상: IntelliJ IDEA 2026.1, Plugin Verifier 1.402
- 결과: Compatible. 13 usages of deprecated API. 6 usages of experimental API
- deprecated methods (12): `DynamicPluginListener.checkUnloadPlugin` x2, `ReadAction.compute` x2, `Document.addDocumentListener` x2, `ToolWindowFactory.isApplicable` x2, `ToolWindowFactory.isDoNotActivateOnStart` x2, `ObjectNode.fields` x1, `ReadAction.run` x1
- deprecated constructor (1): `URL.<init>(String)` x1
- experimental methods (6): `ToolWindowFactory.getAnchor` x2, `ToolWindowFactory.getIcon` x2, `ToolWindowFactory.manage` x2

### 8.3 플랫폼 API / 저장소 확인

- `platform/core-api/src/com/intellij/ide/plugins/DynamicPluginListener.kt`
- `platform/core-api/src/com/intellij/ide/plugins/DynamicPluginVetoer.kt`
- `platform/core-api/src/com/intellij/util/messages/impl/DynamicPluginUnloaderCompatibilityLayer.kt`
- `platform/platform-impl/src/com/intellij/ide/plugins/DynamicPlugins.kt`
- `platform/platform-resources/src/META-INF/PlatformExtensionPoints.xml`
