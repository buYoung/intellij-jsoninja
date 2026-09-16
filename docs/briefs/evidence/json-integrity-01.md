# 01 — JSON 편집기 Undo/Redo 라우팅 검증 기록

2026-09-16, 감사 항목 U1. 상태: **ready**. IDE 액션 경로를 검증했으며 네이티브 키보드·메뉴 조작은 미확인이다.

## Baseline

- 기준 HEAD: `9eed77ed863f82843f9aadca0af479cf6efa0afd`.
- 기존 미추적 `JsonEditorUndoRedoTest.kt`의 8개 사례와 원문을 보존했다. `.codemap`, `.antigravitycli` 및 무관한 브리프는 수정하지 않았다.
- 작업 디렉터리: `/Users/buyong/workspace/private/json-helper2`.
- 명령: `./gradlew test -PskipWasmBuild=true --tests 'com.livteam.jsoninja.ui.component.editor.JsonEditorUndoRedoTest' --tests 'com.livteam.jsoninja.ui.component.editor.FoldingAwareEditorTextFieldTest'`.
- 수정 전 exit 1: 11개 중 4개 실패. JSON Undo/Redo 8개 중 IDE 액션 4개 실패, 직접 UndoManager 제어 4개 통과. 접기 3개 통과.
- 원인: supplementary 편집기에서는 플랫폼이 FILE_EDITOR를 자동 보충하지 않고, 부모가 제공한 FILE_EDITOR가 있으면 부모 문서가 Undo 대상이 된다.

## Implementation

- `JsonEditorTextView`가 `UiDataProvider`를 구현해 생성되어 있고 폐기되지 않은 편집기의 TextEditor를 `PlatformCoreDataKeys.FILE_EDITOR`로 제공한다.
- 호출 흐름: `JsonEditorView` → `JsonEditorTextView` → `EditorTextFieldFactory.createJsonField` → 실제 editor content component의 UI 스냅샷 → IDE `$Undo`/`$Redo` → 해당 Document의 UndoManager 이력.
- 최소 플랫폼 IC 2024.3 (`243.21565.193`)의 캐시된 source JAR에서 `UiDataProvider.kt`의 OverrideOnly 구현 계약과 `TextEditorProvider.kt#getTextEditor` 공개 메서드를 확인했다. 내부 wrapper를 직접 생성하거나 reflection으로 우회하지 않는다. [플랫폼 소스](https://github.com/JetBrains/intellij-community/blob/idea/243.21565.193/platform/platform-impl/src/com/intellij/openapi/fileEditor/impl/text/TextEditorProvider.kt).
- 공용 factory와 presenter의 쓰기/Undo 묶음, JSONINJA_EDITOR_KEY, disposal, viewer/oneLineMode/fileType/dialog 옵션을 변경하지 않았다. 기존 createJsonField/createCodeField/createPlainTextField 호출자 5곳에 영향이 없다.
- 수정 후 `./gradlew compileKotlin`: exit 0.

## Acceptance

- 위의 동일 targeted test 명령 재실행: exit 0. Undo/Redo 8개, 접기 3개, 각각 failures/errors/skipped 0.
- 타이핑 및 presenter replacement: IDE Undo로 이전 내용, Redo로 수정 내용 복원.
- 직접 Undo 이후 IDE Redo: JSON 내용 복원.
- 부모 `{"host":0} `의 공백 보존: JSON Undo/Redo 동안 부모 문서 불변.
- Undo 이후 새 수정: 이전 Redo 분기 무효화.
- `{"value":{{ name }}}` → `{"value":{{name}}}`: 기존 5000ms 대기 한도 내 정규화, 타이핑과 함께 Undo/Redo.
- 보고서: `build/test-results/test/TEST-com.livteam.jsoninja.ui.component.editor.JsonEditorUndoRedoTest.xml`, `TEST-com.livteam.jsoninja.ui.component.editor.FoldingAwareEditorTextFieldTest.xml` (후속 테스트가 덮어쓸 수 있어 결과를 여기에 기록).
- 네이티브 IDE 조작, DevKit 검사, 전체 지원 버전 Plugin Verifier, 동적 unload는 실행하지 않았다. 하위 브리프의 허용대로 액션 라우팅 계약만 후속 03/04/07/16에 전달한다.
- 현재 사용자의 하위 작업별 커밋 요청을 적용한다. 이전 계획 문서의 커밋 금지는 이번 명시적 요청으로 대체되며 push/publish는 수행하지 않는다.

### 검증 소스 SHA-256

- `src/main/kotlin/com/livteam/jsoninja/ui/component/editor/JsonEditorTextView.kt`: `88c01c5a2e3e3b97dee461ee2eea5168bf0442a44bb2186e2b0c8d13b1596754`
- `src/main/kotlin/com/livteam/jsoninja/ui/component/editor/EditorTextFieldFactory.kt`: `40ea83b15d3eb8c6a57b1432d00770b08b68737151d4c664664b5728add45c91`
- `src/main/kotlin/com/livteam/jsoninja/ui/component/editor/JsonEditorTextPresenter.kt`: `6a2c5a0dd5a33daa1e7a655af3a72e4cd634677ce16cdcd96a3c5d4803645c6c`
- `src/test/kotlin/com/livteam/jsoninja/ui/component/editor/JsonEditorUndoRedoTest.kt`: `784310d389c3f7b6d57d665fa246e8b13df93a9472e50f1126e1f74cef57ba15`

- 기준 HEAD 대비 해당 Kotlin 변경 diff SHA-256: `ac50c1d136bbe5b3d97662c8c880979b29cb61babaa81c8e782b678caf9feb36`.
