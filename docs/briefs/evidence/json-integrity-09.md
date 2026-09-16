# 09 — 현재 입력에 속한 미리보기만 소비하기

2026-09-16, R1.6/M1b. 상태: **ready**. 헤드리스 플랫폼에서 두 방향의 상태 전환과 최종 삽입 경계를 관찰했다.

## Baseline

- 기준 HEAD: `e887e716423ad8487288b842cd1c781d7e36836f`. 선행 08의 runtime/bridge/analyzer/generation/executor 트랜잭션 계약은 유지했다.
- 실제 presenter 관찰: JSON_OLD_DURING_PENDING=true, TYPE_OLD_DURING_PENDING=true, TYPE_RESURRECTED_AFTER_BLANK=true. 유효 결과 이후 입력 변경 중에도 기존 결과가 남고, 타입 입력을 비운 뒤 과거 결과가 다시 표시됐다.
- Apple M4 Pro, IC 2024.3, JShell 21.0.2. 1,048,587자 JSON 편집 이벤트 EDT 표본은 유효 입력 52.555167ms, 잘못된 입력 18.605292ms였다. 로그 `/tmp/json-integrity-09-baseline.log`.

## Implementation

- Pending/Ready/Invalid 상태를 명시했다. Ready는 source/config/text를 함께 보관한다. 양 방향 presenter는 변경·blank·invalid 때 먼저 이전 작업을 취소하고 readiness를 무효화한다.
- 소비 시 현재 원문과 collectConfig 결과가 Ready의 snapshot과 같은지 다시 확인한다. 방향은 각각의 presenter에 격리되고 활성 탭에서만 소비한다.
- `consumeCurrentPreview`는 현재 텍스트와 확장자를 삽입 callback에 함께 전달한다. ConvertTypeDialog.doOKAction도 이 경계를 통과해야 하며 거절되면 닫거나 삽입하지 않는다. 기존 copy 진입점도 동일 getter로 확인한다.
- 준비 상태가 없으면 내부 copy 버튼과 대화상자 Copy/Insert action을 비활성화한다. 탭 전환과 child 상태 변경을 부모에게 알린다.
- 기존 validate API는 유지하고 필드 검증/JSON 검증을 분리했다. presenter의 EDT validation은 blank/root 이름/이미 계산한 오류만 다룬다. JSON parsing은 300ms debounce 후 Default에서 실행하며 기존 localized 오류를 유지한다. 타입→JSON은 500ms를 유지한다.
- 08에서 전달한 checkCancellation을 보존했다. pending invalidation은 executor.cancel로 requestSequence와 Job을 함께 무효화한다. 반환이 늦은 동기 계산도 최신 sequence와 input/config 검사 뒤에만 적용된다.

## Acceptance

작업 디렉터리: `/Users/buyong/workspace/private/json-helper2`.

- `./gradlew compileKotlin`: 최종 exit 0.
- `./gradlew test -PskipWasmBuild=true --tests 'com.livteam.jsoninja.services.typeConversion.TypeConversionWasmIntegration*'`: 최종 exit 0, 3개 suite의 11개 테스트, 실패/오류/스킵 0. 최종 소비 경계 추가 뒤 재실행했다.
- 수동 명령: 03과 같은 `python3` → 기존 Gradle test JVM 인자/classpath를 재사용한 JShell. `java.awt.headless=true`; 임시 BasePlatformTestCase 컨텍스트로 실제 presenter/component/Document를 사용했다. 신규 테스트 파일·메서드·Gradle 작업은 없다.
- 로그: `/tmp/json-integrity-09-after.log`, `/tmp/json-integrity-09-consumption.log`, exit 0. 헤드리스 DialogWrapper peer는 실제 위젯 트리를 제공하지 않아 초기 widget 탐색은 미확인이었고, 이후 실제 doOKAction이 호출하는 공개 consumeCurrentPreview 경계와 실제 editor 삽입 consumer를 관찰했다.

