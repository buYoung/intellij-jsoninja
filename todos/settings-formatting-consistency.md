# TODO: 설정된 포맷 상태와 실제 동작 일치시키기

사용자가 설정 화면(`JsoninjaSettingsConfigurable`)에서 PRETTIFY 등을 선택해도 대부분의 기능이 여전히 하드코딩된 상태(JSON 탭 생성, Random JSON, Action 버튼)로 동작하고 있다. `JsonHelperService`는 상태를 저장만 하고 활용되지 않아 "설정은 PRETTIFY인데 다른 결과가 나온다"는 신고가 반복되고 있다.

- [x] `JsonHelperService.setJsonFormatState()`/`getJsonFormatState()`를 호출하는 경로가 `JsonHelperTabbedPane.setupJmesPathComponent()`(src/main/kotlin/com/livteam/jsoninja/ui/component/JsonHelperTabbedPane.kt:323-337)밖에 없는지 확인하고, 새 탭 초기화·Random JSON 삽입·기본 포맷 버튼과 같은 진입점에서 설정값을 참조하도록 통합 계획을 세운다.
- [x] `JsonHelperPanel` 내부 메서드(`setRandomJsonData`, `formatJson` 등, src/main/kotlin/com/livteam/jsoninja/ui/component/JsonHelperPanel.kt:94-150)가 모두 고정 포맷(`JsonFormatState.PRETTIFY`)만 사용하고 있으므로, 설정된 기본 포맷 상태/정렬 옵션을 반영하도록 리팩터링한다.
- [x] 설정 UI에서 저장되는 `JsoninjaSettingsState.jsonFormatState`와 액션 버튼 상태(예: 툴바에서 PRETTIFY 버튼 클릭 시) 간에 동기화가 필요한지 요구사항을 재확인하고, 필요하다면 액션이 실행될 때 `JsonHelperPanel.setJsonFormatState()`를 호출해 설정값과 실제 상태를 일관되게 유지한다. → Prettify 액션은 `JsonHelperPanel.formatJson()`을 호출해 현재 설정된 기본 포맷을 그대로 적용하고, Uglify는 설정 UI에서 선택할 수 없는 옵션이라 별도 동기화 없이 현재 탭에만 적용.
- [x] 동일 설정이 붙여넣기/자동 포맷/Random JSON/다른 서비스에서 서로 다른 Enum을 사용하지 않는지 점검하고, `JsonFormatState` 값이 누락되는 지점이 있으면 guard/default 처리를 추가한다. → Random JSON/ JMESPth/ToolWindow 진입점 모두 `JsonHelperPanel.getJsonFormatState()`를 사용하고, 붙여넣기(`pasteFormatState`)·diff 전용 경로는 `JsonFormatState.fromString()` 기반 guard를 유지하도록 확인.
