# 10 — 원본 JSON을 표현하는 타입과 키 보존

2026-09-16, R1.7/R1.8/R1.9a/R1.9b/R2.9. 상태: **ready**.

## Baseline

- 최초 감사 HEAD `9eed77ed863f82843f9aadca0af479cf6efa0afd`, 02/09 수용 뒤 시작했다. 원본 키 정책 질문 동안 11을 독립 수행하도록 부모를 먼저 보정했고 최종 검증 HEAD는 `7f73a9e`와 아래 변경 소스다.
- 기존 실제 생성물은 `/tmp/json-integrity-types/baseline/{kotlin,java,typescript,go}`에 보존했다. 혼합 배열은 문자열 계열로 추론했고, TypeScript nullable 원소는 괄호 없이 number | null[]로 출력됐다. Kotlin `{}`는 매개변수 없는 data class였다.
- 실제 special-key 출력에서 return 예약어, Kotlin 달러 보간, 따옴표/역슬래시/줄바꿈 어노테이션, Go backtick 태그 문법이 잘못됐다. TypeScript는 a-b/a_b를 aB/aB2로 바꿔 원본 JSON 키를 표현하지 못했다.
- 호출 경로: ConvertTypeDialogPresenter → JsonToTypeDialogPresenter → JsonToTypeConversionService.convertDetailed → InferenceContext/Support → Renderer → 현재 입력의 Ready 미리보기 → 기존 복사/삽입 소비자. 이번 변경은 서비스 내부 추론/렌더링에 한정한다.

## Implementation

- 서로 다른 primitive는 Union으로 보존하고 언어별 표현으로 출력한다. TypeScript는 union, Kotlin/Java/기본 Go는 Any/Object/any다. 숫자끼리의 승격, nullable 옵션과 Go 실험적 출력 옵션은 유지했다.
- TypeScript의 nullable/union 배열 원소에 괄호를 붙였다. 사용자 결정대로 원래 sourceName을 속성명으로 사용하고 문법상 필요한 이름만 문자열로 인용한다. 명명 옵션은 TypeScript 원본 키보다 우선하지 않는다.
- Kotlin 빈 객체는 요청한 이름의 일반 class다. Java 필드/루트 배열에 필요한 import를 수집한다.
- 언어별 문자열 인용을 한 helper에서 처리한다. Kotlin 달러 보간, Java 제어 문자, Go 태그의 raw/interpreted 문자열 경계를 구분한다. Java/Kotlin의 어노테이션 NONE 선택은 유지한다.
- Go encoding/json이 해석하지 못하는 키(따옴표/역슬래시/제어 문자/쉼표/빈 키/하이픈 등)와 태그 없는 이름 변경에는 MarshalJSON/UnmarshalJSON을 생성한다. 원래 키로 map을 읽고 쓰며 기본 태그가 충분한 선언에는 메서드를 추가하지 않는다. Go 표준 라이브러리 encoding/json만 사용한다.
- Go 태그 모드의 optional omitempty는 기존 타입별 빈 값 기준을 유지한다. NONE에는 태그를 추가하지 않는다. 사용자 지정 codec은 정확한 키로 조회하며 map 기반 출력의 속성 순서는 달라질 수 있다. JSON 의미를 비교했다.
- 키워드 목록과 이름 중복 처리를 보완했다. Java PascalCase Class는 final Object.getClass와의 getter 충돌을 피한다. 객체 선언 이름은 원본 경로로 구분하고 원본 키를 구조 서명에 포함해 다른 JSON 매핑을 덮어쓰지 않는다.
- 공유 TypeConversionModels는 이 하위 작업에서 변경하지 않았다. 11의 enumLiteralValues 기본값 확장과 기존 sourceName/nullable/optional 계약을 그대로 소비한다. 플랫폼 API/외부 플러그인 의존성/테스트 파일 추가 없음.

## Acceptance

작업 디렉터리: `/Users/buyong/workspace/private/json-helper2`.

