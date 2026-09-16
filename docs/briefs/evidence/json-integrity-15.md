# 15 — HTTP 연결과 스트림 정리 기록

2026-09-16, M2. 상태: **ready**.

## Baseline

- 기준 HEAD: `d3c31a45e5d8f6e06f8082e81c174bc9e9479f10`. 12의 참조/출처 계약과 13의 정밀도/취소 수정 위에서 진행했다.
- 세 호출자 모두 연결을 만든 뒤 requestMethod/timeout/header 설정을 disconnect finally 밖에서 수행했다. API 본문 쓰기도 같은 경계 밖이었다.
- `python3 /tmp/json-integrity-http-check.py`의 변경 전 실행: 로컬 서버에서 아래 HTTP 18개 경로와 API 메서드/인증 12개 조합을 관찰했다. 이어 임시 HttpURLConnection을 넣어 19개 성공/실패 경로의 close/disconnect 호출을 기록했다. 측정한 소켓 누수라고 주장하지 않는다.
- 설정 실패에서 세 호출자 모두 disconnected=false였다. API body-write 실패는 disconnected=false/requestClosed=false였다. API의 CancellationException은 IOException, 참조 해석 경로는 JsonSchemaGenerationException으로 바뀌었다.
- 첫 임시 fixture는 API view의 getComponent를 생략해 dispose 시 미초기화 필드 오류가 났다. 전송 관찰과 구분하고 실제 화면 생성 순서로 보완한 후 최종 실행에서 정상 종료했다.

## Implementation

- JsonHttpConnection은 연결 생성 직후 Closeable/use 소유 범위를 시작하고 action 전체를 실행한 뒤 disconnect한다. 설정과 본문 쓰기를 포함하며 .use의 기존 예외 보존 규칙을 사용한다. 파싱·상태 판단·정책·캐시는 호출자에게 남겼다.
- API 본문은 outputStream 자체를 use로 소유하고 UTF-8 바이트를 쓴다. Writer의 실패한 flush가 underlying stream.close를 건너뛰는 관찰 경로를 제거했다.
- API의 성공/오류 응답 stream.use는 유지했다. 두 스키마 호출자도 HTTP 오류일 때 errorStream을 닫은 뒤 기존 상태 오류를 전달한다.
- API 전송/파싱, 스키마 UI, 참조 해석에서 coroutine/platform 취소를 일반 요청·파싱 오류로 바꾸지 않는다.
- ApiLoadRequest 필드/enum, 인증 방식, 요청 메서드, 본문 조건, 헤더, redirect/cache/timeout 설정을 그대로 뒀다. 새 HTTP 의존성·재시도·자격 증명 접근을 추가하지 않았다.
- 두 UI 호출자의 withContext(IO)/EDT 경계를 유지했다. 동기 normalizer의 참조 조회도 기존 백그라운드 호출 위치를 유지하며 helper에서 스레드나 UI를 바꾸지 않는다.

## Acceptance

작업 디렉터리: `/Users/buyong/workspace/private/json-helper2`.

- `./gradlew compileKotlin`: exit 0.
- `./gradlew test -PskipWasmBuild=true --tests 'com.livteam.jsoninja.services.schema.JsonSchemaDataGenerationServiceTest' --rerun-tasks --no-build-cache --no-configuration-cache`: exit 0, 기존 3개, 실패/오류/스킵 0. 12에서 확인한 캐시된 테스트 클래스 문제를 피했다.
- `python3 /tmp/json-integrity-http-check.py`: exit 0, 최종 fixture 정상 종료. IC 2024.3, JShell/GraalVM 21.0.2, headless=true, JVM 컴파일 목표 17.
- HTTP 비교에서는 coroutine dispatcher를 잠시 보류해 SchemaStore 자동 요청을 실행하지 않았다. 로컬 endpoint 비교 후 그 프로세스의 http URL handler만 임시 연결로 교체해 설정/본문/응답 실패를 주입했다. 서비스 복원·대기 작업 취소·서버/fixture 정리까지 수행했다.
- 인증 검사 값은 실행 중 임의로 만들었으며 서버에서 일치 여부만 계산했다. 인증 값 자체를 로그나 이 기록에 남기지 않았다.

