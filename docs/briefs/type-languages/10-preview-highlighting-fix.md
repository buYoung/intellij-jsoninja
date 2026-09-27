# 읽기 전용 타입 미리보기의 네이티브 강조 복구

이 문서는 네이티브 미리보기 복구 시점의 기록이다. 이후 플러그인 없는 동작을 보완한 최신 구현·검증·배포물은 [11개 언어 내장 강조 기록](11-bundled-highlighting.md)을 따른다.

## 보고된 현상과 원인

사용자가 제공한 Kotlin 화면에서 `class`, `val`은 렉서 색상이 적용되지만 `data`, 클래스·속성·타입명은 추가 강조가 빠져 있었다. `CodePreviewPanel`은 PSI 문서를 이미 사용하지만 `EditorTextField`의 viewer 생성 과정이 `DaemonCodeAnalyzer.setHighlightingEnabled(file, false)`를 호출한다. 이전 검증은 새 일곱 언어의 렉서와 네이티브 C 제공자를 확인했고, Kotlin의 읽기 전용 PSI 강조는 놓쳤다.

최소 SDK IC 243.21565.193의 `EditorTextField.createEditor()`와 실제 편집기 통합 테스트에서 이 비활성 상태를 확인했다. 수정 전 회귀 테스트는 `Read-only previews must retain native PSI highlighting` 단정에서 실패했다. 결과는 `build/reports/type-preview-highlighting/before-fix.xml`에 보존한다.

## 수정 범위

- 지원 타입 언어의 미리보기 생성 직후 기존 PSI 문서의 네이티브 강조를 활성화한다. viewer 상태와 기존 렉서·네이티브 제공자 선택은 유지한다.
- 임시 미리보기 파일에만 표시를 붙여 `DefaultHighlightingSettingProvider`로 inspections를 생략한다. 일반 파일과 JSON 미리보기의 정책은 바꾸지 않는다.
- Java 실험에서 inspections를 생략해도 프로젝트에 없는 타입의 오류 표시가 남는 것을 재현했다. 표시된 미리보기 파일의 문제 진단만 `HighlightInfoFilter`로 제외하고, 언어가 제공한 색상 정보는 통과시킨다. 일반 편집기의 오류는 계속 표시된다.
- 클래스·속성·타입별 색상은 IDE 언어 플러그인의 `TextAttributesKey`를 그대로 사용한다. RGB 팔레트, 수동 이름 추측, 추가 파서, 별도 분석 루프를 도입하지 않는다.
- Kotlin/Java는 `testBundledPlugins`로 테스트에만 추가한다. 배포 플러그인의 필수 의존성을 추가하지 않는다.

## 회귀 검증 구성

`TypeCodePreviewHighlightingTest`는 실제 `CodePreviewPanel`이 소유한 문서와 편집기를 검사한다. 테스트 프레임워크가 같은 문서에 네이티브 분석을 실행한 뒤 미리보기의 `filteredDocumentMarkupModel`에서 최종 색상 정보를 읽는다.

1. 스크린샷과 같은 Kotlin 소스의 13개 강조 범위·문자열·네이티브 색상 키를 `src/test/resources/typeConversion/highlighting/kotlin-preview.txt` 골든과 비교한다.
2. 밝고 어두운 편집기 스킴과 같은 스킴의 사용자 색상 변경을 적용하고, 기존 미리보기의 속성 색상·배경·선택·문서·읽기 전용 상태를 확인한다. 새 미리보기 텍스트에서도 강조를 갱신한다.
3. Java 미리보기의 외부 타입 오류는 표시하지 않으며 일반 Java 편집기의 동일 오류는 유지되는지 확인한다. 공통 factory의 일반 viewer와 JSON 미리보기에는 새 정책을 적용하지 않는다.

## 공개 API 근거

