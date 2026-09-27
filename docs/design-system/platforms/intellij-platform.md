# IntelliJ Platform 적용 기준

공통 [기초 원칙](../foundations.md)을 JetBrains IDE의 입력·테마·배치·접근성 체계에 연결한다. 이 파일은 플랫폼 차이와 구현 연결을 소유하며, 웹 프레임워크의 컴포넌트나 반응형 화면 폭을 기본값으로 삼지 않는다.

## 확인한 프로젝트 기준

| 항목 | 저장소 설정 | 근거 |
| --- | --- | --- |
| 개발 대상 제품/버전 | `IC`, `2024.3` | [gradle.properties](../../../gradle.properties) |
| 선언한 IDE 빌드 범위 | `243`부터 `263.*`까지 | [gradle.properties](../../../gradle.properties), [빌드 설정](../../../build.gradle.kts#L263) |
| IntelliJ Platform Gradle Plugin | `2.11.0` | [버전 카탈로그](../../../gradle/libs.versions.toml#L15) |
| Kotlin | `2.4.0` | [버전 카탈로그](../../../gradle/libs.versions.toml#L16) |
| 프로젝트 JVM 도구 체인 | `jvmToolchain(17)` | [빌드 설정](../../../build.gradle.kts#L81) |

JVM 도구 체인과 실제 IDE의 부트 런타임은 다르다. 선언한 지원 범위는 모든 IDE·운영체제에서 실행 검증을 마쳤다는 의미가 아니다. 이번 테스트의 범위는 [검증 기록](../governance.md)을 따른다.

## UI 표면 선택

설정값을 입력하는 폼은 현재 기준 화면처럼 Kotlin UI DSL을 사용한다. 지속적인 편집·탐색 작업 영역은 작업의 성격에 맞는 플랫폼 Swing 컴포넌트를 사용한다. UI DSL을 모든 도구 창 배치의 의무로 확대하지 않는다. [공식 Kotlin UI DSL 적용 범위](https://plugins.jetbrains.com/docs/intellij/kotlin-ui-dsl-version-2.html).

`DialogWrapper`는 다이얼로그 제목·본문·검증·확인/취소 동작을 연결하는 기본 표면이다. 액션 버튼의 운영체제별 순서와 기본 닫기 동작은 플랫폼에 맡긴다. 커스텀 하단 버튼으로 이를 다시 구현하지 않는다. [공식 DialogWrapper 계약](https://plugins.jetbrains.com/docs/intellij/dialog-wrapper.html).

## 공통 판단의 구현 연결

| 공통 판단 | 현재 연결 | 적용 경계 |
| --- | --- | --- |
| 라벨과 입력의 관계 | UI DSL `row`, `buttonsGroup` | 폼의 관련 행 안에서 정렬 |
| 상세 설정의 선택적 공개 | `collapsibleGroup` | 펼침과 기능 활성은 별도 상태 |
| 종속 컨트롤 | `enabledIf`, 조건부 `visible` | 숨김/비활성의 선택은 [패턴](../patterns.md)에 따름 |
| 입력 오류 연결 | `ValidationInfo`, 다이얼로그 검증 | 오류를 필드와 연결하고 필요한 영역 표시 |
| 배율 대응 | `JBUI.Borders`, `JBUI.scale` | 실제 수치의 정본은 [토큰](../tokens.md) |
| 보조 설명 표현 | `UIUtil.getContextHelpForeground`, UI DSL 설명 | 테마의 의미를 따름 |
| 생성 진입점 아이콘 | `JsoninjaIcons.getGenerateIcon(project)` | 사용자의 기존 아이콘 팩 선택 경로 유지 |

아이콘 근거는 [JsoninjaIcons](../../../src/main/kotlin/com/livteam/jsoninja/icons/JsoninjaIcons.kt#L97)와 [생성 액션](../../../src/main/kotlin/com/livteam/jsoninja/actions/GenerateRandomJsonAction.kt#L140)이다. 다이얼로그를 맞추기 위해 별도 웹 아이콘 집합을 들여오거나 기존 SVG를 이모지로 바꾸지 않는다.

## 입력, 확대, 테마

마우스·키보드·사용자 키맵과 플랫폼 컨트롤의 상호작용을 보존한다. 기본 이동과 닫기 키를 전역에서 가로채지 않는다. 웹의 `cursor-pointer`, CSS 상태, ARIA 속성을 Swing에 그대로 옮기는 대신, 플랫폼 컨트롤과 접근성 메타데이터를 사용한다.

크기를 고정할 때는 물리 픽셀을 가정하지 않는다. 밝은/어두운 테마와 UI 확대에서 읽히는지는 별도로 확인한다. 화면별 실제 검증 범위와 미확인 항목은 [접근성](../accessibility.md)에 있다.

## 공개 API와 수명 경계

이 문서에 나온 클래스·함수는 현재 구현 연결을 설명한다. 이름이 `public`이거나 컴파일된다는 사실만으로 외부 플러그인에 허용된 신규 API라고 판단하지 않는다. 신규 호출이나 상속을 추가할 때는 대상 버전의 선언·상위 타입·패키지·모듈·공식 문서를 확인한다. `Internal` API, 리플렉션 우회, 내부 구현 복사는 기준 표현을 맞추는 수단으로 사용하지 않는다.

참고한 JetBrains 스킬의 소스 기준은 `idea/2026.2.2`, 커밋 `1c7e601c0423e544917046c23763b15d0282e2a3`이다. 이 기준은 프로젝트의 최소 버전을 올리거나 2024.3 호환성 검토를 대신하지 않는다. 이번 문서는 새로운 API 호출이나 확장점 등록을 추가하지 않았다.

UI 응답성과 수명 처리는 기존 [코루틴·스레딩 표준](../../coroutine-threading-standard.md)의 구현 계약을 따른다. 생성 다이얼로그의 레이아웃 지연 적용을 무거운 작업의 실행 패턴으로 복제하지 않는다. 검증기·편집기·비동기 작업은 소유 화면의 수명과 함께 관리한다.

## 외부 근거

다음 공식 자료를 2026-09-27에 확인했다. 현재 페이지가 설명하는 모든 API를 지원 범위 전체에 검증한 것은 아니다.

- [Kotlin UI DSL](https://plugins.jetbrains.com/docs/intellij/kotlin-ui-dsl-version-2.html): 폼과 행·그룹의 구성
- [Dialogs](https://plugins.jetbrains.com/docs/intellij/dialog-wrapper.html): 액션 영역, 초점, 검증
- [Layout](https://plugins.jetbrains.com/docs/intellij/layout.html): 입력 관계와 배치
- [User Interface FAQ](https://plugins.jetbrains.com/docs/intellij/ui-faq.html): 테마 색상과 배율 대응
- [Validation Errors](https://plugins.jetbrains.com/docs/intellij/validation-errors.html): 오류와 복구 안내
- [Accessibility](https://plugins.jetbrains.com/docs/intellij/accessibility.html): 키보드와 보조 기술
