# diff-ux 01–04 — JSON Diff 비교 흐름 개선 검증 기록

2026-09-26. 상태: **구현 완료, 헤드리스 e2e golden 검증 완료, GUI 샌드박스 관찰 미확인**.

- 사용자 요청에 따라 e2e golden 테스트를 추가했다. 브리프의 "테스트 추가 금지"는 이 명시적 요청으로 대체된다.
- 최소 지원 빌드(IC 2024.3, `243.21565.193`)의 헤드리스 플랫폼에서 실제 diff host, viewer, 툴바 액션, `JsonDiffExtension`을 구동해 검증했다.
- `runIde` 화면 관찰은 여전히 수행하지 않았으므로, 부모 브리프의 Child Briefs 체크박스는 표시하지 않았다.

## Baseline

- 기준 HEAD: `7c5392e17bc34af42ae59de4181180153a82ad7e` (main). 변경은 커밋하지 않은 작업 트리에 있다.
- 작업 디렉터리: `/Users/buyong/workspace/private/json-helper2`.
- 기존 미커밋 변경 `.codemap/config.toml`과 `.antigravitycli/`는 수정하지 않았다.
- 플랫폼 API 확인: 최소 지원 빌드 IC 2024.3(`243.21565.193`) sources JAR, 그리고 intellij-community `master` 원본.

## 01 — 정렬 계산

- `model/JsonDiffAlignment.kt`: `JsonDiffSortMode`(`KEY_ASCENDING`, `LEFT_ORDER`), `JsonDiffAlignmentOptions`(`shouldIgnoreArrayOrder=false`), `JsonDiffAlignmentStatus`(`APPLIED`/`NO_CHANGE`/`INVALID_INPUT`), `JsonDiffAlignmentResult`.
- `services/JsonDiffAlignmentService.align`: 두 텍스트와 옵션을 받아 두 텍스트와 상태를 반환한다. Document와 세션은 변경하지 않는다.
- 입력은 `fullyUnescapeJson` → placeholder 추출 → `isValidJson` → 공유 mapper `readTree` 경로로 해석한다. 출력은 기존 `JsonFormatterService.formatJson`을 거친다. formatter와 parser는 수정하지 않았다.
- 양쪽의 같은 placeholder는 공통 token으로 바꿔 비교하고, 출력할 때 `restorePlaceholders`로 되돌린다.
- `KEY_ASCENDING` + 배열 옵션 꺼짐이면 기존과 같이 각 쪽을 독립적으로 정렬한다. 유효하지 않은 쪽은 원본을 유지한다.
- 그 밖의 조합은 한 쌍 단위로 처리한다. 한쪽이라도 유효하지 않으면 원래 두 텍스트와 `INVALID_INPUT`을 반환한다.
- 배열 매칭은 `JsonNode` 동등성(객체 필드 순서 무시, 중첩 배열 순서와 값 타입 유지)을 해시 인덱스로 조회한다. 매칭되지 않은 요소는 원래 상대 순서로 뒤에 붙인다.
- `CoroutineContext.ensureActive()`로 취소를 전달하고, `CancellationException`은 다시 던진다.
- 코드 추적 결과(실행 관찰 아님):
  - 왼쪽 `{"id":7,"status":"active","name":"Alice"}`, 오른쪽 `{"name":"Alice","id":7,"status":"inactive"}`:
    - `LEFT_ORDER`: 왼쪽은 입력 그대로, 오른쪽은 id/status/name 순서이며 inactive가 유지된다.
    - `KEY_ASCENDING`: 양쪽이 id/name/status 순서가 된다.
  - 왼쪽 `[{"id":1},{"id":2}]`, 오른쪽 `[{"id":2},{"id":1}]`:
    - 배열 옵션 꺼짐: 오른쪽 [2,1]이 유지된다.
    - 배열 옵션 켜짐: 오른쪽이 [1,2]가 된다.
  - id만 같고 다른 값이 있는 요소는 매칭되지 않아 값이 그대로 남는다.
- `./gradlew compileKotlin`: exit 0.

