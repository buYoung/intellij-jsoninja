# Coroutine CPU Hotspot Candidates

## 목적
- 이 문서는 현재 코드베이스에서 아직 `coroutine`으로 분리되지 않았지만 CPU 사용량이 커질 가능성이 있는 동기 실행 지점을 추정한 메모입니다.
- 확정 결론 문서가 아니라 후보 수집 문서입니다. 실제 우선순위는 사용자가 프로파일링, 샘플링, 체감 지연, IDE 로그로 확인하면 됩니다.
- 여기서는 반드시 `coroutine`만 정답이라고 단정하지 않습니다. 다만 현재 구조상 `EDT` 또는 동기 액션 경로에서 무거운 연산이 돌고 있어, `coroutine`이나 다른 백그라운드 메커니즘 검토 가치가 있는 곳을 모았습니다.

## 읽는 법
- `의심도`: 체감 지연 가능성을 기준으로 `높음`, `중간`으로 적었습니다.
- `현재 실행 문맥`: 지금 코드가 어디서 동기 실행되는지 적었습니다.
- `호출 매핑`: 실제 사용 경로를 `entry -> ... -> hotspot` 형태로 적었습니다.
- `무거운 이유`: 전체 JSON 파싱, 재직렬화, 정렬, fuzzy matching, 반복 계산 같은 CPU 비용 근거입니다.

## 제외 기준
- 이미 `Dispatchers.Default` 또는 `Dispatchers.IO`에서 계산되는 경로는 제외했습니다.
- 예를 들어 `JsonQueryPresenter.performSearch`의 query 실행, `GenerateRandomJsonAction`의 생성 본체, `ConvertPreviewExecutor` 기반 type conversion preview는 이미 background 계산으로 분리되어 있습니다.

## 공통 배경
- `JsonFormatterService.formatJson(...)`는 단순 문자열 정리가 아니라 다음을 수행합니다.
  - placeholder 추출/복원
  - 전체 JSON 유효성 검사
  - `readTree(...)` 파싱
  - pretty print 또는 key sorting 기반 재직렬화
- `JsonFormatterService.isValidJson(...)`도 내부에서 전체 입력을 다시 파싱합니다.
  - 단, 이때는 `readTree`로 트리를 만드는 것이 아니라 스트리밍 파서(`JsonParser`)로 토큰을 끝까지 훑어 검증합니다. "다시 파싱"이라는 표현은 맞지만, 트리 생성보다는 가벼운 토큰 스캔에 가깝습니다.
- 따라서 아래 후보 중 `formatJson` 또는 `isValidJson`을 직접 호출하는 경로는 큰 입력에서 체감 정지로 이어질 가능성이 있습니다.

## 요약 표

| ID | 후보 | 의심도 | 현재 실행 문맥 | 핵심 hotspot |
| --- | --- | --- | --- | --- |
| C1 | 툴윈도우 prettify/uglify | 높음 | 액션 후 동기 실행 | `JsonFormatterService.formatJson` |
| C2 | 에디터 컨텍스트 메뉴 prettify/uglify | 높음 | 액션 후 동기 실행 | `isValidJson` + `formatJson` |
| C3 | 쿼리 결과 반영 후 재포맷 | 높음 | query 결과를 `EDT`로 가져온 뒤 동기 실행 | `formatJson` |
| C4 | 랜덤/스키마 생성 결과 삽입 후 재포맷 | 높음 | background 생성 후 `EDT`에서 동기 실행 | `formatJson` |
| C5 | diff 최초 열기 시 포맷/검증 | 높음 | diff open 경로에서 동기 실행 | `isValidJson` + `formatJson` |
| C6 | diff viewer 생성 시 JSON 판별 | 높음 | diff viewer 생성 시 동기 실행 | `isValidJson` |
| C7 | SchemaStore URL 검색 fuzzy filtering | 높음 | 입력 변경 콜백에서 동기 실행 | 정렬 + Levenshtein 반복 계산 |
| C8 | diff의 Sort once 액션 | 중간 | diff action 클릭 후 동기 실행 | `formatJson` 2회 |
| C9 | paste 시 소형 입력 동기 포맷 | 중간 | paste hook 안에서 동기 실행 | `isValidJson` + `formatJson` |
| C10 | query 시작 전 원본 JSON 검증 | 중간 | 검색 시작 직전 동기 실행 | `isValidJson` |
| C11 | schema text validate | 중간 | dialog validation 시 동기 실행 | strict JSON parse |
| C12 | tree 모드 전환 시 파싱/트리 구축 | 높음 | 토글 클릭 핸들러에서 동기 실행 | `readTree` + 전체 트리 재귀 구축 |
| C13 | escape/unescape 액션 | 중간 | 액션 후 동기 실행 | `escapeJson` / `unescapeJson` 문자열 처리 |

