# JSON Ninja 개발 가이드

## 1. 개발 환경 설정

### 1.1 필수 요구사항
- JDK 17 이상
- Kotlin 2.1 이상
- IntelliJ IDEA (Community 또는 Ultimate)
- Gradle 8.0 이상

### 1.2 프로젝트 설정
1. 프로젝트 클론:
   ```bash
   git clone https://github.com/buYoung/intellij-jsoninja.git
   ```
2. IntelliJ IDEA에서 프로젝트 열기
3. Gradle 동기화 실행

## 2. 프로젝트 구조

```
intellij-jsoninja/
├── src/
│   ├── main/
│   │   ├── kotlin/com/livteam/jsoninja/
│   │   │   ├── actions/          # IDE 액션 (메뉴, 단축키 등)
│   │   │   ├── diff/             # JSON Diff 기능
│   │   │   ├── extensions/       # IDE 확장 포인트
│   │   │   ├── icons/            # 아이콘 리소스 연결
│   │   │   ├── listeners/        # 프로젝트/애플리케이션 리스너
│   │   │   ├── model/            # 데이터 모델 및 Enum
│   │   │   ├── services/         # 핵심 비즈니스 로직
│   │   │   ├── settings/         # 설정 관리
│   │   │   ├── ui/               # UI 컴포넌트 (ToolWindow, Dialog 등)
│   │   │   └── utils/            # 유틸리티 함수
│   │   ├── resources/            # 리소스 파일 (아이콘, 메시지 번들 등)
│   │   └── META-INF/             # 플러그인 설정 (plugin.xml)
│   └── test/                     # 테스트 코드
├── docs/                         # 문서
├── gradle/                       # Gradle 래퍼
└── build.gradle.kts             # Gradle 빌드 스크립트
```

## 3. 코딩 컨벤션

### 3.1 Kotlin 코딩 스타일
- Kotlin 공식 코딩 컨벤션 준수
- 들여쓰기: 4칸 공백
- 최대 줄 길이: 120자

### 3.2 명명 규칙
- 클래스: PascalCase
- 함수/변수: camelCase
- 상수: UPPER_SNAKE_CASE
- 패키지: lowerCamelCase
- 서비스 클래스: `*Service` (예: `JsonFormatterService`)
- 액션 클래스: `*Action` (예: `JsonPrettifyAction`)

## 4. 기능 구현 가이드

주요 로직은 `services` 패키지 내의 서비스 클래스들이 담당합니다. `Project` 레벨의 서비스로 등록되어 있습니다.

### 4.1 JSON Formatting (Prettify/Uglify)
`JsonFormatterService`를 사용하여 JSON을 포맷팅합니다.

```kotlin
val service = project.service<JsonFormatterService>()

// Prettify (기본 설정)
val prettyJson = service.formatJson(jsonString, JsonFormatState.PRETTIFY)

// Prettify (Compact Arrays)
val compactJson = service.formatJson(jsonString, JsonFormatState.PRETTIFY_COMPACT)

// Uglify (Minify)
val uglyJson = service.formatJson(jsonString, JsonFormatState.UGLIFY)
```

### 4.2 JSON Escape/Unescape
`JsonFormatterService`에서 이스케이프 처리를 담당합니다.

```kotlin
val service = project.service<JsonFormatterService>()

// Escape
val escapedJson = service.escapeJson(jsonString)

// Unescape
val unescapedJson = service.unescapeJson(escapedJsonString)
```

### 4.3 JSON Query (JMESPath/JsonPath)
`JsonQueryService`를 통해 JSON 데이터를 쿼리합니다. 쿼리 방식(Jayway/JMESPath)은 설정에 따릅니다.

```kotlin
val queryService = project.service<JsonQueryService>()
val result = queryService.query(jsonString, "$.store.book[*].author") // 결과 JSON 문자열 반환 (실패 시 null)
```

## 5. 테스트 작성 가이드

### 5.1 단위 테스트
- JUnit 4 사용
- 각 기능별 테스트 클래스 작성
- 테스트 케이스 명명: `should_ExpectedBehavior_When_StateUnderTest` 또는 한글 명명 허용

### 5.2 통합 테스트
- 플러그인 통합 테스트 프레임워크 사용
- `src/test` 디렉토리 내에 위치

## 6. 빌드 및 배포

### 6.1 로컬 빌드
```bash
./gradlew build
```

### 6.2 플러그인 실행 (샌드박스)
```bash
./gradlew runIde
```

### 6.3 배포용 빌드
```bash
./gradlew buildPlugin
```
생성된 플러그인 파일은 `build/distributions/` 디렉토리에서 확인할 수 있습니다.

## 7. 문제 해결

### 7.1 일반적인 문제
- Gradle 동기화 실패: `./gradlew deepClean` 실행 후 재시도
- IDE 버전 호환성 문제: `gradle.properties`의 `platformVersion` 확인

### 7.2 디버깅
- IDE 로그 확인: Help > Show Log in Explorer
- 디버그 모드로 플러그인 실행: `./gradlew runIde --debug-jvm`