## 02 — 세션 정렬 제어와 복원

- `diff/JsonDiffSession.kt`가 세션 상태를 소유한다. 대상은 Document 쌍, 모드·자동 정렬·배열 옵션, 세대(generation), 복원 스냅샷, 대기 작업 무효화 listener다.
- 새 세션 기본값은 `KEY_ASCENDING`, 배열 옵션 false이고, 자동 정렬은 `diffSortKeys`로 초기화한다. 설정 저장소는 변경하지 않는다.
- `JsonDiffKeys.JSON_DIFF_SESSION`을 추가했다. 기존 marker, sort-key, change-guard 키의 의미는 유지한다.
- `JsonDiffService`에 세션 명령을 추가했다: `applySortOnce`, `changeSortMode`, `changeAutoSort`, `changeIgnoreArrayOrder`, `canRestoreSort`, `restoreSort`, `formatAutomatically`.
  - 수동 정렬은 세대를 올리고 Default에서 계산한 뒤, EDT에서 다음을 모두 확인한다: 세대, 양쪽 stamp, host 활성 상태, project 수명. 확인이 끝나면 하나의 `WriteCommandAction`으로 양쪽을 쓴다.
  - 텍스트가 바뀐 수동 정렬만 복원 스냅샷을 교체한다.
  - 복원은 정렬 직후 stamp가 그대로일 때만 가능하다. 대기 작업을 무효화한 뒤 guard를 건 상태로 양쪽을 쓰므로 다시 정렬되지 않는다.
  - 기존 공개 API의 시그니처는 그대로다: `openDiff(displayMode, currentJson, defaultSortKeys)`, `createDiffRequest` 두 overload, `validateAndFormat`.
- `JsonDiffExtension`은 쪽별 Boolean 포맷터 대신 양쪽 문서를 함께 보는 listener 하나를 사용한다. 그대로 유지한 것:
  - 300ms `Alarm` debounce
  - 작은 공백 편집 스킵
  - change guard
  - 대용량 경고
  - viewer 수명과 연결된 child scope
- 세션이 없는 JSONinja request는 기존 Boolean 값으로 임시 세션을 만든다.
- 툴바 액션(`actions/`)은 `SortJsonDiffKeysOnceAction`(현재 모드 적용), `SelectJsonDiffSortModeAction`(ComboBox), `ToggleJsonDiffAutoSortAction`, `ToggleJsonDiffArrayOrderAction`, `RestoreJsonDiffSortAction`이다.
- 메시지 키 14개를 기본/en/ko/ja/zh_CN 번들에 추가했다.
- `./gradlew test -PskipWasmBuild=true --tests 'com.livteam.jsoninja.services.JsonDiffServiceTest' --tests 'com.livteam.jsoninja.services.JsonFormatterServiceTest'`: exit 0 (7건, 14건, 실패 0).

## 03 — 편집기 선택 비교

- `actions/editor/EditorShowJsonDiffAction.kt`를 추가했다. 선택 영역이 있으면 선택 영역을, 없으면 문서 전체를 한 번 캡처한다.
- Default에서 `isValidJson`으로 검증한 뒤, EDT에서 편집기 폐기, 문서 stamp, 선택 범위가 바뀌지 않았는지 확인한다. 이후 `JsonDiffService.openDiff`를 호출한다.
- 원본 문서를 쓰지 않으며 tool window 내용으로 대체하지도 않는다. 유효하지 않거나 빈 입력이면 오류 힌트만 표시한다.
- `update()`는 파싱 없이 선택 여부로 문구만 바꾼다.
- `plugin.xml`의 `com.livteam.jsoninja.action.group.EditorContextMenu`에 등록했다. 표시 방식 기본값은 `ShowJsonDiffAction.getDefaultDisplayMode`로 기존 로직을 공유한다.
- `./gradlew compileKotlin`: exit 0. `./gradlew test -PskipWasmBuild=true --tests 'com.livteam.jsoninja.actions.ShowJsonDiffActionTest'`: exit 0 (6건, 실패 0).