## 상세 후보

### C1. 툴윈도우 prettify/uglify
- 현재 실행 문맥
  - UI 액션 직후 동기 처리
- 호출 매핑
  - `plugin.xml`
  - `com.livteam.jsoninja.actions.PrettifyJsonAction`
  - `PrettifyJsonAction.actionPerformed`
  - `JsoninjaPanelPresenter.formatJson`
  - `JsoninjaPanelPresenter.processCurrentEditorText`
  - `JsonFormatterService.formatJson`
  - `com.livteam.jsoninja.actions.UglifyJsonAction`
  - `UglifyJsonAction.actionPerformed`
  - `JsoninjaPanelPresenter.formatJson`
  - `JsoninjaPanelPresenter.processCurrentEditorText`
  - `JsonFormatterService.formatJson`
- 무거운 이유
  - 전체 JSON 파싱과 재직렬화를 액션 스레드에서 바로 수행합니다.
  - 입력이 크면 `currentEditor.setText(...)` 이전 단계에서 UI가 멈출 가능성이 있습니다.
- 주요 파일
  - `src/main/kotlin/com/livteam/jsoninja/actions/PrettifyJsonAction.kt`
  - `src/main/kotlin/com/livteam/jsoninja/actions/UglifyJsonAction.kt`
  - `src/main/kotlin/com/livteam/jsoninja/ui/component/main/JsoninjaPanelPresenter.kt`
  - `src/main/kotlin/com/livteam/jsoninja/services/JsonFormatterService.kt`

### C2. 에디터 컨텍스트 메뉴 prettify/uglify
- 현재 실행 문맥
  - 에디터 액션 경로에서 동기 처리
- 호출 매핑
  - `plugin.xml`
  - `com.livteam.jsoninja.actions.editor.EditorPrettifyJsonAction`
  - `BaseEditorJsonAction.actionPerformed`
  - `JsonFormatterService.isValidJson`
  - `EditorPrettifyJsonAction.transformJson`
  - `JsonFormatterService.formatJson`
  - `plugin.xml`
  - `com.livteam.jsoninja.actions.editor.EditorUglifyJsonAction`
  - `BaseEditorJsonAction.actionPerformed`
  - `JsonFormatterService.isValidJson`
  - `EditorUglifyJsonAction.transformJson`
  - `JsonFormatterService.formatJson`
- 무거운 이유
  - 검증 파싱과 실제 포맷 파싱이 둘 다 동기입니다.
  - 실제로는 진입 시 `isValidJson` 1회 + `formatJson` 내부 검증(`isValidJsonText`) 1회 + `readTree` 1회로, 입력을 사실상 2~3회 중복 파싱합니다.
  - 선택 영역이 크거나 전체 문서 대상이면 `C1`보다 더 무거울 수 있습니다.
- 주요 파일
  - `src/main/kotlin/com/livteam/jsoninja/actions/editor/BaseEditorJsonAction.kt`
  - `src/main/kotlin/com/livteam/jsoninja/actions/editor/EditorPrettifyJsonAction.kt`
  - `src/main/kotlin/com/livteam/jsoninja/actions/editor/EditorUglifyJsonAction.kt`
  - `src/main/kotlin/com/livteam/jsoninja/services/JsonFormatterService.kt`

