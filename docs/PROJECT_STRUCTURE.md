# JSON Ninja 프로젝트 구조

## 기본 디렉토리 구조

```
src/
├── main/
│   ├── kotlin/
│   │   └── com/
│   │       └── livteam/
│   │           └── jsoninja/
│   │               ├── actions/           # 플러그인 액션 클래스들 (메뉴, 단축키 등)
│   │               ├── diff/              # JSON Diff 뷰어 확장 로직
│   │               ├── extensions/        # IDE 확장 포인트 (붙여넣기 전처리 등)
│   │               ├── icons/             # 아이콘 등록 및 리소스 참조
│   │               ├── listeners/         # 애플리케이션 라이프사이클 리스너
│   │               ├── model/             # 데이터 모델 및 Enum
│   │               ├── services/          # 비즈니스 로직 서비스
│   │               ├── settings/          # 플러그인 설정 및 UI
│   │               ├── ui/                # UI 컴포넌트
│   │               │   ├── component/     # Presenter/View 컴포넌트 (Editor, Tab 등)
│   │               │   ├── dialog/        # 대화상자 (JSON 생성, 경고 등)
│   │               │   ├── diff/          # Diff UI 헬퍼
│   │               │   └── toolWindow/    # 도구 창 팩토리
│   │               └── utils/             # 공통 유틸리티
│   │
│   └── resources/
│       ├── META-INF/
│       │   └── plugin.xml              # 플러그인 설정 파일
│       ├── icons/                      # 아이콘 리소스
│       └── messages/                   # 다국어 지원 리소스 번들
│
└── test/
    └── kotlin/
        └── com/
            └── livteam/
                └── jsoninja/           # 메인 패키지 구조와 동일한 테스트 코드
```

## 주요 컴포넌트 설명

### 1. Actions (`actions/`)
- `PrettifyJsonAction`: JSON 문자열 포맷팅
- `UglifyJsonAction`: JSON 문자열 압축
- `EscapeJsonAction`: JSON 문자열 이스케이프 처리
- `UnescapeJsonAction`: 이스케이프된 JSON 복원
- `ShowJsonDiffAction`: JSON Diff 보기
- `GenerateRandomJsonAction`: 랜덤 JSON 데이터 생성

### 2. Services (`services/`)
- `JsonFormatterService`: 포맷팅 및 압축 핵심 로직
- `JsonQueryService`: JSON 쿼리 처리 (Jayway, JMESPath)
- `JsonDiffService`: JSON 비교 로직
- `RandomJsonDataCreator`: 랜덤 JSON 데이터 생성 로직

### 3. UI Components (`ui/`)
- `toolWindow/JsoninjaToolWindowFactory`: 메인 도구 창 진입점
- `component/`: 재사용 가능한 UI 컴포넌트 (에디터, 쿼리창, 탭 관리)
- `dialog/`: 각종 대화상자 (대용량 파일 경고, JSON 생성 등)

### 4. Settings (`settings/`)
- `JsoninjaSettingsState`: 플러그인 설정 상태 관리
- `JsoninjaSettingsConfigurable`: 설정 UI 연동

### 5. Utils (`utils/`)
- `JsonHelperUtils`: 일반적인 JSON 헬퍼 함수
- `JsonPathHelper`: JSON 경로 처리 유틸리티

## 개발 가이드라인

1. **패키지 명명**: `com.livteam.jsoninja.*` 사용
2. **Action**: 사용자 상호작용은 독립적인 Action 클래스로 구현
3. **Service**: 비즈니스 로직은 서비스(`@Service`)로 캡슐화
4. **UI**: 가능하면 Presenter/View 패턴을 사용하여 로직 분리
5. **다국어**: UI 문자열은 `messages/` 번들을 통해 관리
