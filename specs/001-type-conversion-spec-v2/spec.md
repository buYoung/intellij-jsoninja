# Feature Specification: Type Conversion (JSON ↔ Type Declaration)

**Feature Branch**: `001-type-conversion-spec-v2`  
**Created**: 2026-04-02  
**Status**: Draft  
**Input**: JSONinja Plugin의 Type Conversion 기능 전체 스펙 v2 — JSON ↔ Type Declaration 양방향 변환

## User Scenarios & Testing *(mandatory)*

### User Story 1 - JSON 데이터를 타입 선언 코드로 변환 (Priority: P1)

개발자가 JSON API 응답이나 샘플 데이터를 보유하고 있을 때, 이를 대상 프로그래밍 언어(Java, Kotlin, TypeScript, Go)의 타입 선언 코드로 자동 변환하여 모델 클래스를 빠르게 생성한다.

**Why this priority**: 타입 변환의 핵심 기능으로, API 개발 시 JSON 응답 구조에서 모델 코드를 수동 작성하는 반복 작업을 제거한다. 가장 빈번한 사용 시나리오이다.

**Independent Test**: JSON 데이터를 입력하고 언어를 선택하면 해당 언어의 타입 선언 코드가 프리뷰에 표시되어야 한다.

**Acceptance Scenarios**:

1. **Given** 유효한 JSON 객체가 입력되어 있고 대상 언어로 Kotlin이 선택됨, **When** 변환을 실행하면, **Then** Kotlin data class 형태의 타입 선언 코드가 생성된다.
2. **Given** 중첩 객체를 포함한 JSON이 입력됨, **When** 변환을 실행하면, **Then** 중첩 객체에 대한 별도 타입 선언이 자동 생성되며 부모타입+필드명 기반의 타입명이 부여된다.
3. **Given** JSON 배열 내 여러 객체가 서로 다른 필드를 포함, **When** 변환을 실행하면, **Then** 모든 필드가 합집합으로 수집되고 일부 객체에만 존재하는 필드는 optional로 처리된다.
4. **Given** JSON에 null 값 필드가 존재하고 nullable 옵션이 활성화됨, **When** 변환을 실행하면, **Then** 해당 필드는 nullable 타입으로 선언된다.
5. **Given** JSON 필드명이 대상 언어의 네이밍 규칙과 다름 (예: `snake_case` → `camelCase`), **When** 변환을 실행하면, **Then** 필드명이 선택된 네이밍 규칙에 따라 변환되고 원본 키 매핑을 위한 어노테이션이 자동 생성된다.

---

### User Story 2 - 타입 선언 코드를 샘플 JSON으로 변환 (Priority: P1)

개발자가 이미 작성된 타입 선언 코드(class, interface, struct 등)를 보유하고 있을 때, 이로부터 샘플 JSON 데이터를 자동 생성하여 API 문서 작성, 테스트 데이터 준비, 목업 응답 생성에 활용한다.

**Why this priority**: JSON → Type과 함께 양방향 변환의 한 축으로, 타입 정의로부터 테스트 데이터나 API 문서용 샘플을 빠르게 생성하는 핵심 워크플로우이다.

**Independent Test**: 타입 선언 코드를 입력하고 언어를 선택하면 해당 타입 구조에 맞는 샘플 JSON이 프리뷰에 표시되어야 한다.

**Acceptance Scenarios**:

1. **Given** Kotlin data class 코드가 입력됨, **When** 변환을 실행하면, **Then** 해당 클래스의 필드 구조에 맞는 샘플 JSON이 생성된다.
2. **Given** 상속 관계가 있는 Java 클래스가 입력됨, **When** 변환을 실행하면, **Then** 부모 클래스의 필드까지 포함된 완전한 JSON이 생성된다.
3. **Given** 현실적 샘플 데이터 옵션이 활성화됨, **When** 변환을 실행하면, **Then** 필드명 패턴에 기반한 현실적 값(이메일, 이름, 전화번호 등)이 생성된다.
4. **Given** 출력 개수가 3으로 설정됨, **When** 변환을 실행하면, **Then** 3개의 JSON 인스턴스가 배열로 래핑되어 생성된다.
5. **Given** 순환 참조가 있는 타입 구조가 입력됨, **When** 변환을 실행하면, **Then** 무한 루프 없이 순환 지점에서 빈 객체로 대체되어 안전하게 생성된다.