### C3. 쿼리 결과 반영 후 재포맷
- 현재 실행 문맥
  - query 실행 자체는 background지만, 결과를 `EDT`로 가져온 뒤 콜백에서 다시 동기 포맷팅
- 호출 매핑
  - `JsonQueryPresenter.performSearch`
  - `withContext(Dispatchers.Default) { JsonQueryService.query(...) }`
  - `withContext(Dispatchers.EDT)`
  - `onSearchCallback`
  - `JsonTabContextFactory.setupJmesPathPresenter`
  - `JsonFormatterService.formatJson`
  - `JsonEditorView.setText`
- 무거운 이유
  - query 결과가 커질수록 결과 JSON을 다시 파싱하고 재직렬화합니다.
  - background 계산을 끝낸 뒤 마지막 적용 단계에서 다시 CPU 비용을 `EDT`에 올립니다.
- 주요 파일
  - `src/main/kotlin/com/livteam/jsoninja/ui/component/jsonQuery/JsonQueryPresenter.kt`
  - `src/main/kotlin/com/livteam/jsoninja/ui/component/tab/JsonTabContextFactory.kt`
  - `src/main/kotlin/com/livteam/jsoninja/services/JsonFormatterService.kt`

### C4. 랜덤/스키마 생성 결과 삽입 후 재포맷
- 현재 실행 문맥
  - 생성 자체는 background지만, 결과 삽입 직전에 `EDT`에서 동기 포맷팅
- 호출 매핑
  - `GenerateRandomJsonAction.actionPerformed`
  - `project.service<JsoninjaCoroutineScopeService>().launch`
  - `withContext(Dispatchers.Default) { ... generate ... }`
  - `withContext(Dispatchers.EDT)`
  - `JsoninjaPanelPresenter.setRandomJsonData`
  - `JsonFormatterService.formatJson`
  - `JsonEditorView.setText`
- 무거운 이유
  - 생성 결과가 크면 마지막 표시 단계에서 다시 전체 파싱/직렬화를 수행합니다.
  - background에서 이미 무거운 연산을 끝냈는데, 후처리가 다시 `EDT`에 붙습니다.
  - 다만 재포맷이 항상 일어나지는 않습니다. `setRandomJsonData(skipFormatting = true)`이면 `formatJson`을 건너뛰며, 이 조건은 JSON5 출력이거나 SCHEMA 모드의 `REQUIRED_AND_OPTIONAL_COMMENTED`일 때입니다.
- 주요 파일
  - `src/main/kotlin/com/livteam/jsoninja/actions/GenerateRandomJsonAction.kt`
  - `src/main/kotlin/com/livteam/jsoninja/ui/component/main/JsoninjaPanelPresenter.kt`
  - `src/main/kotlin/com/livteam/jsoninja/services/JsonFormatterService.kt`

### C5. diff 최초 열기 시 포맷/검증
- 현재 실행 문맥
  - diff 열기 요청 시 동기 처리
- 호출 매핑
  - `plugin.xml`
  - `ShowJsonDiffAction.actionPerformed`
  - `ShowJsonDiffAction.openDiffForCurrentJson`
  - `JsonDiffService.openDiff`
  - `JsonDiffService.getOrCreateContext`
  - `JsonDiffService.prepareDiffText`
  - `JsonDiffService.validateAndFormat`
  - `JsonFormatterService.isValidJson`
  - `JsonFormatterService.formatJson`
  - `createDiffDocument(...)`
  - 추가 진입 경로
  - `ShowJsonDiffInWindowAction.actionPerformed`
  - `ShowJsonDiffInEditorTabAction.actionPerformed`
  - `OnboardingStep8DiffTooltipController.maybeOpenDiff`
- 무거운 이유
  - diff를 열 때 현재 JSON을 바로 검증하고 포맷팅합니다.
  - semantic 정렬 옵션이면 key sorting까지 붙어 비용이 더 커집니다.
  - 단, `OnboardingStep8DiffTooltipController.maybeOpenDiff` 진입점만은 `coroutineScope.launch { withContext(Dispatchers.EDT) { ... } }` 래퍼를 거칩니다. diff를 여는 호출 자체는 여전히 `EDT` 동기이고, 트리거만 coroutine을 통합니다. 나머지 두 액션은 직접 동기 호출입니다.
