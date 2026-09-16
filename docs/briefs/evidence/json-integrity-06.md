# 06 — 실행 가능한 쿼리 경로 기록

2026-09-16, R1.12. 상태: **ready**.

## Baseline

- 기준 HEAD: `b25beec98a5842d70b7cf6e06588578a91c3bd24`. 선행 05의 세 파일은 변경 없이 유지했다.
- 실제 CopyJsonQueryAction과 JsonEditorTooltipListener를 IC 2024.3 헤드리스 fixture에서 실행했다. 복사 경로를 JsonQueryService로 평가하고 실제 tooltip 문자열과 비교했다.
- 일반/템플릿 각 10개 위치 × 세 엔진 × 경로 평가/툴팁 일치 = 120개 항목 중 31개 경로 평가가 실패했다. JMESPath 특수 키 14개, 일반 jq 특수 키 7개, 템플릿 jq 10개다. 두 표시 경로는 잘못된 문자열을 서로 동일하게 내보내고 있었다.

## Implementation

- 경로 생성의 엔진 계약을 JsonQueryType으로 통일했다. 기존 일반 getJsonPath/getJmesPath/getJqPath 진입점은 유지하며 공통 getPath로 위임한다.
- 템플릿 함수의 Boolean 인자를 실제 엔진으로 교체하고 알려진 두 호출자를 함께 변경했다. 루트는 JsonPath `$`, JMESPath `@`, jq `.`이고 배열 경로도 같은 규칙을 적용한다.
- JMESPath/jq의 따옴표 구분자 앞에 있던 불필요한 역슬래시를 제거하고 키 내용 자체의 quote/backslash/control escape는 보존한다. JsonPath의 기존 대괄호 표현과 단순 속성 의미는 유지한다.
- 툴팁의 HTML 표시에서 경로를 XML escape해 `<`·`&` 등이 문자 그대로 표시되도록 한다. placeholder의 위치 매핑·복원은 변경하지 않았다.
- 문법 근거: [JMESPath specification](https://jmespath.org/specification.html), [jq 1.6 manual](https://jqlang.org/manual/v1.6/). 실제 내장 엔진의 실행 결과를 최종 판단에 사용했다.

## Acceptance

작업 디렉터리: `/Users/buyong/workspace/private/json-helper2`.

- `./gradlew compileKotlin`: exit 0.
- `./gradlew test -PskipWasmBuild=true --tests 'com.livteam.jsoninja.services.JMESPathServiceTest' --tests 'com.livteam.jsoninja.services.JacksonJqServiceTest'`: exit 0, 2 + 8 = 10개, 실패/오류/스킵 0.
- `python3 /tmp/json-integrity-path-check.py`: exit 0, `CHECKS=159 FAILURES=0`, fixture 종료 오류 없음.
- 환경: IC 2024.3, JShell/GraalVM 21.0.2, headless=true, JVM 컴파일 목표 17. 실제 PSI/caret/AnActionEvent/CopyPasteManager와 수식키를 담은 EditorMouseEvent를 사용했다. 등록 listener가 200ms 이후 설정한 실제 toolTipText를 읽었다. 네이티브 키보드·창은 조작하지 않았다.

| 입력/대상 | 결과 |
| --- | --- |
| `{"plain":[{"a.b":1,"sp ace":2,"q\"x":3,"slash\\key":4}]}` | 모든 키가 각 엔진에서 1/2/3/4를 조회 |
| 추가 키 한글, 줄바꿈, `<x&y>` | Unicode/control/HTML 문자가 다른 속성으로 바뀌지 않음 |
| 루트, plain 배열, 배열의 객체 | 전체 노드를 JsonNode 동등성으로 비교해 일치 |
| 각 값을 `{{value1}}`~`{{value7}}`로 바꾼 템플릿 | 같은 경로로 바인딩된 일반 JSON의 대응 값 조회, jq 점 보존 |
| `[[9]]`의 루트/안쪽 배열/숫자 | `$[0][0]`, `[0][0]`, `.[0][0]` 등 정상 조회 |
| 자리표시자 내부 caret 7개 × 세 엔진 | 실제 copy 결과와 template helper가 일치, isInsidePlaceholder=true, 각각 대응 숫자 조회 |

- 일반/템플릿/중첩 배열의 69개 위치에서 경로 실행과 툴팁 일치를 각각 확인한 138항목, 자리표시자 내부 21항목으로 총 159항목이다. 템플릿의 속성 이름에 hover하여 실제 템플릿 툴팁 경로를 관찰했다. 자리표시자 내부의 팝업 위치·네이티브 외형은 검증 범위에 포함하지 않았다.
- fixture 클립보드의 기존 Transferable이 있으면 복원하고 fixture를 종료했다. 이번 세션의 초기 fixture 클립보드는 비어 있었다.
- 이전 Boolean 분기 호출자가 남지 않았고 실제 enum 전달 호출자를 확인했다. 새 테스트/설정/의존성을 추가하지 않았다.

### 검증 소스 SHA-256

- `src/main/kotlin/com/livteam/jsoninja/utils/JsonPathHelper.kt`: `1c466386c32c08a4dbdffa8620a0d35f32612fcd7f3d2b1b519f335cd946760e`
- `src/main/kotlin/com/livteam/jsoninja/actions/CopyJsonQueryAction.kt`: `3a1afb6672d611254fd4ddd47544dbae4bd9542910955827b785d3fb4aa6d688`
- `src/main/kotlin/com/livteam/jsoninja/ui/component/editor/JsonEditorTooltipListener.kt`: `45ace52e763775d33d12d87a26bf7a15a14d425dde1348fae203323d5de9fadb`
