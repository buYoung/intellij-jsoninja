# PR #199의 CI 테스트 실행 경계와 줄바꿈 수정

## 확인한 실패

PR의 원래 head는 `dc049ea6620eafa5307776033c39283e8f465f05`이며, Actions는 main과 합친 `5469cdedb38f579b380b93d0cb18bd8696d679f1`을 검사했다. 이 통합 상태에는 기존 로컬 검사보다 테스트가 추가되어 Windows 작업은 212개 테스트를 실행했다.

- [Build의 Compile, test, and package](https://github.com/buYoung/intellij-jsoninja/actions/runs/36308977242/job/108591094929): `check`가 Kover 보고서 생성을 통해 `uiTest`까지 실행했다. 별도 IDE를 먼저 띄워야 하는 `TypeConversionRobotTest`가 실행 중인 Robot 서버에 연결하지 못해 `java.net.ConnectException`으로 실패했다. 후속 호환성 검증 건너뜀과 Build and verify 실패는 이 실패의 결과다.
- [Windows IDE 통합 테스트](https://github.com/buYoung/intellij-jsoninja/actions/runs/36308977244/job/108591264098): `TypeLanguageRendererGoldenTest`, `TypeCodeEditorIntegrationTest`, `TypeCodePreviewHighlightingTest`의 비교 3개가 실패했다. 다운로드한 JUnit XML에서 기대값의 CRLF와 생성 결과·IntelliJ 문서의 LF 차이를 확인했다. 같은 실행의 Ubuntu와 macOS 통합 테스트는 통과했다.

기존 로컬 검증은 `test`를 실행하여 `check`가 추가로 호출하는 작업을 놓쳤고, macOS 환경으로 Windows 체크아웃 변환을 확인하지 못했다.

## 수정

1. Kover 0.9.8의 `currentProject.instrumentation.disabledForTestTasks`에서 `uiTest`를 제외한다. `currentProject.sources.excludedSourceSets`에서도 UI 테스트 클라이언트를 제외하여 운영 코드의 커버리지에 섞이지 않게 한다. 일반 Platform 테스트와 커버리지 검증은 유지하며, `./gradlew uiTest`는 명시적으로 실행할 수 있다. 이 테스트는 별도 IDE 프로세스에서 실제 UI를 검사하므로 Gradle 클라이언트 JVM의 커버리지 대상과 구분한다.
2. `.gitattributes`에서 `src/test/resources/typeConversion/**`를 `text eol=lf`로 지정한다. 골든 기대값이나 실제 결과를 느슨하게 정규화하지 않고, 모든 OS에서 동일한 파일 바이트를 사용한다. 런타임 강조·변환 코드는 변경하지 않는다.

## 검증

- 변경 전/후 `./gradlew check --dry-run --no-configuration-cache` 비교: 변경 전 `uiTest` 호출을 확인했고, 변경 후 해당 호출이 없으며 `test`, `koverXmlReport`, `koverVerify`, `check`가 유지된다.
- `./gradlew uiTest --dry-run --no-configuration-cache`: 별도 UI 테스트 작업을 명시적으로 실행할 수 있음을 확인했다.
- `git -c core.autocrlf=true checkout-index`로 Windows 자동 변환을 재현했다. 수정 전 골든 24개 모두 CRLF로 변환됐으며, 수정 후 24개 모두 LF를 유지하고 원본과 바이트가 같았다.
- `./gradlew --init-script build/reports/type-preview-highlighting/isolated-sandbox.gradle cleanTest compileKotlin check buildPlugin --no-build-cache --no-configuration-cache`: `BUILD SUCCESSFUL in 3m 59s`. 로컬 브랜치의 194개 테스트/30개 클래스가 실패·오류·건너뜀 없이 새로 통과했다. `koverXmlReport`, `koverVerify`, `check`, 패키징까지 성공했고 `uiTest`는 호출되지 않았다. 커버리지 XML에 운영 코드 클래스가 포함되고 Remote Robot 클라이언트가 포함되지 않는 것도 확인했다. 별도 샌드박스 경로만 사용하며 CI와 같은 검증 작업·옵션을 적용했다.

로컬 증거는 `build/reports/pr-199-ci/`에 저장한다. 실제 Windows 실행과 통합 커밋의 최종 결과는 [PR 검사 목록](https://github.com/buYoung/intellij-jsoninja/pull/199/checks)에서 별도로 확인한다. 작업 그래프의 `SKIPPED` 표시는 `--dry-run`의 결과이며 실제 테스트 성공으로 세지 않는다.
