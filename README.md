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

## 개발 체크리스트
- [x] [IntelliJ Platform Plugin Template][template] 프로젝트 생성 완료
- [ ] [템플릿 문서][template] 검토
- [ ] [pluginGroup](./gradle.properties)과 [pluginName](./gradle.properties), [plugin.xml의 id](./src/main/resources/META-INF/plugin.xml)와 [소스 패키지](./src/main/kotlin) 수정
- [ ] `README`의 플러그인 설명 수정 ([참고][docs:plugin-description])
- [ ] [법적 동의사항](https://plugins.jetbrains.com/docs/marketplace/legal-agreements.html?from=IJPluginTemplate) 검토
- [ ] 처음으로 [플러그인 수동 배포](https://plugins.jetbrains.com/docs/intellij/publishing-plugin.html?from=IJPluginTemplate)
- [ ] 위의 README 배지에 `MARKETPLACE_ID` 설정. JetBrains Marketplace에 플러그인이 게시된 후 얻을 수 있음
- [ ] [플러그인 서명](https://plugins.jetbrains.com/docs/intellij/plugin-signing.html?from=IJPluginTemplate) 관련 [시크릿](https://github.com/JetBrains/intellij-platform-plugin-template#environment-variables) 설정
- [ ] [배포 토큰](https://plugins.jetbrains.com/docs/marketplace/plugin-upload.html?from=IJPluginTemplate) 설정
- [ ] 새로운 기능과 수정사항에 대한 알림을 받기 위해 [IntelliJ Platform Plugin Template][template]의 <kbd>Watch</kbd> 버튼 클릭

<!-- Plugin description -->
이 멋진 IntelliJ Platform 플러그인은 여러분이 가진 훌륭한 아이디어를 구현하기 위한 것입니다.

이 특별한 섹션은 빌드 과정에서 [Gradle](/build.gradle.kts)에 의해 추출될 [plugin.xml](/src/main/resources/META-INF/plugin.xml) 파일의 소스입니다.

모든 것이 정상적으로 작동하도록 하기 위해 `<!-- ... -->` 섹션을 제거하지 마세요.
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
