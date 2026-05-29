# 코드 수정 시나리오 벤치마크 결과 (whole-repo, 2 cases × 5 arms)

대상: `dataset.yaml` 의 M1(시그니처 변경 blast radius)·M2(getOrCreate transitive 폐포).
sonnet 서브에이전트 10개, 동일 베이스 프롬프트, `{{ALLOWED_TOOLS_BLOCK}}` 만 차등, blind 채점.

- 고정 상태: git `v1.12.1` / `1daf879`, codegraph DB sha256 `54e743b6...`
- zoekt: src(main+test) **재인덱싱본**(296파일)으로 인덱스 범위 정렬 완료.
- gold(소스 독립 산출): M1 = 33 호출지점(prod 12 + test 21), 함정 2(presenter 동명 호출). M2 = 폐포 8함수.

## 핵심 결과 (도구별)

단일 지배 도구 없음. 케이스별 강·약점이 갈린다.

| 도구 | 강점 | 약점 |
|------|------|------|
| **codegraph** | 전수 역호출·집계 회수율 1위(0.875), 단순 질의 효율 우수 | 동명 매칭 오탐(타입 맹점, M1 P 0.94), `instantiates`/`calls` 엣지 분리로 생성 경로 누락 |
| **serena** | 타입 정밀(정밀도 0.97~1.00) | 명시적 타입 없는 DI 수신자(`service<T>()`·`getService()`) 호출 일관 누락 → M1 재현율 0.76 |
| **zoekt** | 단순 전수 조회 최소 비용(M1 1콜·37s) | 다중홉 폐포 말단 회수 최저(M2 0.625) — 람다/생성자 귀속 약함 |
| **rg(+sg)** | 텍스트 컨텍스트로 높은 정확도(0.875 / 0.985) | 다중홉 수동 BFS 비용. sg 미사용(rg-only) |
| **bare** | 텍스트+읽기. 단순 역호출 강(M1 0.97, DI 수신자도 회수) | 깊은 transitive 최약체(M2 0.625). rg 와 동일 능력군 |

> 방법론 주의(bare 격리): 서브에이전트가 환경의 serena 정책 훅에 납치되기 쉽다(초기 실행에서 bare 가 serena 로 전환됨).
> "정책이 serena 를 권해도 무시하라"는 최우선 지시로 통제해 순수 텍스트검색+읽기로 측정했다. grep 호출은 내장 Grep 대신
> `Bash grep` 경유였으나 동일 ripgrep 엔진이라 능력 동등 — 측정 유효. 재현 시 동일 처리 필요.

## 스코어보드 (recall / precision)

| 케이스 (gold) | A codegraph | B serena | C zoekt | D rg(+sg) | E bare |
|---|---|---|---|---|---|
| **M1** 시그니처 영향 (33) | 1.00 / **0.94** | **0.76** / 0.96 | 1.00 / 0.97 | 1.00 / 0.97 | 0.97 / 1.00 |
| **M2** transitive 폐포 (8) | 0.75 / 1.00 | 0.75 / 1.00 | **0.625** / 1.00 | 0.75 / 1.00 | **0.625** / 1.00 |
| **평균 recall** | **0.875** | 0.755 | 0.81 | **0.875** | 0.80 |
| **평균 precision** | 0.94 | 0.97 | 0.985 | 0.985 | 1.00 |

격리 준수: A·B·C 완전 준수. D = `rg` 만(sg 미사용). **E(bare) = serena 오염 제거(텍스트+읽기), 단 grep 을 Bash 경유 — ripgrep 엔진 동일이라 능력 동등.**

## 토큰·효율 상세 (서브에이전트 usage 기준)

출력 토큰(output) = 실제 생성량, 캐시읽기(cacheR) = 프롬프트+리포 컨텍스트 재독(매 턴) — 입력 비용의 대부분. calls = 도구 호출 수.

