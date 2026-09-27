# 결과 요약

실행 직전에 “무엇이 만들어지고 어디에 적용되는지” 확인하는 공통 하단 영역이다. 입력 설정에서 계산한 설명이며 생성된 JSON의 미리보기는 아니다.

## 구성과 변형

구분선 아래에 요약 한 행과 목적지 설명 한 행을 둔다. 본문과 좌우 기준선을 맞추되, 액션 버튼은 플랫폼 영역에 둔다. 실제 값과 전경색은 [토큰 연결](../tokens.md)이 소유한다.

| 설정 | 현재 문구에 값을 대입한 예 |
| --- | --- |
| 객체, 속성 5개, JSON | 객체 1개 · 속성 5개 · JSON |
| 배열, 객체 5개, 각 속성 3개, JSON5 | 객체 5개를 담은 배열 · 객체당 속성 3개 · JSON5 |
| 위 배열 설정에서 시드 유지 | 객체 5개를 담은 배열 · 객체당 속성 3개 · JSON5 · 시드 유지 |
| 유효하지 않은 설정 | 올바른 생성 설정을 입력하세요. |

아래 목적지 설명은 `현재 JSON 탭의 내용을 교체합니다.`다. 정확한 번들 키와 공통 문구 원칙은 [문구와 용어](../content.md)를 따른다.

## 상태와 갱신

설정 변경 → 활성 탭의 검증·요약 계산 → 공통 요약 라벨 갱신 순서로 연결한다. 탭을 바꾸면 선택된 탭의 요약을 사용한다. 오류가 있을 때 과거의 정상 요약을 계속 보여주지 않는다.

근거 흐름: [GenerateRandomJsonTabView](../../../src/main/kotlin/com/livteam/jsoninja/ui/dialog/generateJson/random/GenerateRandomJsonTabView.kt#L205) → [GenerateJsonDialogPresenter](../../../src/main/kotlin/com/livteam/jsoninja/ui/dialog/generateJson/GenerateJsonDialogPresenter.kt#L72) → [GenerateRandomJsonTabPresenter.getSummary](../../../src/main/kotlin/com/livteam/jsoninja/ui/dialog/generateJson/random/GenerateRandomJsonTabPresenter.kt#L27) → [GenerateJsonDialogView.setSummary](../../../src/main/kotlin/com/livteam/jsoninja/ui/dialog/generateJson/GenerateJsonDialogView.kt#L35).

## 조합과 적응

**불변:** 요약은 결과의 의미를 압축하고, 상세 입력은 수정 가능하게 남긴다. 목적지와 입력 오류를 요약 하나로 합치지 않는다.

**가변:** 스키마 모드에서는 결과 개수와 루트 구조 유지·배열 묶음 여부를 요약한다. 작업에 따라 요약 항목은 달라지지만 실제 적용과 일치해야 한다.

**조건부:** 긴 번역과 UI 확대에서 요약이 잘리지 않는지 확인한다. 현행 `JBLabel`이 모든 길이에 대해 자동으로 여러 줄을 제공한다고 가정하지 않는다. 잘림이 있다면 실제 구현 문제로 기록하고 검증한다.

## 접근성과 사용 제한

요약 갱신 때문에 입력 초점을 빼앗지 않는다. 화면 읽기 프로그램이 변경 내용을 인지할 수 있는지는 [접근성 기준](../accessibility.md)에 따라 확인한다. 이번 테스트는 문자열 변화를 확인했으며 음성 안내까지 검증하지 않았다.

생성 전에 “생성 완료”처럼 성공을 표현하지 않는다. 포맷·수량의 설명이 실제 소비자의 값과 다르거나, 존재하지 않는 미리보기·파일 저장을 약속하면 안 된다. 별도의 상태 변경 애니메이션은 기준에 없다.
