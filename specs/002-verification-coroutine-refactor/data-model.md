# Data Model: Marketplace Verification 경고 제거 및 코루틴 리팩터링

**Date**: 2026-04-10  
**Feature**: 002-verification-coroutine-refactor

## Overview

이 피처는 데이터 모델 변경이 아닌 코드 구조 리팩터링이다. 엔티티 변경 없이 기존 서비스에 `CoroutineScope` 생성자 파라미터가 추가된다.

## Service Structure Changes

### Modified Services (CoroutineScope 추가)

| Service | Level | Current Constructor | Change |
|---------|-------|-------------------|--------|
| `OnboardingService` | PROJECT | `(project: Project)` | `(project: Project, cs: CoroutineScope)` |
| `JsonQueryService` | PROJECT | `(project: Project)` | `(project: Project, cs: CoroutineScope)` — if query execution moves here |

### New Service Candidates

| Service | Level | Purpose | Source of Logic |
|---------|-------|---------|----------------|
| `JsonSchemaLoadService` | PROJECT | SchemaStore 카탈로그/URL 로드 | `GenerateSchemaJsonTabPresenter` lines 126, 379 |
| `JsonApiLoadService` | PROJECT | 외부 API JSON 로드 | `LoadJsonFromApiDialogPresenter` line 56 |

> Note: 새 서비스 생성은 planning 단계에서 최종 결정. 기존 서비스 확장이 가능하면 새 서비스를 만들지 않는다.

## File Change Map

### Deprecated/Experimental API 교체 (P1)

| File | Change Type | Lines |
|------|-------------|-------|
| `JsonDocumentFactory.kt` | `ReadAction.compute` → `readActionBlocking` | 62, 76 |
| `JsonEditorTooltipListener.kt` | `ReadAction.compute` → `readAction` (코루틴 전환 시) | 60 |
| `FoldingAwareEditorTextField.kt` | `ReadAction.run` → `readActionBlocking` | 50 |
| `JsonDiffExtension.kt` | `addDocumentListener` → parentDisposable 추가, `removeDocumentListener` 제거 | 314, 318 |
| `CodeInputPanel.kt` | `addDocumentListener` → parentDisposable 추가 | 79 |
| `JsonToTypeInferenceContext.kt` | `fields()` → `properties()` | 157 |
| `JsonQueryView.kt` | `URL(link)` → `URI.create(link).toURL()` | 72 |
| `JsonHelperActivationListener.kt` | `DynamicPluginListener` 구현 제거 | 3, 8 |
| `plugin.xml` | `DynamicPluginListener` listener 제거, toolWindow 속성 추가 | 40-41, 16-17 |
| `JsoninjaToolWindowFactory.kt` | 필요시 빈 오버라이드 추가 | — |

### 코루틴 전환 (P2)

| File | Change Type |
|------|-------------|
| `JsonQueryPresenter.kt` | `executeOnPooledThread` + `invokeLater` → 서비스 스코프 |
| `ConvertPreviewExecutor.kt` | `Alarm` + `executeOnPooledThread` → 로컬 Job + delay |
| `LoadJsonFromApiDialogPresenter.kt` | `executeOnPooledThread` + `invokeLater` → 서비스 스코프 |
| `GenerateSchemaJsonTabPresenter.kt` | `executeOnPooledThread` x2 + `invokeLater` x6 → 서비스 스코프 |
| `GenerateRandomJsonAction.kt` | `executeOnPooledThread` + `invokeLater` x4 → 서비스 스코프 |
| `JsonDiffExtension.kt` | `Alarm` + `executeOnPooledThread` → 로컬 Job + delay |
| `JsonEditorTooltipListener.kt` | `Alarm` → 로컬 Job + delay |
| `JsonTabsPresenter.kt` | `invokeLater` x3 → `Dispatchers.EDT` |
| `JsonEditorTextPresenter.kt` | `invokeLater` → `Dispatchers.EDT` |
| `FoldingAwareEditorTextField.kt` | `invokeLater` → `Dispatchers.EDT` |
| `JsonToTypeDialogPresenter.kt` | `invokeLater` → `Dispatchers.EDT` |
| `JsoninjaOnboardingStartupActivity.kt` | `invokeLater` → `Dispatchers.EDT` |
| `OnboardingService.kt` | `invokeLater` x3 → 서비스 스코프 + `Dispatchers.EDT` |
| `OnboardingStep8DiffTooltipController.kt` | `invokeLater` → `Dispatchers.EDT` |

## State Transitions

해당 없음 — 엔티티 상태 변경 없음.

## Validation Rules

- 모든 새 비동기 코드에서 `GlobalScope`, `project.coroutineScope`, `Application.getCoroutineScope()` 사용 금지
- `Dispatchers.Main` 사용 금지 (EDT만 사용)
- `CancellationException` / `ProcessCanceledException` catch-all 금지
- 서비스 생성자에서 다른 서비스 파라미터 주입 금지 (호출 시점 `getService()`)
