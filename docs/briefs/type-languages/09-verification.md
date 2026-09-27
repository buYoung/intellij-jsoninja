# 언어 확장 브리프셋 검증 기록

사용자 화면에서 드러난 기존 Kotlin 미리보기의 추가 강조 누락은 후속으로 수정했다. 원인, 네이티브 강조 골든과 최신 검증 결과는 [미리보기 강조 복구 기록](10-preview-highlighting-fix.md)을 따른다. 아래 수치는 첫 브리프셋 통합 시점의 기록이다.

## 대상과 수행 범위

- 기준: `519470b4482c7e0af8a07d450f092b5bca669384`와 현재 작업 트리의 추적·미추적 변경 전체. 커밋·게시하지 않았다.
- 작업 디렉터리: `/Users/buyong/.codex/worktrees/c8e7/json-helper2`.
- 메인 에이전트에서 전체 변경을 한 차례 상세 검토하고, 발견한 문제를 수정한 후 관련 검증을 재실행했다. 하위 에이전트를 사용하지 않았다.
- 원본 9개 브리프 복사본에 강조 규칙을 추가했고, 사용자의 최신 지시에 따라 기존 테스트 추가 제한을 해제하여 골든·단위·통합 검증을 구성했다.

## 구현 범위

| 언어 | 저장 값 / WASM ID | 확장자 | 생성 기준 |
| --- | --- | --- | --- |
| C | C / 4 | c | C11 구조체, 표준 정수, 표시된 컬렉션 보조 타입 |
| C++ | CPP / 5 | cpp | C++17 구조체와 표준 컬렉션·optional·variant |
| C# | CSHARP / 6 | cs | C# 8 클래스, nullable, 선택 가능한 JsonPropertyName |
| Python | PYTHON / 7 | py | Python 3.11 TypedDict, 원래 키와 NotRequired 보존 |
| Rust | RUST / 8 | rs | Rust 2021 소유 타입, Vec/HashMap/Option, 독립적인 값 보조 enum |
| Scala | SCALA / 9 | scala | Scala 2.13/3 공통 case class, 별칭의 object 범위 |
| JSDoc | JSDOC / 10 | js | 실제 JavaScript 문서 주석의 typedef/property |

원래 네 언어의 ID·저장 값·출력 골든을 보존했다. 공통 추론은 언어별 정책/렌더러 레지스트리에 위임하며, 언어 구문은 각 모듈 안에 둔다. 새 문법 의존성 이외에 강조용 의존성·서버·PSI·WASM 호출·전역 파일 연결·색상 설정 페이지를 추가하지 않았다.

## 강조 및 결과 전달

- 변환 입력과 타입 미리보기에서 사용 가능한 네이티브 제공자를 우선한다. JSDoc은 문서 태그/값의 색상 키를 구분할 수 있어야 한다.
- 내장 강조기는 IDE의 `DefaultLanguageHighlighterColors` 키를 직접 반환한다. 네이티브는 해당 언어 설정, 내장은 Language Defaults 사용자 색상을 따른다. 운영 코드에 RGB 팔레트를 넣지 않았다.
- 플랫폼 `EditorColorsListener`의 컴포넌트 트리 전달로 열린 필드의 스킴과 같은 스킴 내 변경을 갱신한다. 문서·커서·선택·undo 및 해제를 검사했다.
- 원래 확장자는 메타데이터 → 두 presenter → 현재 미리보기 → `consumeCurrentPreview` → `ConvertResultUtils` → 새 탭의 `JsonTabContextFactory` 또는 기존 편집기 쓰기까지 전달된다. 마지막 탭/클립보드 동작은 소스 추적으로 확인했으며 실제 화면 조작 성공으로 기록하지 않았다.
- JSON과 경고는 하나의 결과로 취소/순번 검사를 통과한 후 적용한다. 경고 패널은 복사·삽입할 JSON 본문에 포함되지 않는다.

## 검토에서 수정한 결함

| 항목 | 실제 영향 | 수정 및 검증 |
| --- | --- | --- |
| C 자동 루트 선택 | 참조된 컬렉션 보조 별칭이 객체 루트보다 먼저 선택될 수 있음 | C 경계에서 보조 참조를 컬렉션 IR로 확장하고 보조 선언을 제거. 일반 구조체 오인 방지·순환 제한·일곱 언어 기본 루트 검증 |
| 빈 JSDoc 주석 | `/**/`의 잘못된 부분 문자열 범위로 분석 실패 가능 | 최소 길이 검사와 빈 주석 회귀 테스트 |
| JSDoc 속성 경로 깊이 | 매우 긴 경로의 재귀 깊이가 제한되지 않음 | 최대 64개 경로 요소, 초과 시 명시적 오류 |
| C# 중첩 별칭 | 별칭 내부의 짧은 컬렉션 이름은 같은 범위 using을 참조할 수 없음 | 내부 타입까지 완전한 이름 사용, nullable 요소 보존. [C# 언어 규격](https://learn.microsoft.com/en-us/dotnet/csharp/language-reference/language-specification/namespaces#1462-using-alias-directives)에 근거 |
| 생성 타입 이름 충돌 | 언어의 내장/임포트 타입 또는 속성 이름을 가릴 수 있음 | 새 언어 정책에서 충돌 이름을 피하고 필드/보조 이름도 검증 |
| C# 문자열 줄 구분 문자 | 속성명의 Unicode 줄 구분 문자가 원문 문자열에 들어갈 수 있음 | 공통 리터럴 이스케이프 재사용과 회귀 테스트 |
| 숫자 enum의 잘못된 접미사 | 지원하지 않는 리터럴을 정상 숫자로 오인할 수 있음 | 언어별 접미사·구분자·정밀도 한계 검증. 표현식은 실행하지 않음 |

