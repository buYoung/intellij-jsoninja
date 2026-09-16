# 08 — WASM 분석 트랜잭션 격리 기록

2026-09-16, R1.4. 상태: **ready**. 실제 bundled WASM에 대한 기존 통합 테스트와 별도 동시 서비스 호출을 관찰했다.

## Baseline

- 기준 HEAD: `53034b12a94f09df40c5c680ce7fcd9a2613da2f` (02의 후속 오류 진단 보완 포함).
- 초기 RuntimeHandle 생성만 synchronized이고 allocate/write/analyze/error/read/free는 공유 인스턴스에 동시 접근 가능했다. Chicory 1.5.1 source JAR의 InterpreterMachine은 가변 stack/callStack을 보관하며 interrupt 때 ChicoryInterruptedException을 던진다. 실제 초기 충돌 재현을 주장하지 않고 소스상 소유권 결함을 기준으로 수정했다.
- 기존 computePreview는 Default에서 실행되며 Job 취소는 진행 중 동기 WASM 실행을 끝내지 않는다.

## Implementation

- RuntimeHandle의 ReentrantLock으로 전체 분석 트랜잭션을 직렬화한다. 잠금 범위는 입력 alloc/write, 분석, 결과/오류 읽기, 결과/오류/입력 free를 모두 포함한다.
- Default에서 최대 50ms씩 잠금 획득을 기다리는 동안 호출자의 checkCancellation을 확인한다. 이는 대기 확인 간격이며 새 성능 보장값이 아니다. 잠금 획득 직후에도 확인하고 이미 시작한 동기 분석의 finally 정리는 끝까지 수행한다.
- ConvertPreviewExecutor가 현재 coroutine context의 ensureActive를 callback으로 만들고 TypeToJsonDialogPresenter → TypeToJsonGenerationService → TypeDeclarationAnalyzerService → runtime까지 전달한다. 새 Job으로 기존 취소 신호를 덮어쓰지 않는다. EDT에서는 잠금을 기다리지 않는다.
- 기존 analyzeSource 2인자 및 generate 4인자/default 진입점을 오버로드로 유지한다. 첫 통합에서 발생했던 NoSuchMethodError는 시그니처 복원으로 해결했다.
- 입력 alloc 후 memory.write 실패 시 해당 버퍼를 해제하며 cleanup 실패는 원래 예외에 suppressed로 붙인다. 기존 성공/오류 버퍼는 기존 finally에서 반환한다. WASM ABI·Rust 소스·버전·리소스는 변경하지 않았다.
- 취소 callback 전달에 필요한 두 경로를 부모 충돌 목록에 추가해 09/11이 보존하도록 했다.

## Acceptance

모든 명령의 작업 디렉터리: `/Users/buyong/workspace/private/json-helper2`.

- `./gradlew compileKotlin`: exit 0.
- `./gradlew test -PskipWasmBuild=true --tests 'com.livteam.jsoninja.services.typeConversion.TypeConversionWasmIntegration*'`: 최종 exit 0.
- com.livteam.jsoninja.services.typeConversion.TypeConversionWasmIntegrationTest: 4개, 실패 0, 오류 0, 스킵 0
- com.livteam.jsoninja.services.typeConversion.TypeConversionWasmIntegrationV2Test: 5개, 실패 0, 오류 0, 스킵 0
- com.livteam.jsoninja.services.typeConversion.TypeConversionWasmIntegrationV3Test: 2개, 실패 0, 오류 0, 스킵 0

- 수동 명령: `jshell --class-path "$(cat /tmp/json-integrity-classpath)"`, exit 0. 현재 클래스, IC 2024.3, Chicory 1.5.1, 소스 리소스로 MockApplication과 서로 다른 MockProject 2개를 구성했다. UI 조작이나 테스트 파일 추가는 없다.
- 고정 스레드 4개에서 Payload0..Payload7을 각 프로젝트 analyzer로 보냈다. RuntimeHandle.withTransaction 안의 active 계수를 관찰해 `MAX_ACTIVE=1 ACTIVE_AFTER=0`; 결과는 Payload0..Payload7과 각각 일치했다. analyzer의 동일 잠금 재진입을 포함한다.
- 잠금을 보유한 요청과 별도 대기 요청을 구성하고 취소 callback을 활성화: 대기 요청은 CancellationException, 소유자는 정상 해제. 취소된 요청은 버퍼 할당 전에 이탈한다.
- malformed TypeScript는 syntax.error 진단을 반환했다. 같은 잠금 안에서 언어 ID 999를 호출한 오류 경로는 `Unsupported language id: 999`; 오류 문자열 버퍼와 입력 버퍼를 해제한 뒤 새 분석 결과 `Recovered`를 확인했다.
- alloc/write 실패에 대한 반환 경로는 소스 검토로 확인했다. 실제 WASM 메모리 쓰기 실패를 강제로 유발하지 않았다. 네이티브 두 대화상자 조작/프로젝트 종료는 미실행이며 동일 shared runtime의 서비스 동시 경로를 직접 관찰한 결과만 전달한다.
- Rust host 테스트는 이 단계에서 실행하지 않았다. 위 11개는 실제 bundled WASM 통합 테스트다.
- bundled WASM SHA-256: `12575dd4b1fad1b0c9604a40d9db8ab3ab190806754db4af8cae87c10cfc044d`.

### 검증 소스 SHA-256

- `src/main/kotlin/com/livteam/jsoninja/services/treesitter/TreeSitterWasmRuntime.kt`: `2e9aea79ffac3a56b9c6ce986b94f22f81a91453a025958fca98d930bb2fc01b`
- `src/main/kotlin/com/livteam/jsoninja/services/treesitter/WasmMemoryBridge.kt`: `6f1bb97c60a788225b610ac8a7e65d95af2420bc94f7ab6b6b97d908a6a4e16b`
- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/TypeDeclarationAnalyzerService.kt`: `b76a83404a58c66fa9b3044009b7681ae57a5df35e3324c3d778d6d7a71be528`
- `src/main/kotlin/com/livteam/jsoninja/services/typeConversion/TypeToJsonGenerationService.kt`: `7196e6ff094aaebe343f9be21899aac68d25b02688b123130c54efcd06eb9959`
- `src/main/kotlin/com/livteam/jsoninja/ui/dialog/convertType/ConvertPreviewExecutor.kt`: `b4d6240848132fbf59e8f8cb925b33086aeb165a2f7b001f4ba8b7421021c182`
- `src/main/kotlin/com/livteam/jsoninja/ui/dialog/convertType/TypeToJsonDialogPresenter.kt`: `793bc6a5dd53b992c89821d0e1cfa221fb6b313ffd1146a3aba821f9d52be7e4`