- IC 2024.3 sources JAR의 `DaemonCodeAnalyzer.setHighlightingEnabled`, `DefaultHighlightingSettingProvider.getDefaultSetting`, `FileHighlightingSetting.SKIP_INSPECTION`, `HighlightInfoFilter.accept`를 확인했다. 해당 호출과 구현 계약에 Internal/Experimental 제한이 없으며, `HighlightInfo`는 생성·상속 없이 공개 severity만 읽는다.
- 최소 SDK의 `META-INF/LangExtensionPoints.xml`에서 `defaultHighlightingSettingProvider`, `daemon.highlightInfoFilter`의 정확한 이름과 `dynamic="true"` 선언을 확인했다.
- [JetBrains 공식 확장점 목록](https://plugins.jetbrains.com/docs/intellij/intellij-platform-extension-point-list.html)은 기본 강조 설정 제공자를 공개 확장점으로 나열한다. [Controlling Highlighting](https://plugins.jetbrains.com/docs/intellij/controlling-highlighting.html)은 프로젝트 문맥이 다른 코드에서 불필요한 진단을 걸러 내는 `HighlightInfoFilter` 사용을 안내한다.
- IntelliJ Platform Gradle Plugin 2.11.0 sources JAR에서 `testBundledPlugins`가 테스트 전용 configuration을 사용하는 것을 확인했다. [공식 의존성 문서](https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin-dependencies-extension.html)의 테스트 범위 설명과 일치한다.

## 실행 결과와 한계

- 작업 디렉터리: `/Users/buyong/.codex/worktrees/c8e7/json-helper2`.
- `./gradlew compileKotlin test buildPlugin`: 컴파일과 전체 테스트 181개/28개 클래스 통과, 실패·오류·건너뜀 0. 뒤이은 `buildSearchableOptions`는 이미 실행 중인 샌드박스 IDE의 잠금과 충돌하여 전체 명령은 종료 코드 1이었다. 이를 패키징 성공으로 기록하지 않는다.
- 전체 테스트 XML과 합계는 `build/reports/type-preview-highlighting/full-test-results/`와 `full-test-summary.json`에 보존했다. 새 일곱 언어의 기존 렉서/편집기 검사와 변환 골든·WASM 통합도 함께 통과했다.
- `./gradlew --init-script build/reports/type-preview-highlighting/isolated-sandbox.gradle buildPlugin verifyPlugin`: 종료 코드 0, `BUILD SUCCESSFUL in 3m 5s`. 검색 설정 생성도 생략하지 않고 성공했다. 이 임시 init script는 이번 실행의 `sandboxContainer`만 `build/idea-sandbox-preview-verification`으로 지정하며 프로젝트 설정에는 추가하지 않는다. 실행 중인 사용자 IDE를 종료하거나 잠금 파일을 제거하지 않았다.
- 새 ZIP을 대상으로 IC 243.28141.41, 251.29188.72, 252.28539.97 및 IU 253.33813.55, 261.27258.48, 262.10968.63, 263.5701.42 일곱 빌드 모두 `Compatible`. 내부 API·Experimental API·호환성 문제 보고서가 없으며, 263에서 기존 `LocalizationBundle : DynamicBundle(String)` 사용 중단 예정 경고 한 건만 유지된다. 실제 verdict/API 보고서와 `build/reports/type-preview-highlighting/compatibility-summary.json`을 확인했다.
- 최종 ZIP: `build/distributions/intellij-jsoninja-1.15.0.zip`, 32,439,463 bytes, SHA-256 `d1cc4def45affa37cda82861b3c9aa09d75be6bb4e5287e9dcd7dd269f2a839f`. 수정한 미리보기 클래스 3개가 포함되어 있고 테스트 클래스·필수 Kotlin/Java 의존성은 포함되지 않는다. 번들 WASM은 이전 검증과 같은 바이트다. `artifact-integrity.json`에 기록했다.
- `git diff --check`와 `plugin.xml` 파싱·확장점 등록 확인도 통과했다. 기존 WASM Gradle 작업의 configuration-cache 직렬화 경고는 남아 있으며, 위 성공 여부와 구분한다.

실제 사용자의 설치 IDE에 적용하거나 화면을 직접 조작한 것은 아니며, 자동 검증의 네이티브 언어 대상은 현재 테스트 SDK의 Kotlin과 Java다. 다른 선택적 언어 플러그인의 의미 강조 결과까지 동일하다고 단정하지 않는다. 동적 언로드와 실제 화면 테마 전환은 이전 검증 기록과 같이 미확인이다.
