# JSONinja 프로젝트 구조

이 문서는 디렉토리/패키지 구조와 주요 컴포넌트에 대한 **단일 출처(Source of Truth)** 입니다.
개발 환경 설정, 빌드, 기능 구현 방법은 [DEVELOPMENT_GUIDE.md](./DEVELOPMENT_GUIDE.md)를 참고하세요.

## 1. 디렉토리 구조

```
src/
├── main/
│   ├── kotlin/com/livteam/jsoninja/
│   │   ├── actions/              # IDE 액션 (툴바, 메뉴, 단축키)
│   │   │   └── editor/           # 에디터 컨텍스트 메뉴 액션
│   │   ├── diff/                 # JSON Diff 확장 (DiffExtension, 키 정렬)
│   │   ├── extensions/           # IDE 확장 포인트 (붙여넣기 전처리 등)
│   │   ├── icons/                # 아이콘 등록 및 리소스 참조
│   │   ├── listeners/            # 애플리케이션/스타트업 액티비티
│   │   ├── model/                # 데이터 모델 및 Enum
│   │   │   └── typeConversion/   # 타입 변환 도메인 모델
│   │   ├── services/             # 비즈니스 로직 서비스
│   │   │   ├── schema/           # JSON Schema 검증·데이터 생성
│   │   │   ├── treesitter/       # tree-sitter WASM 런타임 브리지
│   │   │   └── typeConversion/   # JSON ↔ 타입 코드 변환 로직
│   │   ├── settings/             # 플러그인 설정 상태 및 UI 연동
│   │   ├── ui/                   # UI 컴포넌트
│   │   │   ├── component/        # Presenter/View 컴포넌트
│   │   │   │   ├── convertType/  # 타입 변환 입력·미리보기 패널
│   │   │   │   ├── editor/       # JSON 에디터(텍스트/트리) 컴포넌트
│   │   │   │   ├── jsonQuery/    # JSON 쿼리 입력 컴포넌트
│   │   │   │   ├── main/         # 메인 패널·툴바
│   │   │   │   ├── model/        # UI 상태 모델
│   │   │   │   └── tab/          # 탭 관리 컴포넌트
│   │   │   ├── dialog/           # 대화상자
│   │   │   │   ├── convertType/  # 타입 변환 다이얼로그
│   │   │   │   ├── generateJson/ # 랜덤·스키마 기반 JSON 생성 다이얼로그
│   │   │   │   └── loadJson/     # API로 JSON 불러오기 다이얼로그
│   │   │   ├── diff/             # Diff 요청 체인·가상 파일·창
│   │   │   └── onboarding/       # 온보딩 튜토리얼 UI
│   │   ├── utils/                # 공통 유틸리티
│   │   └── LocalizationBundle.kt # 메시지 번들 접근자
│   ├── java/com/livteam/jsoninja/
│   │   └── ui/toolWindow/        # JsoninjaToolWindowFactory (도구 창 진입점, Java)
│   └── resources/
│       ├── META-INF/plugin.xml   # 플러그인 설정 파일
│       ├── icons/                # 아이콘 리소스 (classic, expui, languages)
│       ├── images/onboarding/    # 온보딩 이미지
│       ├── messages/             # 다국어 리소스 번들 (ko, en, ja, zh_CN)
│       ├── tree-sitter/queries/  # 타입 선언 파싱용 tree-sitter 쿼리(.scm)
│       └── wasm/tree-sitter/     # tree-sitter WASM 바이너리
└── test/
    └── kotlin/com/livteam/jsoninja/   # 메인 패키지와 동일한 구조의 테스트 코드
```

> 도구 창 진입점인 `JsoninjaToolWindowFactory`만 Java로 작성되어 `src/main/java`에 위치하며, 나머지 코드는 모두 Kotlin입니다.

## 2. 주요 컴포넌트 설명