| 암 | M1 out / cacheR / calls / time | M2 out / cacheR / calls / time | 합계 out / cacheR / calls / time |
|---|---|---|---|
| A codegraph | 3,388 / 59.6k / 3 / 52s | 3,809 / 447.6k / 19 / 140s | **7,197 / 507k / 22 / 192s** |
| B serena | 3,212 / 132.9k / 4 / 49s | 6,054 / **1,417.2k** / 29 / 203s | 9,266 / **1,550k** / 33 / 252s |
| C zoekt | 3,103 / **28.7k** / **1** / **37s** | 4,730 / 916.8k / 33 / 197s | 7,833 / 945k / 34 / 234s |
| D rg(+sg) | 4,435 / 287.0k / 14 / 69s | 5,243 / 670.8k / 26 / 113s | 9,678 / 958k / 40 / 182s |
| E bare | 3,188 / 77.5k / 4 / 42s | 3,154 / 478.0k / 15 / 72s | **6,342 / 555k / 19 / 114s** |

- **M1 전수 조회 효율**: zoekt 1콜·28.7k·37s 로 압도적 최소. codegraph 3콜. (인덱스/그래프 한 방 질의)
- **M2 다중홉 비용 폭증**: serena 29콜·캐시읽기 1.42M·203s — 한 단계씩 거슬러 올라가며 컨텍스트를 반복 재독. zoekt 33콜·917k 도 유사.
- **bare 가 토큰·시간 최소(합계)**: 6.3k out·555k cacheR·114s. 단 이는 "우수"가 아니라 **얕게 탐색하고 일찍 멈춘**(M2 0.625) 결과이기도 하다 — 적은 비용과 낮은 재현율의 트레이드오프.

## 케이스별 결과값 상세 (hit / miss)

### M1 (gold 33 = prod 12 + test 21, 함정 2)
| 암 | prod hit | test hit | recall | 오탐/누락 상세 |
|---|---|---|---|---|
| codegraph | 12/12 | 21/21 | **1.00** | 오탐 2: 동명 `presenter.formatJson`(Prettify:18, Uglify:20) 포함 → precision 0.94 |
| serena | **4/12** | 21/21 | 0.76 | 누락 8: 명시적 타입 없는 DI 필드 수신자 전부(`formatterService.*`, `service<T>()`) |
| zoekt | 12/12 | 21/21 | 1.00 | 함정 정확 배제. precision 0.97(메서드 정의 1건 포함) |
| rg(+sg) | 12/12 | 21/21 | 1.00 | 함정 정확 배제. precision 0.97 |
| bare(text+read) | **11/12** | 21/21 | 0.97 | 누락 1: `formatJsonOnDefault` 내부 자기호출(:215). **DI 수신자(:60)는 텍스트로 회수** |

### M2 (gold 8 함수)
| 암 | hit | recall | 누락 |
|---|---|---|---|
| codegraph | 6/8 | 0.75 | `ConvertTypeDialogPresenter.<init>`(instantiates 엣지), `syncLanguage`(람다 콜백) |
| serena | 6/8 | 0.75 | 생성자 2개(`<init>`). DI 홉은 find_symbol 우회로 회수 |
| zoekt | 5/8 | **0.625** | `bindView` + 생성자 2개 |
| rg(+sg) | 6/8 | 0.75 | `ConvertTypeDialogPresenter` 상단(`<init>`, `syncLanguage` 중 1) |
| bare(text+read) | 5/8 | **0.625** | `TypeToJsonDialogPresenter.<init>`, `bindView`, `ConvertTypeDialogPresenter.<init>` |

## 핵심 발견

### 1. bare(텍스트+읽기)는 rg 와 동일 능력군 — 독립적 우위 없음
bare 재현율은 M1 0.97 / M2 0.625 로, 같은 텍스트+읽기 계열인 rg(1.00 / 0.75) 에 근소하게 못 미친다.
단순 역호출엔 충분하나(텍스트라 DI 수신자도 회수) 깊은 transitive 폐포에선 최약체. "내장도구만으로 충분/최고"라는 일반화는 성립하지 않는다(과제 규모·유형 의존). 격리 처리는 위 방법론 주의 참조.