- 주요 파일
  - `src/main/kotlin/com/livteam/jsoninja/actions/ShowJsonDiffAction.kt`
  - `src/main/kotlin/com/livteam/jsoninja/actions/ShowJsonDiffInWindowAction.kt`
  - `src/main/kotlin/com/livteam/jsoninja/actions/ShowJsonDiffInEditorTabAction.kt`
  - `src/main/kotlin/com/livteam/jsoninja/ui/onboarding/OnboardingStep8DiffTooltipController.kt`
  - `src/main/kotlin/com/livteam/jsoninja/services/JsonDiffService.kt`

### C6. diff viewer 생성 시 JSON 판별
- 현재 실행 문맥
  - `DiffExtension`에서 viewer 생성 시 동기 판단
- 호출 매핑
  - `plugin.xml`
  - `diff.DiffExtension implementation="com.livteam.jsoninja.diff.JsonDiffExtension"`
  - `JsonDiffExtension.onViewerCreated`
  - `editors.map { isJsonContent(...) }`
  - `JsonDiffExtension.isJsonContent`
  - `JsonFormatterService.isValidJson`
- 무거운 이유
  - 양쪽 에디터 텍스트를 대상으로 전체 JSON 검증을 동기 수행합니다.
  - 다만 `isValidJson`은 항상 호출되지 않습니다. `isJsonContent`는 파일 타입/blank/시작 문자 휴리스틱을 먼저 보고, 파일 타입이 JSON/JSON5로 판별되면 `isValidJson` 없이 조기 반환합니다. 휴리스틱 마지막 단계에서만 `isValidJson`이 동기 실행됩니다.
  - viewer 생성 시점이라 초기 표시 지연으로 바로 체감될 수 있습니다.
- 주요 파일
  - `src/main/resources/META-INF/plugin.xml`
  - `src/main/kotlin/com/livteam/jsoninja/diff/JsonDiffExtension.kt`
  - `src/main/kotlin/com/livteam/jsoninja/services/JsonFormatterService.kt`

### C7. SchemaStore URL 검색 fuzzy filtering
- 현재 실행 문맥
  - 입력 변경 콜백에서 즉시 동기 실행
- 호출 매핑
  - `GenerateSchemaJsonTabPresenter.init`
  - `view.setOnSchemaUrlInputChanged { filterSchemaStoreCatalogItemsByInput() }`
  - `GenerateSchemaJsonTabPresenter.filterSchemaStoreCatalogItemsByInput`
  - `schemaStoreCatalogItems.mapNotNull { calculateSearchScore(...) }`
  - `calculateBestTokenSimilarityScore`
  - `calculateLevenshteinDistance`
  - `sortedWith(...)`
  - `view.updateSchemaUrlSuggestions(...)`
- 무거운 이유
  - 전체 카탈로그 순회
  - 각 항목마다 토큰 분해와 Levenshtein 거리 계산
  - 최종 정렬까지 매 타이핑마다 수행
- 주요 파일
  - `src/main/kotlin/com/livteam/jsoninja/ui/dialog/generateJson/schema/GenerateSchemaJsonTabPresenter.kt`

### C8. diff의 Sort once 액션
- 현재 실행 문맥
  - diff 액션 클릭 후 동기 처리
- 호출 매핑
  - `JsonDiffService.createDiffRequest`
  - `DiffUserDataKeys.CONTEXT_ACTIONS`
  - `SortJsonDiffKeysOnceAction`
  - `SortJsonDiffKeysOnceAction.actionPerformed`
  - `JsonFormatterService.formatJson(leftDocument.text, ...)`
  - `JsonFormatterService.formatJson(rightDocument.text, ...)`
- 무거운 이유
  - 양쪽 문서를 연속으로 포맷합니다.
  - 큰 diff 문서에서는 한 번의 클릭으로 두 번의 전체 파싱/정렬/직렬화가 발생합니다.
