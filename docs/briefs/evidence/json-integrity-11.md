# 11 — enum 리터럴과 선택 필드 주석 보존

2026-09-16, R2.5/R2.6. 상태: **ready**.

## Baseline

- 기준 HEAD: `8af2677`, 08의 트랜잭션/취소와 09의 현재 입력 미리보기 계약에서 시작했다. 부모에서 10과 독립적인 실행 순서와 공유 모델 작성자를 먼저 조정했다.
- `/tmp/json-integrity-type-output-baseline.py`로 기존 WASM과 최종 생성 서비스를 실행했다. `ACTIVE = "active"`가 `ACTIVE`로 출력됐고, 숫자 enum도 이름을 출력했다. bare enum member는 Rust 분석 결과에서 빠졌다. COMMENTED는 ALL과 같은 활성 필드를 출력했다.
- 기존 WASM SHA-256: `b4e99ff176b91ea7b36a7ec29c9c905cf34cdc281f847e761669b2e8972050c2`. 재현 로그는 `/tmp/json-integrity-type-output-baseline.log`에 남겼다.

## Implementation

- Rust TypeScript enum 분석에 명시 값 없는 멤버를 포함했다. 기존 IR `name`/`value_text`와 exported ABI/버퍼 소유권을 유지했다.
- Kotlin 디코더는 `value_text`를 읽고 TypeDeclaration의 기존 enumValues 이름 목록과 함께 기본값이 있는 enumLiteralValues를 제공한다. Java/Kotlin은 기존 이름 기반 생성이다.
- 문자열/유한 숫자 리터럴과 implicit 증가를 해석한다. 숫자는 TypeScript/JavaScript Number 의미를 따르므로 큰 정수의 +1 반올림도 같게 처리한다. 임의 계산식과 비유한 값은 다섯 언어 번들의 회복 가능한 진단으로 처리한다. [TypeScript enum 규칙](https://www.typescriptlang.org/docs/handbook/enums.html)을 참고했다.
- OPTIONAL 필드 위치는 생성 호출마다 소유하는 IdentityHashMap에 기록한다. COMMENTED는 이 정보로 JSON5 주석을 출력하고 후속 formatter가 주석을 없애지 않게 했다. REQUIRED/ALL 저장 enum 이름과 기존 생성 함수 시그니처는 유지했다.
- pretty 출력은 행 주석, minified 출력은 블록 주석이다. 중첩 필드와 배열/맵에도 적용하고 블록 종료 문자열 및 U+2028/U+2029를 이스케이프한다. COMMENTED 최종 출력 확장자는 json5이다.
- 기존 취소 콜백/트랜잭션과 Pending/Invalid/Ready 검사를 유지했다. 새 플랫폼 API나 외부 의존성은 없다.

## Acceptance

작업 디렉터리: `/Users/buyong/workspace/private/json-helper2` (Rust 명령만 `tree-sitter-wasm`). IC 2024.3/build 243, JVM 목표 17, JShell/GraalVM 21.0.2, headless=true.

- `./gradlew compileKotlin`: exit 0. 최초 컴파일에서 ObjectMapper.readTree(String)의 불필요한 제네릭 인자를 제거한 뒤 통과했다.
- `cargo test`: exit 0, 기존 12개 통과, 실패/스킵 0.
- `./gradlew test --tests 'com.livteam.jsoninja.services.typeConversion.TypeConversionWasmIntegration*' --rerun-tasks --no-build-cache --no-configuration-cache`: exit 0, 기존 세 suite 4+5+2=11개 통과, 실패/오류/스킵 0. 12에서 기록한 캐시된 테스트 클래스 문제를 피하고 실제 release WASM 빌드/복사를 실행했다.
- `python3 /tmp/json-integrity-type-output-check.py`: exit 0, 아래 검사 모두 PASS, fixture/preview/clipboard 정리 완료. 임시 수동 검사이며 저장소 테스트 파일/사례는 변경하지 않았다.

| 입력/경로 | 관찰 결과 |
| --- | --- |
| ACTIVE = "active" | 최종 JSON 값 active |
| TEN=10, AFTER 및 ZERO,ONE,TWO | 숫자 10/11 및 0/1/2, 기존 이름 목록 유지, raw WASM bare 멤버 포함 |
| -2, 0x10, 0b11, 0o7, 1_000와 implicit 후속 | -2,-1,16,17,3,4,7,8,1000,1001 |
| 9007199254740992 다음 implicit | JavaScript와 같은 9007199254740992 |
| 작은따옴표, \x61, \u0061 | 모두 active |
| Math.random(), 1 + 2, 1e400 | 해당 멤버의 unsupported 진단, 이름 대체 없음 |
| Java/Kotlin 생성자 enum | 기존 ACTIVE 이름 출력 |
| 네 가지 형식 × 출력 수 1/3 × 세 optional 모드 | 세 출력 구분; COMMENTED의 활성 JSON은 REQUIRED와 동일 |
| named/inline/list/map 중첩, 루트 배열 | 선택 필드 주석 유지, 루트 배열 중첩 증가 없음 |
| 주석 값 */ 및 U+2028 | JSON5 활성 내용 정상 파싱 |
| 실제 ConvertTypeDialogPresenter의 세 모드 | pending 복사 차단, enum active 유지, 복사=미리보기=최종 삽입, json/json5 확장자 일치 |
| 지원하지 않는 계산식 입력 변경 | invalid 미리보기와 삽입 차단 |

- 비교 기준: `tsc --target ES2020 --module commonjs --outDir /tmp/json-integrity-enum-js /tmp/json-integrity-enum-fixture.ts` 및 `node /tmp/json-integrity-enum-js/json-integrity-enum-fixture.js` 각각 exit 0. TypeScript 5.8.3/Node 24.14.0의 numeric/rounded 결과가 위와 일치했다.
- 재빌드 결과, `src/main/resources/wasm/tree-sitter/tree-sitter.wasm`, `tree-sitter-wasm/target/wasm32-wasip1/release/tree_sitter_wasm.wasm`, 테스트 플러그인 JAR 내부 `wasm/tree-sitter/tree-sitter.wasm`은 모두 SHA-256 `6da5d6d1aee43bcbbe2eaae798dcbbd58dae9f51a1e0b80b19320dd68973ae76`이다. 생성 바이너리는 기존 ignore 정책대로 커밋하지 않는다. 최종 배포 ZIP 비교는 16에서 수행한다.
- 08 runtime/bridge/executor 소스는 변경하지 않았다. 09 최종 소비자 검사를 위 실제 presenter의 pending/error/복사/삽입 경로로 다시 확인했다. 네이티브 창/키보드 관찰을 주장하지 않는다.

### 검증 소스 SHA-256

- `src/main/kotlin/com/livteam/jsoninja/model/typeConversion/TypeConversionModels.kt`: `07e0ada97af9cf0eb81194262c33ce50ca6e1b91f6103cdc24733af7e1646729`
- `src/main/kotlin/com/livteam/jsoninja/services/treesitter/TreeSitterQueryResult.kt`: `dce1166b8958a44096297059d95c9aac4a44ce69f072e124cce9f2021fe1611b`
- `src/main/kotlin/com/livteam/jsoninja/services/treesitter/TypeScriptEnumValueResolver.kt`: `8acc22ad7f4e2bead5aca39be67b5e7e29f2502564dbe865b6a2cda72e4ce7c7`
- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/TypeToJsonNodeGenerator.kt`: `dec82cdc51c9021cfe3d70eb955d98ef492193222dbd7fe7dc0b9d17171e93e7`
- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/TypeToJsonDocumentBuilder.kt`: `5077965daccc22374f5370a5632a7c0040934c2ec8ab5beb91797a3951d034a2`
- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/TypeToJsonCommentRenderer.kt`: `507f229b60cdc782b12a18e837af9bcac2211ae5672afeb67753b3098990f677`
- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/TypeToJsonGenerationService.kt`: `fb90439238a95360faed0e16d2a1d4dce88b0d0bafb50928e1d37572386defd0`
- `src/main/kotlin/com/livteam/jsoninja/ui/dialog/convertType/TypeToJsonDialogPresenter.kt`: `8fa143ad46617a94480094fa4a41f757e51caae4d12ae218a91bd1acae9cf9f3`
- `src/main/kotlin/com/livteam/jsoninja/ui/dialog/convertType/TypeToJsonDialogView.kt`: `a4909541cee01fbd9c3449636d2991cec90129dad3334db9a9afd118238d9e1a`
- `tree-sitter-wasm/src/analyzer/typescript.rs`: `dd95ceb8144f006ea4714cdb47292320d3919dece0195fd7c980225ad52ea52c`
- `src/main/resources/messages/LocalizationBundle.properties`: `47aa4dc53a931c740c745fb1c255dce78076b4906e0a853d11d7ce4da518567f`
- `src/main/resources/messages/LocalizationBundle_en.properties`: `72febee5d89776747a309939f7c4b495ba680d97a7ca17723097c6c5b960875e`
- `src/main/resources/messages/LocalizationBundle_ja.properties`: `b93b97e87b24e7d8a0c61d325dbc2ddd6392e029c3c51eba425dc7584dec7aca`
- `src/main/resources/messages/LocalizationBundle_ko.properties`: `774f5f95c195f67d42f08120414e4b7319868ec2aaf69f74576de13251abae19`
- `src/main/resources/messages/LocalizationBundle_zh_CN.properties`: `19a935126f1ecb57a2fbfd5b13aab4c66da807e5b6f8668444fb4d88a1ea5ab2`
