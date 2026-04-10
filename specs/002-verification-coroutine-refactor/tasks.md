# Tasks: Marketplace Verification 경고 제거 및 코루틴 리팩터링

**Input**: Design documents from `/specs/002-verification-coroutine-refactor/`
**Prerequisites**: plan.md (required), spec.md (required), research.md, data-model.md, quickstart.md

**Tests**: 테스트는 Constitution V에 따라 기존 테스트 유지 및 회귀 확인 수준으로 포함. 별도 TDD 요청 없음.

**Organization**: Tasks are grouped by user story to enable independent implementation and testing of each story.

## Format: `[ID] [P?] [Story] Description`

- **[P]**: Can run in parallel (different files, no dependencies)
- **[Story]**: Which user story this task belongs to (e.g., US1, US2, US3)
- Include exact file paths in descriptions

---

## Phase 1: Setup

**Purpose**: 코루틴 인프라 준비 — 프로젝트에 코루틴 코드가 전혀 없으므로 기초 설정 필요

- [X] T001 코루틴 의존성 확인 — `build.gradle.kts`에서 IntelliJ Platform SDK의 `kotlinx-coroutines` 번들 사용 가능 여부 확인. 필요시 `gradle.properties` 또는 `build.gradle.kts` 수정

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 모든 User Story에 공통으로 필요한 기반 작업

**⚠️ CRITICAL**: US1의 일부(단순 교체)는 이 Phase 없이도 가능하지만, 코루틴 전환(US2/US3)은 이 Phase 완료 후 진행

- [X] T002 없음 — 이 피처는 기존 프로젝트에 대한 리팩터링이므로 별도 기반 작업 불필요. Phase 1 완료 후 바로 User Story 진행.

**Checkpoint**: 코루틴 의존성 확인 완료 — User Story 구현 시작 가능

---

## Phase 3: User Story 1 — Marketplace Verification 경고 0건 달성 (Priority: P1) 🎯 MVP

**Goal**: Plugin Verifier가 보고한 deprecated 13건 + experimental 6건 경고를 모두 제거하여 깨끗한 verification 결과를 얻는다.

**Independent Test**: `./gradlew runPluginVerifier` 실행 시 deprecated/experimental 경고 0건 확인

### 3a: 단순 API 교체 (병렬 가능)

- [X] T003 [P] [US1] `ObjectNode.fields()` → `ObjectNode.properties()` 교체 in `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/JsonToTypeInferenceContext.kt:157` — `objectNode.fields().forEachRemaining { (fieldName, fieldValue) -> ... }` 를 `for ((fieldName, fieldValue) in objectNode.properties()) { ... }` 로 변환
- [X] T004 [P] [US1] `URL(link)` → `URI.create(link).toURL()` 교체 in `src/main/kotlin/com/livteam/jsoninja/ui/component/jsonQuery/JsonQueryView.kt:72` — `import java.net.URI` 추가, `URL(link)` 를 `URI.create(link).toURL()` 로 변환

### 3b: ReadAction 교체

- [X] T005 [P] [US1] `ReadAction.compute` → `readActionBlocking` 교체 in `src/main/kotlin/com/livteam/jsoninja/ui/component/editor/JsonDocumentFactory.kt:62,76` — 동기 팩토리 메서드이므로 `readActionBlocking { }` 사용. `import com.intellij.openapi.application.readActionBlocking` 추가, `ReadAction.compute<..., RuntimeException> { ... }` 를 `readActionBlocking { ... }` 로 변환 (2곳)
- [X] T006 [P] [US1] `ReadAction.compute` → 코루틴 전환 준비 in `src/main/kotlin/com/livteam/jsoninja/ui/component/editor/JsonEditorTooltipListener.kt:60` — 현재 `Alarm` 콜백 내부에서 호출됨. US2에서 `Alarm` → 코루틴 전환 시 `readAction { }` 로 전환 예정이므로, 이 단계에서는 `readActionBlocking { }` 로 임시 교체. `ReadAction.compute<..., RuntimeException> { ... }` → `readActionBlocking { ... }`
- [X] T007 [P] [US1] `ReadAction.run` → `readActionBlocking` 교체 in `src/main/kotlin/com/livteam/jsoninja/ui/component/editor/FoldingAwareEditorTextField.kt:50` — `invokeLater` 콜백 내부이므로 `readActionBlocking { }` 사용. `ReadAction.run<RuntimeException> { ... }` → `readActionBlocking { ... }`