### 2.1 Actions (`actions/`)
- `PrettifyJsonAction` / `UglifyJsonAction`: JSON 포맷팅·압축
- `EscapeJsonAction` / `UnescapeJsonAction`: 이스케이프 처리·복원
- `ShowJsonDiffAction` / `ShowJsonDiffInEditorTabAction` / `ShowJsonDiffInWindowAction`: JSON Diff 보기(에디터 탭 또는 별도 창)
- `SortJsonDiffKeysOnceAction`: Diff 화면의 키 정렬
- `GenerateRandomJsonAction`: 랜덤·스키마 기반 JSON 생성 다이얼로그 호출
- `TypeConversionAction` / `ConvertJsonToTypeAction` / `ConvertTypeToJsonAction`: JSON ↔ 타입 코드 변환
- `LoadJsonFromApiAction`: API 응답을 JSON으로 불러오기
- `AddTabAction` / `CloseTabAction` / `OpenJsonFileAction` / `OpenSettingsAction` / `CopyJsonQueryAction`: 탭·파일·설정·쿼리 복사
- `editor/`: 에디터 컨텍스트 메뉴 전용 Prettify/Uglify/Escape/Unescape 액션

### 2.2 Services (`services/`)
- `JsonFormatterService`: 포맷팅(Prettify/Compact/Uglify)·이스케이프 핵심 로직
- `JsonQueryService`: JSON 쿼리 처리 (Jayway JsonPath, JMESPath, jq)
- `JsonDiffService`: JSON 비교 로직
- `RandomJsonDataCreator`: 랜덤 JSON 데이터 생성
- `JsonObjectMapperService`: Jackson `ObjectMapper` 제공
- `JsoninjaCoroutineScopeService`: 플러그인 전용 코루틴 스코프
- `OnboardingService` / `OnboardingStateService`: 온보딩 진행·상태 관리
- `BundledResourceService`: 번들 리소스 로딩
- `schema/`: JSON Schema 검증(`JsonSchemaValidationService`) 및 스키마 기반 데이터 생성(`JsonSchemaDataGenerationService`)
- `treesitter/`: tree-sitter WASM 런타임(`TreeSitterWasmRuntime`)과 메모리 브리지
- `typeConversion/`: JSON ↔ 타입 코드 변환(`JsonToTypeConversionService`, `TypeToJsonGenerationService` 등)

### 2.3 UI (`ui/`, `java/.../ui/toolWindow/`)
- `toolWindow/JsoninjaToolWindowFactory`: 메인 도구 창 진입점 (Java)
- `component/main/`: 메인 패널 Presenter/View 및 툴바 팩토리
- `component/editor/`: JSON 텍스트·트리 에디터 컴포넌트
- `component/jsonQuery/`: JSON 쿼리 입력 컴포넌트
- `component/tab/`: 탭 관리 컴포넌트
- `dialog/`: 타입 변환·JSON 생성·API 불러오기·대용량 파일 경고 등 대화상자
- `diff/`: Diff 요청 체인·가상 파일·별도 창
- `onboarding/`: 온보딩 튜토리얼 다이얼로그

### 2.4 Settings (`settings/`)
- `JsoninjaSettingsState`: 플러그인 설정 상태(`PersistentStateComponent`)
- `JsoninjaSettingsConfigurable`: 설정 UI 연동
- `JsoninjaSettingsListener`: 설정 변경 리스너

### 2.5 Model (`model/`)
- `JsonFormatState`, `JsonQueryType`, `JsonDiffDisplayMode`, `JsonIconPack`, `SupportedLanguage` 등 도메인 Enum
- `typeConversion/`: 타입 변환 관련 데이터 모델

### 2.6 Utils (`utils/`)
- `JsonHelperUtils`: 일반적인 JSON 헬퍼 함수
- `JsonPathHelper`: JSON 경로 처리 유틸리티
- `ConvertResultUtils`: 변환 결과 처리 유틸리티

## 3. 패키지 설계 원칙

1. **패키지 명명**: 모든 코드는 `com.livteam.jsoninja.*` 하위에 둡니다.
2. **Action**: 사용자 상호작용은 독립적인 `*Action` 클래스로 구현합니다.
3. **Service**: 비즈니스 로직은 `@Service`로 캡슐화하며, 대부분 Project 레벨 서비스로 등록합니다.
4. **UI**: 가능하면 Presenter/View 패턴으로 로직과 화면을 분리합니다.
5. **다국어**: UI 문자열은 `messages/` 번들과 `LocalizationBundle`을 통해 관리합니다.
6. **신규 기능 콜로케이션**: 새 도메인 기능은 `services/<feature>`, `ui/dialog/<feature>`, `model/<feature>`처럼 기능 단위 하위 패키지로 묶습니다(예: `typeConversion`, `generateJson`).
