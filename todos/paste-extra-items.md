# TODO: 붙여넣기 후 JSON 일부 항목이 추가되는 버그

보고된 현상은 "붙여넣으면 항목이 잘린다"였지만 실제로는 자동 포맷 과정에서 특정 필드가 중복 삽입되어, 긴 JSON에서는 잘린 것처럼 보인다. 공통 파이프라인 TODO(`todos/paste-pipeline-common.md`)와 연계해 아래 사항을 점검한다.

- [x] `handlePotentialPasteContent()`(src/main/kotlin/com/livteam/jsoninja/ui/component/JsonEditor.kt:152-171)이 `event.newFragment`만 교체하고 기존 selection 범위를 그대로 유지하기 때문에, 붙여넣기 직후 기존 텍스트 일부가 남아 JSON이 두 번 중첩되는지 재현한다. selection 모델(`editor.editor?.selectionModel`)을 활용해 실제로 대체해야 하는 범위를 계산하도록 수정 계획 필요.
- [x] `WriteCommandAction` 안에서 `doc.replaceString(start, end, formattedText)` 실행 시 listener 재진입으로 동일 formattedText가 다시 붙여넣어지는지 확인하고, 재진입 guard(`isSettingText`)가 전체 블록을 감싸도록 조정한다.
- [x] JsonFormatterService가 sort 옵션을 변경하면서 map을 새로 구성할 때, Jackson이 key ordering 외에 구조를 복제하는지 확인한다. 붙여넣기 시에만 추가되는 항목이 있다면 formatter 자체에서 원본 JSON을 손상시키고 있는지 검증한다.

**완료**: `DocumentListener` 대신 `CopyPastePreProcessor`를 도입하여 텍스트 삽입 전 포맷팅을 수행함으로써 리스너 재진입 및 중복 삽입 문제를 원천적으로 해결함.