### 3c: DocumentListener parentDisposable 추가

- [X] T008 [P] [US1] `addDocumentListener` parentDisposable 추가 in `src/main/kotlin/com/livteam/jsoninja/diff/JsonDiffExtension.kt:314` — `document.addDocumentListener(documentListener)` → `document.addDocumentListener(documentListener, viewer)`. 이후 line 316-319의 `Disposer.register(viewer) { document.removeDocumentListener(documentListener) ... }` 에서 `removeDocumentListener` 호출 제거 (alarm.cancelAllRequests()는 viewer의 Alarm 생성자에서 이미 처리됨으로 함께 제거 가능 여부 확인)
- [X] T009 [P] [US1] `addDocumentListener` parentDisposable 추가 in `src/main/kotlin/com/livteam/jsoninja/ui/component/convertType/CodeInputPanel.kt:79` — `createdEditorField.document.addDocumentListener(object : DocumentListener { ... })` 에 parentDisposable 파라미터 추가. 패널 또는 에디터 필드의 적절한 Disposable을 parent로 사용

### 3d: DynamicPluginListener 제거

- [X] T010 [US1] `DynamicPluginListener` 구현 및 등록 제거 — 두 파일 동시 수정:
  1. `src/main/kotlin/com/livteam/jsoninja/listeners/JsonHelperActivationListener.kt` — `DynamicPluginListener` import 제거(line 3), 클래스 선언에서 `, DynamicPluginListener` 제거(line 8)
  2. `src/main/resources/META-INF/plugin.xml` — `<listener class="...JsonHelperActivationListener" topic="com.intellij.ide.plugins.DynamicPluginListener"/>` 제거(line 40-41)

### 3e: ToolWindowFactory 바이트코드 브릿지 해결

- [X] T011 [US1] `plugin.xml` toolWindow 선언형 속성 추가 in `src/main/resources/META-INF/plugin.xml:16-17` — `<toolWindow>` 태그에 `anchor="right"` 속성 추가 (현재 기본값이지만 명시적 선언으로 `getAnchor()` 브릿지 방지). `doNotActivateOnStart` 속성은 기본값 false가 의도와 맞으므로 추가하지 않음
- [X] T012 [US1] ToolWindowFactory 브릿지 검증 — T011 적용 후 `./gradlew runPluginVerifier` 실행하여 `isApplicable`, `isDoNotActivateOnStart`, `getAnchor`, `getIcon`, `manage` 관련 경고 잔존 여부 확인. 잔존 시 `src/main/kotlin/com/livteam/jsoninja/ui/toolWindow/JsoninjaToolWindowFactory.kt`에 빈 오버라이드 추가로 deprecated/experimental super 호출 방지

### 3f: 전체 검증

- [X] T013 [US1] Plugin Verifier 전체 검증 — `./gradlew runPluginVerifier` 실행하여 deprecated 0건, experimental 0건 확인. 잔존 경고 있을 시 해당 항목 추가 수정

**Checkpoint**: Plugin Verifier 경고 19건 → 0건 달성. 기존 기능 동작 확인.

---

## Phase 4: User Story 2 — 코루틴 기반 비동기 흐름 전환 (Priority: P2)

**Goal**: `executeOnPooledThread` + `invokeLater` + `Alarm` 패턴을 IntelliJ 코루틴 API로 전환

**Independent Test**: 소스에서 `executeOnPooledThread` 0건, `Alarm` 인스턴스 0건 확인. JSON 쿼리, 랜덤 JSON 생성, 스키마 로드, API 로드, Diff auto-format 기능 정상 동작 확인

### 4a: 서비스 CoroutineScope 추가

- [X] T014 [P] [US2] `OnboardingService`에 CoroutineScope 추가 in `src/main/kotlin/com/livteam/jsoninja/services/OnboardingService.kt` — 생성자에 `private val cs: CoroutineScope` 파라미터 추가. 기존 `invokeLater` 3곳을 `cs.launch { withContext(Dispatchers.EDT) { ... } }` 패턴으로 전환. 중첩 `invokeLater` 제거
- [X] T015 [P] [US2] `JsonQueryService`에 CoroutineScope 추가 (필요시) in `src/main/kotlin/com/livteam/jsoninja/services/JsonQueryService.kt` — 현재 `JsonQueryPresenter`에 있는 쿼리 실행 비동기 로직을 서비스로 이동할 경우 생성자에 `private val cs: CoroutineScope` 추가