## 04 — 양쪽 이름

- 세션이 사용자 지정 이름을 보관한다. 앞뒤 공백을 제거한 빈 값은 `dialog.json.diff.left/right` 기본값으로 돌아간다.
- 요청에는 현재 이름을 content title로 넣는다. 함께 `DiffUserDataKeysEx.EDITORS_TITLE_CUSTOMIZER`로 세션 라벨을 제공한다. 이름을 바꾸면 viewer나 request를 다시 만들지 않고 같은 라벨의 텍스트만 갱신한다. 따라서 Document, 정렬 선택, 복원 가능 상태가 유지된다.
- API 근거:
  - 243 소스에서 `DiffUserDataKeysEx.EDITORS_TITLE_CUSTOMIZER`, `DiffEditorTitleCustomizer`, `DiffUtil.createTextTitles`의 사용을 확인했다. 모두 `@ApiStatus` 표시가 없다.
  - master에서 `DiffEditorTitleCustomizer`는 `fun interface`이며 `getLabel()`이 같다.
  - `DiffTitleHandler`는 `@ApiStatus.Internal`이라 사용하지 않았다.
  - reflection이나 내부 컴포넌트 접근은 없다.
- `RenameJsonDiffTitlesAction`과 `ui/diff/JsonDiffTitlesDialog.kt`를 추가했다. 확인했을 때만 두 이름을 함께 적용하고, 취소하면 변경하지 않는다. 전체 request title 인자는 바꾸지 않는다.
- e2e 작성 중 `showAndGet()`을 `show()` + `isOK`로 바꿨다. 헤드리스의 `HeadlessDialog.isModal()`이 항상 false라 `showAndGet()`이 테스트에서 예외를 내기 때문이다. 실제 IDE에서는 `DialogWrapperPeerImpl.createDialog`가 IDE modality를 설정하므로 동작은 같다.
- 메시지 키 6개를 5개 번들에 추가했다.
- `./gradlew test -PskipWasmBuild=true --tests 'com.livteam.jsoninja.services.JsonDiffServiceTest'`: exit 0 (7건).

## 최종 수정본 검증

- `./gradlew compileKotlin`: exit 0.
- `./gradlew test -PskipWasmBuild=true --tests 'com.livteam.jsoninja.services.JsonDiffServiceTest' --tests 'com.livteam.jsoninja.services.JsonFormatterServiceTest' --tests 'com.livteam.jsoninja.actions.ShowJsonDiffActionTest'`: exit 0. 결과는 7건, 14건, 6건이며 failures/errors/skipped는 모두 0이다.
- 기존 테스트는 창을 열지 않으므로 새 UI 동작을 증명하지 않는다.

## e2e golden 검증

### 추가한 테스트

- `services/JsonDiffAlignmentGoldenTest` (16건): `src/test/testData/jsonDiff/alignment/<case>`의 입력, 옵션, 기대 출력을 `assertSameLinesWithFile`로 비교한다.
  - `LEFT_ORDER`에서는 왼쪽 텍스트가 입력과 바이트 단위로 같은지도 확인한다.
  - 사용 불가 또는 무변경 결과에서는 두 입력이 그대로인지도 확인한다.
  - 케이스: 승인 예시 id/status/name과 두 레코드 배열, 같은 id의 변경 값, 중복 개수, 중첩 객체와 오른쪽 전용 키, 한쪽 입력 오류 3종, placeholder, 숫자 정밀도, JSON5, 무변경.
- `diff/JsonDiffWorkflowE2ETest` (24건, `FileEditorManagerTestCase`):
  - editor tab: 실제 `FileEditorManagerImpl`로 `openDiff`부터 `DiffRequestProcessor` viewer까지 구동한다.
  - window: `UiInterceptors`로 받은 실제 `JsonDiffWindowDialog`의 diff panel을 사용한다.
  - 툴바 액션: request의 `CONTEXT_ACTIONS` 인스턴스를 toolbar 대상 컴포넌트의 실제 UI DataContext로 실행한다.
  - 이름 변경 다이얼로그: `UiInterceptors`로 입력하고 확인/취소를 누른다.
  - 문서 결과: `src/test/testData/jsonDiff/workflow` golden 파일과 비교한다.
