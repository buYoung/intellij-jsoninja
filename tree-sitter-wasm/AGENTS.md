# AGENTS.md

## 1. Overview
`tree-sitter-wasm`는 상위 플러그인 저장소가 사용하는 tree-sitter 기반 WASM 브리지 크레이트입니다. 정수 핸들, 선형 메모리 버퍼, 언어별 query 자산을 이용해 호스트가 파서 생성, 파싱, query 실행을 호출할 수 있게 합니다.

## 2. Folder Structure
- `src`: 런타임과 FFI 진입점이 모여 있는 핵심 구현입니다.
  - `lib.rs`: `#[no_mangle] extern "C"` 함수만 노출하고 각 요청을 내부 모듈로 위임합니다.
  - `memory.rs`: 호스트와 게스트 사이의 버퍼 읽기/쓰기, 포인터·길이 패킹, 호스트 대체 메모리 경로를 관리합니다.
  - `parser.rs`: `Parser` 생성, 소스 파싱, 트리 핸들 수명 관리를 담당합니다.
  - `query.rs`: tree-sitter query 컴파일과 capture 수집을 수행합니다.
  - `runtime_state.rs`: 전역 `RuntimeState`, 마지막 오류 메시지 저장소, 호스트 테스트용 메모리 저장소를 보관합니다.
  - `language.rs`: 지원 언어 목록과 언어 식별자 매핑을 정의합니다.
  - `error.rs`, `handle_store.rs`, `query_result.rs`, `utils.rs`: 오류 타입, 핸들 저장소, query 결과 직렬화, WASM 전용 할당 보조 함수를 제공합니다.
- `tests/fixtures`: 언어별 샘플 소스와 기대 capture 목록을 둡니다.
  - `java`, `kotlin`, `typescript`, `go`: 각 언어의 `sample.*`와 `expected-captures.txt`를 함께 유지합니다.
- `.cargo/config.toml`: 호스트 `cargo test`와 `wasm32-wasip1` 빌드가 공존할 수 있도록 도구 경로와 `WASI_SYSROOT`를 설정합니다.
- `Cargo.toml`: `cdylib` 출력 형식, 기본 feature, tree-sitter grammar 의존성, release 최적화 설정을 정의합니다.
- `build.rs`: 제한된 감시 파일만 등록해 불필요한 재빌드를 줄입니다.

## 3. Core Behaviors & Patterns
- **얇은 FFI 진입점**: 외부에서 호출하는 함수는 모두 `src/lib.rs`에만 두고, 입력 포인터를 문자열로 복원한 뒤 `parser` 또는 `query` 모듈로 넘깁니다. 새 FFI API를 추가할 때도 `lib.rs`에서는 인자 변환과 반환 형식만 처리하고 실질 로직은 하위 모듈에 둡니다.
- **핸들 기반 상태 수명 관리**: 파서와 구문 트리는 직접 노출하지 않고 `HandleStore<T>`가 발급한 양의 정수 핸들로만 다룹니다. `parser_create`가 파서 핸들을 만들고, `parse_source`가 트리 핸들을 따로 만들어 두 수명을 분리하므로 새 상태 객체도 같은 저장소 패턴을 따라야 합니다.
- **전역 상태와 마지막 오류 메시지 분리**: 공유 상태는 `OnceLock<RuntimeState>` 아래의 `Mutex`로 관리하고, 마지막 오류 문자열은 `thread_local!` 저장소에 별도로 둡니다. 호출 시작 시 오류 메시지를 지우고 실패 시에만 채우는 흐름이므로, 새 진입점도 같은 초기화 순서를 유지해야 합니다.
- **오류 평탄화와 panic 차단**: 내부 로직은 `WasmResult<T>`와 `WasmRuntimeError`를 사용하지만, 외부 경계에서는 `catch_unwind`로 panic을 막고 `i32` 반환은 `-1`, `i64` 반환은 패킹된 오류 코드로 변환합니다. 상세 오류는 반환값이 아니라 `get_last_error()`로 조회하는 계약이므로, 새로운 실패 경로도 오류 코드와 메시지를 함께 남겨야 합니다.
- **호스트·게스트 메모리 이중 경로**: 실제 WASM 빌드에서는 선형 메모리에 직접 접근하고, 비 WASM 환경에서는 `HostMemoryStore`가 포인터를 흉내 내며 테스트를 지원합니다. 메모리 관련 변경은 두 경로를 함께 고려해야 하며, 포인터 유효성 검사는 항상 `memory.rs` 경계에서 끝내는 것이 원칙입니다.
- **Query 결과 직렬화 일관성**: `query_execute`는 capture를 모은 뒤 `QueryExecutionResult`를 JSON 문자열로 직렬화해 반환합니다. 결과 포맷은 수동 이스케이프 로직에 의존하므로 필드를 늘리거나 이름을 바꿀 때는 호스트 측 디코딩 계약과 `src/tests.rs`의 비교 로직을 같이 확인해야 합니다.
- **상위 저장소 자산 의존 테스트**: 테스트는 `tests/fixtures`의 샘플 소스만 읽지 않고, 상위 저장소의 `src/main/resources/tree-sitter/queries/...` query 파일도 `include_str!`로 끌어옵니다. 따라서 query 자산 경로나 언어 추가 작업은 이 크레이트와 상위 리소스 디렉터리를 함께 맞춰야 합니다.