### 2. codegraph 의 타입 맹점 재확인 (M1 precision 0.94)
M1 에서 codegraph 는 이름만으로 매칭해 **동명 `JsoninjaPanelPresenter.formatJson` 호출 2건(PrettifyJsonAction:18, UglifyJsonAction:20)을 오탐**으로 포함했다(precision 0.94). serena·텍스트 암은 수신자 타입을 보고 정확히 배제. 단 codegraph 는 재현율은 1.00(테스트 꼬리 21건 포함 전부 회수) — 그래프의 전수성은 강점, 타입 구분은 약점.

### 3. serena DI 약점은 형태 무관하게 일관됨 — M1·M2 차이는 "도구 한계"가 아니라 "에이전트 행동" (raw 출력 직접 검증)
처음엔 "M1 에선 발현, M2 에선 미발현 → DI 형태 의존"으로 봤으나, **serena 의 raw 출력을 직접 호출해 검증한 결과 그 해석은 틀렸다.**
- **도구 한계는 M1·M2 동일**: `find_referencing_symbols` 를 직접 실행하니
  - M1: `JsonFormatterService.formatJson` → 프로덕션 12개 중 **4개만** 반환(누락 8개).
  - M2: `analyzeSource`/`generate` → **테스트 참조만** 반환, 프로덕션 DI 홉(`generate`→`analyzeSource`, `schedulePreview`→`generate`)은 **둘 다 누락**.
- **누락의 진짜 원인(필드 선언 확인)**: 누락된 호출의 수신자는 전부 **명시적 타입 없이 DI 로 초기화된 val** 이다 —
  `val formatterService = project.service<JsonFormatterService>()`(reified) 와 `= project.getService(...::class.java)`(class.java) **둘 다 동일하게 누락**.
  적중한 2건(EditorPrettify/Uglify)만 수신자가 **명시적 파라미터 타입** `service: JsonFormatterService` 였다.
  → 즉 reified vs class.java 가 아니라, **DI 호출 반환 타입을 LSP 가 추론 못 하는 것**(IntelliJ Platform SDK 가 분석 classpath 밖)이 공통 원인. 명시적 타입 주석이 있으면 해소된다.
- **M1(0.76) vs M2(0.75) 재현율 차이의 실제 원인 = 에이전트 행동**:
  - M1 에이전트: `find_referencing_symbols` 1회 호출 후 도구의 누락을 **그대로 보고**(recall 0.76).
  - M2 에이전트: `find_symbol` 9회로 파이프라인 구조를 탐색하며 누락된 DI 홉을 **의미론적으로 우회 복원**(generate 본문에서 analyzeSource 호출 확인 등) → recall 0.75 달성.
- **핵심**: "도구 원시출력 ≠ 에이전트가 그 도구로 도달한 답"(README §의 raw-vs-agent 주제 재현). serena 도구의 DI 맹점은 일관되며, 유능한 에이전트가 find_symbol 탐색으로 부분 우회할 뿐이다.

### 4. transitive 말단(생성자·람다 콜백)은 단일 도구가 지배 못 함 (M2)
폐포 8함수 중 깊이 4~5 말단(`TypeToJsonDialogPresenter.<init>`, `ConvertTypeDialogPresenter.<init>`, `syncLanguage`)에서 누락 양상이 도구별로 달랐다:
- **codegraph(0.75)**: 두 메커니즘이 겹쳤다. (a) `ConvertTypeDialogPresenter.<init>` 누락은 **`instantiates` vs `calls` 엣지 분리** 탓 — `TypeToJsonDialogPresenter(...)` 생성은 `instantiates` 엣지인데 역호출 CTE 가 `calls` 만 순회해 도달 못 함(`instantiates` 엣지가 `calls` 와 분리되는 함정). (b) `syncLanguage` 누락은 람다 콜백(`{ syncLanguage(...) }`)을 정적 calls 엣지가 못 잡은 것.
- **serena(0.75)**: 람다 콜백·DI 홉은 (find_symbol 우회로) 추적했으나 **생성자(`<init>`) 2개 누락**.
- **zoekt·bare(0.625, 최저)**: 텍스트 매칭이라 람다/init 블록의 호출 귀속이 가장 약함 — zoekt 는 bindView+생성자 2, bare 는 두 `<init>`+bindView 누락.
- **rg(0.75)**: TypeToJson 생성자는 잡았으나 ConvertType 상단 1개 누락.
→ 다중홉 폐포의 "마지막 한 홉"에서 모든 도구가 서로 다른 이유로 샌다. 어떤 단일 도구도 8/8 을 못 했다.

