# JSON Helper 2 개발 가이드

## 1. 개발 환경 설정

### 1.1 필수 요구사항
- JDK 17 이상
- Kotlin 1.8 이상
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
│   │   ├── kotlin/           # 소스 코드
│   │   ├── resources/        # 리소스 파일
│   │   └── META-INF/         # 플러그인 설정
│   └── test/                 # 테스트 코드
├── docs/                     # 문서
├── gradle/                   # Gradle 래퍼
└── build.gradle.kts         # Gradle 빌드 스크립트
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

## 4. 기능 구현 가이드

### 4.1 JSON Prettify
```kotlin
class JsonPrettifier {
    fun prettify(json: String): String {
        // 1. JSON 파싱
        // 2. 들여쓰기 적용
        // 3. 포맷팅된 문자열 반환
    }
}
```

### 4.2 JSON Uglify
```kotlin
class JsonUglifier {
    fun uglify(json: String): String {
        // 1. JSON 파싱
        // 2. 공백 제거
        // 3. 한 줄로 변환
    }
}
```

### 4.3 JSON Escape/Unescape
```kotlin
class JsonEscapeProcessor {
    fun escape(json: String): String {
        // 특수 문자 이스케이프 처리
    }

    fun unescape(json: String): String {
        // 이스케이프된 문자 복원
    }
}
```

## 5. 테스트 작성 가이드

### 5.1 단위 테스트
- JUnit 5 사용
- 각 기능별 테스트 클래스 작성
- 테스트 케이스 명명: should_ExpectedBehavior_When_StateUnderTest

### 5.2 통합 테스트
- 플러그인 통합 테스트 프레임워크 사용
- UI 요소 테스트
- 실제 IDE 환경에서의 동작 테스트

## 6. 빌드 및 배포

### 6.1 로컬 빌드
```bash
./gradlew build
```

### 6.2 플러그인 실행
```bash
./gradlew runIde
```

### 6.3 배포용 빌드
```bash
./gradlew buildPlugin
```

## 7. 문제 해결

### 7.1 일반적인 문제
- Gradle 동기화 실패: Gradle 캐시 삭제 후 재시도
- IDE 버전 호환성 문제: build.gradle.kts의 IDE 버전 확인

### 7.2 디버깅
- IDE 로그 확인: Help > Show Log in Explorer
- 디버그 모드로 플러그인 실행: ./gradlew runIde --debug-jvm
