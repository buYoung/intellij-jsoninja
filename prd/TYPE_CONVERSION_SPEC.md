# Type Conversion Feature Specification

> JSONinja Plugin v1.10.1 — Type Conversion Feature 상세 스펙 문서  
> 작성일: 2026-04-02

---

## 1. 개요 (Overview)

Type Conversion은 JSONinja 플러그인의 핵심 기능으로, **JSON ↔ 타입 선언(Type Declaration)** 간 양방향 변환을 제공한다.

### 1.1 기능 범위

| 방향 | 설명 |
|------|------|
| **JSON → Type** | JSON 데이터를 분석하여 대상 언어의 타입 선언 코드를 생성 |
| **Type → JSON** | 타입 선언 코드를 분석하여 샘플 JSON 데이터를 생성 |

### 1.2 지원 언어

| 언어 | 파일 확장자 | 기본 네이밍 | 아이콘 |
|------|------------|------------|--------|
| Java | `.java` | camelCase | `/icons/languages/java.svg` |
| Kotlin | `.kt` | camelCase | `/icons/languages/kotlin.svg` |
| TypeScript | `.ts` | camelCase | `/icons/languages/typescript.svg` |
| Go | `.go` | PascalCase | `/icons/languages/go.svg` |

---

## 2. JSON → Type 변환 스펙

### 2.1 입력

- **JSON 데이터**: 유효한 JSON 문자열 (object 또는 array)
- **Root Type Name**: 최상위 타입 이름 (기본값: `"Root"`, 유효한 식별자여야 함)

### 2.2 변환 옵션 (`JsonToTypeConversionOptions`)

| 옵션 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `rootTypeName` | `String` | `"Root"` | 최상위 타입 이름 |
| `namingConvention` | `NamingConvention` | 언어별 기본값 | 필드 네이밍 규칙 |
| `annotationStyle` | `JsonToTypeAnnotationStyle` | `NONE` | 직렬화 어노테이션 스타일 |
| `allowsNullableFields` | `Boolean` | `true` | null 필드를 nullable 타입으로 처리 |
| `usesExperimentalGoUnionTypes` | `Boolean` | `false` | Go 언어 유니온 타입 실험 기능 |
| `maximumDepth` | `Int` | `10` | 최대 중첩 깊이 |

### 2.3 네이밍 규칙 (`NamingConvention`)

| 규칙 | 예시 | 기본 적용 언어 |
|------|------|---------------|
| `CAMEL_CASE` | `userName` | Java, Kotlin, TypeScript |
| `PASCAL_CASE` | `UserName` | Go |
| `SNAKE_CASE` | `user_name` | (수동 선택) |

### 2.4 어노테이션 스타일 (`JsonToTypeAnnotationStyle`)

| 스타일 | 지원 언어 | 출력 예시 |
|--------|----------|----------|
| `NONE` | 전체 | (어노테이션 없음) |
| `GSON_SERIALIZED_NAME` | Java | `@SerializedName("field_name")` |
| `JACKSON_JSON_PROPERTY` | Java, Kotlin | `@JsonProperty("field_name")` |
| `KOTLIN_SERIAL_NAME` | Kotlin | `@SerialName("field_name")` |
| `GO_JSON_TAG` | Go | `` `json:"field_name"` `` |

**언어별 기본 어노테이션:**
- Java: `JACKSON_JSON_PROPERTY`
- Kotlin: `JACKSON_JSON_PROPERTY`
- TypeScript: `NONE`
- Go: `GO_JSON_TAG`

### 2.5 타입 추론 규칙 (Type Inference)

#### 2.5.1 Primitive 타입 매핑

| JSON 값 | 추론 타입 | Java | Kotlin | TypeScript | Go |
|---------|----------|------|--------|------------|-----|
| `"text"` | STRING | `String` | `String` | `string` | `string` |
| `"2024-01-01"` | STRING (날짜 감지) | `String` | `String` | `string` | `string` |
| `123` | INTEGER | `int` / `Integer` | `Int` | `number` | `int` |
| `1.5` | DECIMAL | `double` / `Double` | `Double` | `number` | `float64` |
| `true` / `false` | BOOLEAN | `boolean` / `Boolean` | `Boolean` | `boolean` | `bool` |
| `null` | Nullable(AnyValue) | `Object` | `Any?` | `any` | `any` |

> **날짜 감지**: ISO-8601 패턴 (`YYYY-MM-DD` + optional time) 매칭 시 STRING으로 처리 (별도 Date 타입 미생성)

#### 2.5.2 복합 타입 매핑

| JSON 구조 | 추론 TypeReference | 설명 |
|-----------|-------------------|------|
| `{ ... }` | `Named` / `InlineObject` | 중첩 객체 → 별도 타입 선언 생성 |
| `[ ... ]` | `ListReference` | 배열 → 요소 타입 추론 |
| `[1, "a"]` | `ListReference(Union)` | 이종 배열 → 유니온 타입 (TS만 지원, 나머지는 `any`) |
| `[{}, {}]` | `ListReference(Named)` | 객체 배열 → 필드 병합 후 단일 타입 |
| `null` | `Nullable(AnyValue)` | nullable any |

