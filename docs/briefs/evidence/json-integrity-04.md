# 04 — 오래된 비동기 문서 쓰기 차단 기록

2026-09-16, R1.2a/R1.2b/R1.2c. 상태: **ready**. 계산 완료와 EDT 적용 사이를 제어한 헤드리스 관찰로 세 최종 소비 경로를 확인했다.

## Baseline

- 기준 HEAD: `02698e766fcd0a25dace2901993ea29c0e5a5547`.
- 소스 기준 결함: diff는 계산 시 문서 stamp 없이 문자열만 반환했고, query callback의 후속 포맷은 새 요청/수정을 검사하지 않았으며, generation 완료는 그때 선택된 editor를 다시 조회했다. 수정 전 경합의 발생 빈도나 실제 사용자 데이터 손실을 주장하지 않는다.
- 선행 01의 파일 편집기/Undo 경계와 03의 formatter/선택 영역/Default 처리는 유지한다. 03 이후 panel의 생성 경로만 확장했다.

## Implementation

- diff는 readAction에서 텍스트와 stamp를 함께 캡처한다. 요청 번호와 stamp가 모두 현재일 때만 EDT에서 쓰며, write command 안에서도 다시 확인한다. 캐시 hash는 수용된 결과만 갱신한다. 공백 편집도 이미 진행 중인 요청을 무효화하되 기존 300ms debounce/소규모 편집 생략/자체 갱신 가드는 유지한다.
- viewer 수명 flag와 실제 editor 폐기를 확인해 늦게 끝난 감지가 폐기된 viewer에 listener를 설치하지 않도록 했다. formatter guard는 finally에서 해제하며 폐기된 결과를 적용 성공으로 기록하지 않는다.
- query presenter의 request ID/무효화 API를 후처리까지 전달한다. tab DocumentListener는 query 자체 쓰기 이외의 변경에서 이전 검색과 포맷 Job을 무효화한다. 마지막 적용은 request ID, document identity/stamp, project/tab 수명을 확인한다.
- generation은 launch 전에 GenerationTarget(editor/document/stamp/request token)을 얻는다. 같은 editor의 새 요청, 수정, 탭 제거, 부모 폐기는 이전 token을 무효화한다. 선택 탭이 바뀌어도 유효한 원래 대상만 갱신한다. 기존 동기 setter API는 유지하고 비동기 action은 captured-target overload를 사용한다.
- generation의 오래된 오류 UI도 같은 target 검사로 억제한다. formatter의 기존 옵션/취소 신호와 한 번의 editor.setText 쓰기 경로를 유지한다.
- IC 2024.3 소스에서 Disposer.isDisposed가 짧은 진단 정보에 의존하는 deprecated API임을 확인하고 사용하지 않았다. 등록한 소유 수명 flag와 JsonTabsPresenter의 실제 등록 editor 목록을 사용한다.

## Acceptance

작업 디렉터리: `/Users/buyong/workspace/private/json-helper2`.

- `./gradlew compileKotlin`: 최종 exit 0.
- `./gradlew test -PskipWasmBuild=true --tests 'com.livteam.jsoninja.services.JsonDiffServiceTest' --tests 'com.livteam.jsoninja.actions.ShowJsonDiffActionTest' --tests 'com.livteam.jsoninja.ui.component.editor.JsonEditorUndoRedoTest'`: exit 0, 각각 7/6/8개, 총 21개, 실패/오류/스킵 0.
- 수동 명령: `python3 -c 'import os; from pathlib import Path; os.execvp("jshell", ["jshell", *Path("/tmp/json-integrity-platform-jshell.args").read_text().splitlines()])'`, 최종 exit 0. 03에서 준비한 IC 2024.3 test classpath/JVM 설정, `java.awt.headless=true`를 사용했다.
- JShell 안의 임시 ManualDispatcher(ConcurrentLinkedQueue에 Runnable을 보관)로 project JsoninjaCoroutineScopeService를 일시 교체했다. 실제 Default 계산을 완료한 뒤 원래 coroutine의 재개만 보류했다. 테스트 파일·메서드·Gradle 작업은 추가하지 않았다. fixture 종료 시 원래 서비스 복원, scope 취소, editor/disposable 정리를 마쳤다.
- diff는 실제 두 Editor를 가진 EditorDiffViewer 구현과 JsonDiffService.createDiffRequest로 실제 onViewerCreated 경로를 실행했다. `formatJsonInBackground`의 readAction 이후 및 Default 계산 반환 지점(`JsonDiffExtension.kt:402`, `:418`)을 큐에서 구분했다.
- query는 실제 SearchTextField에 등록된 Enter listener, JsonQueryPresenter, JsonTabContextFactory를 실행했다. 후속 formatJsonOnDefault 완료 뒤 `JsonTabContextFactory.kt:133`의 재개를 보류했다.

