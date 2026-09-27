# 실제 변환 대화상자의 언어 강조 검증

## 요청과 확인된 누락

사용자는 일부 언어의 강조가 여전히 빠진다고 보고하고, 메인 에이전트가 지원 언어 전체를 JetBrains UI 테스트로 확인하도록 요청했다. 앞선 11개 언어 등록·렉서·편집기 인스턴스 검증만으로 실제 모달 창의 강조 활성화까지 확인했다고 볼 수 없었다.

- 실제 창에서 Kotlin에서 Java로 전환하면 네이티브 필드 강조가 적용되지 않는 것을 재현했다. `CodePreviewPanel`은 숨겨진 성공 카드에 에디터를 추가한 뒤 카드를 표시했다. SDK 243의 편집기 추적기는 윈도에 등록할 때 표시 중인 에디터를 강조 대상으로 수집하므로, 숨겨진 상태의 등록이 누락을 일으켰다. 성공 카드를 먼저 표시하고 에디터를 생성하도록 순서를 바꿨다. 내부 추적기 API를 호출하지 않는다.
- C/C++의 `typedef` 별칭이 필드로 분류되거나 소문자 별칭을 인식하지 못했다. 선언의 중괄호 깊이와 typedef 문맥을 렉서 상태에 보존하여 별칭과 구조체 필드를 구분한다.
- C#의 `[JsonPropertyName]` 같은 특성 이름이 일반 타입으로 표시됐다. 특성 문맥에서 메타데이터 키를 사용하고, 한정 이름·여러 특성·배열 타입을 구분한다.
- 스킴 변경 중 Swing 글꼴 갱신이 전역 편집기 스킴을 수정하는 부작용을 발견했다. 별도 회귀 테스트에서 이를 재현한 뒤 `EditorEx.createBoundColorSchemeDelegate`로 편집기 설정을 분리하고, 색상 스킴을 사용하는 코드 필드는 `setFontInheritedFromLAF(false)`로 IDE 편집기 글꼴을 따르게 했다. 두 공개 API는 최소 SDK 243의 선언과 구현을 확인했다.

## 검증 구조

### Platform UI 테스트

`TypeConversionUiTest`는 실제 `JsonToTypeDialogPresenter`와 언어 드롭다운을 사용한다. 11개 확장자의 연결을 PlainText로 바꿔 내장 강조를 강제하고, 사용자가 보고한 이질적 JSON 배열을 변환한다. 밝은/어두운 기본 스킴에서 11개 필드 전체의 역할과 실제 렌더링된 색상을 검사한다. `Root`/`RootItem` 타입과 C# 특성의 역할도 검사한다. 긴 결과는 전체 높이로 렌더링하며 22개 PNG와 토큰 목록을 남긴다.

### 실행한 IDE의 Remote Robot 테스트

기존 `runIdeForUiTests` 설정을 사용해 별도 IC 2024.3 IDE에 플러그인을 설치하고, 테스트 프로젝트의 JSON 편집기에서 실제 등록된 변환 액션을 실행한다. `TypeConversionRobotTest`는 대화상자의 실제 언어 드롭다운과 두 탭을 전환한다.

- IDE 제공자 사용/PlainText 연결을 통한 내장 강조 강제 × Default/Darcula × 11개 언어를 검사한다.
- Kotlin/Java 네이티브 필드 강조가 모달 창에서 활성화되는지 검사한다. 일반 파일 에디터를 별도로 열어 미리보기의 강조를 대신 유발하지 않는다.
- 열린 에디터/문서를 유지한 상태에서 필드 색상을 변경하고 화면 픽셀에 반영되는지 검사한다. 네이티브 Kotlin/Java 키와 내장 `DEFAULT_INSTANCE_FIELD` 키를 구분한다.
- 스킴 전환 전후에 사용자 지정 편집기 글꼴이 유지되고 전역 설정이 덮어써지지 않는지 검사한다.
- 생성 코드를 `Type → JSON` 입력에 넣고 실제 변환 완료를 기다린다. 입력 에디터가 편집 가능하며 언어 강조를 유지하는지 검사한다.
- 종료 시 스킴과 파일 연결을 복구한다. 추가 라이브러리와 Robot 서버는 UI 테스트 전용이며 배포 플러그인의 의존성에 추가하지 않는다.

재현용 명령은 저장소 루트에서 다음과 같다. 첫 번째 명령은 IDE가 열린 동안 실행 상태를 유지하므로 별도 터미널에서 두 번째 명령을 실행한다.

```sh
./gradlew runIdeForUiTests
./gradlew uiTest
```

