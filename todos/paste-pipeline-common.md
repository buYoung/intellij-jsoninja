# TODO: 붙여넣기 파이프라인 공통 점검

여러 붙여넣기 관련 버그가 모두 `JsonEditor`의 DocumentListener 기반 파이프라인(`src/main/kotlin/com/livteam/jsoninja/ui/component/JsonEditor.kt:120-175`)을 공유하고 있으므로, 아래 공통 작업을 먼저 수행해야 이후 개별 TODO의 원인 분석이 가능하다.

- [ ] `setupClipboardMonitoring()`에서 붙여넣기를 단일 DocumentEvent로 간주하고 있는데, 전체 선택 후 붙여넣기처럼 삭제/삽입 이벤트가 연속으로 발생하는 경우 `insertedText`와 `offset` 정보가 실제 문서 상태와 어긋나는지 검증하고, 필요 시 SelectionModel·CaretModel을 이용해 최신 범위를 다시 확인하는 보호 로직을 추가한다.
- [ ] `handlePotentialPasteContent()`가 `event.newFragment`만을 기준으로 JSON 유효성 및 길이를 판별한다. 부분 붙여넣기·대용량 붙여넣기 상황에서도 항상 전체 JSON 조각을 다루도록, 붙여넣기 감지 시점에 클립보드 내용과 selection 상태를 함께 보관하는 공통 유틸을 만든다.
- [ ] 공통 파이프라인에 디버그 로깅/진단 플래그를 넣어 `pasteFormatState`, `settings.indentSize`, `detectedRange(start,end)`가 어떤 값으로 계산됐는지 추적할 수 있게 하고, 각 버그 재현 시 이 정보를 확보해 원인 분석에 활용한다.
- [ ] 문서 교체 직후 `editor.document.replaceString(...)`을 호출하면 listener 재진입이 발생하므로, 재진입 guard나 `isSettingText` 토글 범위를 재점검해 이중 포맷팅/중복 삽입을 방지한다(여러 개별 버그에서 동일 문제로 의심됨).

아래 개별 TODO 파일들은 모두 이 공통 점검 작업 결과를 기반으로 진행해야 한다.