- 주요 파일
  - `src/main/kotlin/com/livteam/jsoninja/services/JsonDiffService.kt`
  - `src/main/kotlin/com/livteam/jsoninja/actions/SortJsonDiffKeysOnceAction.kt`
  - `src/main/kotlin/com/livteam/jsoninja/services/JsonFormatterService.kt`

### C9. paste 시 소형 입력 동기 포맷
- 현재 실행 문맥
  - paste preprocessor 내부에서 동기 실행
- 호출 매핑
  - `plugin.xml`
  - `copyPastePreProcessor implementation="com.livteam.jsoninja.extensions.JsoninjaPastePreProcessor"`
  - `JsoninjaPastePreProcessor.preprocessOnPaste`
  - `if (text.length < SYNC_PROCESSING_THRESHOLD) return formatText()`
  - `JsonFormatterService.isValidJson`
  - `JsonFormatterService.formatJson`
- 무거운 이유
  - `SYNC_PROCESSING_THRESHOLD`가 `100_000`이라 그 아래 크기에서는 전부 동기 처리입니다.
  - paste hook 특성상 UI 응답에 직접 영향을 줄 수 있습니다.
- 주의
  - 대형 입력은 이미 `ProgressManager.runProcessWithProgressSynchronously(...)`로 우회하고 있으므로, 전면적인 재설계 후보라기보다 임계값과 체감 지연 확인 후보에 가깝습니다.
- 주요 파일
  - `src/main/resources/META-INF/plugin.xml`
  - `src/main/kotlin/com/livteam/jsoninja/extensions/JsoninjaPastePreProcessor.kt`

### C10. query 시작 전 원본 JSON 검증
- 현재 실행 문맥
  - 검색 진입 전 동기 검증
- 호출 매핑
  - `JsonQueryPresenter.performSearch`
  - `isValidJson(model.originalJson)`
  - `JsonFormatterService.isValidJson`
  - 추가 진입
  - `JsonQueryPresenter.setOriginalJson`
  - `isValidJson(json)`
  - `JsonFormatterService.isValidJson`
- 무거운 이유
  - 실제 query 본체는 background지만, 진입 조건 검증은 여전히 sync입니다.
  - `performSearch`의 `isValidJson(model.originalJson)`은 `coroutineScope.launch` 진입 전에 동기 실행됩니다.
  - 추가 진입인 `setOriginalJson`의 `isValidJson(json)`은 "현재 쿼리가 비어 있을 때"의 else 분기에서만 실행됩니다. 쿼리가 비어 있지 않으면 `performSearch` 경로의 검증이 적용됩니다.
  - 큰 원본 JSON이면 검색 버튼/엔터 입력 직후 짧은 정지로 느껴질 수 있습니다.
- 주요 파일
  - `src/main/kotlin/com/livteam/jsoninja/ui/component/jsonQuery/JsonQueryPresenter.kt`
  - `src/main/kotlin/com/livteam/jsoninja/services/JsonFormatterService.kt`

### C11. schema text validate
- 현재 실행 문맥
  - dialog validation 경로에서 동기 처리
- 호출 매핑
  - `GenerateSchemaJsonTabPresenter.validate`
  - `JsonSchemaDataGenerationService.validateSchemaText`
  - `JsonSchemaValidationService.parseStrictSchema`
- 무거운 이유
  - strict JSON parse를 수행합니다.
  - 주석상 가벼운 검증으로 의도돼 있어 우선순위는 낮지만, 매우 큰 schema text에서는 확인 가치가 있습니다.
- 주요 파일
  - `src/main/kotlin/com/livteam/jsoninja/ui/dialog/generateJson/schema/GenerateSchemaJsonTabPresenter.kt`
  - `src/main/kotlin/com/livteam/jsoninja/services/schema/JsonSchemaDataGenerationService.kt`
  - `src/main/kotlin/com/livteam/jsoninja/services/schema/JsonSchemaValidationService.kt`

### C12. tree 모드 전환 시 파싱/트리 구축
- 현재 실행 문맥
  - 에디터의 text/tree 토글 라벨 클릭 핸들러에서 동기 처리