## 자동 검증 결과

최종 전체 실행 `./gradlew compileKotlin test buildPlugin`: 178개 테스트, 27개 테스트 클래스, 실패·오류·건너뜀 0. WASM을 실제 재빌드하여 C 기본 루트 수정까지 포함했다. 이 중 변환·강조·미리보기 관련 검증은 53개다.

마지막 C# `System` 이름 충돌 항목을 보완한 뒤 `./gradlew compileKotlin test --tests 'com.livteam.jsoninja.services.typeConversion.TypeLanguagePolicyTest' buildPlugin`도 성공했다. 정책 테스트 5개 통과, 실패·오류·건너뜀 0이며 최종 ZIP은 이 빌드의 결과다.

전체 결과 XML과 요약은 `build/reports/type-languages-2026-09-27/full-test-results/`, `full-test-summary.json`에 보존했다. 일반 Gradle test 보고서는 마지막 집중 실행으로 갱신될 수 있다.

성공으로 확정한 개별 단계:

- `cargo test --offline --manifest-path tree-sitter-wasm/Cargo.toml`: 37개 통과, 실패·무시 0. 새 언어 의미 골든, 실제 쿼리, 소스 위치, 잘못된 입력과 보조 타입 경계를 포함한다.
- 언어별 단계에서 `./gradlew compileKotlin buildTreeSitterWasm copyWasmToResources` 성공. `-PskipWasmBuild=true`를 사용하지 않았다.
- JSDoc 연결 직후의 변환/강조 집중 검증 42개 통과. 이후 정책·비동기·모든 언어 편집기·자동 루트 사례를 추가했다.
- C11 `clang -std=c11 -fsyntax-only src/test/resources/typeConversion/golden/object.c` 통과.
- C++17 `clang++ -std=c++17 -fsyntax-only src/test/resources/typeConversion/golden/object.cpp` 통과.
- Rust 2021 `rustc --edition=2021 --crate-type=lib --emit=metadata -o /private/tmp/jsoninja-object.rmeta src/test/resources/typeConversion/golden/object.rs` 통과.
- Python 골든을 `ast.parse(..., feature_version=(3, 11))`와 `compile(...)`로 검사했다. 제출한 사용자 코드나 생성 모듈을 실행하지 않았다.
- 11개 언어의 crate/플러그인 쿼리 사본 일치. v3 아이콘 22개 존재. 새 키는 다섯 번들에 각각 한 번 존재하며, 기존 키의 기본 번들 상속도 확인했다.
- `git diff --check` 통과.

골든은 기대 결과를 별도 파일로 유지한다. 렌더러 11개 소스 골든과 새 언어 7개 분석 골든을 비교하고, 실제 번들 WASM에서 각 새 언어의 `parser_create → tree_parse → tree_query` 및 JSON 생성까지 검증한다. 렉서 테스트는 전체 스캔·저장 상태 재시작·모든 잘린 끝 위치·불완전 토큰·UTF-16·취소를 확인한다.

## 실패와 재실행 이력

- 원래 출력의 `address → RootAddres` 이름을 최초 골든 수립 전에 확인하여 기대값을 바로잡았다. 원래 네 언어 골든은 추출 이후 바꾸지 않았다.
- 초기 테스트에서 네이티브 C 제공자 부재를 잘못 가정한 부분과 플랫폼의 지연된 편집기 해제를 바로잡았다. 네이티브 우선과 강제 부재 시 대체 경로를 각각 검증한다.
- 새 정책 테스트의 필수 옵션 누락은 테스트 컴파일 오류였다. 언어별 기본값을 명시했다.
- 전체 176개 실행에서 비동기 테스트의 객체 동일성 단정 한 건이 실패했다. 코루틴의 스택 복원 후 예외 종류·메시지·원래 원인이 유지되는 계약으로 수정했다. 취소가 오류 UI로 바뀌지 않는 단정은 유지했다.
- Cargo 네트워크 실행은 샌드박스 DNS 제한으로 실패했으며, 캐시된 고정 버전으로 `--offline` 실행하여 통과했다.
- 기존 Gradle WASM 작업의 configuration-cache 직렬화 경고는 기준 버전에도 존재한다. 캐시를 폐기하며 검증 종료 코드는 별도로 확인한다.