현재 실행에서는 기존 개발 IDE와 충돌하지 않도록 `build/reports/type-preview-highlighting/isolated-sandbox.gradle`의 sandboxContainer 설정을 첫 번째 명령에 적용했다. Robot은 기본 로컬 포트 8082를 사용하며 테스트 클라이언트 URL은 `-ProbotUrl=http://127.0.0.1:8082`로 지정할 수 있다. 창을 전면에 띄우므로 데스크톱 세션이 필요하다.

근거: [JetBrains Remote Robot의 실행·컴포넌트·화면 검사 API](https://github.com/JetBrains/intellij-ui-test-robot). 네이티브 Java 필드 키 `INSTANCE_FIELD_ATTRIBUTES`와 Kotlin의 `KOTLIN_INSTANCE_PROPERTY`는 사용 중인 SDK와 실제 강조 결과를 기준으로 검사한다.

## 결과와 제한

2026-09-27 최종 코드로 다음 검증을 완료했다.

- `./gradlew uiTest`: `BUILD SUCCESSFUL in 1m 42s`. 11개 언어 × 2개 스킴 × 2개 제공자 모드의 **44개 조합 모두 통과**했다. 각 조합에서 실제 미리보기, 사용자 지정 필드 색상의 화면 픽셀, 글꼴/문서 보존, Type → JSON 입력과 변환 완료를 확인했다. JUnit은 44개 조합을 순회하는 시나리오 1개이며 실패·오류·건너뜀 0이다. 최종 UI IDE 실행 로그의 ERROR/SEVERE는 0건이다.
- `./gradlew --init-script build/reports/type-preview-highlighting/isolated-sandbox.gradle compileKotlin test buildPlugin verifyPlugin`: `BUILD SUCCESSFUL in 6m 1s`. **194개 테스트/30개 클래스**, 실패·오류·건너뜀 0. 기존 변환 골든, 11개 강조 골든, 렉서 재시작, 사용자 색상, 글꼴 회귀, Platform UI 검증을 포함한다.
- Plugin Verifier 1.410: IC 243.28141.41, 251.29188.72, 252.28539.97 및 IU 253.33813.55, 261.27258.48, 262.10968.63, 263.5701.42 **7개 모두 Compatible**. 새 내부/Experimental API·호환성 문제 보고서는 없다. 263에서는 기존 `LocalizationBundle : DynamicBundle(String)` 사용 중단 예정 경고 1건이 남는다.
- ZIP `build/distributions/intellij-jsoninja-1.15.0.zip`: **32,464,354 bytes**, SHA-256 `be993d7ceff45a8181034001b64d0f1d9c742fba3985c8ccb0798889a7888b46`. 11개 강조 프로필을 포함하며 Robot/JUnit 라이브러리·테스트 클래스·골든 파일은 포함하지 않는다. WASM SHA-256은 이전 검증과 같은 `11654583577945aad6a280bfd06966c8142c19e33f7e5511c62e74d4a00af325`다.
- 수정 전 Java 화면과 실패 결과, 전역 글꼴 변경 재현의 실패 XML을 따로 보존했다. 중간 테스트 도구의 탐색·Java 17·색상 생성·스크롤 문제를 수정한 뒤 최종 코드로 전체 UI 조합을 다시 통과시켰다.
- 검증을 위해 띄운 IDE만 종료했다. 기존 WASM Gradle 작업의 configuration-cache 직렬화 경고는 남아 있으며 성공 종료와 구분한다.

산출물 위치:

- `build/reports/type-conversion-ui/`: Platform UI 렌더링과 토큰 목록.
- `build/reports/type-conversion-robot/`: 실제 대화상자 PNG, 입력/미리보기 토큰, 검증 조합별 `results.csv`, 수정 전 Java 화면.
- `build/reports/type-conversion-robot/index.html`: 언어/스킴/제공자별 화면을 선택하는 검증 갤러리.
- `build/reports/type-conversion-robot/final-verification.json`: 전체 테스트 수, ZIP/WASM 식별자와 포함 파일 검사.
- `build/reports/tests/uiTest/`: Remote Robot JUnit 결과.

네이티브 제공자의 실제 확인 범위는 테스트 IDE에 있는 Kotlin/Java다. 다른 언어는 내장 강조 경로로 확인하며, 별도 언어 플러그인을 설치하거나 제거했다고 표현하지 않는다. 기본 스킴이 타입명과 일반 텍스트에 같은 색을 부여하는 경우 그 설정을 유지한다. 동적 플러그인 언로드와 모든 JetBrains 제품의 실제 GUI 실행은 별도 검증 범위다.
