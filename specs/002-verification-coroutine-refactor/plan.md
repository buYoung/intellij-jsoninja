# Implementation Plan: Marketplace Verification 경고 제거 및 코루틴 리팩터링

**Branch**: `002-verification-coroutine-refactor` | **Date**: 2026-04-10 | **Spec**: [spec.md](./spec.md)
**Input**: Feature specification from `/specs/002-verification-coroutine-refactor/spec.md`

## Summary

JetBrains Marketplace Plugin Verifier가 JSONinja 1.11.2에 대해 보고한 19건의 경고(deprecated 13 + experimental 6)를 모두 제거하고, 현재 `executeOnPooledThread` + `invokeLater` + `Alarm` 기반 비동기 흐름을 IntelliJ 플랫폼 코루틴 API 중심으로 재구성한다. 최소 지원 버전이 243(2024.3)이므로 `readAction { }`, `Dispatchers.EDT`, `CoroutineScope` 주입 등 2024.1+ API를 기본으로 사용한다.

## Technical Context

**Language/Version**: Kotlin (JVM), IntelliJ Platform SDK  
**Primary Dependencies**: IntelliJ Platform 2024.3 (`platformVersion=2024.3`), Jackson, JMESPath/jq  
**Storage**: N/A (IDE 플러그인, 별도 저장소 없음)  
**Testing**: IntelliJ Platform test framework (`BasePlatformTestCase`), JUnit  
**Target Platform**: IntelliJ IDEA 243~263.* (2024.3~2026.3)  
**Project Type**: IntelliJ Platform Plugin (desktop IDE extension)  
**Performance Goals**: UI 피드백 200ms 이내 (Constitution Principle I)  
**Constraints**: `sinceBuild=243`, `untilBuild=263.*`, Kotlin stdlib not bundled (`kotlin.stdlib.default.dependency=false`)  
**Scale/Scope**: 단일 플러그인, ~17개 서비스, ~30개 소스 파일 수정 대상

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

| Principle | Status | Notes |
|-----------|--------|-------|
| I. User Experience First | PASS | 비동기 작업을 코루틴으로 전환하여 EDT 차단 방지 유지. UI 피드백 200ms 기준 충족. |
| II. IDE Compatibility & Stability | PASS | deprecated API 교체가 이 피처의 핵심 목적. `sinceBuild=243` 범위 내 API만 사용. |
| III. Performance & Large File Resilience | PASS | `readAction { }` 전환 시 짧고 멱등적 블록 유지. 기존 성능 특성 보존. |
| IV. Code Quality & Maintainability | PASS | 코루틴 패턴으로 코드 가독성 향상. 서비스 스코프로 비동기 소유권 명확화. |
| V. Test Discipline | PASS | 기존 테스트 유지. 서비스 CoroutineScope 추가 시 테스트에서 `TestScope` 사용 가능. |

**Gate result**: PASS — 모든 원칙 충족

## Project Structure

### Documentation (this feature)

```text
specs/002-verification-coroutine-refactor/
├── plan.md              # This file
├── research.md          # Phase 0 output — 비동기 패턴 전수 조사, 서비스 구조, 교체 항목 확인
├── data-model.md        # Phase 1 output — 서비스 구조 변경, 파일 변경 맵
├── quickstart.md        # Phase 1 output — 빌드/테스트/검증 가이드
└── tasks.md             # Phase 2 output (/speckit.tasks command)
```

### Source Code (repository root)