- `diff/JsonDiffLocalizationTest` (2건): 새 키 25개가 5개 번들에 모두 있고, 한국어 번들이 승인 문구와 같은지 확인한다.
- golden 파일 112개는 테스트 출력으로 생성하지 않고 승인 예시와 브리프 규칙에서 직접 작성했다. 전체 내용 SHA-256은 `d633998cf80d387ac47cf678322e8f4bc36daa7bd53285f7bcd63aeae05fc093`이다.

### 전역 기준별 대응

| 기준 | 실행 증거 | 남은 한계 |
|---|---|---|
| 1 왼쪽 기준 / 키 사전순 | 두 host에서 승인 예시 golden 일치, 기준 문서 불변 | — |
| 2 툴바 제어 | 두 host에서 툴바에 6개 액션 포함 및 모드 표시 문구, 자동 왼쪽 기준에서 왼쪽 키 순서 status/id/name이 오른쪽에 전파, 설정값 불변 | 실제 화면 배치와 팝업 렌더링 |
| 3 정렬 되돌리기 | 두 host에서 `{"z":1,"a":2}` 정렬 후 복원, 이후 편집 시 복원 불가·편집 유지, 자동 정렬 중 복원 쓰기가 다시 정렬되지 않고 이후 실제 편집은 정렬 | — |
| 4 배열 순서 무시 | 두 host에서 기본 [2,1] 유지, 켜면 [1,2], 같은 id의 변경 값은 매칭되지 않음(golden) | — |
| 5 선택 JSON 비교 | 선택 E01이 왼쪽에 들어가고 오른쪽 기준·열린 탭 유지, 원본 텍스트·stamp·선택 불변, 선택 없으면 문서 전체 사용, 잘못된 선택이면 대체 없이 힌트만 | light 테스트에는 JSONinja tool window가 없어 "tool window 내용 불변"은 직접 관찰하지 못함 |
| 6 양쪽 이름 | 두 host에서 "개발 서버"/"운영 서버", "배포 전"/"배포 후" 라벨 표시, 취소 시 유지, 비우면 기본값, Document·JSON·모드·배열·복원 가능 상태·전체 title 유지 | — |
| 7 host 재사용 | editor tab에서 두 번째 입력 시 같은 탭·세션·오른쪽 기준·옵션·이름 유지 및 복원 스냅샷 폐기, 표시 방식 전환 시 같은 세션 유지 | 헤드리스 dialog는 `isShowing=false`라 window host 재사용 분기는 실행되지 않음 |
| 8 대기 작업 | count 1→2에서 2 유지, 모드 변경 시 이전 모드 결과 거부, 두 host 모두 닫은 뒤 쓰기 없음 | 300ms는 소스로 확인(`DEBOUNCE_DELAY = 300`) |
| 9 같은 세션 | 재사용 테스트에서 정렬, 배열, 이름 변경 후 두 번째 입력이 같은 세션에 들어감 | — |
| 기존 계약 | Boolean `createDiffRequest`가 marker, sort-key, 기본 제목, 사전순 자동 정렬을 유지하고, marker 없는 IDE diff는 포맷하지 않음 | — |

추가 확인: 짝 정렬이 불가능하면 두 문서를 바꾸지 않고 diff 편집기에 오류 힌트만 표시한다.

### 명령 결과

- `./gradlew compileKotlin`: exit 0.
- `./gradlew test -PskipWasmBuild=true` (전체): exit 0. 118건, failures/errors/skipped 모두 0. 기존 스위트 전부와 `JsonDiffServiceTest` 7건, `JsonFormatterServiceTest` 14건, `ShowJsonDiffActionTest` 6건을 포함한다.
- `./gradlew test -PskipWasmBuild=true --tests 'com.livteam.jsoninja.diff.JsonDiffWorkflowE2ETest' --rerun`: 두 번 반복해 두 번 모두 24/24 통과(각 약 39초).
- golden 비교 자체 확인: `object-left-order/expected-right.json`을 틀리게 바꾸면 `FileComparisonFailedError`로 실패함을 확인한 뒤 원복했다.
- mutation 확인(각각 원복, 원본 SHA-256 일치 확인):

