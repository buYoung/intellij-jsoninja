# 07 — 빈 배열과 최신 문서 트리 갱신 기록

2026-09-16, R2.3a/R2.3b. 상태: **ready**. 헤드리스 플랫폼의 실제 문서·트리 모델과 UndoManager를 관찰했다.

## Baseline

- 기준 HEAD: `2333640a77d0cdbb07b33caeb0c156c14f70cff6`. 선행 01의 텍스트 컴포넌트 데이터 공급과 쓰기/Undo 계약은 유지했다.
- 수정 전 컴파일된 모델을 실제 JsonEditorView에서 관찰했다. `{"items":[],"nested":{"empty":[]}}`는 `[ROOT,nested]`만 표시했고, `setText("{\"changed\":1}")` 후에도 같은 노드가 남았다. 로그 `/tmp/json-integrity-03-panel-07-baseline.log`.

## Implementation

- 빈 배열은 property 이름 또는 index 컨테이너 아래의 `value : []` 노드로 표현한다.
- JsonEditorView가 DocumentListener를 소유한다. TREE 모드에서만 문서 변경에 따라 재구축하며, presenter.setText와 직접 문서 변경/Undo/Redo가 같은 구독으로 들어온다. 기존 사용자 콘텐츠 callback을 교체하지 않는다.
- 문서/수정 stamp/표시 모드를 캡처하고 최종 EDT 적용 때 다시 확인한다. 트리 presenter도 sequence와 disposed 상태를 확인한다.
- 계산은 Default, 적용은 EDT다. TEXT 전환은 현재 재구축을 취소하며 dispose는 listener 제거, job/scope 취소 후 뷰를 폐기한다. 트리 경로는 문서에 쓰지 않는다.
- 기존 한 인자 refreshTreeFromJson 진입점은 유지했다. 기존 전체 펼치기 동작도 유지했다.

## Acceptance

작업 디렉터리: `/Users/buyong/workspace/private/json-helper2`.

- `./gradlew compileKotlin`: exit 0.
- `./gradlew test -PskipWasmBuild=true --tests 'com.livteam.jsoninja.ui.component.editor.JsonEditorUndoRedoTest' --tests 'com.livteam.jsoninja.ui.component.editor.FoldingAwareEditorTextFieldTest'`: exit 0, 8+3개, 실패/오류/스킵 0.
- 수동 명령은 03과 같은 `python3` → JShell 인자 배열 실행. 실제 BasePlatformTestCase 환경, `java.awt.headless=true`, 현재 컴파일 클래스와 IC 2024.3 라이브러리. 로그 `/tmp/json-integrity-07-manual.log`, exit 0. 네이티브 UI나 신규 테스트 파일/사례는 사용하지 않았다.

| 입력/전환 | 실제 트리/결과 |
| --- | --- |
| items:[], nested.empty:[] | items : []와 nested → empty : [] 표시 |
| `[[],{}]` | [0] → value : [], [1] 표시 |
| `[]` | value : [] 표시 |
| TREE를 유지한 setText 교체 | queryResult : 1 표시 |
| Undo / Redo | value : [] / queryResult : 1로 각각 갱신 |
| 같은 EDT에서 old→latest 두 번 교체 | latest : 2만 표시 |
| invalid / blank | Invalid JSON format / 기존 value : 빈 문자열 상태, 예외 없음 |
| 변경 즉시 TEXT 전환 | HIDDEN_NOT_UPDATED=true |
| 변경 즉시 Disposer.dispose | DISPOSED_NOT_UPDATED=true |

- query/generation의 공통 최종 소비자인 JsonEditorView.setText에서 교체를 직접 관찰했다. 실제 query/generation 버튼 조작과 네이티브 표시 자체를 관찰한 것은 아니다.
- 01의 Undo 동작을 기존 8개 테스트로 재검증했다. 생성/포맷/쿼리 원문이나 설정/마커는 변경하지 않았다.

### 검증 소스 SHA-256

- `src/main/kotlin/com/livteam/jsoninja/ui/component/editor/JsonEditorView.kt`: `3fe164f4d17bb2c18b1c4321abd41cc740000bf7bf1b803ad663f27d34b7e7cf`
- `src/main/kotlin/com/livteam/jsoninja/ui/component/editor/JsonEditorTreePresenter.kt`: `1d040299fac6f6c721969e35c7cf51dee85fa6ae64eab3ade273104cf39da4dd`
