# 문구와 용어

입력 이름은 짧게, 동작은 구체적으로, 오류는 고칠 방법까지 쓴다. 사용자가 선택하는 값과 실제 적용 결과를 설명하며 구현 클래스·파서·스레드 같은 내부 용어를 일반 UI 문구에 넣지 않는다.

## 기준 문구

아래는 현재 [한국어 번들](../../src/main/resources/messages/LocalizationBundle_ko.properties)에서 확인한 문구다. 식별자는 그대로 유지하며, 다른 화면에서도 같은 뜻이면 같은 용어를 사용한다.

| 역할 | 키 | 현재 문구 |
| --- | --- | --- |
| 창 제목 | `dialog.generate.json.title` | JSON 생성 |
| 주 동작 | `button.generate` | 생성 |
| 기본 방식 탭 | `dialog.generate.json.tab.random` | 무작위 |
| 결과 형태 | `dialog.generate.json.label.root.type` | 결과 구조: |
| 루트 선택 | `dialog.generate.json.radio.object` / `dialog.generate.json.radio.array` | 객체 / 객체 배열 |
| 배열의 객체 개수 | `dialog.generate.json.label.array.element.count` | 객체 수: |
| 각 객체의 크기 | `dialog.generate.json.label.props.per.object` | 객체당 속성 수: |
| 중첩 제한 | `dialog.generate.json.label.max.depth` | 최대 중첩 깊이: |
| 형식 선택 | `dialog.generate.json.output.format` | 출력 형식: |
| 상세 제어 | `dialog.generate.json.random.group` | 고급 설정 |
| 시드 기능 | `dialog.generate.json.random.keep.seeds` | 시드 유지 |
| 시드 변경 | `dialog.generate.json.random.new.structure` / `dialog.generate.json.random.new.values` | 새 구조 / 새 값 |

사용자가 말하는 “랜덤 생성”은 이 문서의 설명에서는 사용할 수 있지만, 현재 한국어 탭 라벨은 “무작위”다. `JSON`, `JSON5`, `JSON Schema`, URL과 코드 식별자는 형식을 보존한다.

## 이름, 범위, 설명의 역할

입력 라벨은 무엇을 설정하는지 말한다. 짧은 수치 범위는 옆의 보조 라벨에서 보여주고, 동작의 조건이나 이유는 설명 문장으로 제공한다. 라벨에 범위와 사용 설명을 모두 반복하지 않는다.

JSON5 설명은 선택한 형식에 따라 표시한다. 재현성 설명은 “같은 버전에서 설정과 두 시드가 같으면 결과를 재현할 수 있습니다.”라는 현재 조건을 유지한다. 이를 버전 간 동일 결과 보장으로 확대하지 않는다.

## 요약과 목적지

요약은 생성할 결과를 설명하고, 목적지는 별도 문장으로 알린다. [결과 요약 컴포넌트](components/result-summary.md)의 예시는 현재 번들에 실제 값을 대입한 것이다.

| 역할 | 현재 형식 |
| --- | --- |
| 객체 요약 | `객체 1개 · 속성 {0}개 · {1}` |
| 배열 요약 | `객체 {0}개를 담은 배열 · 객체당 속성 {1}개 · {2}` |
| 시드 유지 표시 | `{0} · 시드 유지` |
| 적용 목적지 | `현재 JSON 탭의 내용을 교체합니다.` |

목적지를 “완료”, “저장됨”처럼 성공을 암시하는 문구로 바꾸지 않는다. 이 다이얼로그의 주 버튼을 파일 저장이나 새 탭 생성으로 설명하지 않는다. 실제 대상 적용 경계는 [상호작용 패턴](patterns.md)에 있다.

## 오류와 복구 안내

오류는 실패 원인에 맞는 입력과 다음 행동을 알려준다. 현재 수치 오류는 `{0}부터 {1} 사이의 정수를 입력하세요.`이며, 설정 전체가 유효하지 않을 때의 요약은 `올바른 생성 설정을 입력하세요.`다. 필드 옆의 구체적 오류를 요약의 일반 문구만으로 대체하지 않는다.

시드는 `Long` 범위의 정수라는 실제 검증 조건을 전달한다. 생성 데이터가 너무 큰 경우에는 `객체 수, 속성 수 또는 깊이를 줄이세요.`처럼 조정할 입력을 알려준다. 입력값을 임의로 고쳐 놓고 오류를 숨기지 않는다.

## 번역과 문자열 관리

현재 UI 문자열은 [LocalizationBundle](../../src/main/kotlin/com/livteam/jsoninja/LocalizationBundle.kt)을 통해 조회한다. 새 화면 문구도 기존 기능 네임스페이스와 번들 경로를 따른다. 소스에 표시 문구를 직접 쓰거나, 언어별 어순이 달라지는 문장을 여러 조각으로 이어 붙이지 않는다.

`{0}`, `{1}`, `{2}` 자리표시자와 입력값의 의미를 유지한다. 번역된 라벨이 길어질 수 있으므로 원래 언어에서 맞았던 고정 폭을 정답으로 삼지 않는다. 현재 번들의 모든 언어에 대해 자연스러움이나 잘림이 검증된 것은 아니다.