### 4b: executeOnPooledThread + invokeLater → 서비스 스코프 전환

- [X] T016 [US2] `JsonQueryPresenter` 코루틴 전환 in `src/main/kotlin/com/livteam/jsoninja/ui/component/jsonQuery/JsonQueryPresenter.kt` — `executeOnPooledThread`(line 104) + `invokeLater` 3곳을 서비스 스코프 기반 코루틴으로 전환. `isDisposed` 체크는 코루틴 취소로 대체. `import com.intellij.openapi.application.EDT`, `import kotlinx.coroutines.*` 추가
- [X] T017 [US2] `LoadJsonFromApiDialogPresenter` 코루틴 전환 in `src/main/kotlin/com/livteam/jsoninja/ui/dialog/loadJson/LoadJsonFromApiDialogPresenter.kt` — `executeOnPooledThread`(line 56) + `invokeLater`(line 61)를 서비스 스코프 기반으로 전환. HTTP 호출을 `withContext(Dispatchers.IO)`, UI 업데이트를 `withContext(Dispatchers.EDT)`로 분리
- [X] T018 [US2] `GenerateSchemaJsonTabPresenter` 코루틴 전환 in `src/main/kotlin/com/livteam/jsoninja/ui/dialog/generateJson/schema/GenerateSchemaJsonTabPresenter.kt` — `executeOnPooledThread` 2곳(line 126, 379) + `invokeLater` 6곳을 서비스 스코프 기반으로 전환. 카탈로그 로드와 스키마 URL 로드 각각 별도 `launch` 블록
- [X] T019 [US2] `GenerateRandomJsonAction` 코루틴 전환 in `src/main/kotlin/com/livteam/jsoninja/actions/GenerateRandomJsonAction.kt` — `executeOnPooledThread`(line 35) + `invokeLater` 4곳(line 51, 68, 77, 86)을 서비스 스코프 기반으로 전환. 기존 서비스 또는 새 서비스에서 CoroutineScope 획득
- [X] T020 [US2] `JsonDiffExtension` executeOnPooledThread 코루틴 전환 in `src/main/kotlin/com/livteam/jsoninja/diff/JsonDiffExtension.kt:343` — `executeOnPooledThread` + `invokeLater`를 로컬 Job 또는 서비스 스코프 기반으로 전환

### 4c: Alarm → 로컬 Job + delay 전환

- [X] T021 [P] [US2] `JsonEditorTooltipListener` Alarm 제거 in `src/main/kotlin/com/livteam/jsoninja/ui/component/editor/JsonEditorTooltipListener.kt` — `Alarm`(line 33) → 로컬 Job + `delay` 패턴으로 전환. `private var tooltipJob: Job? = null` 도입. `parentDisposable` 연결된 CoroutineScope 사용. T006에서 적용한 `readActionBlocking`을 `readAction`으로 업그레이드
- [X] T022 [P] [US2] `ConvertPreviewExecutor` Alarm + executeOnPooledThread 제거 in `src/main/kotlin/com/livteam/jsoninja/ui/dialog/convertType/ConvertPreviewExecutor.kt` — `previewAlarm`(line 10) + `executeOnPooledThread`(line 29) + `invokeLater`(line 31)를 로컬 Job + `delay` + `withContext(Dispatchers.EDT)` 패턴으로 전환. `dispose()` 메서드에서 `previewAlarm.dispose()` → Job 취소로 변경
- [X] T023 [P] [US2] `JsonDiffExtension` Alarm 제거 in `src/main/kotlin/com/livteam/jsoninja/diff/JsonDiffExtension.kt:269` — `Alarm`(line 269, viewer 연결) → 로컬 Job + `delay` 패턴으로 전환. viewer dispose 시 Job 자동 취소

### 4d: 독립 invokeLater → Dispatchers.EDT 전환