---

### User Story 3 - 4개 언어 간 타입 변환 전환 (Priority: P2)

개발자가 동일한 JSON 데이터나 타입 코드를 다양한 언어로 전환하며 비교하거나, 팀 내 다른 언어 사용자에게 타입 정의를 공유할 때 언어 선택을 변경하여 즉시 다른 언어의 출력을 확인한다.

**Why this priority**: 다중 언어 지원은 플러그인의 차별화 요소이며, 언어 전환 시 실시간 프리뷰가 핵심 사용자 경험이다.

**Independent Test**: 언어 드롭다운에서 언어를 변경하면 프리뷰가 즉시 갱신되어야 한다.

**Acceptance Scenarios**:

1. **Given** JSON이 입력되어 있고 Java가 선택됨, **When** 언어를 TypeScript로 변경하면, **Then** 프리뷰가 TypeScript interface 형태로 즉시 갱신된다.
2. **Given** 언어를 Go로 변경함, **When** 프리뷰가 갱신되면, **Then** Go의 기본 네이밍(PascalCase)과 struct tag가 자동 적용된다.
3. **Given** 어노테이션 스타일이 설정되어 있고 언어를 변경함, **When** 새 언어가 해당 어노테이션을 지원하지 않으면, **Then** 어노테이션 옵션이 해당 언어의 기본값으로 자동 전환된다.

---

### User Story 4 - 변환 옵션 커스터마이징 (Priority: P2)

개발자가 프로젝트의 코딩 컨벤션에 맞춰 네이밍 규칙, 어노테이션 스타일, nullable 처리, 출력 포맷 등의 옵션을 조정하고, 이 설정이 다음 사용 시에도 유지된다.

**Why this priority**: 프로젝트별 컨벤션 차이를 수용하여 생성된 코드의 실용성을 높이며, 설정 영속화로 반복 조정 비용을 제거한다.

**Independent Test**: 옵션을 변경하면 프리뷰에 즉시 반영되고, 다이얼로그를 닫았다 다시 열었을 때 이전 설정이 유지되어야 한다.

**Acceptance Scenarios**:

1. **Given** 네이밍 규칙이 camelCase로 설정됨, **When** snake_case로 변경하면, **Then** 프리뷰의 필드명이 snake_case로 즉시 갱신된다.
2. **Given** 어노테이션 스타일을 Jackson으로 설정하고 변환을 완료함, **When** 다음 번 다이얼로그를 열면, **Then** 어노테이션 스타일이 Jackson으로 유지되어 있다.
3. **Given** Type → JSON에서 출력 포맷이 Prettify로 설정됨, **When** Uglify로 변경하면, **Then** 프리뷰의 JSON이 한 줄 압축 형태로 즉시 갱신된다.

---

### User Story 5 - 변환 결과 활용 (Priority: P2)

개발자가 생성된 코드나 JSON을 클립보드에 복사하거나, 현재 에디터에 직접 삽입하거나, JSONinja 패널의 새 탭에 추가하여 작업 흐름을 이어간다.

**Why this priority**: 변환 결과의 활용 경로를 다양하게 제공하여 개발자의 워크플로우 중단을 최소화한다.

**Independent Test**: Copy, Insert to Editor, 새 탭 삽입 각각이 올바르게 동작해야 한다.

**Acceptance Scenarios**:

1. **Given** 변환 결과가 프리뷰에 표시됨, **When** Copy 버튼을 클릭하면, **Then** 결과가 시스템 클립보드에 복사되고 성공 알림이 표시된다.
2. **Given** 에디터에 텍스트가 선택되어 있고 변환 결과가 존재, **When** Insert to Editor를 클릭하면, **Then** 선택 영역이 변환 결과로 대체된다.

---

### User Story 6 - 자동 입력 유형 감지 및 탭 전환 (Priority: P3)

