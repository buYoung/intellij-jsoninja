# JSONinja 개발 가이드

이 문서는 개발 환경 설정, 코딩 컨벤션, 기능 구현, 빌드/배포, 문제 해결을 다룹니다.
디렉토리/패키지 구조와 주요 컴포넌트 목록은 [PROJECT_STRUCTURE.md](./PROJECT_STRUCTURE.md)를 참고하세요.

## 1. 개발 환경 설정

### 1.1 필수 요구사항
- JDK 17 (Gradle `jvmToolchain(17)` 기준)
- IntelliJ IDEA (Community 또는 Ultimate)
- IntelliJ Platform 2024.3 (`IC`), 지원 빌드 범위 `243` ~ `263.*`

> Kotlin(2.3.x)과 Gradle(8.14.x)은 별도 설치가 필요 없습니다. Kotlin은 IntelliJ Platform Gradle Plugin이 관리하며, Gradle은 프로젝트에 포함된 래퍼(`./gradlew`)를 사용합니다. 정확한 버전은 `gradle.properties`와 `gradle/libs.versions.toml`에서 확인하세요.

### 1.2 프로젝트 설정
1. 프로젝트 클론:
   ```bash
   git clone https://github.com/buYoung/intellij-jsoninja.git
   ```
2. IntelliJ IDEA에서 프로젝트 열기
3. Gradle 동기화 실행

## 2. 코딩 컨벤션

### 2.1 Kotlin 코딩 스타일
- Kotlin 공식 코딩 컨벤션 준수
- 들여쓰기: 4칸 공백
- 최대 줄 길이: 120자

### 2.2 명명 규칙
- 클래스: PascalCase
- 함수/변수: camelCase
- 상수: UPPER_SNAKE_CASE
- 패키지: 소문자
- 서비스 클래스: `*Service` (예: `JsonFormatterService`)
- 액션 클래스: `*Action` (예: `PrettifyJsonAction`)
- Presenter/View 클래스: `*Presenter` / `*View`

### 2.3 스레딩
- EDT(UI 스레드)와 백그라운드 스레드 사용 규칙, 코루틴 스코프 활용 등 스레딩 표준은 [coroutine-threading-standard.md](./coroutine-threading-standard.md)를 따릅니다.
- 플러그인 전용 코루틴은 `JsoninjaCoroutineScopeService`의 스코프를 사용합니다.

## 3. 기능 구현 가이드

주요 로직은 `services` 패키지의 서비스 클래스가 담당하며, 대부분 `Project` 레벨 서비스로 등록되어 있습니다.

### 3.1 JSON Formatting (Prettify/Uglify)
`JsonFormatterService`를 사용하여 JSON을 포맷팅합니다. 포맷 종류는 `JsonFormatState`로 지정합니다(`PRETTIFY`, `PRETTIFY_SORTED`, `PRETTIFY_COMPACT`, `UGLIFY`).

```kotlin
val service = project.service<JsonFormatterService>()

// Prettify (기본 설정)
val prettyJson = service.formatJson(jsonString, JsonFormatState.PRETTIFY)

// Prettify (키 정렬)
val sortedJson = service.formatJson(jsonString, JsonFormatState.PRETTIFY_SORTED)

// Prettify (Compact Arrays)
val compactJson = service.formatJson(jsonString, JsonFormatState.PRETTIFY_COMPACT)

// Uglify (Minify)
val uglyJson = service.formatJson(jsonString, JsonFormatState.UGLIFY)
```

### 3.2 JSON Escape/Unescape
`JsonFormatterService`에서 이스케이프 처리를 담당합니다.

```kotlin
val service = project.service<JsonFormatterService>()

// Escape
val escapedJson = service.escapeJson(jsonString)

// Unescape
val unescapedJson = service.unescapeJson(escapedJsonString)
```

### 3.3 JSON Query (JsonPath/JMESPath/jq)
`JsonQueryService`를 통해 JSON 데이터를 쿼리합니다. 쿼리 방식(`JsonQueryType`: `JAYWAY_JSONPATH`, `JMESPATH`, `JACKSON_JQ`)은 설정에 따릅니다.

```kotlin
val queryService = project.service<JsonQueryService>()
// 결과 JSON 문자열 반환 (실패 시 null)
val result = queryService.query(jsonString, "$.store.book[*].author")
```

### 3.4 타입 변환 (JSON ↔ 타입 코드)
타입 변환은 `services/typeConversion` 패키지가 담당합니다. tree-sitter WASM 런타임(`services/treesitter`)으로 타입 선언을 파싱하며, 지원 언어는 `model/SupportedLanguage`로 정의됩니다.

- JSON → 타입 코드: `JsonToTypeConversionService`
- 타입 코드 → JSON: `TypeToJsonGenerationService`

### 3.5 JSON Schema 기반 생성/검증
`services/schema` 패키지가 JSON Schema 검증(`JsonSchemaValidationService`)과 스키마 기반 데이터 생성(`JsonSchemaDataGenerationService`)을 담당합니다. 랜덤 데이터 생성은 `RandomJsonDataCreator`를 사용합니다.

## 4. 테스트 작성 가이드

- IntelliJ Platform 테스트 프레임워크를 사용합니다. 테스트 클래스는 `BasePlatformTestCase`를 상속합니다.
- 테스트 러너는 JUnit 4 입니다.
- 테스트 코드는 `src/test/kotlin` 아래에 메인 패키지와 동일한 구조로 배치합니다.
- 테스트 케이스 명명: `should_ExpectedBehavior_When_StateUnderTest` 또는 한글 명명 허용
- 커버리지는 Kover 플러그인으로 측정합니다.

## 5. 빌드 및 배포

### 5.1 로컬 빌드
```bash
./gradlew build
```

### 5.2 플러그인 실행 (샌드박스)
```bash
./gradlew runIde
```

### 5.3 배포용 빌드
```bash
./gradlew buildPlugin
```
생성된 플러그인 파일은 `build/distributions/` 디렉토리에서 확인할 수 있습니다.

> 타입 변환 기능에 필요한 tree-sitter WASM 바이너리는 빌드 시 `processResources` 단계에서 준비됩니다(`checkWasmPrerequisites`, `buildTreeSitterWasm`, `copyWasmToResources` 태스크). WASM 빌드 도구가 없는 환경에서는 해당 태스크의 사전 요구사항 안내를 확인하세요.

## 6. 문제 해결

### 6.1 일반적인 문제
- Gradle 동기화 실패: `./gradlew deepClean --no-daemon` 실행 후 재시도
- IDE 버전 호환성 문제: `gradle.properties`의 `platformVersion` 및 `pluginSinceBuild`/`pluginUntilBuild` 확인

### 6.2 디버깅
- IDE 로그 확인: Help > Show Log in Explorer
- 디버그 모드로 플러그인 실행: `./gradlew runIde --debug-jvm`
