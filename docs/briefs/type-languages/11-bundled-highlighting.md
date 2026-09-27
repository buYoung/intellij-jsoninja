# 언어 플러그인 없이 동작하는 11개 언어 강조

## 요구사항 정정

사용자는 언어 플러그인 유무와 무관한 구문 강조를 주목적으로 요청했다. 기존 구현은 새 일곱 언어에서 키워드·리터럴·주석만 구분했고, 기존 네 언어는 IDE 제공자에 의존했다. Python 화면의 타입과 필드가 거의 같은 색으로 보인 원인이다. 이를 의도한 완료 상태로 설명한 것은 사용자 목적을 충분히 반영하지 못한 판단이었다.

이번 수정은 내장 강조의 범위를 11개 언어 전체와 선언 역할 구분으로 확장한다. 앞선 문서의 원래 네 언어 대체 경로 제외·식별자 일괄 처리 제한보다 이 정정이 우선한다. 기존 저장 값, WASM ID, 생성 코드와 JSON 변환 의미는 유지한다.

## 구현

- Kotlin, Java, TypeScript, Go 내장 프로필을 추가하고 기존 C, C++, C#, Python, Rust, Scala, JSDoc 프로필을 보완한다. 언어 플러그인이 없어도 입력과 읽기 전용 미리보기에 같은 강조 경로를 적용한다.
- 기존 렉서 위에 `TypeCodeRoleLexer`를 구성한다. 언어별 선언 구문과 앞뒤 토큰을 이용해 타입 선언·참조, 내장/표준 타입, 필드, 함수 선언·호출을 분류한다. Python의 `TypedDict`, `NotRequired`, `int`, `float`, `str`, `list`와 타입 주석 앞 필드를 구분한다.
- 문자열·주석 내부는 기존 토큰 경계를 보존한다. Kotlin 보간 문자열, Java 텍스트 블록, Go 원시 문자열, TypeScript 템플릿 문자열도 각 내장 프로필에서 처리한다. JSDoc은 태그·타입 식·속성 이름을 분리한다.
- 새 PSI 모델·WASM 강조 쿼리·외부 의존성·서버·심볼 해석은 도입하지 않는다. 문서 전체를 정규식으로 다시 검사하지 않으며, 렉서 상태에 문맥과 제한된 중첩 깊이를 저장한다. 줄 안의 선행/후행 구문 편집은 안전한 시작 위치부터 다시 스캔한다. 긴 스캔은 취소를 확인한다.
- 실제 언어 파일형식과 `ParserDefinition`을 제공하는 IDE 언어 지원은 계속 재사용한다. 단순 키워드 테이블은 선언 역할을 제공하지 못하므로 내장 강조보다 우선하지 않는다. 기본 C 키워드 제공자가 내장 강조를 가리던 경로도 변경했다.

| 역할 | 사용하는 IDE 표준 키 |
| --- | --- |
| 타입 선언·참조 | `DefaultLanguageHighlighterColors.CLASS_NAME` |
| 내장·표준 타입 | `DefaultLanguageHighlighterColors.PREDEFINED_SYMBOL` |
| 필드·속성 선언 | `DefaultLanguageHighlighterColors.INSTANCE_FIELD` |
| 함수 선언 | `DefaultLanguageHighlighterColors.FUNCTION_DECLARATION` |
| 함수 호출 | `DefaultLanguageHighlighterColors.FUNCTION_CALL` |
| 키워드·문자열·숫자·주석·기호 | 기존 역할별 `DefaultLanguageHighlighterColors` 키 |

운영 코드에는 RGB 팔레트나 사용자 스킴 변경을 추가하지 않는다. 내장 강조는 Language Defaults의 사용자 설정을 따르고, 네이티브 강조는 해당 언어의 설정을 따른다. 스킴이 서로 다른 역할에 같은 색을 지정하면 그 선택도 존중한다. 이 분류는 편집기에서 제공하는 구문 기반 표시이며 컴파일러 수준의 심볼 해석 결과를 주장하지 않는다.

## 검증 구성