개발자가 에디터에서 텍스트를 선택하고 타입 변환 다이얼로그를 열 때, 선택된 텍스트가 JSON인지 타입 코드인지 자동으로 판별하여 적절한 탭(JSON→Type 또는 Type→JSON)이 자동 활성화된다.

**Why this priority**: 사용 편의성 향상 기능으로, 수동 탭 전환 없이 의도에 맞는 변환 방향이 자동 선택되어 워크플로우가 매끄러워진다.

**Independent Test**: JSON 텍스트를 선택하고 다이얼로그를 열면 JSON→Type 탭이, 타입 코드를 선택하고 열면 Type→JSON 탭이 활성화되어야 한다.

**Acceptance Scenarios**:

1. **Given** 에디터에서 유효한 JSON 텍스트가 선택됨, **When** 타입 변환 다이얼로그를 열면, **Then** JSON→Type 탭이 활성화되고 입력 필드에 선택된 JSON이 자동 입력된다.
2. **Given** 에디터에서 Kotlin class 코드가 선택됨, **When** 타입 변환 다이얼로그를 열면, **Then** Type→JSON 탭이 활성화되고 입력 필드에 선택된 코드가 자동 입력된다.

---

### User Story 7 - 유니온 타입 처리 (Priority: P3)

개발자가 이종 배열이나 유니온 타입을 포함한 JSON/타입 코드를 변환할 때, 언어별 유니온 타입 지원 수준에 맞게 적절히 처리된다.

**Why this priority**: 고급 타입 시스템 기능으로, TypeScript와 Go에서 유니온 타입의 정확한 표현이 가능하다. Java/Kotlin은 유니온을 지원하지 않으므로 안전한 fallback을 제공한다.

**Independent Test**: 유니온 타입이 포함된 데이터의 변환 결과가 언어별로 올바르게 생성되어야 한다.

**Acceptance Scenarios**:

1. **Given** 이종 배열 `[1, "a", true]`가 JSON에 존재하고 TypeScript가 선택됨, **When** 변환하면, **Then** `(number | string | boolean)[]` 유니온 타입으로 출력된다.
2. **Given** 동일한 이종 배열에서 Java가 선택됨, **When** 변환하면, **Then** `List<Object>`로 fallback 처리된다.
3. **Given** Go가 선택되고 실험적 유니온 옵션이 활성화됨, **When** 이종 배열을 변환하면, **Then** interface + type alias 패턴으로 유니온이 표현된다.

---

### Edge Cases

- JSON 입력이 빈 문자열이거나 유효하지 않은 JSON인 경우 어떻게 처리하는가?
  - 빈 입력: "No JSON content entered" 에러 메시지 표시
  - 유효하지 않은 JSON: "Invalid JSON: {error details}" 에러 메시지 표시
- Root Type Name이 대상 언어의 예약어와 충돌하는 경우?
  - 예약어 + "Value" 접미사 자동 추가 (예: `class` → `classValue`)
- JSON 중첩 깊이가 매우 깊은 경우 (10단계 초과)?
  - 최대 깊이 초과 시 AnyValue로 fallback하고 결과 상단에 경고 주석 표시
- JSON 필드명에 특수문자가 포함된 경우?
  - 유효하지 않은 문자(영문/숫자/밑줄 이외) 제거 후 정규화, 필요 시 어노테이션으로 원본 키 매핑
- 숫자로 시작하는 필드명?
  - 접두사 추가하여 유효한 식별자로 변환
- 1MB 이상의 대용량 JSON 입력?
  - 경고 다이얼로그 표시 후 사용자 확인 시 진행
- 동일한 필드 시그니처를 가진 중첩 타입이 여러 개인 경우?
  - 타입 중복 제거: 동일 시그니처 타입은 재사용, 이름 충돌 시 숫자 접미사 추가
- 빈 배열 `[]`이 JSON에 존재하는 경우?
  - 요소 타입을 알 수 없으므로 AnyValue 리스트로 처리
- 날짜 형식 문자열(ISO-8601)이 포함된 경우?
  - STRING 타입으로 유지하되, 날짜 형식이 감지되었음을 경고 메시지로 표시
- Type → JSON에서 출력 개수가 범위(1~100)를 벗어나는 경우?
  - "Output count must be between 1 and 100" 에러 메시지 표시
