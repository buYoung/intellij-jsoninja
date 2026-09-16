# 14 — 튜토리얼 diff 소유권 수정 기록

2026-09-16, R1.3. 구현·기존 검증 완료. 현재 상태: **ready — 후속 사용자 확인으로 수용**. 에이전트의 네이티브 UI 직접 관찰과 사용자 확인을 구분한다.

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
- 에이전트 직접 관찰 미실시: 일반 diff와 IDE 프레임을 열어 둔 상태에서 단계 전환·튜토리얼 종료, 전용 dialog 수동 닫기 후 반복 종료, 일반 editor-tab/window의 실제 정렬. 브리프가 직접 UI 조작 재시도를 금지하므로 실행하지 않았다.
- 최초 인계 당시에는 위 관찰 대기로 not-ready였고, 부모 14 완료 체크를 보류했다. 이후 아래 사용자 확인으로 해당 대기를 종료했다.

### 후속 사용자 확인

- 출처: 이 작업의 후속 대화에서 사용자가 “온보딩 확인됐어”라고 확인 완료를 알렸다. 앞서 설명한 온보딩 수정과 남은 실제 창 검증에 대한 사용자 확인으로 수용하고 부모 14 완료 체크를 갱신했다.
- 에이전트가 직접 창 동작을 관찰한 것으로 바꾸지 않는다. 세부 시나리오별 로그나 사용자 확인 시의 IDE 버전은 별도로 제공되지 않았다.
- 코드 변경은 없으며 컨트롤러의 SHA-256은 아래 기존 검증 기록과 일치한다. 외부 CI/서명/배포 실행은 이 확인에 포함되지 않는다.

### 검증 소스 SHA-256

- `src/main/kotlin/com/livteam/jsoninja/ui/onboarding/OnboardingStep8DiffTooltipController.kt`: `74e2c408b039fa0e1b45972d3cfa0e143a76710b9d61995c4159fe6b6b8ea985`.
