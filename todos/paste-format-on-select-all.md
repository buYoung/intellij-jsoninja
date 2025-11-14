# TODO: 전체 선택 후 붙여넣기 시 자동 포맷 미동작

환경: 기존 JSON이 있는 에디터에서 Cmd+A → Cmd+V를 수행하면 `format on paste`가 동작하지 않고 원본 그대로 들어간다. 빈 에디터나 커서를 특정 위치에 둔 상태에서는 정상 작동한다.

- [ ] `setupClipboardMonitoring()`(src/main/kotlin/com/livteam/jsoninja/ui/component/JsonEditor.kt:120-147)에서 붙여넣기 감지 조건을 `changeLength > PASTE_DETECTION_THRESHOLD`로만 판단하기 때문에, "전체 선택 → 붙여넣기"처럼 삭제 길이가 길고 삽입 길이가 동일한 경우 이벤트 순서가 달라지면 검출이 누락되는지 디버깅한다. 필요 시 `DocumentEvent.isWholeTextReplaced` 등을 함께 참조한다.
- [ ] 전체 선택 상태에서 붙여넣을 때 selection 영역이 즉시 사라지고 caret이 문서 끝으로 이동하므로, `handlePotentialPasteContent()` 호출 시점에는 selection 정보를 다시 계산해야 한다. SelectionModel을 조회하거나 `doc.getText(TextRange)`로 실제 붙여넣어진 내용과 길이를 재확인하는 보호 로직을 추가한다.
- [ ] `Cmd+A` 시 기존 DocumentListener가 먼저 기존 텍스트 삭제 이벤트(길이 0)를 받고, 이어 삽입 이벤트가 발생하는지 로깅해 순서를 확인한다. 삽입 이벤트가 발생하지 않거나 `insertedText`가 빈 문자열로 들어오는 경우, 공통 파이프라인 TODO에 따라 처리 방식을 수정한다.
- [ ] 문제가 재현되는 IDE 버전(최소/최대 build 243~252)별로 이벤트 시퀀스가 동일한지 확인해, 특정 버전에서만 발생하는지 여부도 함께 기록한다.