| 넣은 결함 | 잡아낸 테스트 |
|---|---|
| 복원 쓰기에서 guard 제거 | `testRestoreWithAutoSortDoesNotResortItsOwnWrite` 실패 |
| 옵션 변경 시 대기 작업 취소 제거 | `testModeChangeRejectsPendingResultOfPreviousMode` 실패 |
| 자동 정렬 토글이 설정을 기록 | 세션 한정 테스트 2건 실패 |
| `LEFT_ORDER`가 왼쪽을 다시 씀 | e2e 1건과 golden 8건 실패 |

### 테스트를 위한 제품 코드 변경

- `SelectJsonDiffSortModeAction.createPopupActionGroup`의 가시성을 `protected`에서 `public`으로 넓혔다. 테스트가 실제 팝업 항목을 실행하기 위해서이며, 동작 변화는 없다.
- `RenameJsonDiffTitlesAction`의 `showAndGet()`을 `show()` + `isOK`로 바꿨다(위 04 참고).

## 미확인 (완료로 표시하지 않음)

- `./gradlew runIde -PskipWasmBuild=true` GUI 샌드박스 관찰은 수행하지 않았다. 확인하지 못한 것은 다음과 같다: 실제 창에서의 툴바 배치, ComboBox 팝업 렌더링, 다이얼로그 모양, 보이는 창에서의 window host 재사용, 실제 tool window 내용 불변.
- Undo 묶음 동작과 대용량 파일 경고 경로는 테스트하지 않았다.
- Plugin Verifier, DevKit 검사, 동적 unload, 263 빌드에서의 실행은 확인하지 않았다.

## 검증 소스 SHA-256

- `model/JsonDiffAlignment.kt`: `77f270ef9b879667b5cde2358f8e4ec1e8efc892a5da7b2fecf8b4aeecf42c5e`
- `services/JsonDiffAlignmentService.kt`: `eedcaa92f1e5c7232c7e80af7f4bf44bf594f845208f845aafe2490e35880be7`
- `diff/JsonDiffSession.kt`: `d59ec3244cb4c977d38698b781f41a196981a0b594dd3b69cf7b0fefd34de4e3`
- `diff/JsonDiffExtension.kt`: `ea418eed80c515de9b07252ecd87ff5f96b19151b62ab64a4b46c3bec22e08e6`
- `services/JsonDiffService.kt`: `a644acb16f348b016b467ae0ee0a0e7403b759de50de73866799423e01b9a493`
- `actions/editor/EditorShowJsonDiffAction.kt`: `b213f163ca6f69c999004318f1b88235372c504b9141842d023e575b87bee7f9`
- `ui/diff/JsonDiffTitlesDialog.kt`: `7dbba91e9baabcad6020519414aa75e798db57c3b1e9926574ec0aba156d2667`
- `actions/RenameJsonDiffTitlesAction.kt`: `9194c11a00843e3ffc990c381430cbd03d31a686d6c520df1587d9b5f9527f44`
- `actions/SelectJsonDiffSortModeAction.kt`: `99949841981c4aba2435a2e5adc87b72c9cda083af93f634daec36e570d80d93`
- `test/.../diff/JsonDiffWorkflowE2ETest.kt`: `4b45415bfca1304fb8a9874bdfc621d67f66a79022bf24d79d3cacf812e87afc`
- `test/.../services/JsonDiffAlignmentGoldenTest.kt`: `1bf72fd08f491b3dc0a92c08ef25b311d3e000d6e6c96fa90e925f1d75581375`
- `test/.../diff/JsonDiffLocalizationTest.kt`: `2f301648d0ce13dc3c3adeabfc4897102d5c7958fb1d8024a3be6e24260b62ca`
