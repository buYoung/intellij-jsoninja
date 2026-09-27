# 토큰과 구현 값

이 문서는 현재 구현에 존재하는 값과 플랫폼 연결의 조회표다. **확인한 생성 UI 범위에는 중앙 디자인 토큰 모듈이 없다.** 아래의 한글 항목명은 설명용이며, 코드에 없는 토큰 식별자나 새 상수를 정의하지 않는다.

## 값의 세 단계

| 단계 | 현재 근거 | 소비자 |
| --- | --- | --- |
| 플랫폼 기본값 | UI 테마, UI DSL 배치, 표준 컨트롤 | 일반 본문·버튼·선택·오류·초점 표현 |
| 의미 연결 | `UIUtil.getContextHelpForeground()` | 수치 범위 라벨, 하단 결과 요약 |
| 구성 요소의 명시 값 | `JBUI.Borders.empty`, `JBUI.scale`, `columns` | 생성 다이얼로그의 여백·폭, 시드 입력 폭 |

일반 본문색·버튼색·오류색의 정확한 HEX 값, 글꼴 이름·크기, 모서리 반경은 이 화면에서 독립적으로 정하지 않는다. 테마마다 달라질 수 있는 값을 하나의 고정값으로 문서화하지 않는다. [플랫폼 색상·스케일 근거](https://plugins.jetbrains.com/docs/intellij/ui-faq.html).

## 생성 다이얼로그 수치

`JBUI`에 전달하는 값은 배율 적용 전 기준값이다. 스크린샷에서 측정한 물리 픽셀이나 운영체제 창 전체 크기가 아니다.

| 항목 | 소스의 값 | 의미와 적용 범위 | 구현 근거 |
| --- | --- | --- | --- |
| 탭 본문 여백 | `JBUI.Borders.empty(16, 12, 12, 12)` | 위·왼쪽·아래·오른쪽 순서. 무작위/스키마 탭 본문 | [무작위 뷰](../../src/main/kotlin/com/livteam/jsoninja/ui/dialog/generateJson/random/GenerateRandomJsonTabView.kt#L202), [스키마 뷰](../../src/main/kotlin/com/livteam/jsoninja/ui/dialog/generateJson/schema/GenerateSchemaJsonTabView.kt#L289) |
| 하단 요약 여백 | `JBUI.Borders.empty(0, 12, 0, 12)` | 본문과 같은 좌우 기준선. 액션 버튼 영역의 여백은 아님 | [전체 뷰](../../src/main/kotlin/com/livteam/jsoninja/ui/dialog/generateJson/GenerateJsonDialogView.kt#L63) |
| 탭 영역의 선호 폭 하한 | `JBUI.scale(600)` | `maxOf`로 내용의 선호 폭과 비교. 고정 폭이 아님 | [전체 뷰](../../src/main/kotlin/com/livteam/jsoninja/ui/dialog/generateJson/GenerateJsonDialogView.kt#L41) |
| 탭 영역의 최소 폭 하한 | `JBUI.scale(560)` | 내용의 최소 폭이 더 크면 그 값을 사용 | [전체 뷰](../../src/main/kotlin/com/livteam/jsoninja/ui/dialog/generateJson/GenerateJsonDialogView.kt#L46) |
| 시드 입력 폭 | `columns(21)` | 문자 열 기준의 선호 폭. 픽셀 폭이나 최대 입력 길이가 아님 | [무작위 뷰](../../src/main/kotlin/com/livteam/jsoninja/ui/dialog/generateJson/random/GenerateRandomJsonTabView.kt#L169) |
| 영역 사이 간격 | `TopGap.SMALL` | 출력 형식 구분선 앞에서 사용. 픽셀값으로 환산해 복제하지 않음 | [무작위 뷰](../../src/main/kotlin/com/livteam/jsoninja/ui/dialog/generateJson/random/GenerateRandomJsonTabView.kt#L150) |

이 값은 **생성 다이얼로그의 확인된 기준**이다. 다른 기능의 편집기나 복합 다이얼로그에 모두 같은 폭과 여백을 강제하지 않는다. 제품 전역의 별도 간격 척도는 정하지 않았다.

## 의미와 상태 연결

| 화면 역할 | 현재 연결 | 조건 |
| --- | --- | --- |
| 보조 설명 전경색 | `UIUtil.getContextHelpForeground()` | 범위·요약을 본문보다 낮은 위계로 표시 |
| 설명 문장 | UI DSL `comment(...)` | 플랫폼의 설명 표현을 사용 |
| 종속 입력 비활성 | `enabledIf(...)` | 부모 선택이 적용되지 않을 때 |
| 조건부 설명 표시 | `Row.visible(...)` | JSON5처럼 설명이 필요한 선택일 때 |
| 입력 오류 | `ValidationInfo(message, component)` | 오류를 해당 입력에 연결 |

값 → 컴포넌트 → 상태의 연결은 [폼 컨트롤](components/form-controls.md), [고급 설정](components/advanced-options.md), [결과 요약](components/result-summary.md)에서 이어진다.

## 변경과 검증

새 값이 필요하면 먼저 기존 플랫폼 값이나 현재 컴포넌트가 표현할 수 있는지 확인한다. 별도 토큰 체계를 도입하거나 기존 값의 의미를 바꾸는 일은 이 문서 작성으로 승인된 변경이 아니다.

수치 변경 시에는 실제 소비자의 적용 위치와 영향을 받는 상태를 함께 검토한다. 예를 들어 탭의 최소 폭 변경은 본문뿐 아니라 번역된 라벨과 하단 요약의 잘림 여부도 영향을 받는다. 명명·별칭·폐기 절차는 구현된 토큰 체계가 생길 때 해당 소스와 함께 결정한다.