- `src/test/resources/typeConversion/highlighting/fallback/`에 11개 언어의 독립적인 역할 골든을 작성했다. 소스에 기대 범위와 역할을 명시하고 실제 렉서 결과와 비교한다. 실행 결과를 자동으로 기대값으로 저장하지 않는다.
- 기존 11개 생성 소스 골든에서도 Root 타입과 id 필드 역할이 존재하는지 확인한다. 사용자 Python 화면의 `NotRequired[float | None]`, `NotRequired[bool | str | None]`, `Root = list[RootItem]`를 별도로 검사한다.
- 모든 언어에서 전체 스캔과 모든 토큰 경계의 새 렉서 재시작 결과를 비교하고, 잘린 UTF-16 입력 범위·Unicode·미완성 리터럴·취소를 검사한다. 한 줄 Go 필드와 Kotlin 보간, 문맥 키워드를 필드명으로 쓰는 경우도 포함한다.
- 실제 `CodeInputPanel`과 `CodePreviewPanel`에서 11개 확장자의 파일형식을 PlainText로 강제해 네이티브 제공자를 차단한다. PSI 문서가 없는 것을 단정하고, 두 편집기에서 타입/필드 역할과 사용자 색상·배경 갱신을 확인한다. 설치된 언어 플러그인을 실제 제거했다고 표현하지 않는다.
- 실제 편집기에서 구분자·주석·문자열 닫기 문자를 삽입/삭제하고 선언 역할의 회복을 확인한다. 기존 문서·선택·undo·해제 및 네이티브 Kotlin/Java 강조 검증도 유지한다.
- 최초 실행의 C++ 생성 소스 검사에서 `std::int64_t id`의 필드 분류 오류가 드러났다. 네임스페이스 `::`와 타입 주석 `:`의 문맥을 분리하여 수정했다.

## API와 언어 근거

IC 243.21565.193 SDK 소스에서 `LexerBase`, `DefaultLanguageHighlighterColors`의 위 키, `LanguageFileType.getLanguage`, `LanguageParserDefinitions.INSTANCE`, 상위 `LanguageExtension.forLanguage`의 공개 계약과 제한을 확인했다. 새 내부 API 사용·언어 플러그인 클래스 참조·반사 호출은 없다.

- [Kotlin 키워드와 문맥 키워드](https://kotlinlang.org/docs/keyword-reference.html)
- [Go 토큰 및 문자열 문법](https://go.dev/ref/spec#Tokens)
- [Java 17 텍스트 블록](https://docs.oracle.com/javase/specs/jls/se17/html/jls-3.html#jls-3.10.6)
- [Python 3.11 TypedDict와 타입 표기](https://docs.python.org/3.11/library/typing.html#typing.TypedDict)

## 실행 결과

작업 디렉터리: `/Users/buyong/.codex/worktrees/c8e7/json-helper2`.

강조 집중 실행 `./gradlew compileKotlin test --tests 'com.livteam.jsoninja.ui.component.convertType.highlighting.*'`는 30개 통과했다. 이후 한 줄 Go·Kotlin 보간 사례를 추가하여 전체 회귀 검증을 수행했다.

전체 명령: `./gradlew --init-script build/reports/type-preview-highlighting/isolated-sandbox.gradle compileKotlin test buildPlugin verifyPlugin`. 실행 중인 사용자 IDE와 경로가 충돌하지 않도록 이전 수정에서 검증한 별도 sandboxContainer를 사용했다. `BUILD SUCCESSFUL in 5m 58s`, 전체 테스트 **192개/29개 클래스**, 실패·오류·건너뜀 0. 그중 강조 테스트는 31개다.

- IC 243.28141.41, 251.29188.72, 252.28539.97 및 IU 253.33813.55, 261.27258.48, 262.10968.63, 263.5701.42 모두 `Compatible`. 내부 API·Experimental API·호환성 문제 보고서는 없다. 263의 기존 `LocalizationBundle : DynamicBundle(String)` 사용 중단 예정 경고 한 건은 유지된다.
- ZIP: `build/distributions/intellij-jsoninja-1.15.0.zip`, 32,463,583 bytes, SHA-256 `92eb59ef08d3f42bf21267dc9e6d5ed8191ca5c6bf29557c9ec04b735b01acd4`.
- ZIP 안에 11개 내장 프로필과 역할 렉서가 모두 존재하고 테스트 클래스·강조 골든은 포함되지 않는다. 필수 의존성은 기존 platform/lang/JSON 모듈뿐이며 언어별 플러그인을 추가하지 않았다. WASM은 앞선 검증과 같은 바이트다.
- 테스트 XML·요약, API/호환성 요약, 배포물 식별자는 `build/reports/bundled-type-highlighting/`에 보존했다. `git diff --check`도 통과했다.
- 기존 WASM Gradle 작업의 configuration-cache 직렬화 경고는 남아 있다. 이 경고 및 Verifier의 IDE 레이아웃 경고와 실제 성공 종료·호환성 판정은 구분했다.

실제 Editor 인스턴스의 자동 검증과 사용자가 연 창의 수동 화면 검증은 구분한다. 실제 창의 재실행·테마 전환·동적 언로드를 수행했다고 주장하지 않는다.