#### 2.5.3 배열 요소 병합 규칙

- 배열 내 모든 객체의 필드를 합집합(union)으로 수집
- 일부 객체에만 존재하는 필드 → `isOptional = true`
- null 값을 포함하는 필드 → `Nullable` 래핑 (allowsNullableFields 옵션 적용 시)
- 빈 배열 `[]` → `ListReference(AnyValue)`

#### 2.5.4 중첩 객체 타입 이름 생성

- 부모 타입명 + 필드명을 PascalCase로 결합
- 예: `Root` 타입의 `address` 필드 → `RootAddress`
- 배열 필드는 단수화: `items` → `RootItem`
- 단수화 규칙: 마지막 's' 제거 (단순 규칙)

#### 2.5.5 타입 중복 제거 (Deduplication)

- 동일한 필드 시그니처를 가진 타입은 재사용
- 시그니처 = 필드명 + 타입의 정렬된 조합
- 이름 충돌 시 숫자 접미사 추가 (`User`, `User2`, `User3` ...)

#### 2.5.6 유니온 타입 지원

| 언어 | 유니온 지원 | 출력 형태 |
|------|-----------|----------|
| TypeScript | **지원** | `string \| number` |
| Go | **실험적** (`usesExperimentalGoUnionTypes`) | interface + type alias |
| Java | 미지원 | `Object` (fallback) |
| Kotlin | 미지원 | `Any` (fallback) |

#### 2.5.7 깊이 제한

- `maximumDepth` (기본 10) 초과 시 `AnyValue`로 fallback
- 경고 메시지를 결과 상단에 주석으로 표시

### 2.6 필드 이름 처리

#### 2.6.1 이름 정규화 (Sanitization)

1. 소스 JSON 키에서 단어 분리 (camelCase, delimiter 기반)
2. 선택된 `NamingConvention`에 따라 재조합
3. 유효하지 않은 문자 제거 (영문, 숫자, 밑줄만 허용)
4. 숫자로 시작하는 이름 → 접두사 추가
5. 언어 예약어 충돌 시 `"Value"` 접미사 추가 (예: `class` → `classValue`)
6. 필드명 중복 시 숫자 접미사 추가

#### 2.6.2 어노테이션 필요성 판단

- 정규화된 필드명 ≠ 원본 JSON 키 → 어노테이션 생성
- `requiresNameAnnotation()` 메서드로 전체 판단 가능

### 2.7 언어별 출력 형태

#### 2.7.1 Java

```java
// import 블록 (어노테이션 사용 시)
import com.fasterxml.jackson.annotation.JsonProperty;

public class Root {
    @JsonProperty("user_name")
    private String userName;
    private int age;
    private List<Address> addresses;
}
```

- 클래스 기반 (`public class`)
- 필드: `private` 접근 제한자
- 컬렉션: `List<T>`, `Map<String, T>`
- Nullable: boxed 타입 (`Integer`, `Double`, `Boolean`)
- Non-nullable primitive: unboxed 타입 (`int`, `double`, `boolean`)

#### 2.7.2 Kotlin

```kotlin
import com.fasterxml.jackson.annotation.JsonProperty

data class Root(
    @JsonProperty("user_name")
    val userName: String,
    val age: Int,
    val addresses: List<Address>,
)
```

- `data class` 기반
- Nullable: `?` 접미사 (`String?`, `Int?`)
- 컬렉션: `List<T>`, `Map<String, T>`

#### 2.7.3 TypeScript

```typescript
interface Root {
    userName: string;
    age: number;
    addresses: Address[];
}
```

- `interface` 기반
- Optional: `?` 접미사 (`field?: type`)
- 배열: `T[]` 문법
- 유니온: `string | number` 문법
- Map: `Record<string, T>` 또는 `{ [key: string]: T }`

#### 2.7.4 Go

```go
type Root struct {
    UserName  string    `json:"user_name"`
    Age       int       `json:"age"`
    Addresses []Address `json:"addresses"`
}
```

- `struct` 기반
- Nullable: 포인터 타입 (`*string`, `*int`)
- 컬렉션: `[]T` (slice), `map[string]T`
- JSON 태그: `` `json:"field_name"` ``
- 실험적 유니온: `interface{}` + type alias 패턴

### 2.8 Import 블록 생성

| 언어 | 조건 | Import 문 |
|------|------|----------|
| Java | `@JsonProperty` 사용 | `import com.fasterxml.jackson.annotation.JsonProperty;` |
| Java | `@SerializedName` 사용 | `import com.google.gson.annotations.SerializedName;` |
| Java | `List` 사용 | `import java.util.List;` |
| Java | `Map` 사용 | `import java.util.Map;` |
| Kotlin | `@JsonProperty` 사용 | `import com.fasterxml.jackson.annotation.JsonProperty` |
| Kotlin | `@SerialName` 사용 | `import kotlinx.serialization.SerialName` |

---

