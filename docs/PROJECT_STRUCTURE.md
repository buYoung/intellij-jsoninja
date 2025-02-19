# JSON Helper 2 프로젝트 구조

## 기본 디렉토리 구조

```
src/
├── main/
│   ├── kotlin/
│   │   └── com/
│   │       └── buyoung/
│   │           └── jsonhelper/
│   │               ├── actions/           # 플러그인 액션 클래스들
│   │               │   ├── PrettifyAction.kt     # JSON 포맷팅 액션
│   │               │   ├── UglifyAction.kt       # JSON 압축 액션
│   │               │   ├── EscapeAction.kt       # JSON 이스케이프 액션
│   │               │   └── UnescapeAction.kt     # JSON 언이스케이프 액션
│   │               │
│   │               ├── services/         # 비즈니스 로직 서비스
│   │               │   ├── JsonFormatter.kt      # JSON 포맷팅 서비스
│   │               │   └── JsonEscapeService.kt  # JSON 이스케이프 처리 서비스
│   │               │
│   │               ├── ui/              # UI 관련 컴포넌트
│   │               │   ├── JsonToolWindow.kt     # JSON 도구 창
│   │               │   └── dialogs/             # 대화상자 컴포넌트
│   │               │
│   │               ├── utils/           # 유틸리티 클래스들
│   │               │   └── JsonUtils.kt          # JSON 관련 유틸리티
│   │               │
│   │               └── settings/        # 플러그인 설정 관련
│   │                   └── JsonHelperSettings.kt # 설정 관리 클래스
│   │
│   └── resources/
│       └── META-INF/
│           ├── plugin.xml              # 플러그인 설정 파일
│           ├── pluginIcon.svg          # 플러그인 아이콘
│           └── messages/               # 다국어 지원
│               └── JsonHelperBundle.properties
│
└── test/
    └── kotlin/
        └── com/
            └── buyoung/
                └── jsonhelper/
                    ├── actions/        # 액션 테스트
                    ├── services/       # 서비스 테스트
                    └── utils/          # 유틸리티 테스트

```

## 주요 컴포넌트 설명

### 1. Actions (액션)
- `PrettifyAction`: JSON 문자열을 보기 좋게 포맷팅
- `UglifyAction`: JSON 문자열을 한 줄로 압축
- `EscapeAction`: JSON 문자열 이스케이프 처리
- `UnescapeAction`: 이스케이프된 JSON 문자열 복원

### 2. Services (서비스)
- `JsonFormatter`: JSON 포맷팅 관련 핵심 로직
  - Prettify 기능
  - Uglify 기능
- `JsonEscapeService`: JSON 이스케이프 처리 관련 로직
  - 문자열 이스케이프
  - 문자열 언이스케이프

### 3. UI Components (UI 컴포넌트)
- `JsonToolWindow`: 플러그인의 메인 도구 창
  - JSON 입력 영역
  - 도구 버튼들
  - 결과 출력 영역

### 4. Utils (유틸리티)
- `JsonUtils`: 공통으로 사용되는 JSON 관련 유틸리티 함수들
  - JSON 유효성 검사
  - JSON 파싱
  - 오류 처리

### 5. Settings (설정)
- `JsonHelperSettings`: 플러그인 설정 관리
  - 기본 들여쓰기 설정
  - 테마 설정
  - 단축키 설정

## 리소스 구성

### META-INF
- `plugin.xml`: 플러그인 메타데이터 및 설정
  - 액션 등록
  - 도구 창 등록
  - 의존성 정의
- `messages/`: 다국어 지원을 위한 리소스 번들
  - 한국어 지원
  - 영어 지원

## 테스트 구조

테스트 코드는 메인 코드와 동일한 패키지 구조를 따르며, 각 컴포넌트별로 테스트 클래스를 구성합니다.

### 테스트 범위
- 단위 테스트: 각 컴포넌트의 독립적인 기능 테스트
- 통합 테스트: 여러 컴포넌트의 상호작용 테스트
- UI 테스트: 사용자 인터페이스 동작 테스트

## 개발 가이드라인

1. 각 기능은 독립적인 액션 클래스로 구현
2. 비즈니스 로직은 서비스 클래스에 구현
3. UI 관련 코드는 ui 패키지에 구현
4. 공통 유틸리티는 utils 패키지에 구현
5. 설정 관련 코드는 settings 패키지에 구현

## 확장성

새로운 기능 추가 시:
1. 해당 기능의 액션 클래스 생성
2. 필요한 서비스 클래스 구현
3. UI 컴포넌트 추가
4. plugin.xml에 새로운 액션 등록