- [X] T024 [P] [US2] `JsonTabsPresenter` invokeLater 전환 in `src/main/kotlin/com/livteam/jsoninja/ui/component/tab/JsonTabsPresenter.kt` — `invokeLater` 3곳(line 51, 66, 101)을 적절한 코루틴 문맥으로 전환. 이미 EDT에서 호출되는 경우 확인 후 불필요한 invokeLater 제거 또는 `withContext(Dispatchers.EDT)` 적용
- [X] T025 [P] [US2] `JsonEditorTextPresenter` invokeLater 전환 in `src/main/kotlin/com/livteam/jsoninja/ui/component/editor/JsonEditorTextPresenter.kt:70` — `invokeLater` → `withContext(Dispatchers.EDT)` 전환
- [X] T026 [P] [US2] `FoldingAwareEditorTextField` invokeLater 전환 in `src/main/kotlin/com/livteam/jsoninja/ui/component/editor/FoldingAwareEditorTextField.kt:44` — `ApplicationManager.getApplication().invokeLater(...)` → 코루틴 기반 EDT 전환
- [X] T027 [P] [US2] `JsonToTypeDialogPresenter` invokeLater 전환 in `src/main/kotlin/com/livteam/jsoninja/ui/dialog/convertType/JsonToTypeDialogPresenter.kt:94` — `invokeLater(ModalityState.any())` → `withContext(Dispatchers.EDT)` 전환
- [X] T028 [P] [US2] `JsoninjaOnboardingStartupActivity` invokeLater 전환 in `src/main/kotlin/com/livteam/jsoninja/listeners/JsoninjaOnboardingStartupActivity.kt:11` — 이미 `suspend fun execute` 내부이므로 `invokeLater` → `withContext(Dispatchers.EDT)` 직접 전환
- [X] T029 [P] [US2] `OnboardingStep8DiffTooltipController` invokeLater 전환 in `src/main/kotlin/com/livteam/jsoninja/ui/onboarding/OnboardingStep8DiffTooltipController.kt:47` — `invokeLater(ModalityState.any())` → `withContext(Dispatchers.EDT)` 전환

> Note: `OnboardingTutorialDialogView.kt:330`의 `SwingUtilities.invokeLater`는 Swing 포커스 용도이므로 유지

### 4e: 검증

- [ ] T030 [US2] 코루틴 전환 전체 검증 — 소스에서 `executeOnPooledThread` 0건, `Alarm` import 0건 확인 (`rg "executeOnPooledThread" src/`, `rg "import.*Alarm" src/`). JSON 쿼리 실행, 랜덤 JSON 생성, 스키마 로드, API 로드, Diff auto-format, 프리뷰 디바운스, hover 툴팁 기능 수동 테스트

**Checkpoint**: `executeOnPooledThread` 0건, `Alarm` 0건. 모든 비동기 기능 정상 동작.

---

## Phase 5: User Story 3 — 서비스 수명주기 기반 리소스 관리 (Priority: P3)

**Goal**: 서비스 스코프 기반 비동기 작업이 프로젝트 종료/플러그인 언로드 시 자동 취소되도록 보장

**Independent Test**: 프로젝트 닫기 시 진행 중인 비동기 작업 자동 취소 확인, Document 리스너 자동 해제 확인

### Implementation

- [ ] T031 [US3] 서비스 수명주기 검증 — US2에서 CoroutineScope가 추가된 서비스들(`OnboardingService`, `JsonQueryService` 등)이 프로젝트 close 시 진행 중인 코루틴이 자동 취소되는지 수동 테스트. IDE 로그에서 취소 관련 경고/에러 없는지 확인
- [X] T032 [US3] `CancellationException` 처리 검증 — 모든 코루틴 전환 코드에서 `CancellationException` 또는 `ProcessCanceledException`이 catch-all로 삼켜지지 않는지 코드 리뷰. `rg "catch.*Exception" src/` 로 전수 검사
- [ ] T033 [US3] 플러그인 동적 언로드 검증 — DynamicPluginListener 제거 후 플러그인 동적 언로드/로드가 정상 동작하는지 IDE에서 수동 테스트

**Checkpoint**: 서비스 수명주기 기반 자동 취소 확인. 플러그인 동적 로드/언로드 정상.

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 전체 리팩터링 후 최종 정리