## 3. Type → JSON 변환 스펙

### 3.1 입력

- **타입 선언 코드**: 대상 언어의 타입 선언 소스 코드
- **Root Type Name**: (선택) 지정하지 않으면 첫 번째 선언된 타입 사용

### 3.2 변환 옵션 (`TypeToJsonGenerationOptions`)

| 옵션 | 타입 | 기본값 | 설명 |
|------|------|--------|------|
| `propertyGenerationMode` | `SchemaPropertyGenerationMode` | `REQUIRED_AND_OPTIONAL` | 필드 포함 범위 |
| `includesNullableFieldWithNullValue` | `Boolean` | `true` | nullable 필드를 null 값으로 포함 |
| `usesRealisticSampleData` | `Boolean` | `true` | Faker 기반 현실적 샘플 데이터 |
| `outputCount` | `Int` | `1` | 생성할 JSON 인스턴스 수 (1~100) |
| `formatState` | `JsonFormatState` | `PRETTIFY` | 출력 포맷 |

### 3.3 필드 생성 모드 (`SchemaPropertyGenerationMode`)

| 모드 | 설명 |
|------|------|
| `REQUIRED_AND_OPTIONAL` | 모든 필드 포함 |
| `REQUIRED_ONLY` | optional/nullable이 아닌 필드만 포함 |

### 3.4 출력 포맷 (`JsonFormatState`)

| 포맷 | 설명 |
|------|------|
| `PRETTIFY` | 들여쓰기 + 줄바꿈 |
| `PRETTIFY_COMPACT` | 컴팩트 들여쓰기 |
| `UGLIFY` | 한 줄 압축 |

### 3.5 언어별 타입 선언 파싱

#### 3.5.1 Java 파싱

**지원 선언 형태:**
- `class` → `TypeDeclarationKind.CLASS`
- `record` → `TypeDeclarationKind.RECORD`
- `enum` → `TypeDeclarationKind.ENUM`

**파싱 규칙:**
- 선언 패턴: `\b(class|record|enum)\s+([A-Za-z_][A-Za-z0-9_]*)\b`
- 주석 제거 (블록 `/* */` + 라인 `//`)
- Record: 괄호 내 컴포넌트에서 필드 추출
- Class: 본문의 필드 선언에서 추출 (`;` 기준 분리)
- `static` 필드 제외
- 가시성 수식어 제거 (`public`, `private`, `protected`, `final`, `volatile`, `transient`)
- 배열 문법 `[]` 지원
- 상속: `extends` 키워드로 부모 타입 추적

**지원 타입 매핑 (Java → TypePrimitiveKind):**

| Java 타입 | PrimitiveKind |
|-----------|--------------|
| `String`, `char`, `Character`, `UUID`, `LocalDate`, `LocalDateTime`, `ZonedDateTime`, `Instant`, `Date` | STRING |
| `int`, `Integer`, `long`, `Long`, `short`, `Short`, `byte`, `Byte`, `BigInteger` | INTEGER |
| `double`, `Double`, `float`, `Float`, `BigDecimal` | DECIMAL |
| `boolean`, `Boolean` | BOOLEAN |

**지원 제네릭 타입:**
- `List<T>`, `ArrayList<T>`, `Set<T>`, `Collection<T>` → `ListReference`
- `Map<K,V>`, `HashMap<K,V>` → `MapReference`
- `Optional<T>` → `Nullable`

#### 3.5.2 Kotlin 파싱

**지원 선언 형태:**
- `class` / `data class` → `TypeDeclarationKind.CLASS`
- `enum class` → `TypeDeclarationKind.ENUM`

**파싱 규칙:**
- 선언 패턴: `\b((?:data\s+)?class|enum\s+class)\s+([A-Za-z_][A-Za-z0-9_]*)\b`
- 생성자 파라미터에서 `val`/`var` 프로퍼티 추출
- 본문에서 추가 `val`/`var` 프로퍼티 추출
- 생성자 + 본문 프로퍼티 합산 (이름 기준 중복 제거)
- 가시성 수식어 제거 (`public`, `private`, `protected`, `internal`, `open`, `override`, `lateinit`)
- 상속: `:` 키워드 뒤 타입 목록 추적 (생성자 호출 `()` 제거)

**지원 타입 매핑 (Kotlin → TypePrimitiveKind):**

| Kotlin 타입 | PrimitiveKind |
|-------------|--------------|
| `String`, `Char` | STRING |
| `Int`, `Long`, `Short`, `Byte`, `UInt`, `ULong`, `UShort`, `UByte` | INTEGER |
| `Double`, `Float` | DECIMAL |
| `Boolean` | BOOLEAN |

- Nullable: `?` 접미사 감지 → `Nullable` 래핑

#### 3.5.3 TypeScript 파싱

**지원 선언 형태:**
- `interface` → `TypeDeclarationKind.INTERFACE`
- `type` → `TypeDeclarationKind.TYPE_ALIAS`
- `enum` → `TypeDeclarationKind.ENUM`

