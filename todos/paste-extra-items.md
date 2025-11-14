# TODO: 붙여넣기 후 JSON 일부 항목이 추가되는 버그

보고된 현상은 "붙여넣으면 항목이 잘린다"였지만 실제로는 자동 포맷 과정에서 특정 필드가 중복 삽입되어, 긴 JSON에서는 잘린 것처럼 보인다. 공통 파이프라인 TODO(`todos/paste-pipeline-common.md`)와 연계해 아래 사항을 점검한다.

- [ ] `handlePotentialPasteContent()`(src/main/kotlin/com/livteam/jsoninja/ui/component/JsonEditor.kt:152-171)이 `event.newFragment`만 교체하고 기존 selection 범위를 그대로 유지하기 때문에, 붙여넣기 직후 기존 텍스트 일부가 남아 JSON이 두 번 중첩되는지 재현한다. selection 모델(`editor.editor?.selectionModel`)을 활용해 실제로 대체해야 하는 범위를 계산하도록 수정 계획 필요.
- [ ] `WriteCommandAction` 안에서 `doc.replaceString(start, end, formattedText)` 실행 시 listener 재진입으로 동일 formattedText가 다시 붙여넣어지는지 확인하고, 재진입 guard(`isSettingText`)가 전체 블록을 감싸도록 조정한다.
- [ ] JsonFormatterService가 sort 옵션을 변경하면서 map을 새로 구성할 때, Jackson이 key ordering 외에 구조를 복제하는지 확인한다. 붙여넣기 시에만 추가되는 항목이 있다면 formatter 자체에서 원본 JSON을 손상시키고 있는지 검증한다.
- [ ] 문제를 재현할 수 있는 JSON/동작 시나리오를 기록하여, 향후 이 버그가 재발했을 때 동일한 케이스를 다시 테스트할 수 있도록 한다(예: 중첩 배열/객체가 있는 JSON을 탭에 붙여넣기).