- `./gradlew compileKotlin`: exit 0.
- `./gradlew test --tests 'com.livteam.jsoninja.services.typeConversion.TypeConversionWasmIntegration*' --rerun-tasks --no-build-cache --no-configuration-cache`: exit 0, 세 suite 4+5+2=11개 통과, 실패/오류/스킵 0. 최종 import 보완을 포함해 다시 컴파일했으며 11의 역방향 변환도 통과했다.
- `python3 /tmp/json-integrity-types-generate.py`: exit 0. 4개 언어 × 3개 명명 규칙 × 지원 어노테이션 조합(총 27개)의 선언/필드 이름 유일성과 원본 키 목록을 확인했다. 마지막 실행은 루트 중첩 배열을 추가했다.
- 수동 입력: items=[1,true], numbers=[1,2.5], nullable=[1,null], nested=[[1,null],[2]], 서로 다른 optional 필드를 가진 objects 배열. 키는 return/class, quote, 역슬래시, LF, 달러, backtick, 한글, a-b/a_b, 빈 키, 하이픈, 쉼표, U+0001/U+007F, user_name, MarshalJSON/UnmarshalJSON이다.
- `python3 /tmp/json-integrity-types-compile.py`: exit 0. 27개 생성물과 EmptyRoot.kt를 실제 컴파일했다. TypeScript는 원본 JSON 리터럴을 Root에 대입하는 strict 검사도 통과했다. Go 6조합은 json.Unmarshal→json.Marshal 후 원본의 모든 키·값을 재귀 비교해 일치했다. NONE은 optional 빈 필드를 생략하지 않는 기존 선택을 유지하므로 추가 null 필드를 허용했다.
- `python3 /tmp/json-integrity-types-array-compile.py`: exit 0. 루트 `[[1,null],[2,true]]`를 네 언어에서 컴파일했고 Kotlin/TypeScript에서는 원본 값을 생성 타입에 대입했다.
- `python3 /tmp/json-integrity-types-annotations.py`: exit 0. Java/Kotlin 12조합의 컴파일된 필드/메서드/생성자 어노테이션을 읽어 원본 키와 정확히 일치함을 확인했다. JSON 라이브러리별 모든 직렬화 설정을 검증했다는 의미는 아니다.
- 컴파일러: Kotlin CLI 2.0.21 (`-jvm-target 17`), javac/GraalVM 21.0.2 (`--release 17`), TypeScript 5.8.3 (`--strict --noEmit --target ES2020`), Go 1.26.3. Java의 여러 public class는 기존 출력 의미대로 각각 동일 import를 가진 파일로 분리했다. Go는 package 선언을 붙였다.
- nullable=false의 기존 출력과 기본 Go []any/실험적 int | bool 선택을 직접 확인했다. 실험적 Go union은 기존 비표준 필드 문법을 유지하며 이 옵션을 일반 Go 컴파일 성공으로 보고하지 않는다.
- 09의 미리보기 수명/취소/소비자 파일은 변경하지 않았다. 테스트 WASM 해시는 11의 `6da5d6d1aee43bcbbe2eaae798dcbbd58dae9f51a1e0b80b19320dd68973ae76`과 같다. 통합 UI 경로는 부모 최종 검증에서 이어서 기록한다.
- 모든 보조 스크립트/컴파일 생성물은 /tmp에만 있다. 네이티브 UI 관찰은 수행하지 않았다.

### 검증 소스 SHA-256

- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/JsonToTypeGoJsonSupport.kt`: `f2b0fcbacc0b28865b556164364bbee314ae2612919b0d1ca8f38182c9bb9e80`
- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/JsonToTypeInferenceContext.kt`: `1856fbd4b475cec47201d4b0ffeb52d12f07c2c203bccbaf35ddcba2554397d2`
- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/JsonToTypeLiteralSupport.kt`: `9017ac57307508c595ec6d16497bbbcd00de398c02e32408c1e6b633c804b55a`
- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/JsonToTypeNamingSupport.kt`: `81a760f14cf7ee4d1f2033546a1aad106edcad58747bb418009f0376029c4a0f`
- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/JsonToTypeRenderer.kt`: `65492bfff767a9fcdd380382b9a2115ab604252a5d2aae7373589710247a7837`
- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/JsonToTypeSupport.kt`: `2e3d7e4c0ac064c48f74024557df90609e67d0d7e5d2b7836770fe9ee96442fa`