- [X] T034 [P] 불필요 import 정리 — `ReadAction`, `Alarm`, `DynamicPluginListener`, `executeOnPooledThread` 관련 미사용 import 제거. IDE의 Optimize Imports 실행
- [X] T035 [P] Plugin Verifier 최종 검증 — `./gradlew runPluginVerifier` 실행하여 deprecated 0건, experimental 0건 최종 확인
- [X] T036 기존 테스트 통과 확인 — `./gradlew test` 실행하여 모든 기존 테스트 통과 확인. 테스트 파일의 `ObjectNode.fields()` (`RandomJsonDataCreatorTest.kt:84`)는 테스트 코드이므로 verification 대상 외이지만 일관성을 위해 `properties()` 전환 권장
- [X] T037 빌드 성공 확인 — `./gradlew build` 전체 빌드 성공 확인

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies — 즉시 시작
- **Foundational (Phase 2)**: Phase 1 완료 후 — 실질적 작업 없음
- **US1 (Phase 3)**: Phase 1 완료 후 즉시 시작 가능 — 코루틴 인프라 불필요 (단순 API 교체)
- **US2 (Phase 4)**: Phase 1 완료 필수 (코루틴 의존성). US1과 병렬 가능하나 T005~T007 ReadAction 교체가 US2 코루틴 전환과 겹치므로 US1 3b 완료 후 시작 권장
- **US3 (Phase 5)**: US2 완료 필수 (서비스 스코프 존재 전제)
- **Polish (Phase 6)**: US1 + US2 + US3 완료 후

### User Story Dependencies

- **US1 (P1)**: 독립 — 코루틴 전환 없이 단순 API 교체로 완결
- **US2 (P2)**: US1의 ReadAction 교체(T005~T007)에 약간 의존 — 코루틴 전환 시 readActionBlocking을 readAction으로 업그레이드 가능
- **US3 (P3)**: US2 완료 필수 — 서비스 스코프 기반 수명주기 검증

### Within Each User Story

- 3a, 3b, 3c, 3d 서브그룹은 병렬 가능
- 3e (ToolWindow 브릿지)는 T011 후 T012 (검증 → 추가 수정)
- 3f (전체 검증)는 3a~3e 모두 완료 후
- 4a (서비스 CoroutineScope) → 4b (executeOnPooledThread) 순서 필수
- 4c (Alarm) 와 4d (invokeLater)는 4a 완료 후 병렬 가능

### Parallel Opportunities

**Phase 3 (US1)**: T003, T004, T005, T006, T007, T008, T009 모두 서로 다른 파일 → 7개 태스크 병렬 가능
**Phase 4 (US2)**: T014, T015 병렬 → T016~T020 순차 (서비스 스코프 필요) → T021, T022, T023, T024~T029 병렬 가능

---

## Parallel Example: User Story 1

```bash
# Phase 3a + 3b + 3c 모두 병렬 실행 가능 (7개 태스크, 모두 다른 파일):
Task T003: "ObjectNode.fields() → properties() in JsonToTypeInferenceContext.kt"
Task T004: "URL → URI in JsonQueryView.kt"
Task T005: "ReadAction.compute → readActionBlocking in JsonDocumentFactory.kt"
Task T006: "ReadAction.compute → readActionBlocking in JsonEditorTooltipListener.kt"
Task T007: "ReadAction.run → readActionBlocking in FoldingAwareEditorTextField.kt"
Task T008: "addDocumentListener parentDisposable in JsonDiffExtension.kt"
Task T009: "addDocumentListener parentDisposable in CodeInputPanel.kt"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Complete Phase 1: Setup (코루틴 의존성 확인)
2. Complete Phase 3: US1 (deprecated/experimental 경고 제거)
3. **STOP and VALIDATE**: `./gradlew runPluginVerifier` → 경고 0건 확인
4. 이것만으로 Marketplace verification 통과 → 릴리즈 가능

### Incremental Delivery

1. US1 완료 → Marketplace 경고 제거 → 즉시 릴리즈 가능 (MVP!)
2. US2 추가 → 코루틴 전환 완료 → 코드 품질 개선 릴리즈
3. US3 추가 → 수명주기 검증 → 안정성 강화 릴리즈
4. 각 단계가 독립적 가치를 제공

---

## Notes

- [P] tasks = different files, no dependencies
- [Story] label maps task to specific user story for traceability
- `OnboardingTutorialDialogView.kt`의 `SwingUtilities.invokeLater`는 Swing 포커스 용도로 전환 대상에서 제외
- `RandomJsonDataCreatorTest.kt:84`의 `fields()`는 테스트 코드이므로 verification 대상 외이나 일관성을 위해 Polish 단계에서 전환 권장
- 서비스 생성자에서 다른 서비스를 파라미터로 주입하지 않음 (deprecated). 호출 시점에 `getService()` 사용
- 커밋은 각 태스크 또는 논리적 그룹 단위로 생성
