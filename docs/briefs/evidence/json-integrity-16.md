# 16 — PR 검증과 검증 산출물 전달 기록

2026-09-16, M3a/M3b. 상태: **ready (로컬 구현/정적 검증)**. 외부 PR/태그 작업과 실제 서명·배포는 실행하지 않았다.

## Baseline

- 기준 HEAD: `300f83fbc1c8a0ce4da81b505e19345972f84fc2` + 아래 CI 변경 파일. 01–13/15의 수용 기록이 통합됐다. 14의 구현과 기존 검증은 완료됐으나 네이티브 창 관찰은 대기 중이다. 부모에서 이와 독립적인 16 로컬 작업의 시작 조건을 먼저 보정했다. 14나 부모 전체 수용을 완료로 바꾸지 않았다.
- build.yml은 태그만 받았고 build/test/verify가 각각 checkout과 WASM 빌드를 수행했다. 업로드는 원본 ZIP을 해제한 내용이었다. release.yml은 다른 checkout에서 skipWasmBuild=true로 publishPlugin을 실행했다. 실제 배포 불일치는 관찰하지 않았으며 소스로 확인한 재빌드 경로가 수정 대상이다.
- 설치된 IntelliJ Platform Gradle Plugin 2.11.0의 sources.jar에서 PublishPluginTask/SignPluginTask/VerifyPluginTask를 읽었다. 세 작업 모두 공개 archiveFile 입력을 제공하고 PublishPluginTask는 기본적으로 buildPlugin과 signPlugin에 의존한다. task 인자를 추측하지 않고 이 계약으로 연결했다.
- sources.jar: `/Users/buyong/.gradle/caches/modules-2/files-2.1/org.jetbrains.intellij.platform/intellij-platform-gradle-plugin/2.11.0/ed11d69c9ebad96fe4758eaaa238a6dd8e8cd2d9/intellij-platform-gradle-plugin-2.11.0-sources.jar`.

## Implementation

