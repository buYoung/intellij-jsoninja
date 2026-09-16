# 05 — 쿼리 원문과 성공한 null 보존 기록

2026-09-16, R2.1/R2.2/R2.8. 상태: **ready**.

## Baseline

- 변경 전 HEAD는 `bbc058b`; 12의 별도 변경과 병행했으며 쿼리 기준은 04에서 넘긴 원문/결과 쓰기 경계다.
- `python3 /tmp/json-integrity-query-baseline.py`, IC 2024.3 헤드리스 실제 JsonTabContextFactory/JsonQueryPresenter/SearchTextField Enter 경로에서 재현했다. 저장소 테스트는 추가하지 않았다.
- 새 탭에 `{"x":1}`을 입력하고 빈 Enter: 결과는 빈 문서였다.
- x=1 조회 → 비우기 → x=2 교체 → 다시 조회: 결과는 1이었다.
- `{"x":null}`에서 JsonPath `$.x`, JMESPath `x`는 문서를 그대로 남겼고 jq `.x`만 null을 표시했다.

## Implementation

- query가 쓰는 결과·복원은 `isApplyingQueryResult`로 구분한다. 나머지 DocumentEvent는 내용 전체를 새 원문으로 저장하고 이전 요청/포맷을 무효화한다. 사용자 편집·외부 교체·Undo/Redo는 모두 이 규칙을 따른다.
- 원문 저장은 자동 재검색이나 문서 쓰기를 하지 않는다. 내용 편집 중에는 그대로 남기고 다음 Enter부터 그 원문을 사용한다. 편집 없는 연속 검색은 결과 문자열을 원문으로 승격하지 않는다.
- 빈 Enter는 검색 이력이 있을 때만 현재 원문을 복원한다. 새 탭이나 직접 편집 직후에는 문서를 쓰지 않아 서식까지 정확히 보존한다.
- JsonPath의 예외 억제를 제거해 없는 경로의 실패와 실제 null 값을 구분한다. null 값을 직렬화해 `null`로 전달한다. JMESPath의 NullNode도 같은 경로로 처리한다. jq의 빈 스트림은 기존 nullable 실패/결과 없음 경로를 유지한다.
- JSON/표현식 검증과 평가를 Default에 모았다. 04의 요청 번호, 문서 identity/stamp, 탭 수명, 자체 쓰기 finally 가드, scope/listener 해제를 보존했다. 원문 동기화가 즉시 요청을 무효화하므로 오래된 복원도 현재 편집을 덮어쓰지 못한다.

## Acceptance

작업 디렉터리: `/Users/buyong/workspace/private/json-helper2`.

- `./gradlew compileKotlin`: exit 0.
- `./gradlew test -PskipWasmBuild=true --tests 'com.livteam.jsoninja.services.schema.JsonSchemaDataGenerationServiceTest' --tests 'com.livteam.jsoninja.services.JMESPathServiceTest' --tests 'com.livteam.jsoninja.services.JacksonJqServiceTest' --tests 'com.livteam.jsoninja.ui.component.editor.JsonEditorUndoRedoTest' --rerun-tasks --no-build-cache --no-configuration-cache`: exit 0. 관련 JMESPath 2 + jq 8 + Undo/Redo 8 = 18개, 함께 검증한 스키마 3개 포함 21개, 실패/오류/스킵 0. 12에서 확인한 오래된 테스트 클래스 캐시 복원을 피하려고 재컴파일했다.
- `python3 /tmp/json-integrity-query-check.py`: exit 0, 아래 관찰 모두 통과, fixture 종료 오류 없음. 실제 화면 presenter/factory와 문서/UndoManager를 사용했다. JShell 21.0.2, IC 2024.3, headless=true, Kotlin 목표 JVM 17.

| 실제 경로 | 결과 |
| --- | --- |
| 새 탭 `{"x":1}` → 빈 Enter | 문자열 정확히 보존 |
| `{"x":1,"y":8}` → $.x → $.y | 1 → 8, 같은 원문 사용 |
| 비우기 → 원문에서 1을 2로 직접 편집 | 편집 중 자동 검색 없음, 다음 $.x는 2 |
| 표시된 결과를 `{"x":4}`로 교체 → $.x | 새 원문에서 4 |
| x=1 → 별도 명령으로 2 → 3 → Undo → Redo → $.x | 2 → 3, 최종 조회 3 |
| 세 엔진의 null 경로 | 모두 literal `null` |
| 세 엔진의 잘못된 `[` 표현식 | 이전 표시 유지 |
| jq `empty` | 기존 객체 그대로 유지 |
| $.x 바로 뒤 $.y 요청 | 마지막 결과 2만 적용 |
| 복원 또는 검색 후 EDT 적용을 보류한 사이 원문 교체 | 각각 x=99, x=100 유지 |
| 검색 직후 탭 폐기 | 결과 적용 없음 |
| 없는 JsonPath $.missing | 문자열 null이 아닌 결과 없음, 기존 오류 경로 |

- 마지막 두 경합은 수동 명령에서 EDT를 150ms 점유한 동안 다른 dispatcher를 실행시킨 뒤, EDT 반환 전에 문서를 편집했다. 04의 계산 완료 후 재개를 직접 제어한 증거도 유지된다. 실제 키보드·네이티브 창 관찰은 수행하지 않았다.
- 원문 변경 listener는 tabDisposable에 한 번 등록된다. query scope, 후처리 scope, 메시지 버스, callback의 소유자는 그대로다. fixture에서 전체 탭 폐기 후 editor 누수 없이 종료했다.
- 12 커밋 이후 검사 HEAD: `75722fc80c1ab773ba5029cbf6893d1eef110aa3`. 아래 파일들이 이 결과를 만든 쿼리 변경이다. diff/generation 경계에는 추가 변경이 없다.

### 검증 소스 SHA-256

- `src/main/kotlin/com/livteam/jsoninja/services/JsonQueryService.kt`: `30acfe256d5189ef46223d803cbeb9863fd0a96f3f9180de9813812e7ae51d61`
- `src/main/kotlin/com/livteam/jsoninja/ui/component/jsonQuery/JsonQueryPresenter.kt`: `fac7ee3ebea3dcf9b0c04d28dc4532eab8c93d5ab697dc9ffd7ad0e92fe2049c`
- `src/main/kotlin/com/livteam/jsoninja/ui/component/tab/JsonTabContextFactory.kt`: `ecf050640686db02b65954cb9116c11d5467b3e5d96d26458dd50770afd40b12`
