# TODO: 붙여넣기 시 사용자/문서 indent 적용

현상: 붙여넣기를 하면 항상 indent 4로 강제되며, 설정 화면에서 지정한 indent 또는 현재 문서의 Code Style을 따르지 않는다. 공통 파이프라인 TODO(`todos/paste-pipeline-common.md`)를 선행한 뒤 아래 항목을 처리한다.

- [ ] `JsonFormatterService.createConfiguredPrettyPrinter()`(src/main/kotlin/com/livteam/jsoninja/services/JsonFormatterService.kt:108-138)에서 가져오는 `settings.indentSize`가 설정 UI에서 변경한 값과 실제로 동기화되는지 확인한다. 필요 시 settings 인스턴스가 Project 서비스로 초기화될 때 최신 값을 읽도록 invalidate/caching 로직을 추가한다.
- [ ] 붙여넣기 자동 포맷(`JsonEditor.handlePotentialPasteContent`, src/main/kotlin/com/livteam/jsoninja/ui/component/JsonEditor.kt:152-171)에서 editor별 indent를 적용할 수 있도록, `CodeStyle.getSettings(project).getIndentSize(JsonLanguage)` 혹은 선택한 문서의 `EditorSettings` 값을 읽어와 `JsonFormatterService`에 일시적으로 주입하는 방식을 설계한다.
- [ ] 설정 우선순위(사용자 지정 indent vs. 현재 문서 indent)를 정의하고, UI/설정 설명에도 명시한다. 예를 들어 "플러그인 설정 우선, 미설정 시 문서 Code Style"과 같은 정책을 명확히 해둔다.
- [ ] indent 값이 바뀐 뒤에도 `prettyPrinterCache`에 이전 indent가 남아 있는지 검사하여, indent 변경 시 해당 캐시 키를 무효화하거나 새 Printer를 강제로 생성하도록 한다.
