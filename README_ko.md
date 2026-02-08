# JSON Ninja

![빌드 상태](https://github.com/buYoung/intellij-jsoninja/workflows/Build/badge.svg)
[![버전](https://img.shields.io/jetbrains/plugin/v/26715.svg)](https://plugins.jetbrains.com/plugin/26715)
[![다운로드](https://img.shields.io/jetbrains/plugin/d/26715.svg)](https://plugins.jetbrains.com/plugin/26715)
[![ko-fi](https://ko-fi.com/img/githubbutton_sm.svg)](https://ko-fi.com/C0C21TTT0W)


## 소개

JSON Ninja는 JetBrains IDE를 위한 JSON 처리 플러그인입니다. 이 플러그인은 개발자들이 JSON 데이터를 더 쉽게 다룰 수 있도록 다음과 같은 핵심 기능들을 제공합니다:

### 주요 기능

- **JSON Prettify**: 
  - JSON 데이터를 보기 좋게 포맷팅
  - (+1.0.3) 붙여넣기시 자동 포멧팅
- **JSON Uglify**: JSON 데이터를 한 줄로 압축
- **JSON Escape**: JSON 문자열 이스케이프 처리
- **JSON Unescape**: 이스케이프된 JSON 문자열을 원래 형태로 복원
- **JMES Path / Jayway JsonPath**: JSON 데이터 내에서 선택 가능한 쿼리 엔진으로 고급 검색 및 필터링
- **JSON Diff Viewer**: 에디터 탭 또는 별도 창에서 자동 포맷팅·정렬 옵션을 포함한 JSON 비교
- **멀티탭 JSON 에디터**: 툴 윈도우에서 탭 추가/닫기로 여러 JSON 문서를 동시에 관리
- **JSON Generator**: 루트 타입, 깊이, 속성/요소 개수를 설정해 샘플 JSON 생성
- **Copy JSON Query**: 커서 위치 요소의 JsonPath/JMESPath를 컨텍스트 메뉴/hover로 빠르게 복사
- **대용량 처리 가드레일**: 붙여넣기 전처리와 Diff 포맷팅 시 크기 검증 및 선택적 경고 지원

## 개발 체크리스트
- [x] [IntelliJ Platform Plugin Template][template] 프로젝트 생성 완료
- [x] [템플릿 문서][template] 검토
- [x] [pluginGroup](./gradle.properties)과 [pluginName](./gradle.properties), [plugin.xml의 id](./src/main/resources/META-INF/plugin.xml)와 [소스 패키지](./src/main/kotlin) 수정
- [x] `README`의 플러그인 설명 수정 ([참고][docs:plugin-description])
- [x] [법적 동의사항](https://plugins.jetbrains.com/docs/marketplace/legal-agreements.html?from=IJPluginTemplate) 검토
- [x] 처음으로 [플러그인 수동 배포](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html?from=IJPluginTemplate)
- [x] 위의 README 배지에 `MARKETPLACE_ID` 설정. JetBrains Marketplace에 플러그인이 게시된 후 얻을 수 있음
- [ ] [플러그인 서명](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html?from=IJPluginTemplate) 관련 [시크릿](https://github.com/JetBrains/intellij-platform-plugin-template#environment-variables) 설정
- [ ] [배포 토큰](https://plugins.jetbrains.com/docs/marketplace/plugin-upload.html?from=IJPluginTemplate) 설정
- [ ] 새로운 기능과 수정사항에 대한 알림을 받기 위해 [IntelliJ Platform Plugin Template][template]의 <kbd>Watch</kbd> 버튼 클릭

<!-- Plugin description -->
JSONinja는 JetBrains IDE를 위한 강력한 JSON 처리 플러그인입니다.
JSON 데이터 조작을 위한 고급 도구를 제공하여, 개발 워크플로우를 방해하지 않으면서 효율적으로 작업할 수 있습니다.

주요 기능:  
• JSON Prettify: 가독성 향상을 위한 JSON 데이터 포맷팅  
• JSON Uglify: 전송이나 저장을 위해 JSON 데이터를 한 줄로 압축  
• JSON Escape/Unescape: 이스케이프된 JSON 문자열 처리 및 복원  
• JMES Path / Jayway JsonPath 지원: 복잡한 JSON 데이터 내에서 특정 값 검색 및 필터링  
• JSON Diff Viewer: 자동 포맷팅과 함께 에디터 탭/별도 창에서 비교  
• 멀티탭 인터페이스: 여러 JSON 문서를 동시에 작업  
• JSON Generator: 루트 타입, 깊이, 속성/요소 개수를 설정해 샘플 JSON 생성  
• Copy JSON Query: 현재 커서 위치의 JsonPath/JMESPath를 빠르게 복사  
• 고급 포맷팅 옵션: 들여쓰기 크기, 키 정렬, compact 배열, 붙여넣기 포맷 지정  

[개발 중인 기능]
• JSON 스키마 지원  
• JSON을 다양한 형식으로 변환하는 기능  
• 히스토리 및 즐겨찾기 관리  

JSONinja는 REST API 응답 분석, 구성 파일 편집, 데이터 변환 작업 수행에 이상적입니다. 직관적이고 효율적인 인터페이스를 통해 JSON 관련 작업을 간소화하세요!

**JSONinja의 탄생** 😄
신뢰할 수 있는 JSON 도구의 필요성에서 영감을 받았습니다! IDE 업데이트 후 기존 솔루션과의 호환성 문제에 직면했을 때, 우리는 새로운 것을 만들 기회를 발견했습니다. 개발 과정이 예상보다 조금 길어졌지만, 마침내 JSONinja를 커뮤니티와 공유하게 되어 기쁩니다. 때로는 최고의 도구는 개인적인 필요에서 탄생합니다!  
<!-- Plugin description end -->

## 설치 방법

- IDE 내장 플러그인 시스템 사용:
  
  <kbd>설정/환경설정</kbd> > <kbd>플러그인</kbd> > <kbd>마켓플레이스</kbd> > <kbd>"jsoninja" 검색</kbd> >
  <kbd>설치</kbd>
  
- JetBrains 마켓플레이스 사용:

  [JetBrains 마켓플레이스](https://plugins.jetbrains.com/plugin/26715)에서 IDE가 실행 중인 경우 <kbd>Install to ...</kbd> 버튼을 클릭하여 설치하세요.

  또는 JetBrains 마켓플레이스에서 [최신 릴리즈](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID/versions)를 다운로드하고
  <kbd>설정/환경설정</kbd> > <kbd>플러그인</kbd> > <kbd>⚙️</kbd> > <kbd>디스크에서 플러그인 설치...</kbd>를 통해 수동으로 설치하세요.

---
이 플러그인은 [IntelliJ Platform Plugin Template][template]을 기반으로 제작되었습니다.

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation
