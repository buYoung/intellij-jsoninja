# JSONinja 디자인 시스템

JSONinja의 입력 중심 화면은 **현재 저장소의 랜덤 JSON 생성 다이얼로그**를 기준으로 정보 위계와 상태 표현을 맞춘다. 기본 설정을 읽고 결과 요약과 적용 대상을 확인한 뒤 실행하는 흐름을 유지하며, 접이식 고급 설정의 적용 여부는 [정보량과 작업 필요성](patterns.md#기본-노출과-고급-설정의-적용-조건)에 따라 결정한다.

이 문서는 화면을 설계하거나 구현하는 기여자와 코드 에이전트가 함께 사용하는 제품 UI 기준이다. 기존 화면의 공통 판단을 정리한 문서이며, 코드에 공통 컴포넌트나 토큰 모듈을 새로 추가한 것은 아니다.

## 기준과 적용 범위

| 항목 | 기준 |
| --- | --- |
| 방향의 승인 근거 | 사용자가 랜덤 JSON 생성 화면의 하단 요약과 고급 옵션을 기준으로 선택함. 이후 **내용이 적으면 숨기지 않고, 많을 때 일반 사용자에게 불필요한 고급 기능만 접는다**고 적용 조건을 명시함 |
| 구현 기준 | 생성 화면은 `main`의 `148ccfd`, 2026-09-27에 확인한 작업 트리. API 불러오기 화면의 후속 수정과 검증 범위는 [변경 기록](governance.md#기본-노출-기준과-api-다이얼로그-수정)에 구분 |
| 성숙도 | 디자인 시스템 문서화. 현행 코드와 대표 컴포넌트 상태를 검증한 기준이며, 전체 제품의 시각·접근성 적합성 인증은 아님 |
| 주 적용 대상 | 설정을 입력한 뒤 실행하는 다이얼로그, 그 안의 폼·선택·보조 설명·요약·오류 표현 |
| 구체적 플랫폼 | IntelliJ Platform 기반 JetBrains IDE 플러그인. [플랫폼 적용 기준](platforms/intellij-platform.md) 참조 |
| 대표 기준 화면 | `GenerateJsonDialog`의 무작위 탭. JSON Schema 탭은 정보량이 많은 기존 변형 사례 |

다른 화면에는 정보 위계와 상태 표현을 적용한다. 코드 편집기·비교 화면·도구 창에 랜덤 생성 폼의 폭이나 행 구성을 그대로 적용하지 않는다. 마켓플레이스 이미지와 웹사이트는 이 문서의 적용 대상이 아니다.

## 문서 지도

| 필요한 결정 | 정본 |
| --- | --- |
| 정보 위계, 색상·글꼴·여백·형태·움직임의 원칙 | [기초 원칙](foundations.md) |
| 코드에서 확인한 치수와 플랫폼 값의 연결 | [토큰과 구현 값](tokens.md) |
| 기본 노출과 고급 설정의 적용 조건, 입력부터 실행까지의 상태 전환 | [상호작용 패턴](patterns.md) |
| 화면 문구와 번역 기준 | [문구와 용어](content.md) |
| 키보드, 초점, 보조 기술, 테마·확대 검증 | [접근성](accessibility.md) |
| 구성 요소와 구현 위치 찾기 | [구성 요소 목록](components/index.md) |
| 다이얼로그 전체 구성과 창 크기 | [작업 다이얼로그](components/task-dialog.md) |
| 입력·선택·도움말·검증 | [폼 컨트롤](components/form-controls.md) |
| 접힘·펼침과 종속 옵션 | [고급 설정](components/advanced-options.md) |
| 실행 전 결과·목적지 확인 | [결과 요약](components/result-summary.md) |
| JetBrains 컴포넌트와 지원 버전 경계 | [IntelliJ Platform 적용](platforms/intellij-platform.md) |
| 변경 판단, 실제 검증 결과, 남은 한계 | [운영과 검증 기록](governance.md) |

## 규칙을 읽는 방법

- **불변**: 적용 범위 안에서 유지할 공통 판단이다.
- **가변**: 내용과 작업에 맞춰 조정할 수 있다.
- **조건부**: 명시한 모드·상태·플랫폼에서만 달라진다.
- **금지**: 승인된 방향을 깨거나 사용자에게 잘못된 정보를 주는 사용이다.
- **검증**: 구현 결과에서 관찰하거나 검사할 조건이다.

규칙의 근거도 구분한다. 사용자 선택은 방향의 근거이고, 소스는 현재 동작과 값의 근거다. 공식 플랫폼 문서는 구현 제약의 근거이며, 테스트 통과는 해당 테스트가 검사한 상태만 보장한다.

## 대표 상태 검증과 한계

고급 설정이 접힌 간결한 상태와, 펼쳐져 입력이 활성화된 상태를 기존 테스트로 대조했다. 별도 테스트에서는 유효한 시드와 잘못된 시드, 시드 유지 해제에 따른 검증·요약 차이를 확인했다. **선택한 테스트 2개가 통과**했다. 명령과 검증 범위는 [검증 기록](governance.md)에 있다.

현재 저장소 버전의 실제 창 렌더링, 밝은/어두운 테마 대비, 확대, 전체 키보드 이동, 화면 읽기 프로그램은 검증하지 않았다. 실행 중인 IDE에서 관찰한 화면은 저장소와 다른 구성이어서 대표 검증에서 제외했다. 이 차이가 문서의 기준을 바꾸지는 않는다.

## 근거의 우선순위

1. 사용자가 현재 대화에서 확정한 방향과 요구사항
2. 이 문서 집합의 해당 결정 정본
3. 연결된 현행 구현과 기존 검증 결과
4. 대상 버전에 적용되는 JetBrains 공식 문서

플랫폼 공개 API와 접근성 제약은 외형을 맞추기 위해 우회하지 않는다. 구현과 문서가 다르면 우선 원인과 적용 버전을 확인한다. 문서에 없는 색상·치수·저장 정책을 관례만으로 확정하지 않는다.

주요 구현 근거: [다이얼로그](../../src/main/kotlin/com/livteam/jsoninja/ui/dialog/generateJson/GenerateJsonDialog.kt), [전체 뷰](../../src/main/kotlin/com/livteam/jsoninja/ui/dialog/generateJson/GenerateJsonDialogView.kt), [무작위 탭 뷰](../../src/main/kotlin/com/livteam/jsoninja/ui/dialog/generateJson/random/GenerateRandomJsonTabView.kt), [무작위 탭 프레젠터](../../src/main/kotlin/com/livteam/jsoninja/ui/dialog/generateJson/random/GenerateRandomJsonTabPresenter.kt), [API 불러오기 뷰](../../src/main/kotlin/com/livteam/jsoninja/ui/dialog/loadJson/LoadJsonFromApiDialogView.kt).