| 정책 | API 호출자 | 스키마 URL UI / 참조 normalizer |
| --- | --- | --- |
| 메서드 | GET/POST/PUT/DELETE | GET |
| 인증 | NONE/BASIC/BEARER, 기존 UTF-8 Basic 인코딩 | 기존 정책 유지 |
| 본문 | GET은 생략, 나머지는 비어 있지 않을 때 UTF-8 | 없음 |
| Accept | application/json | application/schema+json, application/json;q=0.9, */*;q=0.8 |
| Content-Type | application/json; charset=UTF-8 | 기존 미설정 |
| 연결/읽기 timeout | 10000ms / 15000ms | 두 호출자 각각 10000ms / 15000ms |
| redirect/cache | true / false | true / 기존 true |
| JSON 파싱 | 응답 유효성 검사 | fetch는 원문 반환, 별도 스키마 계층에서 검사 |

- 메서드 × 인증 12조합은 헤더/본문/UTF-8 일치, `API_POLICY_MISMATCHES=0`이었다.
- 세 호출자 각각 정상 UTF-8, redirect, HTTP 503, 빈 응답, malformed body, 지연 응답의 6경로를 실행했다. 변경 전후 성공 텍스트와 오류 종류/문구가 일치했다. API는 빈 응답/잘못된 JSON을 거부하고, 스키마 fetch 함수는 원문을 상위 스키마 처리로 넘기는 기존 계약을 유지한다.
- 실제 지연 endpoint의 변경 전 읽기 timeout은 15001/15001/15002ms, 변경 후 15002/15001/15000ms에 SocketTimeoutException이었다. 성능 보장이 아닌 관찰값이다.

| 주입한 상황 | 최종 관찰 |
| --- | --- |
| 세 호출자의 설정 실패 | 모두 disconnected=true; stream은 아직 생성되지 않음 |
| API 본문 쓰기 실패 | disconnected=true, requestClosed=true |
| 세 호출자의 응답 읽기 실패 | disconnected=true, responseClosed=true |
| 세 호출자의 HTTP 오류 | disconnected=true, errorClosed=true |
| timeout/cancel/정상 완료 | 모두 disconnected=true, 생성한 request/response stream은 닫힘 |
| 설정을 마친 모든 임시 연결 | connectMs=10000, readMs=15000, redirect=true, 각 cache 정책 일치 |
| API와 두 fetch 함수의 CancellationException | 그대로 CancellationException |
| normalizer의 실제 $ref 해석 중 취소 | 그대로 CancellationException |

- `python3 /tmp/json-integrity-schema-check.py`: exit 0. 마지막 실행은 자동 HTTPS 카탈로그를 임시 응답으로 대체했고, 실제 로컬 HTTP 조회/중첩 URI/앵커/편집 후 출처/전체 교체 초기화/최종 생성은 모두 통과했다. 12/13의 공유 파일 변경을 이 검증으로 다시 연결했다.
- UI 성공 callback의 EDT와 기존 수명 검사는 소스에서 유지됨을 확인했고, URL UI의 실제 설정→최종 생성은 위 헤드리스 경로에서 실행했다. 네이티브 창·키보드나 운영 API를 검증했다고 주장하지 않는다.

### 검증 소스 SHA-256

- `src/main/kotlin/com/livteam/jsoninja/services/JsonHttpConnection.kt`: `544425407f7226ee08189d6392fa3dd997025697dce73fbec9e099e7d909f68d`
- `src/main/kotlin/com/livteam/jsoninja/ui/dialog/loadJson/LoadJsonFromApiDialogPresenter.kt`: `ddea1bda82f3072c08d91829442583174101e25bfc90eb1a22979fafc9591fe4`
- `src/main/kotlin/com/livteam/jsoninja/ui/dialog/generateJson/schema/GenerateSchemaJsonTabPresenter.kt`: `2b015b654b6c3918914af5d3e1c8b053331b866e08fa29b20ff57ba6569db8d2`
- `src/main/kotlin/com/livteam/jsoninja/services/schema/JsonSchemaNormalizer.kt`: `0660f7cb810201167550a2257075c9594e1e4ec498192d7e4c817c67700f0335`
