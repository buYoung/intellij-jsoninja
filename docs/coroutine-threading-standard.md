# Coroutine / Threading 표준 (정본)

## 1. 목적과 범위
- 이 문서는 JSONinja 플러그인의 **coroutine 기반 비동기 처리와 threading 경계에 대한 단일 진실 소스(single source of truth)** 입니다.
- 새 비동기 코드를 작성하거나 기존 흐름을 수정하는 기여자(개발자/AI 에이전트)가 "어떤 패턴을 따라야 하는지"를 판단하는 기준입니다.
- 이 문서는 다음 두 문서를 **통합·대체**합니다. (둘은 `e695772` 리팩토링 *이전* 상태를 기준으로 작성되어 일부 outdated 상태였습니다.)
  - `docs/coroutine-threading-audit.md` (감사 결과)
  - `docs/coroutine-cpu-hotspot-candidates.md` (CPU hotspot 후보 C1~C13)
- 판단 기준은 `AGENTS.md`의 `Threading and Disposal Boundaries`, 그리고 IntelliJ 플랫폼의 `readAction` / `WriteCommandAction` / `Dispatchers.EDT` 경계입니다.

> AGENTS.md의 `Threading Rules` 표는 아직 레거시(`executeOnPooledThread` / `invokeLater`) 패턴을 기준으로 적혀 있어 이 문서와 모순됩니다. [7. Known issues](#7-known-issues) 참고.

## 2. 핵심 전제 (Invariants)
새 코드를 읽거나 쓸 때 반드시 전제로 깔아야 하는 불변식입니다.

1. **`JsoninjaCoroutineScopeService`의 기본 dispatcher는 `Dispatchers.Default`다.**
   - 이 서비스는 IntelliJ 플랫폼이 주입하는 project-level service scope를 그대로 보관합니다. 플랫폼 service scope의 기본 dispatcher는 `Dispatchers.Default`입니다.
   - 따라서 `launch { heavyCompute() }`처럼 `withContext` 없이 작성한 본문도 **현재는 EDT가 아닌 Default에서 실행**됩니다.
   - 단, 이는 *암시적* 전제입니다. 가독성과 안전성을 위해 CPU 계산은 가능한 한 명시적으로 `withContext(Dispatchers.Default)`로 감싸는 것을 권장합니다.
2. **`createChildScope()`는 부모 context를 상속하고 `SupervisorJob`만 새로 단다.**
   - dispatcher 등 context는 부모(= 위 1번, 즉 `Dispatchers.Default`)와 동일합니다.
   - 호출자가 수명 종료 시점에 직접 `cancel()` 할 책임을 집니다.
3. **EDT 접근 규칙**
   - Swing/IDE UI 갱신, `Document`/`Editor` 텍스트 쓰기 트리거는 `Dispatchers.EDT`에서 수행합니다.
   - PSI / 일부 VFS 메타데이터 읽기는 read action(`readAction { }` / `runReadAction { }`) 안에서 해야 합니다. EDT는 암시적 read 권한을 갖지만, **Default로 빠져나온 코드는 그렇지 않습니다.**
   - 문서 변경 + Undo 보존은 `WriteCommandAction.runWriteCommandAction(project) { }`로만 합니다.

## 3. 표준 패턴 (Canonical Pattern)
수명 주기에 결합된 비동기 작업의 정본 형태입니다. 본 리팩토링(`e695772`) 전반이 이 형태를 따릅니다.

```kotlin
class SomePresenter(
    private val project: Project,
    parentDisposable: Disposable,
) {
    // 1) 수명 주기 결합 child scope 소유
    private val coroutineScope = project.service<JsoninjaCoroutineScopeService>().createChildScope()
    private var job: Job? = null

    init {
        // 2) dispose 시 진행 중 job + scope 취소
        Disposer.register(parentDisposable) {
            job?.cancel()
            coroutineScope.cancel()
        }
    }

    fun doWork(input: String) {
        // 3) 직전 요청 취소 (staleness 방지)
        job?.cancel()
        job = coroutineScope.launch {
            // 4) CPU 계산은 Default
            val result = withContext(Dispatchers.Default) { heavyCompute(input) }

            // 5) UI 반영은 EDT + 가드
            withContext(Dispatchers.EDT) {
                if (project.isDisposed) return@withContext
                if (isStale()) return@withContext   // 6) staleness 가드
                applyToUi(result)
            }
        }
    }
}
```

### 단계별 의미
| 단계 | 규칙 |
| --- | --- |
| (1) scope 소유 | 수명 주기 결합 작업은 `createChildScope()`로 자기 scope를 소유한다 |
| (2) 취소 등록 | `Disposer.register` 또는 `dispose()`에서 `job.cancel()` + `coroutineScope.cancel()` |
| (3) 직전 취소 | 같은 종류의 새 요청이 들어오면 직전 job을 먼저 cancel |
| (4) 계산 | CPU 비용(parse/format/sort/tree build 등)은 `withContext(Dispatchers.Default)` |
| (5) 반영 | UI/Document 반영은 `withContext(Dispatchers.EDT)` (모달 안이면 `+ ModalityState.any().asContextElement()`) |
| (6) 가드 | 반영 직전 `project.isDisposed` + staleness 확인 |

### Staleness 가드 카탈로그 (실제 사용되는 형태)
계산이 끝나 EDT로 돌아왔을 때 "그 사이 상태가 바뀌었는지"를 확인하는 가드. 상황에 맞는 것을 고른다.

- `if (project.isDisposed) return@withContext` — 항상.
- 에디터 동일성: `if (getCurrentEditor() !== currentEditor) return@withContext` (`JsoninjaPanelPresenter`).
- 텍스트 변경: `if (currentEditor.getText() != jsonText) return@withContext` (`JsoninjaPanelPresenter`).
- 문서 변경 스탬프: `if (document.modificationStamp != captured) return@withContext` (`BaseEditorJsonAction`, `SortJsonDiffKeysOnceAction`).
- 시퀀스 번호: `AtomicInteger` 증가 후 `if (searchSequence.get() != sequenceNumber) return@withContext` (`JsonQueryPresenter`).
- 입력 일치: `if (view.getSchemaUrlEditorText() != editorText) return@withContext` (`GenerateSchemaJsonTabPresenter`).
- Document별 job 레지스트리: `if (documentProcessingJobs[document] !== processingJob) return@withContext` (`BaseEditorJsonAction`, 아래 참고).

### Shared scope 액션의 취소 패턴
액션(`AnAction`)은 per-instance 수명 scope가 없으므로 shared scope(`service<JsoninjaCoroutineScopeService>().launch`)를 쓴다. 이때 직전 작업 취소는 별도 장치로 한다.

- `BaseEditorJsonAction`: `CoroutineStart.LAZY`로 job을 만들고 → `Document`-keyed 정적 레지스트리(`Collections.synchronizedMap(WeakHashMap())`)에 등록하며 직전 job을 cancel → `start()` 호출. LAZY로 만드는 이유는 "레지스트리에 등록되기 전에 job이 먼저 끝나버리는 race"를 막기 위함이다.
- `SortJsonDiffKeysOnceAction`: 취소 대신 `modificationStamp` 가드로 stale write를 방지.

## 4. Scope 선택 규칙
| 상황 | 선택 | 예시 |
| --- | --- | --- |
| 수명 주기 결합(presenter/view/tab/controller가 소유, dispose 시 취소 필요) | `createChildScope()` + dispose에서 `cancel()` | `JsoninjaPanelPresenter`, `JsonEditorTreePresenter`, `JsonQueryPresenter`, `JsonTabContextFactory`(쿼리 결과 포맷 scope), dialog presenter 다수 |
| 액션에서 일회성으로 시작하는 프로젝트 수명 작업 | shared `JsoninjaCoroutineScopeService.launch { }` | `GenerateRandomJsonAction`, `SortJsonDiffKeysOnceAction`, `JsonDiffService.openDiff`, `JsonDiffExtension.onViewerCreated`, `OpenJsonFileAction` |

판단 기준: **"이 작업을 취소시켜야 할 명확한 수명 주기 소유자가 있는가?"** 있으면 child scope, 없으면 shared scope + (필요 시) 별도 staleness 장치.

## 5. Dispatcher / 레거시 primitive 매핑
| 의도 | 패턴 |
| --- | --- |
| CPU 계산 (parse/format/sort/Levenshtein/tree build) | `withContext(Dispatchers.Default)` |
| I/O (파일 읽기, 네트워크) | `withContext(Dispatchers.IO)` |
| UI 갱신 (비모달) | `withContext(Dispatchers.EDT)` |
| UI 갱신 (모달 대화상자 안) | `withContext(Dispatchers.EDT + ModalityState.any().asContextElement())` |
| 쓰기 + Undo 보존 | `WriteCommandAction.runWriteCommandAction(project) { }` |
| PSI / Document 읽기 (off-EDT) | `readAction { }` / `runReadAction { }` |
| coroutine debounce | 작업 전 `delay(...)` (예: `SCHEMA_STORE_FILTER_DEBOUNCE_MS`) |
| Swing debounce (editor/viewer 수명 결합) | `Alarm` (코루틴으로 치환하지 않음) |
| 단일 포커스/한 tick 지연 | `SwingUtilities.invokeLater` (코루틴으로 치환하지 않음) |

> `Alarm`, `SwingUtilities.invokeLater`, `WriteCommandAction`, `readAction`/`runReadAction`은 역할이 명확하며 **기계적으로 coroutine으로 치환할 대상이 아닙니다.**

## 6. 적용 현황 (감사 표)
`e695772` 기준으로, 통합 전 두 문서의 감사 표와 CPU hotspot 후보(C1~C13)를 현재 코드에 맞춰 갱신했습니다.

### 6.1 CPU hotspot 후보 처리 결과
| ID | 후보 | 상태 | 위치 / 비고 |
| --- | --- | --- | --- |
| C1 | 툴윈도우 prettify/uglify | ✅ 해결 | `JsoninjaPanelPresenter.processCurrentEditorTextAsync` (단, 인라인 `withContext(Default)` 사용 — 7.① 참고) |
| C2 | 에디터 컨텍스트 prettify/uglify | ✅ 해결 | `BaseEditorJsonAction` (LAZY job + Document-keyed 레지스트리) |
| C3 | 쿼리 결과 반영 후 재포맷 | ✅ 해결 | `JsonTabContextFactory` child scope + `formatJsonOnDefault` |
| C4 | 랜덤/스키마 생성 후 재포맷 | ✅ 해결 | `GenerateRandomJsonAction`이 `Default` 안에서 포맷 후 `skipFormatting = true`로 전달 |
| C5 | diff 최초 열기 시 포맷/검증 | ✅ 해결 | `JsonDiffService.openDiff`: `prepareDiffText`를 `Default`로, 문서 생성/host 오픈을 `EDT`로 |
| C6 | diff viewer 생성 시 JSON 판별 | ✅ 해결 | `JsonDiffExtension.onViewerCreated`: `isJsonContent`를 `Default`로 이동. off-EDT read는 안전한 편 — 7.③ 참고 |
| C7 | SchemaStore fuzzy filtering | ✅ 해결 | `delay(250ms)` debounce + 무거운 연산(`filterSchemaStoreCatalogItems`)을 `Dispatchers.Default`로 이동, EDT 블록엔 staleness 가드 + UI 반영만 |
| C8 | diff Sort once | ✅ 해결 | `SortJsonDiffKeysOnceAction`: `Default` 포맷 + `modificationStamp` 가드 + `WriteCommandAction` |
| C9 | paste 시 소형 입력 동기 포맷 | ➖ 의도적 제외 | `JsoninjaPastePreProcessor`는 미변경. 임계값(`SYNC_PROCESSING_THRESHOLD`) 튜닝 후보 |
| C10 | query 시작 전 원본 검증 | ✅ 해결 | `JsonQueryPresenter.performSearch`: 검증을 launch 본문(암시적 Default)으로 이동 — 7.⑤ |
| C11 | schema text validate | ➖ 미변경(불가피) | `DialogWrapper` validation 경계는 동기 반환(`ValidationInfo`)이 필요해 그대로 둠 |
| C12 | tree 모드 전환 시 파싱/트리 구축 | ✅ 해결 | `JsonEditorTreePresenter.refreshTreeFromJson`: `buildTreeModel`을 `Default`로, `setTreeModel`만 `EDT` |
| C13 | escape/unescape | ✅ 해결 | `JsoninjaPanelPresenter`(툴윈도우) + `BaseEditorJsonAction`(에디터) 경로 모두 비동기화 |

### 6.2 정상 유지 지점 (변경 불필요)
- 레거시 primitive: `JsonDiffService.replaceDocumentText` / `ConvertResultUtils.insertToEditor` / `BaseEditorJsonAction` / `SortJsonDiffKeysOnceAction`의 `WriteCommandAction`, `JsonDocumentFactory.createJsonDocument`의 `runReadAction`, `FoldingAwareEditorTextField` / `JsonDiffExtension`의 `Alarm`, `OnboardingTutorialDialogView`의 `SwingUtilities.invokeLater` — 모두 역할이 명확해 유지.
- 이미 background로 분리되어 있던 경로: `JsonQueryService.query` 실행 본체, `GenerateRandomJsonAction` 생성 본체, `ConvertPreviewExecutor` 기반 type conversion preview, schema generation 본체.

## 7. Known issues
이 문서를 만드는 시점에 발견된 항목들입니다. 후속 작업에서 #1·#4·#6은 **해결**되어 ✅로 표시했고, 나머지 #2·#3·#5는 **기록만** 합니다.

1. **✅ 해결됨 — 미사용 `*OnDefault` 메서드 제거.**
   `JsonFormatterService`의 dead code였던 `isValidJsonOnDefault` / `escapeJsonOnDefault` / `unescapeJsonOnDefault` 3개를 제거했습니다. 실제 사용되는 `formatJsonOnDefault`(`JsonTabContextFactory` 한 곳)만 남았습니다. (참고: `JsoninjaPanelPresenter`는 여전히 인라인 `withContext(Dispatchers.Default) { processor(...) }`를 쓰므로 "전용 메서드 vs 인라인" 스타일 혼재는 남아 있으나, dead code가 사라져 영향은 경미합니다.)
2. **`try/catch (CancellationException) { throw }`의 두 부류 — 일괄 제거 금지.**
   `try { ... } catch (cancellationException: CancellationException) { throw cancellationException }` 패턴이 코드 전반(약 16곳)에 반복됩니다. **같은 텍스트라도 두 부류로 나뉘며, 한쪽을 제거하면 버그가 됩니다.** 판별 기준은 **"같은 `try`에 뒤따르는 광범위 catch(`Exception` / `Throwable` / `RuntimeException`)가 있는가"** 입니다. (`CancellationException`은 `Exception`/`Throwable`의 하위 타입이므로, 뒤에 광범위 catch가 있으면 그게 취소까지 삼킨다.)
   - **(a) 진짜 no-op — 제거 가능 (7곳).** 뒤에 다른 catch가 없어 "잡아서 그대로 재던질 뿐", `try/catch`가 없는 것과 동일하다. → `JsonDiffService`, `BaseEditorJsonAction`, `SortJsonDiffKeysOnceAction`, `JsonEditorTreePresenter`, `JsonTabContextFactory`, `JsoninjaPanelPresenter`, `JsonQueryPresenter`(`setOriginalJson`의 빈 쿼리 else 분기).
   - **(b) 필수 — 절대 제거 금지 (9곳).** 뒤에 `catch (Exception)` 또는 `catch (Throwable)`가 있어, 이 재던짐이 없으면 **그 광범위 catch가 코루틴 취소(`CancellationException`)를 삼켜 cancellation/structured-concurrency가 깨진다.** → `GenerateRandomJsonAction`, `JsonDiffExtension`, `JsonQueryPresenter`(`performSearch`), `GenerateSchemaJsonTabPresenter`(카탈로그 로드 · 스키마 URL fetch 2곳), `JsonEditorTooltipListener`, `LoadJsonFromApiDialogPresenter`, `FoldingAwareEditorTextField`, `ConvertPreviewExecutor`.
   → 정리한다면 **(a)만** 제거한다. (b)는 유지가 정답이며, 오히려 새 코드에서 `catch (Exception/Throwable)`를 쓸 때는 반드시 그 앞에 `catch (CancellationException) { throw it }`를 두어야 한다(8장 체크리스트 참고). 같은 파일이라도 분류가 다를 수 있으니(예: `JsonQueryPresenter`는 (a)·(b) 둘 다 보유) 파일 단위가 아니라 **각 `try` 단위**로 판단할 것.
3. **`JsonDiffExtension.isJsonContent`의 off-EDT read (낮은 우선순위).**
   원래 EDT(암시적 read 권한)에서 돌던 `isJsonContent`를 `Dispatchers.Default`로 옮겼습니다. 확인 결과 이 함수는 `editor.virtualFile?.fileType` / `?.extension`(캐시된 메타데이터)과 `document.text`(스레드 안전한 immutable 스냅샷)만 읽고, 실제 무거운 read(`formatJsonInBackground`)는 이미 `readAction { }` 안에 있어 **현재 코드 기준으로는 안전한 편**입니다. 즉 즉시 고칠 버그는 아닙니다. 다만 향후 `isJsonContent`에 PSI 접근이 추가되면 read action이 필요해지므로, 그 경계만 인지하면 됩니다. (불필요하게 `isJsonContent` 전체를 `readAction`으로 감싸지 말 것 — 과잉 래핑.)
4. **✅ 해결됨 — C7 fuzzy filtering을 표준 패턴으로 정렬.**
   `GenerateSchemaJsonTabPresenter.filterSchemaStoreCatalogItemsByInput`의 무거운 연산(`filterSchemaStoreCatalogItems` = 토큰 분해 + Levenshtein + 정렬)을 `withContext(Dispatchers.Default)`로 옮기고, `withContext(Dispatchers.EDT + ModalityState.any())` 블록에는 staleness 가드(`isDisposed` / catalog state / editorText 일치)와 `view.updateSchemaUrlSuggestions` UI 반영만 남겼습니다. `delay(250ms)` debounce는 유지. 이제 "Default 계산 → EDT 반영" 표준 패턴을 따릅니다.
5. **암시적 Default 의존의 일관성 부족.**
   `JsonQueryPresenter.performSearch`는 `launch { ... isValidJson(...) }`처럼 `withContext` 없이 검증을 돌립니다(2. 전제에 의해 off-EDT). 그러나 같은 클래스의 `setOriginalJson`은 동일 검증을 `withContext(Dispatchers.Default)`로 명시 래핑합니다. → 명시 래핑으로 통일 권장(전제 1이 깨지면 암시적 경로가 EDT로 새는 위험).
6. **✅ 해결됨 — AGENTS.md `Threading Rules` 표 갱신.**
   AGENTS.md의 `Threading Rules` 표를 coroutine 매핑(`JsoninjaCoroutineScopeService`/`createChildScope`, `Dispatchers.Default`/`IO`/`EDT`, `WriteCommandAction`, `readAction`)으로 교체하고, 본 문서를 정본(canonical source)으로 링크했습니다. 레거시 `executeOnPooledThread`/`invokeLater`는 표에서 제거되고 "표준 아님"으로 명시됐습니다. (`src/main` 레거시 primitive 잔존 0개로, AGENTS.md 섹션 3과의 자기모순도 해소.)

## 8. 새 비동기 코드 리뷰 체크리스트
PR에서 비동기 코드를 추가/수정할 때 확인할 항목.

- [ ] 수명 주기 소유자가 있으면 `createChildScope()`를 쓰고 dispose에서 `cancel()` 하는가? (없으면 shared scope가 적절한가?)
- [ ] CPU 계산이 `withContext(Dispatchers.Default)`로 **명시적으로** 감싸여 있는가? (암시적 Default 의존 금지)
- [ ] I/O는 `Dispatchers.IO`, UI 반영은 `Dispatchers.EDT`(모달이면 `+ ModalityState.any()`)인가?
- [ ] EDT 반영 직전에 `project.isDisposed` + 상황에 맞는 staleness 가드가 있는가?
- [ ] 같은 종류의 요청이 연달아 들어올 때 직전 작업을 cancel(또는 sequence/stamp 가드)하는가?
- [ ] off-EDT에서 PSI/VFS를 읽는다면 `readAction { }`으로 감쌌는가?
- [ ] 문서 변경은 `WriteCommandAction`을 통하는가?
- [ ] 새로 추가한 suspend 헬퍼가 실제로 사용되는가? (dead code 금지)
- [ ] `catch (Exception)` / `catch (Throwable)`를 쓴다면, **그 앞에** `catch (CancellationException) { throw it }`를 두어 취소가 삼켜지지 않게 했는가? (반대로, 뒤따르는 광범위 catch가 전혀 없다면 그 재던짐 블록은 no-op이므로 넣지 말 것 — Known issue 2 참고)
