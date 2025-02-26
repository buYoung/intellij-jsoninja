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

## 벤치마크 결과

JSON 라이브러리 성능 비교 벤치마크를 통해 Jackson 라이브러리가 가장 우수한 성능을 보여주었습니다. 이 결과를 바탕으로 JSON Helper 2는 Jackson 라이브러리를 기반으로 구현되었습니다.

### 성능 비교 (일반적인 경향)

#### 직렬화 성능
- 작은 데이터: Gson ≈ Moshi > Jackson
- 중간 데이터: Jackson > Gson > Moshi
- 큰 데이터: Jackson > Gson > Moshi

#### 역직렬화 성능
- 작은 데이터: Moshi > Gson > Jackson
- 중간 데이터: Jackson > Gson > Moshi
- 큰 데이터: Jackson > Gson > Moshi

### 기본 JSON 작업 성능
- Beautify(포맷팅): Jackson > Gson > Moshi
- Minify(압축): Jackson > Gson > Moshi
- Escape(이스케이프): Jackson > Gson > Moshi
- Unescape(이스케이프 해제): Jackson > Gson > Moshi

### 데이터 크기별 성능
- 중간 크기 데이터: Jackson > Gson > Moshi
- 대용량 데이터: Jackson > Gson > Moshi

### 전체 성능 순위
1. Jackson - 대부분의 작업에서 가장 빠른 성능을 보여줍니다.
2. Gson - Jackson보다는 느리지만 Moshi보다 빠르며, 중간 수준의 성능을 제공합니다.
3. Moshi - 전반적으로 가장 느린 성능을 보이지만 Kotlin 친화적인 API를 제공합니다.

자세한 벤치마크 결과는 [kotlin-json-library-bench](https://github.com/buYoung/kotlin-json-library-bench) 저장소에서 확인할 수 있습니다.

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