- PR와 기존 버전 태그를 Build에 연결했다. 한 작업 공간에서 기존 compile/check/test/package/verifier와 Rust 검사를 수행하고 성공한 ZIP만 전달한다. 테스트와 검증 작업 사이의 재빌드 출처 차이를 없앴다.
- PR build는 contents: read, checkout persist-credentials=false이며 secrets 참조가 없다. 쓰기 권한이 있는 Qodana/서명/초안 생성은 push + refs/tags/v 조건으로 제한했다. 기존 태그 초안→수동 공개→Release 흐름을 유지했다.
- plugin_artifact.py는 커밋/태그, 버전, ZIP/WASM/실제 테스트 샌드박스 WASM 해시, 도구 버전, 현재 버전의 IDE 판정/보고서 해시를 기록하고 재검사한다. 이전 버전 보고서와 누락/불일치 기록을 받지 않는다. 이 파일은 산출물 전달 기능이며 신규 테스트/린트 자동화가 아니다.
- 태그 작업은 전달받은 검증 ZIP을 설정된 경우 서명하고 최종 바이트를 다시 verifyPlugin에 입력한다. 원래 빌드와 최종 검증의 도구 기록을 함께 보존한다. 서명 결과와 원본 중 실제 선택한 ZIP, provenance, 보고서를 release bundle로 묶는다.
- Release는 해당 태그의 bundle을 내려받아 checkout 커밋/태그와 해시를 확인하고 같은 ZIP을 publishPlugin에 지정한다. CHANGELOG 수정이 패키지를 다시 만들지 않는다. 배포 직전에 같은 검사를 반복하며 검증 bundle이 없는 이전 릴리스에는 임의 재빌드로 대체하지 않는다.
- Gradle 속성 verifiedPluginArchive가 있을 때 signPlugin/verifyPlugin/publishPlugin의 archiveFile을 지정한다. publishPlugin의 기본 빌드/서명/CHANGELOG 의존성은 제거한다. 속성이 없는 기존 수동 명령의 기본 동작은 유지한다.
- 릴리스 본문은 이벤트 JSON/환경 값에서 읽어 파일로 전달하며 셸 코드에 직접 삽입하지 않는다. 플러그인 버전 1.13.0, 호환성 243–263.*, JVM 목표/도구 체인 17을 변경하지 않았다.
- 새 download-artifact@v8의 현재 run/name/path 동작은 [공식 문서](https://github.com/actions/download-artifact)에서 확인했고 기존 upload-artifact@v7과 연결했다.

## Acceptance

작업 디렉터리: `/Users/buyong/workspace/private/json-helper2` (cargo test만 `tree-sitter-wasm`).

| 명령 | 실제 결과 |
| --- | --- |
| `./gradlew compileKotlin` | exit 0 |
| `./gradlew test --rerun-tasks --no-build-cache --no-configuration-cache` | exit 0, 15 suite / 76개, 실패·오류·스킵 0. 기존 Undo/Redo 8개 포함 |
| `cargo test` | exit 0, 12개 통과, 실패·ignored 0 |
| `./gradlew buildPlugin` | exit 0, 현재 1.13.0 ZIP 생성 |
| `./gradlew verifyPlugin` | exit 0, Plugin Verifier 1.410, 아래 7개 모두 Compatible |
| `./gradlew check --no-build-cache --no-configuration-cache` | exit 0, 기존 test 결과 사용 및 Kover report/verify 수행 |
| `python3 /tmp/json-integrity-workflow-check.py` | exit 0, Ruby/Psych YAML, 각 run의 bash -n, 트리거/권한/전달 연결 확인 |
| `python3 /tmp/json-integrity-artifact-check.py` | exit 0, 로컬 producer→handoff 바이트 일치 및 아래 6개 불일치 차단 |
| `python3 /tmp/json-integrity-hotspot-check.py` | exit 0, 기록된 계약 파일 65개 모두 최종 소스와 일치하는 선행 기록 존재 |
| `python3 /tmp/json-integrity-integrated-check.py` | exit 0, 아래 통합 흐름 16개 관찰 PASS |
| `python3 /tmp/json-integrity-toolchains.py` | exit 0, 실제 버전 기록 |

- 테스트는 12에서 발견한 캐시된 테스트 클래스 문제를 피하도록 동일 실행에서 재컴파일했다. 테스트 사례/파일은 이번 하위 작업에서 바꾸지 않았다.
- 추가 명령 `./gradlew -I /tmp/json-integrity-artifact-inputs.gradle inspectVerifiedArtifactInputs -PverifiedPluginArchive=build/distributions/intellij-jsoninja-1.13.0.zip --no-configuration-cache`: exit 0. 세 archiveFile이 지정한 파일에 일치했다. signPlugin/verifyPlugin의 의존성은 initializeIntellijPlatformPlugin만, publishPlugin은 빈 목록이었다. 실제 서명/배포 작업이나 자격 증명 값을 실행·조회하지 않은 메타데이터 검사다.

| 검사 IDE 빌드 | 판정 |
| --- | --- |
| `IC-243.28141.41` | Compatible |
| `IC-251.29188.72` | Compatible |
| `IC-252.28539.97` | Compatible |
| `IU-253.33813.55` | Compatible |
| `IU-261.27258.48` | Compatible |
| `IU-262.10315.125` | Compatible |
| `IU-263.4732.28` | Compatible. 1 usage of deprecated API |

- 263의 경고는 기존 LocalizationBundle의 `com.intellij.DynamicBundle(String)` 생성자 사용이다. 호환성 실패가 아니며 이번 범위에서 최소 플랫폼을 올리거나 억제 설정을 넣지 않았다.
- IDE SDK를 준비할 때 일부 layout의 존재하지 않는 classPath 경고가 나왔으나 최종 7개 판정과 Gradle 종료 코드는 위와 같다. 네이티브 IDE 사용 검증을 의미하지 않는다.
- buildPlugin의 searchable-options 수집 중 IC 2024.3 내장 GradleJvmSupportMatrix에서 `IllegalArgumentException: 25` 경고/오류 로그가 있었다. 219 configurables 수집과 패키징은 성공했다. buildPlugin/verifyPlugin에서 기존 WASM 작업의 구성 캐시 직렬화 경고로 캐시가 폐기됐으며 빌드 종료는 0이었다. CI에서는 해당 기존 경고 경로를 피하도록 --no-configuration-cache를 사용한다.

### 산출물 출처와 전달

- 원본 ZIP: `build/distributions/intellij-jsoninja-1.13.0.zip`, SHA-256 `e104babdf5121d36616994125d697a09f60bc3a6b643009189cd795d6b9a8237`.
- 실제 테스트 플러그인 JAR, 원본 ZIP 내부, 생성 리소스의 WASM SHA-256: `6da5d6d1aee43bcbbe2eaae798dcbbd58dae9f51a1e0b80b19320dd68973ae76`. 11의 Rust 수정 재빌드 결과와 일치한다.
- ZIP의 항목 32개를 확인했고 .codemap/.antigravitycli/docs/briefs/evals 경로는 없었다. 오래된 로컬 1.11.2/1.11.3 ZIP은 삭제하거나 선택하지 않았다.
- `build/verified-local` → `build/verified-local-handoff`의 verified-plugin.zip은 원본 ZIP과 바이트가 같았다. provenance에는 local 실행임을 기록했다. 실제 서명 결과를 만들었다는 주장이 아니다.
- 임시 복사본에서 커밋, 태그, ZIP 바이트, tested_wasm_sha256, 판정 파일, 도구 기록을 각각 바꿨다. 6경로 모두 해당 mismatch 오류와 exit 1로 차단됐으며 원본 검증 bundle은 보존됐다.
- 기록 파일: `build/verified-local-handoff/provenance.json`, `toolchains.txt`, `verifier-reports/`. 임시 실행 로그/명령은 `/tmp/json-integrity-artifact-results.json`에 있다. 외부 실행의 run ID나 게시 결과로 표시하지 않는다.
- 로컬 도구: macOS 15.7.1/aarch64, Gradle 9.3.1, launcher GraalVM 21.0.2, Kotlin 플러그인 2.3.10, 컴파일 toolchain Adoptium 17.0.15, 테스트/검증용 IC JBR 21.0.5, rustc/cargo 1.98.1, Homebrew clang 22.1.6, wasm32-wasip1. Gradle 자체의 내장 Kotlin 2.2.21과 프로젝트 Kotlin 플러그인을 구분했다.

### 통합 동작과 남은 범위

- 실제 헤드리스 BasePlatformTestCase에서 JsonTabContextFactory와 문서/쿼리/트리/변환 presenter를 연결했다. `0.12345678901234567890123456789` 및 이스케이프 문자열 → sort+compact → null 쿼리 → clear → 원문 편집 후 재쿼리 → 소유 편집기의 Undo/Redo → 표시 중인 트리의 빈 배열/최신 값 → TypeScript 원본 키 미리보기 → clipboard → ConvertResultUtils의 실제 삽입까지 통과했다. 호스트 문서는 Undo/Redo에 영향받지 않았고 삽입 때만 선택한 대상에 결과가 적용됐다.
- 첫 통합 실행은 압축 배열의 정확한 공백 폭을 잘못 가정한 수동 단언 1개가 실패했다. 소스 변경 없이 같은 줄에 배열 원소가 유지되는 조건으로 수정했고 최종 실행의 16개 관찰이 모두 통과했다.
- 01–15의 공유 파일은 최신 해시와 기존 수용 기록 연결이 유지된다. 04→05 쿼리 요청/원문, 08→09→11 런타임/미리보기, 12→13→15 스키마/HTTP 변경은 각 후속 기록과 전체 검사로 연결했다.
- 14의 네이티브 창 관찰은 브리프의 UI 조작 제한에 따라 여전히 not-ready다. PR/태그 작업의 GitHub 실행, 실제 서명, 업로드/릴리스/Marketplace 게시도 수행하지 않았다. 16의 로컬 수용과 부모 전체 수용을 구분한다.

### 검증 소스 SHA-256

- `.github/workflows/build.yml`: `6934768765827f519d0a4b0f23c1a63910b473e2de08520430c0b4e6ec52029a`
- `.github/workflows/release.yml`: `e8b974707e1cb756bffe338f4332712380dbb2c52cd8b6e2caa35372570c4f06`
- `.github/scripts/plugin_artifact.py`: `33478e61aefcf908ab6173ca437296b7a1286a374c46f9d4cd56126aba745124`
- `build.gradle.kts`: `8cdd1e6a0d887f443d14519748ba50c306eebb5b2a592f285ca79c55779a7941`