| 상황 | 실제 관찰 |
| --- | --- |
| 양 방향 유효 미리보기 | JSON_READY=true, TYPE_READY=true |
| 입력 변경 직후 | JSON/TYPE_PENDING_EMPTY=true, COPY_DISABLED=true; copyPreview 호출도 과거 결과를 소비하지 않음 |
| 타입 입력 blank | TYPE_BLANK_STAYS_EMPTY=true |
| JSON 새 결과/언어 변경 | JSON_NEW_READY=true, LANGUAGE_PENDING_EMPTY=true, LANGUAGE_READY=true |
| 잘못된 입력 즉시/계산 후 | INVALID_IMMEDIATE_EMPTY=true, INVALID_ERROR=true, EMPTY=true |
| 1MiB 유효/잘못된 입력 | LARGE_VALID_READY=true, LARGE_INVALID_EMPTY=true |
| dispose 후 새 presenter | REOPEN_PENDING_EMPTY=true, REOPEN_CURRENT_READY=true |
| 다른 방향 탭/돌아오기 | OTHER_DIRECTION_DISABLED=true, RETURN_DIRECTION_READY=true |
| callback 없이 원문만 바뀐 상태에서 최종 소비 | FINAL_IDENTITY_GUARD=true; 대상 sentinel 불변 |
| root 이름 옵션 변경/대기 중 삽입 | OPTION_CHANGE_DISABLED=true, PENDING_INSERT_BLOCKED=true |
| 새 Ready를 실제 editor에 삽입 | CURRENT_INSERT_READY=true, 확장자 kt, INSERT_CURRENT_OUTPUT=true (Fresh와 현재 b 입력) |

- 스택 표본의 JSON validator 실행 스레드는 `DefaultDispatcher-worker-13 ...#918`, `DefaultDispatcher-worker-6 ...#920`이었다. EDT에서 validator parsing을 관찰하지 않았다.
- 변경 후 같은 크기 편집 이벤트 EDT 표본: 유효 29.848041ms, 잘못된 입력 16.569042ms. 문서 변경/기존 listener 처리까지 포함하는 단일 표본이며 성능 SLA나 개선율로 해석하지 않는다.
- 최초 dialog seed 분류는 기존 resolver 경로를 유지한다. 이번 이동 대상은 입력 변경마다 실행되던 JSON validation이다.
- 네이티브 창/키보드/시스템 clipboard 조작 및 전체 지원 버전 UI는 미실행이다. 최종 getter/소비 callback, 실제 editor 쓰기와 내부 copy 상태가 검증 범위다.

### 검증 소스 SHA-256

- `src/main/kotlin/com/livteam/jsoninja/ui/dialog/convertType/ConvertPreviewState.kt`: `f980e98ec6e3b9b6978655447c8561a9f73943c3e0dcb60ccae3b94d64a91d7c`
- `src/main/kotlin/com/livteam/jsoninja/ui/dialog/convertType/JsonToTypeDialogPresenter.kt`: `0e075e2e5a7276fbb900f2798bb36bbe24623aa4e9eca40341d8823117191f02`
- `src/main/kotlin/com/livteam/jsoninja/ui/dialog/convertType/TypeToJsonDialogPresenter.kt`: `e2dabef865e2ff018836b14429a885c984a6a2d263afd4e71805b1aaee17ca8e`
- `src/main/kotlin/com/livteam/jsoninja/ui/dialog/convertType/JsonToTypeDialogValidator.kt`: `df06f1351155b89fb251d8fb44e15f8e69f9b9cab12a230c167fd06ac0c64a8f`
- `src/main/kotlin/com/livteam/jsoninja/ui/dialog/convertType/ConvertTypeDialogPresenter.kt`: `78560c79f370cb3072a38ce690819274d97bcdf96d8516c295b7c5d6dfca3ec7`
- `src/main/kotlin/com/livteam/jsoninja/ui/dialog/convertType/ConvertTypeDialogView.kt`: `2886779f87efbffc71b73adb471a06a658f841b12c1da8c971d6c6cdd3c19d3f`
- `src/main/kotlin/com/livteam/jsoninja/ui/dialog/convertType/ConvertTypeDialog.kt`: `c39d55c2eb2d43d616eea06126da576a6ef902f5f264e03e70aef71afa26407b`
- `src/main/kotlin/com/livteam/jsoninja/ui/component/convertType/CodePreviewPanel.kt`: `ec9264204ff29dfb96b7c68d6762325faf6c96d49ce290925157ae582e5e8f8e`
