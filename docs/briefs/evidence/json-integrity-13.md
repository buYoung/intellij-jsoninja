# 13 — 정확한 숫자 제약과 생성 기록

2026-09-16, R2.4a/R2.4b. 상태: **ready**.

## Baseline

- 기준 HEAD: `99958caa53c5d853301e32ebd0b2bb729c9c4419`. 선행 12를 적용한 서비스에서 재현했다.
- `python3 /tmp/json-integrity-number-baseline.py`: 11개 입력 중 유효한 10개 가운데 9개가 생성에 실패했다. lower-only minimum=300, upper-only maximum=-300, 정확한 2147483648, Long 밖의 큰 정수, 좁은 배타 구간, default가 잘못된 큰 정수가 포함된다. min=5/max=1의 실제 모순은 별도로 오류를 냈다.
- 기존 함수는 없는 경계를 0/100으로 채우고 배타 경계에 고정 0.1 또는 1을 더했으며, Double 변환/소수 6자리 반올림/Int 변환을 사용했다. fallback은 주로 0/1 후보를 사용했다.

## Implementation

- 작은 숫자 생성 helper를 같은 schema 패키지에 뒀다. 실제 최댓값/최솟값과 배타 여부를 먼저 계산하고, 샘플 창은 없는 경계에만 적용한다. 제약 검사에 임의 0..100 상한을 넣지 않는다.
- 모든 구간 계산을 BigDecimal로 수행한다. 연속 구간은 내부의 정확한 십진 가중값, 이산 구간은 가능한 배수 인덱스를 계산한다. 정수와 소수 multipleOf가 함께 있으면 유리수 분모를 약분해 정수에 맞는 간격을 구한다. 재시도 루프나 Double/Long 축소가 없다.
- 정수는 toBigIntegerExact/BigIntegerNode를 사용한다. 기본 생성과 fallback/필수 자식의 최소 숫자 후보가 같은 계산을 사용하며 최종 validator 검사는 그대로 유지한다.
- strict schema mapper 두 곳도 USE_BIG_DECIMAL_FOR_FLOATS를 사용해 입력·참조 파일의 좁은 경계를 보존한다. 12의 출처/캐시/스키마 위치 처리는 유지한다.
- 생성 액션의 coroutine 취소 검사를 서비스에 전달하고 생성 전후·각 결과 경계에서 확인한다. 기존 한 인자 API는 유지한다. 플랫폼 취소 검사를 숫자 연산에도 두고 CancellationException/ProcessCanceledException을 fallback이나 일반 오류로 바꾸지 않는다. 04의 대상/stamp/request 가드는 바꾸지 않았다.
- 중첩 오류 검증에서 기존 `#/properties~1x` 결함을 발견했다. 경로 토큰을 분리해 escape하고, fallback 실패 시 원래 오류 포인터를 보존한다.
- [JSON Schema 숫자 제약 정의](https://json-schema.org/draft/2020-12/json-schema-validation)의 의미를 유지하며 새 dialect 지원을 주장하지 않는다.

## Acceptance

작업 디렉터리: `/Users/buyong/workspace/private/json-helper2`.

- `./gradlew compileKotlin`: 마지막 액션의 플랫폼 취소 전파까지 포함하여 exit 0.
- `./gradlew test -PskipWasmBuild=true --tests 'com.livteam.jsoninja.services.schema.JsonSchemaDataGenerationServiceTest' --rerun-tasks --no-build-cache --no-configuration-cache`: exit 0, 기존 3개, 실패/오류/스킵 0. 12에 기록한 오래된 테스트 클래스 문제 때문에 현재 소스로 재컴파일했다.
- `python3 /tmp/json-integrity-number-check.py`: exit 0. 21개 유효 스키마 × 8회 = 168개 생성물을 strict mapper로 읽고 실제 compiledSchema로 검증해 모두 유효했다.
- `python3 /tmp/json-integrity-schema-check.py`: exit 0. 12의 데이터 경계, 앵커, 로컬/원격/중첩 URI, 화면 출처 전달을 다시 확인했다. 두 fixture 모두 정상 정리했다.
- IC 2024.3, headless=true, JShell/GraalVM 21.0.2, 컴파일 목표 JVM 17. 새 저장소 테스트나 의존성을 추가하지 않았다. 예상되는 실패의 TestLogger 등록만 LoggedErrorProcessor로 관찰했으며 실제 예외/validator 검사는 그대로 실행했다.

| 검증 입력 | 관찰 |
| --- | --- |
| number minimum=300 / maximum=-300 | 각각 385.8 / -309.8 등의 유효한 표본 |
| integer min=max 2147483648 | 정확히 2147483648 |
| 정수 Long.MIN/MAX 및 ±92233720368547758081234567890123456789 | 모든 값 그대로 보존 |
| number 0.001 < x < 0.002 | 0.001207 등의 내부값 |
| 0.123456789012345678901 < x < 0.123456789012345678902 | 0.123456789012345678901125 등의 내부값 |
| integer 2.2 < x < 3.5, 1.1 ≤ x ≤ 2.1, -3.2 ≤ x < -2.1 | 각각 3, 2, -3 |
| integer multipleOf 1.5 / 0.5 / 2.5 | 각 범위에서 3 / 10 / 10 |
| number 0 < x < 0.01, multipleOf 0.001 | 유효한 정확한 배수 |
| minimum=100/exclusiveMinimum=1/maximum=100, maximum=1/exclusiveMaximum=10 | 더 강한 실제 경계를 적용 |
| 큰 정수에 잘못된 default=0, 동일 제약을 필수 자식에 배치 | fallback도 정확한 2147483648, validator 통과 |
| allOf가 합친 좁은 구간 | 유효한 내부값 |
| 모순/빈 정수 구간/불가능한 배수/0 배수/중첩 모순 | 값 미반환, 포인터 있는 예외 |
| 필드 x 및 a/b~c의 배타 구간 모순 | `#/properties/x`, `#/properties/a~1b~0c` |
| 호출자 취소 callback을 첫 후보 직후 취소 | 4회 검사 후 CancellationException 전파, 결과 미반환 |
| 플랫폼 indicator를 숫자 생성 직전에 취소 | ProcessCanceledException 전파, fallback으로 전환하지 않음 |
| 별도 file URI 스키마의 min=max 0.123456789012345678901 | 조회·파싱·생성 후 정확한 값 유지 |

- 숫자 helper/서비스의 취소 전파를 직접 실행했다. 마지막 액션의 PCE 재전파와 기존 대상 검사 유지 여부는 소스와 컴파일로 확인했으며 네이티브 생성 대화상자를 조작했다고 주장하지 않는다.
- 12의 historical hash 중 strict mapper/서비스/포인터 소유 파일은 이 변경으로 달라졌다. 위 12 전체 수동 증거 재실행이 후속 15/16의 통합 근거다.

### 검증 소스 SHA-256

- `src/main/kotlin/com/livteam/jsoninja/services/schema/JsonSchemaNumericGenerator.kt`: `e41514ad879ed3f8c64e3a8bfcb41e7c63e99227dc610eb38993986d9c6d9599`
- `src/main/kotlin/com/livteam/jsoninja/services/schema/JsonSchemaValueGenerator.kt`: `fe151036484014bf46fd5186c84918b464d50fef3a3c0ae86c47eb066039454e`
- `src/main/kotlin/com/livteam/jsoninja/services/schema/JsonSchemaDataGenerationService.kt`: `f34253bc4d027be525d33612a4b42e22340474ca3091e5a2b034f964baf6cf79`
- `src/main/kotlin/com/livteam/jsoninja/services/schema/JsonSchemaNormalizer.kt`: `a033f44902552ac8436fb7645d12c75722b79d7403d34f8effc77a22161f14fe`
- `src/main/kotlin/com/livteam/jsoninja/services/schema/JsonSchemaValidationService.kt`: `855020ffb407660e253488d7553c2f100a71bb199590416fcc50f39333f45f83`
- `src/main/kotlin/com/livteam/jsoninja/actions/GenerateRandomJsonAction.kt`: `2a1bb5a32167c3eefd61cb42843c2ecb9d293656d561ae36f39fff311c9bef92`