## 의존성 및 배포물

실제 해결 버전: tree-sitter 0.25.10, C 0.24.2, C++ 0.23.4, C# 0.23.5, Python 0.25.0, Rust 0.24.2, Scala 0.26.2, JavaScript 0.25.0. 새 문법은 정확한 버전으로 고정했고, 기존 Cargo.lock 무시 정책을 유지했다. 각 새 문법의 MIT 고지 7개를 배포 리소스에 포함했다. C++ 0.23.4 고지는 같은 태그의 업스트림 파일을 사용했다.

- ZIP: `build/distributions/intellij-jsoninja-1.15.0.zip`, 32,434,009 bytes.
- ZIP SHA-256: `4e3a63e1d6ba7dd507cc5e3d4969ecfcc85ea2776608c13c21f89c4853ceec92`.
- WASM: 22,743,570 bytes; SHA-256 `11654583577945aad6a280bfd06966c8142c19e33f7e5511c62e74d4a00af325`.
- Rust 빌드 산출물 = 리소스 사본 = ZIP 안 구현 JAR의 WASM 바이트. ZIP의 11개 쿼리는 소스 사본과 같고, 7개 라이선스 고지가 포함되어 있다.
- `build/reports/type-languages-2026-09-27/artifact-integrity.json`에 기계 판독용 결과도 보존했다.

## Plugin Verifier

`./gradlew verifyPlugin`: 종료 코드 0, IntelliJ Plugin Verifier 1.410, 7개 대상 모두 호환. 실제 보고서의 verdict와 API 상태 파일까지 확인했다. 플러그인 클래스 548개가 검사되었으며, 호환성 오류·내부 API·Experimental API 문제 파일은 생성되지 않았다.

| 검증 IDE 빌드 | 결과 |
| --- | --- |
| IC-243.28141.41 | Compatible |
| IC-251.29188.72 | Compatible |
| IC-252.28539.97 | Compatible |
| IU-253.33813.55 | Compatible |
| IU-261.27258.48 | Compatible |
| IU-262.10968.63 | Compatible |
| IU-263.5701.42 | Compatible. 1 usage of deprecated API |

263 대상의 사용 중단 예정 API 1건은 기준 버전에도 그대로 있는 `LocalizationBundle : DynamicBundle(String)` 생성자 호출이다. 해당 소스는 이번 작업에서 변경하지 않았다. 무관한 현지화 API 이관으로 범위를 넓히지 않았다.

IDE 레이아웃의 일부 classPath 항목이 없다는 로더 경고도 출력됐지만, 7개 검증은 모두 완료됐다. 실제 결과는 `build/reports/pluginVerifier/<IDE>/plugins/com.livteam.jsoninja/1.15.0/`에 있다. 정적 도구의 “동적 활성화/비활성화가 가능할 것”이라는 판정은 실제 언로드 관찰 성공으로 기록하지 않는다.

기존 recommended 설정의 가장 낮은 대상은 IC-243.28141.41이다. 컴파일과 실제 편집기/플랫폼 테스트는 더 이른 IC-243.21565.193 SDK에서 수행했다. 두 확인 범위를 구분하며 다른 IDE 제품의 추가 언어 플러그인 조합까지 검증했다고 주장하지 않는다.

## 검증 한계

- `runIde`로 IC 243.21565.193 샌드박스를 시작했지만, 화면 자동화가 별도의 Java Main 앱에 연결하지 못했다. 사용자의 다른 체크아웃을 연 설치 IDE는 검증 대상으로 조작하지 않았다. 실제 밝은/어두운 UI 테마 전환, 클립보드·삽입 버튼 클릭 및 동적 플러그인 언로드는 미확인이다.
- 테스트의 밝고 어두운 편집기 스킴·같은 스킴 Apply·해제 검증은 실제 Editor 인스턴스를 사용한다. 이를 수동 화면 관찰이나 플러그인 언로드 검증으로 바꾸어 표현하지 않는다.
- 네이티브 제공자 검증은 현재 IC에 있는 C 제공자로 수행했다. 별도 C#/Python/Rust/Scala/JavaScript 플러그인을 설치한 IDE 조합은 미확인이다.
- 로컬에 dotnet/csc/mcs/scalac가 없어 C#·Scala 외부 컴파일은 미확인이다. 소스 골든·문법 분석·실제 WASM 변환은 검증했다.
- 타입에서 JSON을 만드는 기능은 샘플 생성이다. 매크로·사용자 코드·동적 타입 식·런타임 기본값을 실행하지 않으며, 지원 밖 의미와 손실은 진단으로 노출한다.

## 완료 상태

구현, 메인 에이전트의 전체 상세 검토, 골든·단위·통합 테스트, 배포 ZIP 일치 및 7개 IDE 정적 호환성 검증을 완료했다. 검토 중 확인된 범위 내 결함은 수정하고 재검증했으며 남겨 둔 확정 결함은 없다. 수동 화면 검증이 필요한 하위 브리프의 최종 `ready` 표시는 보류한다.