- 호출 매핑
  - `JsonEditorView` 토글 라벨 `MouseAdapter.mouseClicked`
  - `JsonEditorView.switchToTreeMode`
  - `JsonEditorTreePresenter.refreshTreeFromJson`
  - `TemplatePlaceholderSupport.extractAndReplaceValuePlaceholders`
  - `objectMapper.readTree(...)`
  - `JsonEditorTreePresenter.appendJsonNode` (전체 노드 재귀 순회)
  - `JsonEditorView.setTreeModel(...)`
- 무거운 이유
  - placeholder 치환 + 전체 JSON `readTree` 파싱 + Swing `DefaultMutableTreeNode` 트리를 재귀로 전부 구축합니다.
  - coroutine/background 없이 토글 클릭 즉시 `EDT`에서 동기 실행되므로, 대용량 JSON에서 tree 탭 전환 시 UI 정지로 이어질 수 있습니다.
  - 파싱과 트리 노드 전체 생성이 겹쳐 `C1`(formatJson)과 동급 이상으로 무거울 수 있습니다.
- 주요 파일
  - `src/main/kotlin/com/livteam/jsoninja/ui/component/editor/JsonEditorView.kt`
  - `src/main/kotlin/com/livteam/jsoninja/ui/component/editor/JsonEditorTreePresenter.kt`

### C13. escape/unescape 액션
- 현재 실행 문맥
  - UI 액션 직후 동기 처리 (`C1`과 동일한 `processCurrentEditorText` 경로)
- 호출 매핑
  - `plugin.xml`
  - `com.livteam.jsoninja.actions.EscapeJsonAction` / `UnescapeJsonAction`
  - `JsoninjaPanelPresenter.escapeJson` / `unescapeJson`
  - `JsoninjaPanelPresenter.processCurrentEditorText`
  - `JsonFormatterService.escapeJson` / `unescapeJson`
  - 에디터 컨텍스트 변형: `EditorEscapeJsonAction` / `EditorUnescapeJsonAction` → `BaseEditorJsonAction.actionPerformed`
- 무거운 이유
  - 전체 입력에 대한 O(n) 단일 패스 문자열 처리 + Jackson `writeValueAsString`/`readValue`를 `EDT`에서 동기 수행합니다.
  - 파싱/정렬/재직렬화보다는 가볍지만, `C1`과 동일한 동기 경로라 큰 입력에서는 짧은 정지 가능성이 있습니다. 의심도는 `C1`보다 낮습니다.
- 주요 파일
  - `src/main/kotlin/com/livteam/jsoninja/actions/EscapeJsonAction.kt`
  - `src/main/kotlin/com/livteam/jsoninja/actions/UnescapeJsonAction.kt`
  - `src/main/kotlin/com/livteam/jsoninja/actions/editor/EditorEscapeJsonAction.kt`
  - `src/main/kotlin/com/livteam/jsoninja/actions/editor/EditorUnescapeJsonAction.kt`
  - `src/main/kotlin/com/livteam/jsoninja/ui/component/main/JsoninjaPanelPresenter.kt`
  - `src/main/kotlin/com/livteam/jsoninja/services/JsonFormatterService.kt`

## 먼저 확인할 만한 순서
1. `C5` diff 최초 열기
2. `C6` diff viewer 생성 시 JSON 판별
3. `C12` tree 모드 전환 시 파싱/트리 구축
4. `C1`, `C2` prettify/uglify 액션
5. `C3`, `C4` background 작업 후 `EDT` 재포맷
6. `C7` SchemaStore fuzzy filtering
7. `C8`, `C9`, `C10`, `C11`, `C13`

## 메모
- `type conversion preview`, `schema generation`, `query 실행 본체`, `random JSON generation 본체`는 이미 background 계산으로 빠져 있으므로 이번 문서의 “미사용 coroutine 후보”에서는 제외했습니다.
- 다만 background 계산 뒤 최종 결과를 `EDT`에서 다시 포맷하는 `C3`, `C4`는 별도 hotspot으로 봤습니다.