**파싱 규칙:**
- 선언 패턴: `\b(interface|type|enum)\s+([A-Za-z_][A-Za-z0-9_]*)\b`
- Interface: 본문에서 프로퍼티 추출 (패턴: `name?: type`)
- Type alias: `=` 뒤 타입 표현식 파싱
- Optional 필드: `?` 마커 감지
- 상속: `extends` 키워드로 부모 타입 추적 (다중 상속 지원)
- `export`, `default`, `declare` 수식어 제거

**지원 타입 매핑 (TypeScript → TypePrimitiveKind):**

| TypeScript 타입 | PrimitiveKind |
|----------------|--------------|
| `string` | STRING |
| `number` | NUMBER |
| `boolean` | BOOLEAN |

**특수 타입:**
- `T[]` / `Array<T>` → `ListReference`
- `Record<K,V>` / `Map<K,V>` / `{ [key: string]: T }` → `MapReference`
- `T | U` → `Union`
- `null` / `undefined` → `Nullable`
- `{ field: type; ... }` → `InlineObject`

#### 3.5.4 Go 파싱

**지원 선언 형태:**
- `struct` → `TypeDeclarationKind.STRUCT`
- `interface` → `TypeDeclarationKind.INTERFACE` 또는 `TYPE_ALIAS` (유니온 패턴)

**파싱 규칙:**
- 선언 패턴: `\btype\s+([A-Za-z_][A-Za-z0-9_]*)\s+(struct|interface)\s*\{`
- Struct: 필드 선언 파싱 (이름 + 타입 + struct tag)
- JSON 필드명 결정 순서:
  1. struct tag의 `json:"name"` 값 사용
  2. tag에 `json:"-"` → 필드 제외
  3. tag 없음 → 필드명 변환 (소문자 첫 글자, 약어 처리)
- Interface: 본문에 `|` 포함 시 유니온 타입 alias로 처리

**지원 타입 매핑 (Go → TypePrimitiveKind):**

| Go 타입 | PrimitiveKind |
|---------|--------------|
| `string`, `rune` | STRING |
| `int`, `int8`~`int64`, `uint`, `uint8`~`uint64` | INTEGER |
| `float32`, `float64` | DECIMAL |
| `bool` | BOOLEAN |

**특수 타입:**
- `[]T` → `ListReference`
- `map[K]V` → `MapReference`
- `*T` → `Nullable`
- inline `struct { ... }` → `InlineObject`

### 3.6 상속 처리 (Field Inheritance)

- 부모 타입의 필드를 깊이 우선(depth-first)으로 수집
- 자식 타입 필드 + 부모 타입 필드 합산
- 필드명 중복 시 먼저 발견된 필드 우선 (자식 > 부모)
- 순환 상속 방지: `visitedTypeNames` 추적

### 3.7 샘플 값 생성 (`SampleValueGenerator`)

`usesRealisticSampleData = true`일 때 Faker 라이브러리를 사용하여 필드명 기반 현실적 값 생성:

| 필드명 패턴 | 생성 값 예시 |
|------------|-------------|
| `email` | `john.doe@example.com` |
| `name` | `John Smith` |
| `phone` | `+1-555-123-4567` |
| `city` | `New York` |
| `country` | `United States` |
| `street` | `123 Main St` |
| `zip`, `postal` | `10001` |
| `url`, `uri` | `https://example.com` |
| `id` (exact/suffix) | 8자리 랜덤 숫자 |
| `company` | `Acme Corp` |
| `age` | 18~80 범위 |
| `count` | 1~10 범위 |
| `year` | 2020~2030 범위 |
| 기타 문자열 | 랜덤 단어 |
| 기타 숫자 | 1~1000 범위 |

**Boolean 값 규칙:**
- 필드명이 `is`, `has`, `can`으로 시작 → `true`
- 그 외 → `false`

**`usesRealisticSampleData = false`일 때:**
- 문자열: `""` (빈 문자열)
- 정수: `0`
- 실수: `0.0`
- Boolean: 위 규칙 동일

### 3.8 순환 참조 방지

- `visitedTypeNames: MutableSet<String>`으로 방문한 타입 추적
- 이미 방문한 타입 재방문 시 빈 객체 `{}` 반환
- Enum, Type Alias는 순환 참조 추적 대상 제외

### 3.9 다중 출력 (Output Count)

- `outputCount = 1`: 단일 JSON 객체/배열 출력
- `outputCount > 1`: JSON 배열로 래핑하여 N개 인스턴스 출력
- 최대 100개까지 지원
- 루트 타입이 `List`이고 다중 변형(variant)이 가능한 경우 → 확장 배열 생성

### 3.10 변형(Variant) 생성

- Union 타입: 각 멤버별 변형 생성
- InlineObject: 필드별 변형의 카르테시안 프로덕트
- 중복 변형은 JSON 직렬화 기준으로 제거

---

## 4. 타입 시스템 모델 (Type Reference Hierarchy)

### 4.1 `TypeReference` Sealed Interface

