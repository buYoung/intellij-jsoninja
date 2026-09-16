# 14 — 튜토리얼 diff 소유권 수정 기록

2026-09-16, R1.3. 구현·기존 검증 완료. 상태: **not-ready** — 브리프의 네이티브 IDE 조작 금지로 실제 창 수명 관찰은 남아 있다.

## Baseline

- 기준 HEAD: `da9e1a3e3239ca91fd795933618ea83a4012c2f3`.
- closeDiffWindow는 저장한 Window가 없으면 전역 Window.getWindows에서 sort 버튼을 찾고 그 조상 Window를 dispose했다. 기존 diff와 메인 프레임도 탐색 대상에 들어갈 수 있었다. 실제 창을 닫아 결함을 재현하지는 않았다.
- maybeOpenDiff는 일반 JsonDiffService의 activeDiffContext를 재사용하는 액션을 호출하므로 빌린 창의 소유권을 보장하지 못했다.

## Implementation

- 컨트롤러가 튜토리얼 전용 JsonDiffWindowDialog를 생성하고 ownedDiffDialog에 보관한다. 일반 diff의 activeDiffContext를 건드리지 않는다.
- 현재 JSON/정렬 옵션을 EDT에서 캡처하고 validateAndFormat은 Default, Document/request/dialog 생성은 EDT에서 실행한다. 생성 직전 단계·프로젝트·요청 sequence를 확인한다.
- closeDiffWindow는 참조를 먼저 비운 뒤 해당 dialog.close(CANCEL_EXIT_CODE)만 호출한다. 없으면 아무 창도 닫지 않는다. 수동 닫기 callback도 같은 객체일 때만 참조를 비운다. 반복 종료가 다른 창에 전달되지 않는 구조다.
- tooltip 부모 폐기와 presenter.dispose 모두 취소·tooltip/timer 정리·소유 dialog 닫기를 수행한다. 동일 정리는 재호출 가능하다.
- 정렬 tooltip 탐색은 전용 dialog 내부로 제한했다. Window.getWindows, 조상 Window의 dispose, 비공개 필드/메서드 reflection을 제거했다. 표시용 tooltip label은 창 소유권 결정에 쓰지 않는다.
- 기존 JsonDiffWindowDialog의 DiffManager request panel/disposable 계약, JsonDiffService.createDiffRequest의 JSON marker/정렬 액션을 재사용한다. IC 2024.3 소스의 DialogWrapper.getWindow 및 기존 프로젝트 close 경로를 확인했다.
- welcome 상태·단계 번호·메시지 키·일반 editor-tab/window 호스트 코드는 변경하지 않았다.

## Acceptance

작업 디렉터리: `/Users/buyong/workspace/private/json-helper2`.

- `./gradlew compileKotlin`: exit 0.
- `./gradlew test -PskipWasmBuild=true --tests 'com.livteam.jsoninja.services.JsonDiffServiceTest' --tests 'com.livteam.jsoninja.actions.ShowJsonDiffActionTest'`: exit 0.
- com.livteam.jsoninja.actions.ShowJsonDiffActionTest: 6개, 실패 0, 오류 0, 스킵 0
- com.livteam.jsoninja.services.JsonDiffServiceTest: 7개, 실패 0, 오류 0, 스킵 0

- 소유 창 없음/있음/중복 dispose 경로와 정상 diff 요청 marker/정렬 전달을 소스에서 확인했다. 이를 네이티브 창 수명 관찰로 보고하지 않는다.
- 미확인: 일반 diff와 IDE 프레임을 열어 둔 상태에서 단계 전환·튜토리얼 종료, 전용 dialog 수동 닫기 후 반복 종료, 일반 editor-tab/window의 실제 정렬. 브리프가 직접 UI 조작 재시도를 금지하므로 실행하지 않았다.
- 후속 조치 소유자: 사용자 또는 향후 승인된 IDE 관찰 세션. 부모 14 완료 체크 및 16의 최종 전체 수용 판정은 보류한다. 독립적인 다른 코드 작업은 계속할 수 있다.

### 검증 소스 SHA-256

- `src/main/kotlin/com/livteam/jsoninja/ui/onboarding/OnboardingStep8DiffTooltipController.kt`: `74e2c408b039fa0e1b45972d3cfa0e143a76710b9d61995c4159fe6b6b8ea985`.