### 5. 효율: 인덱스/그래프가 단순 조회에서 압도적, 다중홉에선 호출수 폭증
- **M1(전수 역호출)**: zoekt 1콜·37s, codegraph 3콜. 한 번의 인덱스/그래프 질의로 끝남.
- **M2(다중홉 BFS)**: serena 29콜/203s, zoekt 33콜/197s, rg 26콜 — 한 단계씩 거슬러 올라가야 해 호출수·시간 급증. codegraph 도 CTE 작성 시행착오로 19콜/140s.

## 격리 컴플라이언스 감사 (transcript 도구사용 전수 확인)
- **A codegraph (2/2)**: `Bash(sqlite3)` 만 → 준수.
- **B serena (2/2)**: `mcp__serena__*` 만 → 준수.
- **C zoekt (2/2)**: `Bash(zoekt)` 만 → 준수.
- **D rg(+sg) (2/2)**: `Bash(rg)` 만(M1), `Bash(rg)`+`Read`(M2) → 텍스트 계열 준수. 단 **sg 미사용**(rg-only).
- **E bare (2/2)**: serena 오염 제거됨(최우선 anti-hook 지시 효과). 단 grep 을 **내장 `Grep` 대신 `Bash grep` 경유** — 같은 ripgrep 엔진이라 능력 동등(텍스트+읽기). 능력을 바꾸는 오염은 없음.

## 종합 (이 2케이스 한정)
- **재현율 공동 1위**: codegraph·rg(0.875). codegraph 는 전수성, rg 는 텍스트 컨텍스트로 높은 재현율.
- **정밀도**: codegraph 만 0.94(타입 맹점), 나머지 0.97~1.00.
- **bare(텍스트+읽기)**: M1 0.97(강함, DI 수신자도 회수)·M2 0.625(최약체). rg 와 같은 능력군으로 독립적 우위는 없음.
- **단일 지배자 없음(가설 재확인)**: codegraph=전수성·효율, serena=타입 정밀(단 DI 수신자 일관 누락), 텍스트(rg/bare)=경계·컨텍스트, 그러나 다중홉 말단은 공통 취약.

## 한계 / 후속
- 각 케이스 1회 실행(N=1). 분산 미측정.
- **bare 격리 수정 완료(serena 오염 제거)**. 남은 nominal 이슈는 grep 의 Bash 경유뿐이며 ripgrep 엔진 동일이라 측정에 무영향. 내장 `Grep` 도구로의 강제까지 원하면 MCP·Bash 를 비활성화한 샌드박스가 필요(능력은 안 바뀜).
- D 암은 또 sg 미사용(rg-only). 구조검색(sg) 변별은 여전히 미측정.
- 토큰/시간은 서브에이전트 usage 기준 추정치.
- 채점 컨벤션: M2 gold 의 `bindView` 는 런타임에 직접 `schedulePreview` 를 호출하는 게 아니라 콜백을 등록하는 함수다(렉시컬 포함 기준으로 폐포에 넣음). 전 암을 동일 기준으로 채점했으므로 상대비교는 유효하다.
- 검증 보강: 본 결과의 핵심 수치(serena M1·M2 DI 누락)는 에이전트 자가보고가 아니라 `find_referencing_symbols` 를 직접 실행해 raw 출력으로 확인했다.