## 4. Conventions
- **명명 규칙**: 함수는 `parser_create`, `parse_source`, `query_execute`, `supported_languages_json`처럼 동사와 대상을 드러내는 `snake_case`를 사용합니다. 타입은 `SupportedLanguage`, `WasmRuntimeError`, `QueryExecutionResult`, `ParserHandleState`처럼 `PascalCase`와 책임 접미사를 사용합니다.
- **모듈 역할 분리**: 한 파일은 한 책임에 가깝게 유지합니다. 새 기능을 추가할 때는 `lib.rs`에 로직을 키우기보다 `memory`, `parser`, `query`, `runtime_state`처럼 경계가 분명한 모듈 옆에 배치합니다.
- **외부 노출 위치 고정**: `pub extern "C"`와 `#[no_mangle]`는 `lib.rs`에만 둡니다. 내부 모듈은 Rust 친화적인 함수 시그니처를 유지하고 FFI 친화 형식으로의 변환은 진입점에서 끝냅니다.
- **오류 타입 통일**: 복구 가능한 실패는 문자열이나 `Option`으로 흩뿌리지 말고 `WasmRuntimeError::new(code, message)`로 생성합니다. 오류 코드는 `WasmErrorCode`에 모으고, 메시지는 호스트가 그대로 읽을 수 있는 문장으로 유지합니다.
- **공유 상태 접근 규칙**: 공유 저장소 접근은 `runtime_state()`를 통해서만 가져오고, 핸들 조회는 `HandleStore`의 `insert`/`get`/`get_mut`/`remove`를 사용합니다. 직접 전역 정적 변수나 별도 맵을 추가하기보다 기존 저장소에 상태 타입을 더하는 방식을 우선합니다.
- **수동 직렬화 관례**: query 결과는 직렬화 라이브러리를 추가하지 않고 `query_result.rs`의 문자열 조합과 `escape_json_string`으로 만듭니다. 같은 경로의 출력 포맷을 바꿀 때는 새 의존성을 도입하기보다 기존 수동 포맷을 유지하는 쪽을 우선 검토합니다.
- **테스트 자산 배치**: 언어별 테스트 데이터는 `tests/fixtures/<language>/` 아래에 샘플 코드와 기대 capture 텍스트를 한 쌍으로 둡니다. 새 언어를 추가하면 `SupportedLanguage`, fixture 디렉터리, 상위 query 자산 경로를 함께 확장합니다.
- **빌드와 검증 기준**: grammar 의존성은 `Cargo.toml`의 `language-grammars` feature에 모으고, WASM 빌드 환경 변수는 `.cargo/config.toml`에 둡니다. 이 크레이트의 기본 타입 안전성 확인은 `cargo check`를 기준으로 삼습니다.

## 5. Working Agreements
공통 작업 규약은 루트 `/AGENTS.md`를 따릅니다.