- 타입 선언 코드에 문법 오류가 있는 경우?
  - 파싱 가능한 부분까지만 추출하고, 타입 선언이 하나도 발견되지 않으면 에러 표시
- 순환 상속 구조(A extends B, B extends A)?
  - 방문 추적으로 순환 감지, 이미 방문한 타입 재방문 시 순환 중단

## Requirements *(mandatory)*

### Functional Requirements

**JSON → Type 변환 (Core)**

- **FR-001**: 시스템은 유효한 JSON 문자열(object 또는 array)을 Java, Kotlin, TypeScript, Go 4개 언어의 타입 선언 코드로 변환할 수 있어야 한다.
- **FR-002**: 시스템은 JSON 값으로부터 타입을 자동 추론해야 한다 — 문자열(STRING), 정수(INTEGER), 실수(DECIMAL), 불리언(BOOLEAN), null(Nullable), 객체(Named), 배열(List), 맵(Map) 타입을 식별한다.
- **FR-003**: 시스템은 중첩 객체를 별도 타입 선언으로 자동 추출하고, 부모타입명+필드명 PascalCase 결합으로 타입명을 생성해야 한다. 배열 필드의 타입명은 단수화(마지막 's' 제거)를 적용한다.
- **FR-004**: 시스템은 배열 내 여러 객체의 필드를 합집합으로 수집하고, 일부 객체에만 존재하는 필드를 optional로 처리해야 한다.
- **FR-005**: 시스템은 동일한 필드 시그니처를 가진 타입을 재사용하여 중복 선언을 제거해야 한다.
- **FR-006**: 시스템은 3가지 네이밍 규칙(camelCase, PascalCase, snake_case)을 지원해야 하며, 각 언어별 기본 규칙을 적용해야 한다 — Java/Kotlin/TypeScript는 camelCase, Go는 PascalCase.
- **FR-007**: 시스템은 5가지 어노테이션 스타일(NONE, GSON @SerializedName, Jackson @JsonProperty, Kotlin @SerialName, Go json tag)을 지원해야 하며, 필드명이 원본 JSON 키와 다를 때만 어노테이션을 생성해야 한다.
- **FR-008**: 시스템은 어노테이션 사용 시 필요한 import 문을 자동 생성해야 한다.
- **FR-009**: 시스템은 null 값 필드를 nullable 타입으로 처리하는 옵션을 제공해야 한다.
- **FR-010**: 시스템은 최대 중첩 깊이(기본 10)를 적용하고, 초과 시 AnyValue로 fallback하며 경고 메시지를 표시해야 한다.
- **FR-011**: 시스템은 유니온 타입을 지원해야 한다 — TypeScript는 네이티브 유니온(`A | B`), Go는 실험적 옵션(interface + type alias), Java/Kotlin은 Object/Any로 fallback.
- **FR-012**: 시스템은 필드명을 정규화해야 한다 — 유효하지 않은 문자 제거, 숫자 시작 처리, 언어 예약어 충돌 시 "Value" 접미사 추가, 필드명 중복 시 숫자 접미사 추가.
- **FR-013**: 시스템은 ISO-8601 날짜 형식 문자열을 감지하고 경고 메시지를 표시해야 한다 (STRING 타입으로 유지).

**JSON → Type 변환 (언어별 출력)**

- **FR-014**: Java 출력은 `public class` 기반이어야 하며, private 필드, boxed/unboxed 타입 구분(nullable시 boxed), `List<T>`/`Map<String, T>` 컬렉션을 사용해야 한다.
- **FR-015**: Kotlin 출력은 `data class` 기반이어야 하며, val 프로퍼티, `?` nullable 접미사, `List<T>`/`Map<String, T>` 컬렉션을 사용해야 한다.
- **FR-016**: TypeScript 출력은 `interface` 기반이어야 하며, `?` optional 접미사, `T[]` 배열 문법, `string | number` 유니온 문법을 사용해야 한다.
- **FR-017**: Go 출력은 `struct` 기반이어야 하며, PascalCase 필드명, 포인터(`*T`) nullable, `[]T` slice, `map[string]T` 맵, `` `json:"name"` `` struct tag를 사용해야 한다.

