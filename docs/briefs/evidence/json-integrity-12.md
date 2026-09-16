# 12 — 스키마 참조 경계와 출처 기록

2026-09-16, R1.10/R1.11. 상태: **ready**. 실제 스키마 생성 서비스와 헤드리스 화면 구성요소를 통해 확인했다.

## Baseline

- 기준 HEAD: `bbc058b2a85eab1baf0142180667c3d8e470ca8f`. 선행 02의 mapper/factory/normalizer 세 파일 해시는 동일하다. JsonFormatterService는 선행 03에서 검증한 변경을 사용한다. 병행 중인 10의 타입 생성 변경은 이 검증 대상에 포함하지 않았다.
- 변경 전 `const: {"$ref":"#/missing"}`는 `Invalid JSON pointer reference: #/missing`으로 실패했다.
- `const: {"minimum":5,"maximum":1}`도 실제 숫자 스키마와 같은 모순 오류를 냈다. 앵커 수집 결과에는 실제 `real` 외에 examples의 `fake`, `fakeDynamic`이 포함됐다.
- URL loader → config에 조회 URI가 없었고, normalizer는 모든 객체 값을 순회했다. SchemaStore 대체 조회가 실패한 뒤 빈 스키마를 반환하는 경로도 확인했다.

## Implementation

