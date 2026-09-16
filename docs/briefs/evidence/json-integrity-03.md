# 03 — 문자열과 복합 포맷 옵션 보존 기록

2026-09-16, R1.1/R2.7/M1a. 상태: **ready**. 헤드리스 IC 2024.3 플랫폼에서 서비스, 실제 에디터 액션, 도구 창 presenter를 관찰했다. 네이티브 키보드/창 조작은 별도 미확인이다.

## Baseline

- 기준 HEAD: `8b64837a6b2787f699e767a3c47e8c90f038c383`. 선행 01의 JsonEditorTextView/Undo 계약은 그대로다. 선행 02의 factory/normalizer/mapper는 변경하지 않고 formatter만 후속 수정했다.
- 실제 fullyUnescapeJson 호출에서 multiline JSON의 `C:\\temp`가 `C:\temp`로, `a\"b`가 `a"b`로 바뀌어 원래 문자열 의미/문법이 손상됐다.
- `{"z":[1,2],"a":0}`에 PRETTIFY_COMPACT + sortOverride=true를 적용하면 키는 정렬되지만 배열이 여러 줄로 출력됐다.
- Apple M4 Pro/aarch64, IC 2024.3 라이브러리/JShell 21.0.2. 1,048,592자(ASCII 약 1MiB) 경로 문자열의 기존 fullyUnescapeJson을 Swing EDT에서 실행한 표본은 34.13375ms였다.

## Implementation

- formatJson은 최초 원문을 보관한 채 필요한 경우에만 외부 인코딩을 푼다. fullyUnescapeJson은 첫 유효 JSON/JSON5에서 중단하며 끝내 유효해지지 않으면 최초 원문을 반환한다. 유효한 문자열 값의 escape는 건드리지 않는다.
- 패널은 원문을 formatter에 전달한다. 에디터 액션도 원문으로 transform을 호출하며 사전 decode/validation을 Default 안으로 옮겼다. formatter 내부 실패도 초기 원문으로 되돌아간다.
- effective sorting을 pretty/compact 상태와 분리했다. UGLIFY의 기존 정렬 무시를 유지하고 나머지는 caller sortOverride를 우선한다. 기존 enum 이름과 pretty-printer cache key는 그대로다.
- 취소 예외는 다시 던진다. 에디터 disposed/선택 영역/문서 stamp와 패널 문서 stamp를 최종 적용 전에 확인한다. 선택 범위 쓰기와 하나의 WriteCommandAction을 유지한다.

## Acceptance

작업 디렉터리: `/Users/buyong/workspace/private/json-helper2`.

- `./gradlew compileKotlin`: exit 0.
- `./gradlew test -PskipWasmBuild=true --tests 'com.livteam.jsoninja.services.JsonFormatterServiceTest' --tests 'com.livteam.jsoninja.ui.component.editor.JsonEditorUndoRedoTest' --info`: exit 0, 14+8개, 실패/오류/스킵 0. 상세 로그 `/tmp/json-integrity-03-gradle.log`.
- 수동 서비스 기준선은 `jshell --class-path "$(cat /tmp/json-integrity-classpath)"`로 관찰했다.
- 실제 액션/패널 관찰은 `python3`에서 `subprocess.run(['jshell', *Path('/tmp/json-integrity-platform-jshell.args').read_text().splitlines()], input=...)`로 실행했다. 기존 Gradle test의 classpath/JVM 설정을 재사용하고 `java.awt.headless=true`를 강제했다. JShell 안에서 기존 BasePlatformTestCase의 setup/teardown을 노출한 임시 컨텍스트를 사용했다. 테스트 파일·테스트 메서드·Gradle 작업을 추가하지 않았다.
- 로그: `/tmp/json-integrity-03-manual.log`, `/tmp/json-integrity-03-panel-07-baseline.log`. 각 프로세스 exit 0. 첫 패널 관찰은 비동기 초기 탭 생성 전에 접근해 실패했으며, 생성 대기 후 같은 경로의 후속 관찰이 통과했다.

| 관찰 | 최종 결과 |
| --- | --- |
| 경로/quote/newline/Unicode 문자열 포맷 | SERVICE_VALUES=true |
| 두 겹 외부 encoding을 포맷 | ENCODED_VALUES=true; 첫 유효 문서와 값 동일 |
| malformed encoding | INVALID_UNCHANGED=true |
| compact+sort | a가 z보다 앞에 있고 z 배열이 한 줄 |
| PRETTIFY_SORTED + override=false | 원래 z,a 순서 유지 |
| UGLIFY + override=true | 정렬 없이 한 줄 원래 순서 |
| prefix/suffix 사이 선택 영역만 액션 실행 | SELECTION_ONLY=true |
| 실제 액션 결과 Undo 1회/Redo 1회 | UNDO_ORIGINAL=true, REDO_FORMATTED=true |
| 액션 제출 직후 같은 EDT에서 새 문서 입력 | STALE_DISCARDED=true, 최신 내용 보존 |
| 실제 JsoninjaPanelView/presenter 포맷 | PANEL_VALUES=true, IDEMPOTENT=true |
| 정확한 소수, 모든 JsonFormatState | EXACT_PRETTIFY/UGLIFY/PRETTIFY_SORTED/PRETTIFY_COMPACT=true |
| 1MiB 실제 액션 제출 | EDT 0.730833ms, LARGE_VALUES=true |

- EDT 수치는 전처리와 제출이라는 서로 다른 구간의 단일 표본이며 속도 개선율이나 SLA로 해석하지 않는다. 전체 decode/validation/transform은 Default, 문서 적용만 EDT임을 소스와 실제 액션 경로로 확인했다.
- 01의 기존 8개 Undo/Redo 사례를 유지했고 02의 숫자 정밀도를 변경 후 재확인했다. placeholder/blank/invalid/cache 관련 기존 14개 formatter 사례도 통과했다.
- DevKit/동적 unload/전체 지원 IDE 버전 확인 및 네이티브 키보드·메뉴 관찰은 미실행이다.

### 검증 소스 SHA-256

- `src/main/kotlin/com/livteam/jsoninja/services/JsonFormatterService.kt`: `5e2add27571fbee6a788d8125e82819ce9881e83f1d56c8c61e77ea2ae58579d`
- `src/main/kotlin/com/livteam/jsoninja/actions/editor/BaseEditorJsonAction.kt`: `f4edfdb935458e1c3b859cf6718a3df2a73d99e9e0d77d16bd9f314a1db6669d`
- `src/main/kotlin/com/livteam/jsoninja/ui/component/main/JsoninjaPanelPresenter.kt`: `0ee4e489b12ef433b84c42b89010a3138d1ddff9ac220f36f930a37b84e7469e`
