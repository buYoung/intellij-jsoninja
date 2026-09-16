# 02 — JSON 숫자와 JSON5 입력 검증 기록

2026-09-16, 감사 항목 R1.5/R2.10. 상태: **ready**. 서비스 호출로 값 보존을 관찰했으며 네이티브 UI 조작은 수행하지 않았다.

## Baseline

- 기준 HEAD: `4c6efa8df9704db0c4032d6a21d5caea5883e603`. 시작 시 이 작업의 서비스 파일은 변경되지 않은 상태였다.
- `JsonObjectMapperService.objectMapper`를 JShell에서 직접 호출: `0.12345678901234567890` → `0.12345678901234568`. `+1`, `0x10`, `.5`, `1.`, 문자열 줄 연속은 JsonParseException. 한글 키·후행 쉼표는 이미 허용됐다.
- 실행 환경: IC 2024.3 라이브러리, 프로젝트 Jackson 2.21.0, Kotlin 2.3.10. 빌드 toolchain 17, 수동 JShell 21.0.2. 네이티브 IDE를 기동하거나 조작하지 않았다.

## Implementation

- 공유 매퍼는 ObjectMapper 계약을 유지하며 Json5Factory를 사용한다. 모든 현재 원문 소비자는 `readTree(String)`, `readValue(String, ...)`, `factory.createParser(String)` 경로이며 mapper.copy()도 같은 factory 동작을 유지한다.
- Json5InputNormalizer는 문자열, 이스케이프, 주석, 식별자, 숫자를 분리한다. 16진수는 BigInteger, 소수는 USE_BIG_DECIMAL_FOR_FLOATS로 처리한다. 전체 문법·후행 토큰 검사는 Jackson에 남긴다.
- [JSON5 1.0 명세](https://spec.json5.org/)의 유한 숫자, 문자열 줄 연속·이스케이프, Unicode 키·공백을 기준으로 구현했다. 외부 의존성을 추가하지 않았다.
- 비유한 숫자를 문자열과 구분하여 일반 JsonParseException으로 거절한다. 오류 메시지에는 원문 대신 종류와 위치만 포함한다. 포맷터의 원문 로깅을 제거했다.
- 최종 경로: 원문 → 공유 factory → 정확한 JsonNode/BigDecimal → 일반/정렬 mapper → 출력. Jayway는 JacksonJsonProvider의 untyped 경로, JMESPath/jq는 JsonNode 경로, 타입 추론은 같은 readTree를 거친다.
- 소비자 검토: tree, API 응답, type seed/validator, type inference, WASM 결과 디코딩, template highlight와 formatter/query가 공유한다. 타입 추론은 isFloatingPointNumber/isBigDecimal로 판단하여 DecimalNode를 수용한다. schema validation/normalizer의 독립 strict ObjectMapper는 변경하지 않았다. diff와 tooltip은 formatter/기존 PSI 경로를 유지한다.
- 새 JSON5 정규화는 현재 공유 소비자의 String 입력에 적용한다. 미사용 binary/Reader API를 새로운 JSON5 API로 확장하지 않는다.

## Acceptance

작업 디렉터리: `/Users/buyong/workspace/private/json-helper2`.

- `./gradlew compileKotlin`: exit 0 (기존 Object 클래스 사용 경고 2개).
- `./gradlew test -PskipWasmBuild=true --tests 'com.livteam.jsoninja.services.JsonFormatterServiceTest' --tests 'com.livteam.jsoninja.services.JMESPathServiceTest' --tests 'com.livteam.jsoninja.services.JacksonJqServiceTest'`: exit 0. 각각 14/2/8개, 실패·오류·스킵 0.
- 수동 관찰 명령: `jshell --class-path "$(cat /tmp/json-integrity-classpath)"`, exit 0. 현재 `build/classes/kotlin/main`, 위 버전의 Jackson/Kotlin/query JAR, IC 2024.3 lib, src/main/resources를 사용했다. MockApplication/MockProject에 실제 설정과 매퍼 서비스를 등록하고 실제 formatter/query/type conversion 서비스를 호출했다. 테스트 파일·사례·Gradle 작업은 추가하지 않았다.

| 입력 | 관찰한 최종 값 |
| --- | --- |
| `{n:+1}` | formatter와 세 query engine 모두 n=1; TypeScript number |
| `{n:0x10}` | 모두 n=16; number |
| `{n:.5}` | 모두 n=0.5; number |
| `{n:1.}` | 모두 수치 1 (Jayway 표기 1.0); number |
| single quote 안의 a + backslash + newline + b | 모두 문자열 ab; string |
| `{한글:1,}` | 한글 키와 수치 1 보존; 타입 추론 number (출력 이름 정책은 10 담당) |
| 주석 + `n:'Infinity'` + 후행 쉼표 | 모두 문자열 Infinity; string |
| `0.12345678901234567890` | 모든 JsonFormatState 출력의 BigDecimal 비교가 0; untyped 값 0.1234567890123456789 (허용된 끝자리 0 생략) |
| Infinity / -Infinity / NaN 각각 root, object, array | 9개 모두 isValidJson=false, formatJson 원문과 동일, 세 query engine 모두 recoverable null |
| 문자열 Infinity / NaN / -Infinity | 정상 문자열로 직렬화 |
| 값 placeholder와 +1 혼합 | placeholder 유지, 수치 1 변환 |
| 01 / 잘못된 hex / 쉼표 누락 / 배열 구멍 / 미종결 문자열 / 후행 쓰레기 | 6개 모두 invalid, 원문 유지 |

- 비유한 입력은 validation/parser에서 출력 생성 전에 중단되므로 UI의 기존 오류/원문 반환 경로로 전달된다. 문서 교체 및 네이티브 UI 관찰로 과장하지 않는다.
- 형식·정렬 매퍼 copy에서 정밀도를 확인했다. 정렬+compact 동시 옵션과 일반 escape 전처리는 후속 03의 소유다.
- 클래스 및 dependency artifact의 최종 ZIP 포함 여부는 16의 통합 패키징에서 확인한다. 의존성 변경은 없다.

### 검증 소스 SHA-256

- `src/main/kotlin/com/livteam/jsoninja/services/JsonObjectMapperService.kt`: `b0696ceaaf90b1ed9bd5e43973a383f0a73bcee2d93b1b68fb0767bbe7ca06b1`
- `src/main/kotlin/com/livteam/jsoninja/services/Json5Factory.kt`: `98249ece4cfe37c125b1e89fb856656a555666614313376ab45ea13c6aaad491`
- `src/main/kotlin/com/livteam/jsoninja/services/Json5InputNormalizer.kt`: `834bbd6f5b89ad5574117f7bcec8c0d9b6cae3ff6e4e97a62438e5058b484854`
- `src/main/kotlin/com/livteam/jsoninja/services/JsonFormatterService.kt`: `3a2561fbcaabb097856c5b2bcc9db7ceaccc3d5da5c03edc066d32b363363de5`

### 후속 통합에서 확인한 오류 계약 보완

08의 WASM V2 기존 실패 입력 검증이 후행 토큰 오류에 `trailing`를 요구했다. 정규화 대상이 아닌 일반 토큰은 Jackson에 그대로 전달하도록 보완하여 기존 진단을 유지했다. 이후 `./gradlew compileKotlin` 및 `./gradlew test -PskipWasmBuild=true --tests 'com.livteam.jsoninja.services.typeConversion.TypeConversionWasmIntegration*'`가 exit 0 (11개 통과)였다. 숫자·문자열 정규화 경로에는 변경이 없다. 위 SHA-256은 이 보완을 포함한다.