**Type → JSON 변환 (Core)**

- **FR-018**: 시스템은 4개 언어의 타입 선언 코드를 파싱하여 해당 구조에 맞는 샘플 JSON을 생성할 수 있어야 한다.
- **FR-019**: 시스템은 필드 생성 모드를 지원해야 한다 — 모든 필드 포함(REQUIRED_AND_OPTIONAL) 또는 필수 필드만 포함(REQUIRED_ONLY).
- **FR-020**: 시스템은 Faker 기반 현실적 샘플 데이터 생성을 지원해야 한다 — 필드명 패턴(email, name, phone, city 등)에 따라 맥락에 맞는 값을 생성한다.
- **FR-021**: 시스템은 비현실적 모드(빈 문자열, 0, false 등 기본값)도 지원해야 한다.
- **FR-022**: 시스템은 순환 참조를 감지하고 안전하게 처리해야 한다 — 이미 방문한 타입 재방문 시 빈 객체 `{}`를 반환한다.
- **FR-023**: 시스템은 1~100개 범위의 다중 JSON 인스턴스 출력을 지원해야 한다 — 2개 이상 시 배열로 래핑.
- **FR-024**: 시스템은 3가지 출력 포맷(Prettify, Prettify Compact, Uglify)을 지원해야 한다.
- **FR-025**: 시스템은 상속 관계의 타입에서 부모 필드를 포함한 완전한 JSON을 생성해야 한다 — 깊이 우선 수집, 자식 우선 중복 제거.
- **FR-026**: 시스템은 Union 타입의 각 멤버별 변형과 InlineObject의 카르테시안 프로덕트 변형을 생성해야 한다.

**Type → JSON 변환 (언어별 파싱)**

- **FR-027**: Java 파서는 class, record, enum 선언을 지원해야 하며, static 필드 제외, extends 상속 추적, 제네릭 타입(List, Map, Optional) 파싱을 수행해야 한다.
- **FR-028**: Kotlin 파서는 class, data class, enum class 선언을 지원해야 하며, 생성자 파라미터와 본문 프로퍼티를 합산하고 nullable(`?`) 접미사를 감지해야 한다.
- **FR-029**: TypeScript 파서는 interface, type alias, enum 선언을 지원해야 하며, optional(`?`) 필드, extends 다중 상속, Union, InlineObject, Record 타입을 파싱해야 한다.
- **FR-030**: Go 파서는 struct, interface 선언을 지원해야 하며, struct tag의 `json:"name"` 파싱, `json:"-"` 필드 제외, 포인터(`*T`) nullable, inline struct 파싱을 수행해야 한다.

**UI 및 사용자 인터랙션**

- **FR-031**: 시스템은 탭 기반 다이얼로그(JSON→Type / Type→JSON)를 제공해야 한다.
- **FR-032**: 시스템은 실시간 프리뷰를 제공해야 한다 — 입력 변경 시 디바운스(JSON→Type 300ms, Type→JSON 500ms) 후 비동기로 변환을 실행하고 결과를 표시한다.
- **FR-033**: 프리뷰 패널은 4가지 상태(Empty, Loading, Error, Success)를 표시해야 한다.
- **FR-034**: 시스템은 변환 결과를 클립보드 복사, 에디터 삽입(선택 영역 대체), 새 탭 삽입 방식으로 활용할 수 있어야 한다.
- **FR-035**: 시스템은 입력 텍스트의 유형(JSON vs 타입 코드)을 자동 판별하여 적절한 탭을 활성화해야 한다.
- **FR-036**: 시스템은 1MB 초과 입력 시 경고 다이얼로그를 표시해야 한다.

**유효성 검사**

- **FR-037**: JSON → Type 검증: 빈 입력, 유효하지 않은 루트 타입명(식별자 패턴 불일치), 유효하지 않은 JSON에 대해 각각 명확한 에러 메시지를 표시해야 한다.
- **FR-038**: Type → JSON 검증: 빈 입력, 출력 개수 범위(1~100) 위반에 대해 명확한 에러 메시지를 표시해야 한다.

**설정 영속화**

