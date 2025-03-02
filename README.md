# JSON Helper 2

![빌드 상태](https://github.com/buYoung/json-helper2/workflows/Build/badge.svg)
[![버전](https://img.shields.io/jetbrains/plugin/v/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)
[![다운로드](https://img.shields.io/jetbrains/plugin/d/MARKETPLACE_ID.svg)](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)

## 소개

JSON Helper 2는 JetBrains IDE를 위한 JSON 처리 플러그인입니다. 이 플러그인은 개발자들이 JSON 데이터를 더 쉽게 다룰 수 있도록 다음과 같은 핵심 기능들을 제공합니다:

### 주요 기능

- **JSON Prettify**: JSON 데이터를 보기 좋게 포맷팅
- **JSON Uglify**: JSON 데이터를 한 줄로 압축
- **JSON Escape**: JSON 문자열 이스케이프 처리
- **JSON Unescape**: 이스케이프된 JSON 문자열을 원래 형태로 복원
- **JMES Path**: JSON 데이터 내에서 JMES Path를 사용한 고급 검색 및 필터링 기능

## 개발 체크리스트
- [x] [IntelliJ Platform Plugin Template][template] 프로젝트 생성 완료
- [x] [템플릿 문서][template] 검토
- [x] [pluginGroup](./gradle.properties)과 [pluginName](./gradle.properties), [plugin.xml의 id](./src/main/resources/META-INF/plugin.xml)와 [소스 패키지](./src/main/kotlin) 수정
- [x] `README`의 플러그인 설명 수정 ([참고][docs:plugin-description])
- [x] [법적 동의사항](https://plugins.jetbrains.com/docs/marketplace/legal-agreements.html?from=IJPluginTemplate) 검토
- [ ] 처음으로 [플러그인 수동 배포](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html?from=IJPluginTemplate)
- [ ] 위의 README 배지에 `MARKETPLACE_ID` 설정. JetBrains Marketplace에 플러그인이 게시된 후 얻을 수 있음
- [ ] [플러그인 서명](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html?from=IJPluginTemplate) 관련 [시크릿](https://github.com/JetBrains/intellij-platform-plugin-template#environment-variables) 설정
- [ ] [배포 토큰](https://plugins.jetbrains.com/docs/marketplace/plugin-upload.html?from=IJPluginTemplate) 설정
- [ ] 새로운 기능과 수정사항에 대한 알림을 받기 위해 [IntelliJ Platform Plugin Template][template]의 <kbd>Watch</kbd> 버튼 클릭

<!-- Plugin description -->
JSONinja is a powerful JSON processing plugin for JetBrains IDEs.  
It provides advanced tools for JSON data manipulation, allowing you to work efficiently without disrupting your development workflow.

Key Features:  
• JSON Prettify: Format JSON data for improved readability  
• JSON Uglify: Compress JSON data into a single line for transmission or storage  
• JSON Escape/Unescape: Process and restore escaped JSON strings  
• JMES Path Support: Find and filter specific values within complex JSON data  
• Multi-tab Interface: Work with multiple JSON documents simultaneously  
• Advanced Formatting Options: Customize indentation size, key sorting, and more  

[Under Development]  
• Enhanced JSON Validation  
• JSON Schema Support  
• JSON to Various Format Converters  
• History and Favorites Management  

JSONinja is ideal for analyzing REST API responses, editing configuration files, and performing data transformation tasks. Streamline your JSON-related work with our intuitive and efficient interface!

**The Birth of JSONinja** 😄  
Inspired by the need for reliable JSON tools! When we faced compatibility challenges with existing solutions after an IDE update, we saw an opportunity to create something new. Our development journey took a bit longer than expected, but we're excited to finally share JSONinja with the community. Sometimes the best tools come from personal necessity!  
<!-- Plugin description end -->

## 설치 방법

- IDE 내장 플러그인 시스템 사용:
  
  <kbd>설정/환경설정</kbd> > <kbd>플러그인</kbd> > <kbd>마켓플레이스</kbd> > <kbd>"json-helper2" 검색</kbd> >
  <kbd>설치</kbd>
  
- JetBrains 마켓플레이스 사용:

  [JetBrains 마켓플레이스](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID)에서 IDE가 실행 중인 경우 <kbd>Install to ...</kbd> 버튼을 클릭하여 설치하세요.

  또는 JetBrains 마켓플레이스에서 [최신 릴리즈](https://plugins.jetbrains.com/plugin/MARKETPLACE_ID/versions)를 다운로드하고
  <kbd>설정/환경설정</kbd> > <kbd>플러그인</kbd> > <kbd>⚙️</kbd> > <kbd>디스크에서 플러그인 설치...</kbd>를 통해 수동으로 설치하세요.

- 수동 설치:

  [최신 릴리즈](https://github.com/buYoung/json-helper2/releases/latest)를 다운로드하고
  <kbd>설정/환경설정</kbd> > <kbd>플러그인</kbd> > <kbd>⚙️</kbd> > <kbd>디스크에서 플러그인 설치...</kbd>를 통해 수동으로 설치하세요.


---
이 플러그인은 [IntelliJ Platform Plugin Template][template]을 기반으로 제작되었습니다.

[template]: https://github.com/JetBrains/intellij-platform-plugin-template
[docs:plugin-description]: https://plugins.jetbrains.com/docs/intellij/plugin-user-experience.html#plugin-description-and-presentation