```
TypeReference
├── Primitive(primitiveKind: TypePrimitiveKind)
├── Named(name: String)
├── ListReference(elementType: TypeReference)
├── MapReference(keyType: TypeReference, valueType: TypeReference)
├── Nullable(wrappedType: TypeReference)
├── InlineObject(fields: List<TypeField>)
├── Union(members: List<TypeReference>)
└── AnyValue (object)
```

### 4.2 `TypePrimitiveKind` Enum

| 값 | 설명 |
|----|------|
| `STRING` | 문자열 |
| `INTEGER` | 정수 |
| `DECIMAL` | 실수 |
| `NUMBER` | 숫자 (정수/실수 구분 불가 시) |
| `BOOLEAN` | 불리언 |

### 4.3 `TypeDeclaration` Data Class

| 필드 | 타입 | 설명 |
|------|------|------|
| `name` | `String` | 타입 이름 |
| `declarationKind` | `TypeDeclarationKind` | 선언 종류 |
| `fields` | `List<TypeField>` | 필드 목록 |
| `superTypeNames` | `List<String>` | 부모 타입 이름 목록 |
| `enumValues` | `List<String>` | enum 값 목록 |
| `aliasedTypeReference` | `TypeReference?` | type alias 대상 |

### 4.4 `TypeDeclarationKind` Enum

| 값 | Java | Kotlin | TypeScript | Go |
|----|------|--------|------------|-----|
| `CLASS` | `class` | `class` / `data class` | - | - |
| `INTERFACE` | - | - | `interface` | `interface` |
| `RECORD` | `record` | - | - | - |
| `STRUCT` | - | - | - | `struct` |
| `ENUM` | `enum` | `enum class` | `enum` | - |
| `TYPE_ALIAS` | - | - | `type X = ...` | `type X interface{ ... }` (유니온) |

### 4.5 `TypeField` Data Class

| 필드 | 타입 | 설명 |
|------|------|------|
| `name` | `String` | 정규화된 필드 이름 |
| `typeReference` | `TypeReference` | 필드 타입 참조 |
| `isOptional` | `Boolean` | optional 여부 (기본 `false`) |
| `sourceName` | `String` | 원본 이름 (기본 = `name`) |

---

## 5. UI 구조 스펙

### 5.1 다이얼로그 구조

```
ConvertTypeDialog (DialogWrapper)
└── ConvertTypeDialogPresenter
    └── ConvertTypeDialogView
        └── JBTabbedPane (980x700)
            ├── Tab 0: "JSON -> Type"
            │   └── JsonToTypeDialogPresenter
            │       └── JsonToTypeDialogView (980x640)
            └── Tab 1: "Type -> JSON"
                └── TypeToJsonDialogPresenter
                    └── TypeToJsonDialogView (980x640)
```

### 5.2 JSON → Type 탭 레이아웃

```
┌─────────────────────────────────────────────────────┐
│ [Language ▼]  Root: [________]  ☑ Nullable fields   │
│ Naming: [camelCase ▼]  Annotation: [Jackson ▼]      │
│ ☐ Experimental Go union (Go 선택 시만 표시)           │
├────────────────────────┬────────────────────────────┤
│  JSON Data (입력)       │  Preview (읽기전용)         │
│  ┌──────────────────┐  │  ┌──────────────────────┐  │
│  │ JsonEditorView   │  │  │ CodePreviewPanel     │  │
│  │ (에디터 컴포넌트)  │  │  │ (4가지 상태:          │  │
│  │                  │  │  │  Empty/Loading/       │  │
│  │                  │  │  │  Error/Success)       │  │
│  └──────────────────┘  │  └──────────────────────┘  │
├────────────────────────┴────────────────────────────┤
│            [Insert to Editor]  [Copy]  [Cancel]      │
└─────────────────────────────────────────────────────┘
```

- 스플리터 비율: 50/50
- 최소 크기: 860x420

### 5.3 Type → JSON 탭 레이아웃

```
┌─────────────────────────────────────────────────────┐
│ [Language ▼]  Fields: [Req+Opt ▼]  ☑ Nullable null  │
│ ☑ Realistic data  Count: [1]  Format: [Prettify ▼]  │
├────────────────────────┬────────────────────────────┤
│  Type Declaration       │  Preview (읽기전용)         │
│  ┌──────────────────┐  │  ┌──────────────────────┐  │
│  │ CodeInputPanel   │  │  │ CodePreviewPanel     │  │
│  │ (에디터 텍스트필드) │  │  │                      │  │
│  │ (플레이스홀더 지원) │  │  │                      │  │
│  └──────────────────┘  │  └──────────────────────┘  │
├────────────────────────┴────────────────────────────┤
│            [Insert to Editor]  [Copy]  [Cancel]      │
└─────────────────────────────────────────────────────┘
```

- 스플리터 비율: 46/54
- 최소 크기: 860x420
- 플레이스홀더: 언어별 타입 선언 예시 표시

### 5.4 CodePreviewPanel 상태 머신

```
EMPTY → LOADING → SUCCESS
                → ERROR
     → LOADING → SUCCESS
                → ERROR
```

| 상태 | UI 표현 |
|------|---------|
| `EMPTY` | 회색 이탤릭 플레이스홀더 메시지 |
| `LOADING` | "Generating..." + 애니메이션 아이콘 |
| `ERROR` | 빨간색 에러 메시지 |
| `SUCCESS` | 모노스페이스 폰트, 읽기전용 텍스트 + Copy 버튼 활성화 |

### 5.5 프리뷰 갱신 메커니즘 (`ConvertPreviewExecutor`)

- **디바운스**: JSON→Type 300ms / Type→JSON 500ms
- **비동기 실행**: pooled thread에서 변환 수행
- **EDT 콜백**: 결과를 EDT(Event Dispatch Thread)에서 UI 반영
- **요청 시퀀스**: `previewRequestSequence`로 stale 결과 폐기
- **취소**: 새 요청 시 이전 알람 취소

---

## 6. 액션 및 진입점

### 6.1 등록된 액션

| 액션 ID | 클래스 | 설명 | 단축키 |
|---------|--------|------|--------|
| `ConvertJsonToTypeAction` | `ConvertJsonToTypeAction` | JSON→Type 변환 | - |
| `ConvertTypeToJsonAction` | `ConvertTypeToJsonAction` | Type→JSON 변환 | - |
| `TypeConversionAction` | `TypeConversionAction` | 통합 변환 다이얼로그 | `Ctrl+Shift+J` / `Cmd+Shift+J` |

### 6.2 액션 동작 흐름

```
사용자 트리거 (단축키/메뉴/툴바)
  ↓
TypeConversionAction.actionPerformed()
  ↓
JsonHelperActionUtils.resolveConvertActionContext()  // 입력 텍스트 + 삽입 대상 결정
  ↓
(1MB 이상?) → LargeFileWarningDialog 표시
  ↓
ConvertTypeInputSeedResolver.seed()  // JSON인지 타입 코드인지 판별
  ↓
ConvertTypeDialog 표시
  ↓
(JSON이면 Tab 0, 타입 코드면 Tab 1 활성화)
```

### 6.3 입력 시드 판별 (`ConvertTypeInputSeedResolver`)

- Jackson `objectMapper.readTree()` 시도
- 파싱 성공 → JSON 입력으로 판별 (Tab 0)
- 파싱 실패 → 타입 코드로 판별 (Tab 1)

### 6.4 결과 처리 (`ConvertResultUtils`)

| 기능 | 메서드 | 설명 |
|------|--------|------|
| 클립보드 복사 | `copyToClipboard()` | 결과를 클립보드에 복사 + 알림 |
| 에디터 삽입 | `insertIntoTarget()` | 현재 에디터에 결과 삽입 (선택 영역 대체) |
| 새 탭 삽입 | `insertToNewTab()` | JSONinja 패널에 새 탭으로 결과 추가 |

**삽입 대상 (`ConvertInsertionTarget`) 구현:**
- `EditorConvertInsertionTarget`: IntelliJ 에디터에 WriteCommandAction으로 삽입
- `JsonEditorViewConvertInsertionTarget`: 커스텀 JsonEditorView에 텍스트 설정

---

## 7. 설정 영속화

### 7.1 저장 위치

- `JsoninjaSettingsState` (`@State` PersistentStateComponent)
- 저장 파일: `jsoninja.xml` (프로젝트 레벨)

### 7.2 JSON → Type 설정 필드

| 설정 키 | 타입 | 기본값 | 설명 |
|---------|------|--------|------|
| `jsonToTypeLastLanguage` | `String` | `"KOTLIN"` | 마지막 선택 언어 |
| `convertTypeLastLanguage` | `String` | `"KOTLIN"` | 공유 언어 선택 |
| `jsonToTypeDefaultNaming` | `String` | `"AUTO"` | 네이밍 전략 (`"AUTO"` = 언어별 기본값) |
| `jsonToTypeNullableByDefault` | `Boolean` | `true` | nullable 필드 허용 |
| `jsonToTypeAnnotationStyle` | `String` | `"NONE"` | 어노테이션 스타일 |
| `jsonToTypeUsesExperimentalGoUnionTypes` | `Boolean` | `false` | Go 유니온 실험 기능 |

### 7.3 Type → JSON 설정 필드

| 설정 키 | 타입 | 기본값 | 설명 |
|---------|------|--------|------|
| `typeToJsonLastLanguage` | `String` | `"KOTLIN"` | 마지막 선택 언어 |
| `typeToJsonFieldsMode` | `String` | `"REQUIRED_AND_OPTIONAL"` | 필드 포함 모드 |
| `typeToJsonIncludesNullableFieldWithNullValue` | `Boolean` | `true` | nullable null 포함 |
| `typeToJsonUsesRealisticSampleData` | `Boolean` | `true` | Faker 사용 여부 |
| `typeToJsonOutputCount` | `Int` | `1` | 출력 개수 |
| `typeToJsonFormatState` | `String` | `"PRETTIFY"` | 출력 포맷 |

---

