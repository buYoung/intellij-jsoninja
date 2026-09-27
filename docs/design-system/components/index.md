# 구성 요소 목록

아래 이름은 디자인 판단의 단위다. 모두 독립적인 재사용 클래스라는 뜻은 아니다. 실제 구현은 연결된 뷰·프레젠터·플랫폼 컴포넌트에 분산돼 있다.

| 구성 요소 | 정본 | 구현 연결 | 상태 |
| --- | --- | --- | --- |
| 작업 다이얼로그 | [task-dialog.md](task-dialog.md) | `GenerateJsonDialog`, `GenerateJsonDialogView` | 현재 랜덤 생성 화면을 기준으로 문서화 |
| 폼 컨트롤 | [form-controls.md](form-controls.md) | UI DSL 행, 라디오 버튼, `intTextField`, `ComboBox`, `ValidationInfo` | 현행 사용과 조건 확인 |
| 고급 설정 | [advanced-options.md](advanced-options.md) | `GenerateRandomJsonTabView`의 `collapsibleGroup`과 시드 제어 | 접힘·펼침·활성 상태를 기존 테스트로 확인 |
| 결과 요약 | [result-summary.md](result-summary.md) | `GenerateRandomJsonTabPresenter.getSummary()`, `GenerateJsonDialogView.setSummary()` | 설정·검증 상태와의 연결 확인 |

새로운 화면에서는 목적이 같은 요소를 먼저 찾고, 기존 구현이 고정하는 배치와 지원하는 변형을 확인한다. 공통 클래스가 없는 곳에 문서상의 이름을 코드 API처럼 호출하지 않는다.

JSON Schema 편집기는 정보량이 많은 기존 변형의 예시다. 이 문서 집합은 범용 코드 편집기·표·트리의 전체 컴포넌트 계약까지 정의하지 않는다. 새 구성 요소의 문서가 필요하면 [운영 기준](../governance.md)에 따라 실제 구현과 사용 상태를 근거로 추가한다.