```text
src/main/kotlin/com/livteam/jsoninja/
├── actions/                          # Action 클래스 (GenerateRandomJsonAction 등)
├── diff/                             # JsonDiffExtension
├── listeners/                        # JsonHelperActivationListener, StartupActivity
├── services/                         # 기존 서비스 (CoroutineScope 추가 대상)
│   ├── OnboardingService.kt
│   ├── JsonQueryService.kt
│   ├── JsonHelperService.kt
│   ├── JsonDiffService.kt
│   ├── JsonFormatterService.kt
│   ├── schema/                       # 스키마 관련 서비스
│   └── typeConversion/               # 타입 변환 서비스
├── ui/
│   ├── component/
│   │   ├── editor/                   # JsonDocumentFactory, TooltipListener, FoldingAwareEditor
│   │   ├── jsonQuery/                # JsonQueryPresenter, JsonQueryView
│   │   ├── tab/                      # JsonTabsPresenter
│   │   └── convertType/              # CodeInputPanel
│   ├── dialog/
│   │   ├── convertType/              # ConvertPreviewExecutor, JsonToTypeDialogPresenter
│   │   ├── loadJson/                 # LoadJsonFromApiDialogPresenter
│   │   └── generateJson/schema/      # GenerateSchemaJsonTabPresenter
│   ├── onboarding/                   # OnboardingStep8DiffTooltipController, TutorialDialogView
│   └── toolWindow/                   # JsoninjaToolWindowFactory
└── settings/
src/main/resources/META-INF/
└── plugin.xml                        # 서비스 등록, 리스너 선언, toolWindow 속성
src/test/kotlin/com/livteam/jsoninja/
└── services/                         # 기존 테스트
```

**Structure Decision**: 기존 프로젝트 구조를 유지. 새 디렉토리 생성 없음. 기존 서비스에 CoroutineScope를 추가하고, Presenter의 비동기 로직을 서비스로 이동한다.

## Complexity Tracking

> No constitution violations — this section is intentionally empty.

## Phase 1 Design Summary

### Deprecated/Experimental API 교체 전략 (19건)

| Category | Count | Strategy |
|----------|-------|----------|
| ReadAction.compute/run | 3 | 동기 문맥 → `readActionBlocking`, 코루틴 문맥 → `readAction` |
| Document.addDocumentListener | 2 | parentDisposable 추가 + 수동 remove 제거 |
| ObjectNode.fields() | 1 | → `properties()` |
| URL(String) | 1 | → `URI.create(...).toURL()` |
| DynamicPluginListener bridge | 2 | 구현/등록 제거 |
| ToolWindowFactory bridge | 10 | plugin.xml 속성 추가 + 필요시 빈 오버라이드 |

### 코루틴 전환 전략

| Pattern | Count | Target |
|---------|-------|--------|
| `executeOnPooledThread` + `invokeLater` | 7곳 | 서비스 CoroutineScope + `Dispatchers.EDT` |
| Standalone `invokeLater` | ~16곳 | `withContext(Dispatchers.EDT)` 또는 서비스 스코프 |
| `Alarm` debounce | 3곳 | 로컬 Job + `delay` |

### 서비스 변경

| Service | Change |
|---------|--------|
| `OnboardingService` | CoroutineScope 주입 추가 |
| `JsonQueryService` | CoroutineScope 주입 추가 (쿼리 실행 로직 이동 시) |
| 신규 또는 기존 서비스 | SchemaStore 로드, API 로드 비동기 로직 수용 (구체적 결정은 tasks 단계) |

## Constitution Re-Check (Post Phase 1 Design)

| Principle | Status | Notes |
|-----------|--------|-------|
| I. User Experience First | PASS | EDT 차단 없음 유지. 코루틴 전환으로 취소 처리 개선. |
| II. IDE Compatibility & Stability | PASS | 모든 deprecated/experimental API 교체. 243+ API만 사용. |
| III. Performance & Large File Resilience | PASS | readAction 블록 짧고 멱등적. 기존 성능 보존. |
| IV. Code Quality & Maintainability | PASS | 비동기 소유권 명확화. executeOnPooledThread 제거로 패턴 단순화. |
| V. Test Discipline | PASS | 기존 테스트 유지. CoroutineScope 주입으로 테스트 용이성 개선. |

**Gate result**: PASS