## 8. 유효성 검사

### 8.1 JSON → Type 검증 (`JsonToTypeDialogValidator`)

| 검증 항목 | 조건 | 에러 메시지 |
|----------|------|------------|
| 빈 입력 | `sourceJsonText.isBlank()` | `"No JSON content entered"` |
| 잘못된 루트 이름 | `!isValidIdentifier(rootTypeName)` | `"Root name must be a valid identifier"` |
| 잘못된 JSON | `objectMapper.readTree()` 실패 | `"Invalid JSON: {error}"` |

- 유효한 식별자 패턴: `[A-Za-z_][A-Za-z0-9_]*`

### 8.2 Type → JSON 검증 (`TypeToJsonDialogValidator`)

| 검증 항목 | 조건 | 에러 메시지 |
|----------|------|------------|
| 빈 입력 | `typeDeclarationText.isEmpty()` | `"No type declaration entered"` |
| 출력 수 범위 | `outputCount !in 1..100` | `"Output count must be between 1 and 100"` |

---

## 9. 아키텍처 패턴

### 9.1 MVP (Model-View-Presenter)

```
Dialog (DialogWrapper)
  → Presenter (로직 조율, 서비스 호출)
    → View (UI 레이아웃, 콜백 바인딩)
      → Component (재사용 UI 컴포넌트)
```

### 9.2 서비스 계층

```
Action → Presenter → ConversionService → InferenceContext / Renderer
                   → SettingsAdapter → JsoninjaSettingsState
                   → Validator
                   → PreviewExecutor (비동기)
```

### 9.3 Type → JSON 파이프라인

```
소스 코드
  → TypeDeclarationAnalyzerService.analyzeTypeDeclaration()
    → LanguageTypeDeclarationAnalyzer.analyze()  (언어별)
    → TypeDeclarationParsingSupport (파싱 유틸)
  → TypeAnalysisResult
  → TypeToJsonDocumentBuilder.createRootJsonNode()
    → TypeDeclarationFieldResolver.collectFields()  (상속 해결)
    → TypeToJsonNodeGenerator.createJsonNode()  (재귀 생성)
      → SampleValueGenerator  (샘플 값)
  → JsonNode → String (포맷팅)
```

### 9.4 JSON → Type 파이프라인

```
JSON 문자열
  → Jackson ObjectMapper.readTree()
  → JsonToTypeInferenceContext.inferRootTypeReference()
    → 재귀 타입 추론
    → 타입 등록 & 중복 제거
  → typeDeclarations: Map<String, TypeDeclaration>
  → JsonToTypeRenderer.render()
    → 경고 헤더
    → Import 블록
    → 타입 선언 렌더링 (언어별)
  → String (최종 출력)
```

### 9.5 Disposal 체인

```
ConvertTypeDialog.dispose()
  → ConvertTypeDialogPresenter.dispose()
    → JsonToTypeDialogPresenter.dispose()
      → ConvertPreviewExecutor.dispose()
      → JsonToTypeDialogView.dispose()
    → TypeToJsonDialogPresenter.dispose()
      → ConvertPreviewExecutor.dispose()
      → TypeToJsonDialogView.dispose()
```

---

## 10. 파일 목록

### 10.1 모델

| 파일 | 역할 |
|------|------|
| `model/typeConversion/TypeConversionModels.kt` | 핵심 도메인 모델 (TypeReference, TypeDeclaration 등) |
| `model/SupportedLanguage.kt` | 지원 언어 enum + NamingConvention |

### 10.2 서비스

| 파일 | 역할 |
|------|------|
| `services/typeConversion/JsonToTypeConversionService.kt` | JSON→Type 변환 진입점 |
| `services/typeConversion/JsonToTypeInferenceContext.kt` | JSON 분석 → TypeReference 추론 |
| `services/typeConversion/JsonToTypeRenderer.kt` | TypeReference → 소스 코드 렌더링 |
| `services/typeConversion/JsonToTypeNamingSupport.kt` | 이름 정규화/네이밍 규칙 |
| `services/typeConversion/JsonToTypeSupport.kt` | 타입 유틸리티 함수 |
| `services/typeConversion/JsonToTypeConversionOptions.kt` | JSON→Type 옵션 + 어노테이션 스타일 |
| `services/typeConversion/TypeToJsonGenerationService.kt` | Type→JSON 변환 진입점 |
| `services/typeConversion/TypeToJsonGenerationOptions.kt` | Type→JSON 옵션 |
| `services/typeConversion/TypeToJsonDocumentBuilder.kt` | JSON 문서 구성 오케스트레이터 |
| `services/typeConversion/TypeToJsonNodeGenerator.kt` | TypeReference → JsonNode 생성 |
| `services/typeConversion/SampleValueGenerator.kt` | Faker 기반 샘플 값 생성 |
| `services/typeConversion/TypeDeclarationAnalyzerService.kt` | 언어별 분석기 오케스트레이터 |
| `services/typeConversion/LanguageTypeDeclarationAnalyzer.kt` | 분석기 인터페이스 |
| `services/typeConversion/JavaTypeDeclarationAnalyzer.kt` | Java 파싱 |
| `services/typeConversion/KotlinTypeDeclarationAnalyzer.kt` | Kotlin 파싱 |
| `services/typeConversion/TypeScriptTypeDeclarationAnalyzer.kt` | TypeScript 파싱 |
| `services/typeConversion/GoTypeDeclarationAnalyzer.kt` | Go 파싱 |
| `services/typeConversion/TypeDeclarationParsingSupport.kt` | 파싱 유틸리티 (공유) |
| `services/typeConversion/TypeDeclarationFieldResolver.kt` | 상속 기반 필드 수집 |
| `services/typeConversion/TypeDeclarationAssetMetadataProvider.kt` | Tree-sitter 에셋 메타데이터 |