- **FR-039**: 시스템은 모든 변환 옵션(언어, 네이밍, nullable, 어노테이션, 필드 모드, 출력 포맷 등)을 프로젝트 레벨에서 영속화하고, 다이얼로그 재진입 시 이전 설정을 복원해야 한다.

### Key Entities

- **TypeReference**: 타입 시스템의 핵심 모델 — 8가지 변형(Primitive, Named, ListReference, MapReference, Nullable, InlineObject, Union, AnyValue)으로 모든 타입을 표현한다.
- **TypeDeclaration**: 타입 선언 단위 — 이름, 선언 종류(CLASS/INTERFACE/RECORD/STRUCT/ENUM/TYPE_ALIAS), 필드 목록, 상속 관계, enum 값, type alias 대상을 포함한다.
- **TypeField**: 필드 단위 — 정규화된 이름, 타입 참조, optional 여부, 원본 이름을 포함한다.
- **ConversionOptions**: 변환 옵션 — JSON→Type(루트 타입명, 네이밍 규칙, 어노테이션 스타일, nullable, 유니온 플래그, 최대 깊이)과 Type→JSON(필드 모드, nullable 포함, 현실적 데이터, 출력 수, 포맷)으로 나뉜다.
- **SupportedLanguage**: 지원 언어 단위 — 언어 식별자, 파일 확장자, 기본 네이밍 규칙, 아이콘 경로를 포함한다.

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 사용자가 JSON 데이터를 입력하고 타입 선언 코드를 생성하는 전체 과정이 3단계(입력 → 옵션 선택 → 결과 획득) 이내에 완료되어야 한다.
- **SC-002**: 프리뷰 갱신이 사용자 입력 후 1초 이내에 완료되어야 한다 (디바운스 포함).
- **SC-003**: 4개 지원 언어(Java, Kotlin, TypeScript, Go) 모두에서 생성된 타입 선언 코드가 해당 언어의 문법적으로 유효해야 한다.
- **SC-004**: Type → JSON 변환에서 생성된 JSON이 항상 유효한 JSON 형식이어야 한다.
- **SC-005**: 10단계 이하 중첩 깊이의 JSON에 대해 모든 중첩 객체가 정확하게 별도 타입으로 추출되어야 한다.
- **SC-006**: 순환 참조를 포함한 타입 구조에서 무한 루프 없이 안전하게 JSON이 생성되어야 한다.
- **SC-007**: 사용자 설정이 프로젝트 레벨에서 영속화되어 다이얼로그 재진입 시 100% 복원되어야 한다.
- **SC-008**: 각 언어별 어노테이션 스타일이 해당 언어의 공식 라이브러리 규격에 부합해야 한다.
- **SC-009**: 현실적 샘플 데이터 모드에서 email, name, phone 등 10개 이상의 필드명 패턴에 대해 맥락에 맞는 값이 생성되어야 한다.
- **SC-010**: 언어별 예약어 충돌이 발생하는 필드명에 대해 100% 안전한 대체 이름이 생성되어야 한다.

## Assumptions

- 사용자는 IntelliJ IDEA 기반 IDE를 사용하며 JSONinja 플러그인이 설치되어 있다.
- 입력 JSON은 표준 JSON 형식을 따른다.
- 타입 선언 코드 파싱은 정규 표현식 기반으로 수행되며, 완벽한 언어 파서 수준의 정확도를 보장하지는 않는다.
- 날짜 타입은 별도 Date/DateTime 타입으로 변환하지 않고 STRING으로 유지한다 (향후 확장 가능).
- 단수화 규칙은 단순(마지막 's' 제거)하며, 불규칙 복수형(children, mice 등)은 처리하지 않는다.
- Java/Kotlin에서 유니온 타입은 지원하지 않으며 Object/Any로 대체한다.
- Go 유니온 타입은 실험적 기능으로, 별도 옵션 플래그로 활성화해야 한다.
- Getter/Setter 기반 프로퍼티는 분석하지 않으며, 필드 선언만 파싱한다.
- 매우 복잡한 다단계 제네릭 구조는 AnyValue로 fallback될 수 있다.
- 설정은 프로젝트 레벨에 저장되며, IDE 레벨 공유는 지원하지 않는다.