| 제어한 상황 | 실제 결과 |
| --- | --- |
| diff old 계산 완료 → latest 편집 → 재개 | DIFF_STALE_DISCARDED=true, 최신 값만 정상 포맷 |
| 정상 diff 포맷 Undo/Redo | DIFF_UNDO=true, DIFF_REDO=true |
| diff guard | DIFF_GUARD_CLEAR=true; 추가 drain에서 재귀 쓰기 없음 |
| 계산 완료 뒤 viewer 폐기 | DIFF_DISPOSED_UNCHANGED=true |
| query A 후처리 대기 → query B | QUERY_NEW_REQUEST_WINS=true (2) |
| query 후처리 대기 → 직접 문서 수정 | QUERY_MANUAL_EDIT_PRESERVED=true |
| 정상 query 결과 Undo/Redo | QUERY_UNDO=true, QUERY_REDO=true |
| query 후처리 대기 → tab 폐기 | QUERY_DISPOSED_UNCHANGED=true |
| generation A 캡처 → B 선택 → 완료 | GENERATION_ORIGINAL_TARGET=true, GENERATION_OTHER_TAB_UNCHANGED=true |
| 같은 generation token 재사용 | GENERATION_APPLIED_ONCE=true |
| 정상 generation Undo/Redo | GENERATION_UNDO=true, GENERATION_REDO=true |
| generation 대상 수정/닫기/중복 요청/부모 폐기 | GENERATION_EDIT_INVALIDATES/CLOSED_TAB_REJECTED/OLD_REQUEST_REJECTED/NEW_REQUEST_APPLIED/DISPOSED_REJECTED 모두 true |
| 계산 완료 뒤 주입된 project 수명 scope 취소 | PROJECT_SCOPE_CANCEL_BLOCKS_WRITE=true |

- 네이티브 생성 대화상자·창·실제 프로젝트 닫기 조작은 하지 않았다. generation은 공개 capture/apply 최종 소비 API로 계산 완료를 지연시켰고, 프로젝트 종료는 그 수명 scope의 취소 경로를 직접 관찰했다. 기존 scope 주입과 project.isDisposed 최종 검사는 소스에서도 확인했다.

## 05에 넘기는 원문 계약

- `isApplyingQueryResult` 동안의 editor.setText는 query-result 쓰기다. 이 밖의 문서 변경은 현재 검색/후처리를 무효화한다.
- stale/disposed 작업은 editor.setText에 도달하지 않는다. 원문 저장소 동기화와 성공 null 의미는 아직 05가 맡는다. 05는 이 origin flag, request ID, stamp, tab 수명 검사를 보존해야 한다.

### 검증 소스 SHA-256

- `src/main/kotlin/com/livteam/jsoninja/diff/JsonDiffExtension.kt`: `0c8ec14ef5900bd02020a4791c1702e4b3ce4aff57f2428924acd16b7353d91f`
- `src/main/kotlin/com/livteam/jsoninja/ui/component/jsonQuery/JsonQueryPresenter.kt`: `8cc7a4c31fb94208c0cc66678f9f4accd31bfd518a69a3fef1c2f8588a6888fa`
- `src/main/kotlin/com/livteam/jsoninja/ui/component/tab/JsonTabContextFactory.kt`: `d50a10c4a5736cb36e8646858de84c5e1d2df8befe394b5be18a83e5b8d9b32b`
- `src/main/kotlin/com/livteam/jsoninja/ui/component/tab/JsonTabsPresenter.kt`: `c6f0a6fdff964d646cb42536bfac9c853e62b4686248384448fb0289835d4421`
- `src/main/kotlin/com/livteam/jsoninja/ui/component/main/JsoninjaPanelPresenter.kt`: `a36be14ae97707b80638c45a6b5073f144b455f3aef080e6e13b731660506380`
- `src/main/kotlin/com/livteam/jsoninja/actions/GenerateRandomJsonAction.kt`: `48a08804ddf848c2aa7af9d4fe3614f58ce3003cc0373494f9946514f35fccad`