### 10.3 UI — 다이얼로그

| 파일 | 역할 |
|------|------|
| `ui/dialog/convertType/ConvertTypeDialog.kt` | 최상위 DialogWrapper |
| `ui/dialog/convertType/ConvertTypeDialogView.kt` | 탭 구조 뷰 |
| `ui/dialog/convertType/ConvertTypeDialogPresenter.kt` | 탭 전환 조율 |
| `ui/dialog/convertType/JsonToTypeDialogView.kt` | JSON→Type 뷰 |
| `ui/dialog/convertType/JsonToTypeDialogPresenter.kt` | JSON→Type 프레젠터 |
| `ui/dialog/convertType/TypeToJsonDialogView.kt` | Type→JSON 뷰 |
| `ui/dialog/convertType/TypeToJsonDialogPresenter.kt` | Type→JSON 프레젠터 |
| `ui/dialog/convertType/ConvertPreviewExecutor.kt` | 비동기 프리뷰 실행기 |
| `ui/dialog/convertType/ConvertTypeInputSeedResolver.kt` | 입력 유형 판별 |
| `ui/dialog/convertType/JsonToTypeDialogValidator.kt` | JSON→Type 검증 |
| `ui/dialog/convertType/TypeToJsonDialogValidator.kt` | Type→JSON 검증 |
| `ui/dialog/convertType/JsonToTypeDialogSettingsAdapter.kt` | JSON→Type 설정 어댑터 |
| `ui/dialog/convertType/TypeToJsonDialogSettingsAdapter.kt` | Type→JSON 설정 어댑터 |
| `ui/dialog/convertType/model/JsonToTypeDialogConfig.kt` | JSON→Type 설정 모델 |
| `ui/dialog/convertType/model/TypeToJsonDialogConfig.kt` | Type→JSON 설정 모델 |

### 10.4 UI — 컴포넌트

| 파일 | 역할 |
|------|------|
| `ui/component/convertType/LanguageSelectorComponent.kt` | 언어 선택 드롭다운 |
| `ui/component/convertType/CodeInputPanel.kt` | 코드 입력 에디터 |
| `ui/component/convertType/CodePreviewPanel.kt` | 프리뷰 패널 (4상태) |

### 10.5 액션

| 파일 | 역할 |
|------|------|
| `actions/TypeConversionAction.kt` | 핵심 액션 로직 |
| `actions/ConvertJsonToTypeAction.kt` | JSON→Type 액션 위임 |
| `actions/ConvertTypeToJsonAction.kt` | Type→JSON 액션 위임 |

### 10.6 유틸리티

| 파일 | 역할 |
|------|------|
| `utils/ConvertResultUtils.kt` | 결과 삽입/복사 유틸리티 |

### 10.7 리소스

| 파일 | 역할 |
|------|------|
| `resources/tree-sitter/asset-manifest.json` | 언어별 tree-sitter 에셋 경로 |
| `resources/tree-sitter/queries/{language}/type-declarations.scm` | Tree-sitter 쿼리 파일 |
| `resources/messages/LocalizationBundle.properties` | 다국어 메시지 |

---

## 11. 제약 사항 및 알려진 한계

1. **날짜 타입 미지원**: ISO-8601 날짜 문자열을 감지하지만 별도 Date 타입으로 변환하지 않음 (STRING 유지)
2. **단순 단수화**: 배열 필드명에서 마지막 `s`만 제거 (불규칙 복수형 미처리)
3. **유니온 타입 제한**: Java/Kotlin에서 유니온 타입 미지원 → `Object`/`Any`로 fallback
4. **Go 유니온 실험적**: `usesExperimentalGoUnionTypes` 플래그 필요
5. **정적 필드 제외**: Java의 `static` 필드는 파싱에서 제외
6. **Getter/Setter 미분석**: 필드 선언만 분석 (메서드 기반 프로퍼티 감지 불가)
7. **제네릭 타입 제한**: 다단계 제네릭 (`List<Map<String, List<T>>>`) 파싱 가능하나 매우 복잡한 구조는 `AnyValue`로 fallback 가능
8. **1MB 파일 경고**: 1MB 초과 입력 시 경고 다이얼로그 표시
9. **최대 깊이 10**: 기본 최대 중첩 깊이 제한