- JsonSchemaTraversal에서 버전별 스키마 위치를 공유한다. 참조 해석, 앵커 수집, 모순 검사가 같은 경계를 사용하고 const/enum/default/examples는 값으로 보존한다.
- 조회 URI와 중첩 `$id`를 스키마 자원별로 등록하고, 같은 파일의 중첩 자원·앵커·포인터도 해당 문맥으로 해석한다. 컴파일러에 전달하는 `$id`는 해석된 절대 URI다.
- 기존 파일/URI 캐시와 SchemaStore 대체 조회 후보를 유지했다. 잘못된 포인터, 순환, 미지원 URI와 데이터 위치를 가리키는 참조는 포인터를 가진 예외로 처리한다. 대체 조회 실패를 빈 스키마로 약화하지 않는다.
- `schemaRetrievalUri`를 설정 → prepareSchema → normalize에 전달한다. 기존 한 인자 service API는 유지했다.
- URL 불러오기 완료 시 출처를 설정한다. 일부 편집은 유지하고 새 불러오기, 전체 선택 후 교체, 문서 전체 setText, 비우기는 출처를 교체/초기화한다. 새 로컬 입력은 프로젝트 상대 경로를 사용한다.
- 공개 DocumentEvent/SelectionModel API를 사용했다. 수동 화면 확인에 쓴 EditorTextField.getEditor(true)도 IC 2024.3 소스에서 확인했다. HTTP 구현의 중복 정리는 15가 맡으며 URI 전달을 보존해야 한다.
- 새 참조 오류 세 종류를 다섯 언어 리소스에 추가했다.
- 참조: [JSON Schema 2020-12 core의 자원·기준 URI·스키마 위치 정의](https://json-schema.org/draft/2020-12/json-schema-core).

## Acceptance

작업 디렉터리: `/Users/buyong/workspace/private/json-helper2`.

- `./gradlew compileKotlin`: exit 0.
- `./gradlew test -PskipWasmBuild=true --tests 'com.livteam.jsoninja.services.schema.JsonSchemaDataGenerationServiceTest' --tests 'com.livteam.jsoninja.services.JMESPathServiceTest' --tests 'com.livteam.jsoninja.services.JacksonJqServiceTest' --tests 'com.livteam.jsoninja.ui.component.editor.JsonEditorUndoRedoTest' --rerun-tasks --no-build-cache --no-configuration-cache`: exit 0, 총 21개 중 기존 스키마 3개 실행, 실패/오류/스킵 0.
- 일반 테스트 실행에서는 이전 생성자를 참조하는 캐시된 테스트 클래스가 복원돼 `NoSuchMethodError`가 발생했다. 구성 캐시만 옮기는 것으로 해결되지 않았고, 위의 전체 재컴파일에서는 통과했다. 캐시 내부 원인을 단정하지 않으며 최종 통합 검증에 전달한다. 테스트 소스·Gradle 설정은 변경하지 않았다.
- `python3 /tmp/json-integrity-schema-check.py`: exit 0, 아래 모든 관찰 통과, fixture/server/editor 정리 중 오류 없음. IC 2024.3, headless=true, JShell/GraalVM 21.0.2, Kotlin 컴파일 목표 17. 임시 명령은 저장소 테스트로 추가하지 않았다.

| 입력 또는 경로 | 확인 결과 |
| --- | --- |
| const/enum/default/examples 안의 `$ref`, `$dynamicRef`, minimum=5/maximum=1 | 모두 원본 값 유지, 데이터 참조 HTTP 요청 0; const/enum 생성 결과도 원본 객체와 동일 |
| `$defs.target.$anchor=real`, examples의 fake/fakeDynamic; examples를 앞에 둔 순서도 반복 | 수집한 map의 키는 real만 존재, 생성 value는 문자열 |
| 실제 number minimum=5/maximum=1 | 모순 예외, 포인터 `#` |
| draft-07의 미지원 prefixItems에 `#/missing` | 데이터로 유지, 참조 조회 없음 |
| `$ref: #/const` | 스키마 위치가 아니라는 명시적 오류 |
| `/schemas/root.json` → `defs.json#real`; 중첩 `$id: nested/resource.json` → `defs.json#local` | 실제 요청 경로는 `/schemas/defs.json`, `/schemas/nested/defs.json`; 생성 `{"first":7,"second":"nested"}` |
| 루트 `$id: canonical/root.json` → `defs.json` | `/schemas/canonical/defs.json`, 결과 true |
| 문서 안 `$id: embedded.json`, `$anchor: target` | 원격 재조회 없이 결과 9 |
| missing.json, defs.json#/missing, `$ref: #` 순환, ftp URI | 각각 포인터가 있는 복구 가능한 예외 |
| 프로젝트 폴더의 manual-integrity-defs.json, 출처 없는 입력 | 파일 상대 참조 해석, 결과 11 |
| 실제 URL 버튼 → getConfig → 일부 편집 → generateFromSchema | 조회 URI 유지, 최종 second 값 nested |
| 문서 전체 setText / 실제 Editor의 전체 선택 후 replaceString | 두 경우 모두 조회 URI 초기화 |

- 위 생성 결과를 각 prepareSchema의 compiledSchema에 다시 검증해 모두 유효함을 확인했다. 기존 REQUIRED_ONLY/전체/COMMENTED 세 모드도 기존 테스트가 통과했다.
- 실제 네이티브 창·키보드는 조작하지 않았다. 공개 Swing/Editor/문서 경로와 최종 생성 API를 헤드리스에서 실행했다. SchemaStore 인터넷의 특정 시점 응답은 고정 검증하지 않았으며 기존 후보 생성/캐시 소유권을 보존했다.
- 처음 수동 명령의 checked exception 선언 누락, 지연 Editor 초기화, 인위적 addNotify에 대한 removeNotify 누락을 고친 뒤 최종 명령을 완료했다. 중간 실패를 성공으로 집계하지 않았다.

### 검증 소스 SHA-256

- `src/main/kotlin/com/livteam/jsoninja/services/schema/JsonSchemaNormalizer.kt`: `e49cfde4baae1753f2349f4cad13f0b5f029dffc94d167340315f328686d752f`
- `src/main/kotlin/com/livteam/jsoninja/services/schema/JsonSchemaTraversal.kt`: `2caae1d801c96d0716b0c07b673a58a4423c46dde732efaca5fd346c94abc058`
- `src/main/kotlin/com/livteam/jsoninja/services/schema/JsonSchemaDataGenerationService.kt`: `82751ddfad1e561b878f84e6bb4cb56e247efcdb4e2b83a5e6c0427d049db0ee`
- `src/main/kotlin/com/livteam/jsoninja/ui/dialog/generateJson/model/JsonGenerationConfig.kt`: `4c070bc01b2b17fb4309f4e6c2f7b32f95b626c8ee8a487b3a885b3c8e8cb073`
- `src/main/kotlin/com/livteam/jsoninja/ui/dialog/generateJson/schema/GenerateSchemaJsonTabPresenter.kt`: `e855e484a689bde8efc7a686a70037c2ed1315b3ed32e2e1d801a6d0e85d1f26`
- `src/main/kotlin/com/livteam/jsoninja/ui/dialog/generateJson/schema/GenerateSchemaJsonTabView.kt`: `8c91dd559fe6e8cacdd04e2efaa4185d4e5ecaf000c19da627d9d8b02ba9fb40`
- `src/main/resources/messages/LocalizationBundle.properties`: `629fef6232c1a778612ba80acd26c3726f7c903dbeaa84d367ee6fc6f9854689`
- `src/main/resources/messages/LocalizationBundle_en.properties`: `d7059843cab0c75838b6844e7acb8d8c26ccd16d0ed10f34985d8ab656acb475`
- `src/main/resources/messages/LocalizationBundle_ja.properties`: `4bfa7abf57c6fa49d4117949f266fbffedb826c24ba9566bdab9907a6c62f272`
- `src/main/resources/messages/LocalizationBundle_ko.properties`: `07bab7f702bf556425c9e2699aed6a9a9f8f11957fcbdead955ca913fa35748e`
- `src/main/resources/messages/LocalizationBundle_zh_CN.properties`: `eed111f3d7af85d6b916b14a0792a040f70e22ab1ac3664749c6883fe30252fc`
