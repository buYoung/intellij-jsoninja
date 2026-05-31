# 다음 세션 작업 지시서 — SCIP 암(G) · zoekt+ctags 암(H) · M3-W/M3-L 케이스 추가

> 이 문서 하나로 새 세션에서 작업을 시작할 수 있도록 자립형으로 정리했다.
> 대상 리포: `json-helper2` (IntelliJ Platform 플러그인 — **polyglot**: Kotlin 플러그인 + Rust `tree-sitter-wasm` 크레이트 + 다중 타깃언어).
> 작업 디렉터리: `evals/codegraph-vs-serena/`.
>
> **확정 사항(이전 세션 결정)**: ① M3 는 초안의 "거울상 Presenter" 안을 폐기하고 **polyglot 경계 저격**(M3-W: Kotlin↔Rust WASM ABI seam,
> M3-L: 다중 타깃언어 enum)으로 재설계함(근거 §3.0). ② **M3-W 범위는 A안 확정** = 전 암(A~H) 인덱스에 `tree-sitter-wasm/`(Rust) 포함 재인덱싱.
> 구성: **8 암 × 4 케이스(M1·M2·M3-W·M3-L)**, 추가 실행 20 서브에이전트(총 32). 상세는 §3~§7.

---

## 0. 현재 상태 (컨텍스트)

기존 벤치마크는 **6개 암 × 2 케이스(M1·M2) × N=1**, sonnet 서브에이전트, 동일 베이스 프롬프트,
`{{ALLOWED_TOOLS_BLOCK}}` 만 차등, blind 채점이다.
이번에 **2 암(G·H)** + **2 케이스(M3-W·M3-L)** 를 추가하여 **8 암 × 4 케이스**로 확장한다(M3 설계는 §3, 확정사항은 문서 상단 참조).

| 암 | 백엔드 | 상태 |
|----|--------|------|
| A codegraph | `.codegraph/codegraph.db` 그래프 SQLite | 완료 |
| B serena | LSP MCP | 완료 |
| C zoekt | 트라이그램 텍스트 인덱스(**ctags 없음**, src main+test) | 완료 |
| D rg+sg | ripgrep + ast-grep(실제론 rg-only) | 완료 |
| E bare | 내장 Grep+Glob+Read | 완료 |
| F cocoindex | tree-sitter 청킹 + MiniLM 임베딩 + SQLite 벡터검색 | 완료 (`cocoindex/`) |

- **provenance 고정**: git `v1.12.1` / commit `1daf879beae6ecc853be6a7e496a240568bddea5`. 모든 인덱스·gold 는 이 커밋 기준.
- **gold 원칙**: 측정 대상 도구 중 **어느 것도 gold 산출에 쓰지 않는다**(순환논리 차단). 소스 직접 판독으로 손산출.
- **채점**: recall = 맞춘 gold / 전체, precision = 맞춘 / 제출. 라인 ±1 정규화. blind(에이전트에 gold 비공개).
- **환경**: `uv`, `python3.13`, `docker`, `go`(zoekt 는 `$HOME/go/bin`) 사용 가능. cocoindex 암 참고 구현이
  `cocoindex/`(build_index.py·query.py·README.md)에 있으니 **쿼리 래퍼 패턴을 그대로 모방**할 것.
- 문서 6종: `dataset.yaml`, `benchmark-design.md`, `README.md`, `results.md`, `report.html`, `cocoindex/README.md`.

### 핵심 기존 결과 (왜 이 3개를 추가하나)
- M1(시그니처 blast radius·33 호출지점): codegraph/zoekt/rg 1.00, **serena 0.76**(DI 수신자 누락), cocoindex 0.67(임베딩 비전수).
- M2(getOrCreate transitive 폐포·8함수): codegraph/serena/rg/cocoindex 0.75, **zoekt/bare 0.625**(생성자·람다 함수귀속 실패).
- **공통 미해결 지점** = ① serena 의 DI 수신자 타입 맹점, ② 생성자(`<init>`)·람다 콜백의 transitive 함수귀속.
  → 새 도구는 이 두 지점을 정조준해야 정보량이 크다.

---

## 1. 추가 ① — SCIP 암 (G): scip-kotlin **정밀(precise)** 인덱스

### 목적
컴파일러급 정밀 xref 가 **serena LSP 의 DI 수신자 맹점**과 **codegraph 의 instantiates-엣지 누락**을
동시에 메우는지 본다. "단일 지배 도구 없음" 결론을 뒤집을 수 있는 유일한 후보.

### ⚠️ 반드시 precise 인덱서를 쓸 것 (함정)
- **써야 할 것**: `scip-kotlin` (sourcegraph, Kotlin 컴파일러 기반). SCIP 심볼이 **완전수식**
  (scheme+package+descriptor)이라 `TypeToJsonDialogPresenter#updateLanguage` 와
  `JsonToTypeDialogPresenter#updateLanguage` 가 **서로 다른 심볼 문자열** → 정밀 구분 가능.
- **쓰면 안 되는 것**: `scip-ctags` (ctags 결과를 SCIP 로 포장한 저정밀 폴백). 타입을 모르니 ctags 와 동급 →
  이 실험의 목적(컴파일러급 검증)이 무력화됨. 절대 혼동 금지.

### 구축 절차
> ⚠️ 아래 설치 명령·서브커맨드(`scip print --json` 등)의 정확한 플래그/서브커맨드는 실행 세션에서 반드시 확인할 것(본 문서 작성 시점 네트워크 미실행).

0. **선행 설치** (세션에서 미설치 시 실행):
   ```bash
   # scip CLI 설치
   go install github.com/sourcegraph/scip/cmd/scip@latest
   # scip-kotlin: GitHub Releases(sourcegraph/scip-kotlin)에서 바이너리 다운로드 후 PATH 에 추가
   # (예시 — 버전은 최신 릴리스로 대체)
   # curl -L https://github.com/sourcegraph/scip-kotlin/releases/latest/download/scip-kotlin-<OS> -o ~/bin/scip-kotlin
   # chmod +x ~/bin/scip-kotlin
   ```
1. `scip-kotlin` 설치/실행 (sourcegraph/scip-kotlin, Gradle autoindex). v1.12.1 소스를 컴파일해 `scip/index.scip`(protobuf) 생성.
2. `scip` CLI(sourcegraph/scip)로 JSON 변환: `scip print --json scip/index.scip > scip/index.json`.
3. provenance 기록: `sha256sum scip/index.scip` 결과를 기록.
4. **쿼리 래퍼**(`scip/query.py`) 작성: 심볼명/수식명을 받아 그 심볼의 **정의 + 모든 참조 occurrence(file:line + role)**
   를 반환.
   - **CLI**: `python scip/query.py "<symbol_name_or_pattern>" [--index scip/index.json] [--role def|ref|all]`
   - **출력 JSON**: `{symbol, definitions:[{file,line}], references:[{file,line,role}], total}`
   - ⚠️ cocoindex/query.py(시맨틱 벡터검색)와 출력 구조가 **근본적으로 다름**(cocoindex = top-k 청크, scip = 심볼 xref). **동일 스키마 아님**. 벤치마크 채점기가 사용하는 최소 필드(`file`, `line`)는 반드시 포함할 것.
5. provenance: v1.12.1 고정. 인덱싱 입력은 `cocoindex/.src-v1.12.1` 처럼 `git archive v1.12.1` 추출본 사용
   (단 scip-kotlin 은 **빌드가 필요**하므로 전체 소스+Gradle 이 있어야 함 → worktree 또는 클린 체크아웃 권장).

### 서브에이전트 격리(ALLOWED_TOOLS_BLOCK)
`Bash 로 scip 쿼리 스크립트('python scip/query.py "심볼" ...')만. 소스 직접 Read/grep 금지.`

### 리스크 (막히면 여기서 멈추고 보고)
- scip-kotlin 이 **프로젝트 Gradle 빌드를 컴파일**해야 인덱싱됨. IntelliJ Platform 플러그인이라 Kotlin/Gradle
  버전 호환 이슈로 인덱싱이 한 번에 안 될 수 있음(cocoindex 처럼 깔끔하지 않을 가능성 높음).
- 대안: 인덱싱이 끝내 안 되면 `scip-kotlin` 대신 IntelliJ 의 Kotlin Analysis API 기반 추출을 검토하되, 비용 큼 → 보고 후 결정.

---

## 2. 추가 ② — zoekt+ctags 암 (H): 기존 zoekt 업그레이드가 아닌 **신규 암**

### 목적
기존 C(plain 트라이그램)는 "라인만 주고 함수는 모름" 때문에 M2 에서 0.625(최약). zoekt 인덱싱 시
**universal-ctags 를 물려 심볼 메타데이터**를 넣으면 라인→심볼 매핑이 생겨 그 약점이 해소되는지 본다.

### 왜 신규 암(C 유지)인가
C(plain) 를 덮어쓰면 "ctags 가 뭘 더 해주나"를 비교할 baseline 이 사라진다. **C(plain) + H(ctags) 를 둘 다 두고
A/B 비교**해야 ctags 효과가 측정된다.

### 구축 절차
> ⚠️ 아래 설치 명령·플래그는 실행 세션에서 반드시 확인할 것(본 문서 작성 시점 네트워크 미실행).

0. **선행 설치** (세션에서 미설치 시 실행):
   ```bash
   # universal-ctags 설치
   brew install universal-ctags
   # Universal Ctags 여부 확인 (BSD ctags 와 구분)
   ctags --version | head -1
   # zoekt-index / zoekt 검색 바이너리 설치
   go install github.com/sourcegraph/zoekt/cmd/zoekt-index@latest
   go install github.com/sourcegraph/zoekt/cmd/zoekt@latest
   # go bin 경로 확인
   export PATH="$HOME/go/bin:$PATH"
   ```
1. `universal-ctags` 설치(brew). `zoekt-index` 가 PATH 의 ctags 를 자동 감지하도록.
2. `src/main/kotlin + src/test/kotlin`(+ M3-W 공정성 전제 준수 시 `tree-sitter-wasm/src`) 을 **ctags 활성 상태로 재인덱싱** → `.codegraph/zoekt-ctags-index` 등 별도 경로.
   (C 의 기존 인덱스와 분리. sha·파일수 기록.)
   ```bash
   # 재인덱싱 예시 (경로·플래그는 세션에서 검증)
   zoekt-index -index .codegraph/zoekt-ctags-index src/main/kotlin src/test/kotlin
   # 인덱스 생성 검증
   ls .codegraph/zoekt-ctags-index/*.zoekt | wc -l
   ```
3. 서브에이전트는 zoekt 검색 CLI 만 사용하되, **`sym:` 심볼 검색**을 적극 쓰도록 프롬프트에 명시.
   - `sym:` 검색 예시: `zoekt -index .codegraph/zoekt-ctags-index "sym:SupportedLanguage"`
4. ⚠️ ctags 의 Kotlin 지원 수준 확인: universal-ctags 가 Kotlin 을 native 로 지원하는지(아니면 보강 필요).
   지원이 빈약하면 "ctags 효과 제한적"이 결과의 일부가 됨(정직하게 기록).

### 서브에이전트 격리(ALLOWED_TOOLS_BLOCK)
`Bash 로 zoekt 검색 CLI 만 (ctags 활성 재인덱싱본 대상, sym: 검색 권장). 소스 직접 Read 금지.`

---

## 3. 추가 ③ — M3 케이스: **polyglot 경계 저격** (Kotlin 플러그인 ↔ Rust `tree-sitter-wasm` ↔ 다중 타깃언어)

### 시나리오 재정의 (사용자 의도 = "tree-sitter-wasm + Kotlin 을 다루는 코드베이스를 저격")
초안의 "거울상 Presenter(TypeToJson↔JsonToType)" 안은 **현 소스 실측 결과 무효**다(근거 §3.0).
대신 이 리포의 **진짜 다중 세계**는 두 가지이고, 둘 다 정밀↔텍스트 서열을 **정반대 방향**으로 가른다:
- **경계①(언어 간 ABI seam)**: Kotlin 호스트가 Rust WASM export 를 **문자열 이름으로** 바인딩
  (`TreeSitterWasmRuntime.kt:61-64` 의 `instance.export("alloc"/"dealloc"/"analyze_source"/"get_last_error")`
  ↔ `tree-sitter-wasm/src/lib.rs` 의 `#[no_mangle] pub extern "C"` 정의). → 단일언어 정밀도구의 **맹점**.
- **경계②(언어 내 다중 타깃언어 분기)**: 단일 클래스가 `when(language)` 로 4개 타깃언어(KOTLIN/JAVA/TYPESCRIPT/GO)를
  분기. `SupportedLanguage.KOTLIN` enum 상수 **심볼 참조** vs 텍스트 `'kotlin'` 과대매칭. → 정밀도구 **우위**.

### 3.0 초안 무효 근거 (실측, v1.12.1 — advisor 3종 교차검증)
- `updateLanguage(language: SupportedLanguage)` 는 **두 Presenter 에만** 존재(`TypeToJsonDialogPresenter.kt:38`,
  `JsonToTypeDialogPresenter.kt:47`). View 는 동명 아님(`updateInputLanguage`/`updateLanguageOptions`) → "4중 이름충돌" 거짓.
- 그 호출지점은 `ConvertTypeDialogPresenter.kt:79,81` **딱 2곳**(gold 1 + trap 1)이고 인접 2줄이며 수신자 변수명
  (`typeToJsonPresenter`/`jsonToTypePresenter`)이 방향을 자기설명 → 텍스트 도구도 무료 정답 → **변별력 0**.
- 서비스측은 `generate`(TypeToJson) vs `convert`(JsonToType) 로 **이름이 달라** 함정 자체가 없음 → 초안 M3-b 무효.
- 타깃언어 핸들러는 클래스-당-언어가 아니라 **단일 클래스 + `when(language)`**(`JsonToTypeRenderer.kt:112`);
  언어별 private 메서드는 `renderKotlinType`/`renderJavaType` 처럼 **이름이 달라** 교차클래스 충돌 없음.

### 3.A — M3-W: 언어 간 WASM ABI seam (가설: **서열 역전** — 정밀도구 맹점)
- **질문**: "Kotlin 호스트가 Rust WASM 모듈의 export 를 이름으로 바인딩하는 모든 지점과, 그에 대응하는 Rust 측
  `#[no_mangle] pub extern \"C\"` **정의**를 모두 나열하라. 대상 ABI: `analyze_source`, `get_last_error`, `alloc`, `dealloc`."
- **gold 구성**: (Kotlin 문자열 바인딩 `TreeSitterWasmRuntime.kt:61-64` 4항목) + (Rust 정의 `tree-sitter-wasm/src/lib.rs` 의 대응 4함수: alloc:25, dealloc:30, analyze_source:92, get_last_error:121)
  = **총 8 항목**. **언어 2개에 걸친 cross-file 쌍**.
  - 채점 단위: 항목별 `(file:line)`. **Kotlin 측 recall 과 Rust 측 recall 을 분리 집계** 권장(B serena 처럼 한쪽만 포착하는 부분 실패를 표현하기 위함).
  - ⚠️ `WasmMemoryBridge.kt:19`(`runtimeHandle.alloc`)·`:36`(`runtimeHandle.dealloc`)은 2차 사용 지점(ABI 바인딩 포인트 아님) → **traps_must_exclude** 처리.
- **가설**:
  - G scip-kotlin = Kotlin 전용 인덱서, Rust 파일 처리 불가 + Kotlin 측 `export(...)` 인자는 **문자열 리터럴**(심볼 아님) → **양방향 전멸**.
  - B serena(Kotlin+Rust LSP, serena project.yml(현재 설정)에 rust 포함) = Rust 함수 정의 자체는 인덱싱/포착 가능(rust-analyzer 경유)하나 Kotlin 측 문자열 리터럴 바인딩 포인트는 미포착 → **Kotlin 측 miss / Rust 측 hit → 부분 실패(recall>0)**.
  - A codegraph(Kotlin 파서) = .rs 인덱싱 지원 여부 세션에서 확인 필요. 미지원이면 전멸, 지원이면 serena 와 유사 패턴(Rust 함수는 포착, 문자열→Rust 함수 링크 추론은 불가).
  - C zoekt / D rg / E bare = 텍스트로 Kotlin·Rust 양측 매칭 → **승리**.
  - F cocoindex = Rust 청크 인덱싱 시 토큰 강함 → 부분 성공 가능.
- **이게 핵심 신규 정보**: M1/M2 의 "정밀>텍스트" 서열을 **뒤집는** 유일 케이스 → "단일 지배 도구 없음"을 비단조성으로 강화.
- ✅ **공정성 전제(확정 — A안)**: **모든 암(A~H)의 인덱싱 범위에 `tree-sitter-wasm/`(Rust)를 포함**한다.
  기존 6암(A~F)은 `src/main+src/test`(Kotlin)만 인덱싱했으므로 **Rust 포함으로 재인덱싱**한다(범위 통일이 성립 조건).
  scip(Kotlin 전용)·serena 등 정밀암이 Rust 를 못 커버하는 것은 결과의 일부(=맹점)로 그대로 드러난다 — 이게 M3-W 의 의도.
- ⚠️ `alloc`/`dealloc` 은 generic 토큰이라 텍스트 오탐 큼 → 변별 핵심은 distinctive 한 `analyze_source`/`get_last_error` 에 둘 것.
- 📝 **lib.rs export 범위 주석(채점자 혼란 방지)**: `tree-sitter-wasm/src/lib.rs` 의 `#[no_mangle] pub extern "C"` export 는 **총 12개**이나, Kotlin 측 `instance.export()` 바인딩이 존재하는 것은 4종(alloc:25, dealloc:30, analyze_source:92, get_last_error:121)뿐이다. 나머지 8개(parser_create:35·parser_destroy:40·tree_parse:45·parse:57·tree_query:66·query_execute:81·tree_destroy:111·get_supported_languages:116)는 M3-W gold 스코프 **밖**이다.

### 3.B — M3-L: 다중 타깃언어 enum 정밀 (가설: **정상 서열** — 정밀도구 우위)
- **질문**: "`SupportedLanguage.KOTLIN` enum 상수를 **심볼로서 참조**하는 모든 지점(`when` 분기 포함)을 나열하라.
  단순 문자열 `\"kotlin\"`/`\"kt\"`, 리소스 경로(`tree-sitter/queries/kotlin/…`), `Kotlin*` 식별자,
  코루틴/플랫폼 문맥의 'kotlin' 등은 **제외**(traps_must_exclude)."
- **gold 규모(정찰 추정 — 설계 타당성 근거일 뿐, 확정 gold 는 세션 산출)**: `SupportedLanguage.KOTLIN` 심볼 참조
  ≈ **28개**(main 12 + test 16). 텍스트 `'kotlin'`(대소문자무시) 매칭 ≈ **180라인/약 42파일** → 텍스트 도구 예상 정밀도 ≈ 15%.
  *(정찰 추정: scope/도구 옵션에 따라 변동; gold 산출 세션에서 src/main+src/test 범위로 실측 후 정밀도 재계산)*
  (M2 의 gold 8 보다 풍부 → 측정 신뢰도 충분.)
- **가설**:
  - B serena / G scip / A codegraph = enum 상수 참조 정밀 해소 → **고정밀**.
  - C zoekt / D rg / E bare = `'kotlin'` 양측 과대매칭(문자열·경로·타입명·코루틴) → 함정 대량 → **저정밀**.
  - H zoekt+ctags = `sym:` 로 일부 개선 관전.
  - F cocoindex = 'kotlin' 의미 분산 → 저재현.

### 서브에이전트 격리(ALLOWED_TOOLS_BLOCK)
W·L 모두 각 암의 **기존 ALLOWED_TOOLS_BLOCK 그대로**(소스 직접 Read/grep 금지, 해당 암 도구만).
M3-W 는 공정성 전제(인덱스 범위에 Rust 포함, A안 확정)를 모든 암에 **동일 적용**한 인덱스를 대상으로 실행한다.

### gold 산출 방법 (도구 비사용·소스 직접 판독, 무오염)
1. 후보 전수(탐색용일 뿐 정답 출처 아님): (W) `instance.export(` + `pub extern "C"` / (L) `SupportedLanguage.KOTLIN` + `rg -i kotlin`.
2. 각 매치를 소스에서 읽어 (W) 호스트-바인딩/Rust-정의 여부, (L) enum 상수 심볼 참조 여부(문자열·경로·타입명 trap 배제)를 손판별.
3. `(file:line)` 단위 gold/trap 고정. provenance(v1.12.1 / 1daf879). gold 수치는 본 문서에 적지 않음(세션 산출).

### 가설 요약표
| 암 | M3-W (ABI seam, 언어 간) | M3-L (다중언어 enum, 언어 내) |
|----|----|----|
| A codegraph | Rust 링크 누락 → 미포착 | enum 참조 정밀(이름오탐 일부 위험) |
| B serena | 문자열=심볼아님 → Kotlin 바인딩 포인트 미포착(Rust 함수는 포착, recall>0) | **고정밀** |
| C zoekt | 텍스트로 양측 → **승리** | 'kotlin' 과대매칭 → 저정밀 |
| D rg+sg | 텍스트로 양측 → **승리** | 과대매칭 → 저정밀 |
| E bare | 텍스트로 양측 → 승리(수작업) | 과대매칭 → 저정밀 |
| F cocoindex | Rust 청크 시 부분성공 | 의미분산 → 저재현 |
| G scip | **맹점(전멸)** | **고정밀(승자)** |
| H zoekt+ctags | 텍스트로 양측 → 승리 | sym: 개선 관전 |

→ **비단조 서열**: 경계가 *언어 간*(W)이면 텍스트 승, *언어 내 심볼*(L)이면 정밀 승. polyglot 코드베이스의 핵심 교훈.

---

## 4. 손대야 할 파일 (6-way → 8-way, 2-case → 4-case: M1·M2·M3-W·M3-L)

- `scip/` (신규 디렉터리): `requirements.txt`(or 설치 메모)·`build_index`(인덱싱 절차 스크립트)·`query.py`·`README.md`. cocoindex/ 패턴 모방.
- `zoekt-ctags` 관련: 재인덱싱 절차를 `README.md` 재현 절에 추가(별도 디렉터리 불필요, 인덱스는 `.codegraph/` 하위·gitignore).
- `dataset.yaml`: `arms` 에 `G_scip`, `H_zoekt_ctags` 추가. `cases` 에 **M3-W·M3-L 블록 신규**(question·discriminators·gold(세션산출)·traps).
  M3-W 는 `scope` 필드에 `tree-sitter-wasm/`(Rust) 포함을 명시(전 암 동일 적용).
  - ⚠️ **arms 는 맵 형태**(기존 `A_codegraph: '...'` / `B_serena: '...'` 패턴). 기존 항목과 동일하게 아래 구조로 추가:
    ```yaml
    # arms 맵에 추가 (기존 F_cocoindex 항목 직후)
    G_scip:     'Bash 로 scip 쿼리 스크립트(''python scip/query.py "심볼" ...'') 만. 소스 직접 Read/grep 금지.'
    H_zoekt_ctags: 'Bash 로 zoekt 검색 CLI 만 (ctags 활성 재인덱싱본 대상, sym: 검색 권장). 소스 직접 Read 금지.'
    ```
  - M3-W gold 구조(기존 M1 패턴 준용, 실제 라인 값은 세션 산출):
    ```yaml
    # cases 에 M3-W 블록 추가
    - id: M3-W
      question: "Kotlin 호스트가 Rust WASM 모듈의 export 를 이름으로 바인딩하는 모든 지점과 대응 Rust 정의를 나열하라."
      scope: "whole-repo (src/main/kotlin + src/test/kotlin + tree-sitter-wasm/src)"
      matching_key: "M3-W = Kotlin 측 (file:line) + Rust 측 (file:line), 분리 채점 후 합산"
      gold:
        kotlin_sites:   # TBD — TreeSitterWasmRuntime.kt:61-64 (세션 산출)
          - { file: "...", line: TBD }
        rust_sites:     # TBD — lib.rs alloc:25/dealloc:30/analyze_source:92/get_last_error:121
          - { file: "...", line: TBD }
        total_gold_sites: 8
      traps_must_exclude:
        - { file: "WasmMemoryBridge.kt", line: 19, note: "2차 사용(runtimeHandle.alloc) — ABI 바인딩 아님" }
        - { file: "WasmMemoryBridge.kt", line: 36, note: "2차 사용(runtimeHandle.dealloc) — ABI 바인딩 아님" }
    # cases 에 M3-L 블록 추가
    - id: M3-L
      question: "SupportedLanguage.KOTLIN enum 상수를 심볼로서 참조하는 모든 지점을 나열하라."
      scope: "whole-repo (src/main/kotlin + src/test/kotlin)"
      matching_key: "(file:line)"
      gold:
        use_sites: []   # TBD — 세션 산출 (≈28항목)
        total_gold_sites: TBD
      traps_must_exclude:
        - note: "단순 문자열 'kotlin'/'kt', 리소스 경로(tree-sitter/queries/kotlin/…), Kotlin* 식별자, 코루틴/플랫폼 문맥의 kotlin"
    ```
- `benchmark-design.md`: 6→8 암 표, ALLOWED_TOOLS_BLOCK 표 2행 추가, 케이스 표에 M3-W·M3-L 열,
  실행 수 "기존 12 + 신규 20 = 총 32 서브에이전트 (8 암 × 4 케이스)", **비단조 서열(W=텍스트승 / L=정밀승)** 가설 명기.
- `README.md`(parent): 암 표·결론·재현 절차(scip-kotlin, ctags 재인덱싱).
- `results.md`: 스코어보드·효율·hit/miss·발견·격리감사·종합에 G·H 열과 M3 행 추가.
- `report.html`: 제목·암 표·KPI·스코어보드 표·효율 표·hit/miss·차트 JS(AGG)에 G·H·M3 반영. (cocoindex 토큰처럼 입도 다르면 ‡ 주석.)

---

## 5. 공정성 / provenance 체크리스트
- [ ] G(scip)·H(zoekt+ctags) 인덱스 모두 **v1.12.1 / 1daf879** 소스로 생성. sha·파일수 기록.
- [ ] M3-W·M3-L gold 는 **8개 측정 도구 어느 것도 안 쓰고** 소스 직접 판독으로 산출.
- [ ] 인덱스 범위 정렬: 모든 암이 `src/main + src/test` 동일 범위(M3-L gold 가 test 에도 있으면 포함).
- [ ] **M3-W 전용(A안 확정)**: 모든 암(A~H) 인덱스에 `tree-sitter-wasm/`(Rust) **포함하여 재인덱싱**. 기존 A~F 인덱스는
      Kotlin-only 였으므로 반드시 재구축. 한쪽만 Rust 보이면 비교 무효 → 전 암 범위 일치가 성립 조건. 재인덱싱본 sha·파일수 기록.
- [ ] 각 서브에이전트 transcript 전수 감사(허용 도구 외 사용 0 확인) — cocoindex 때처럼 `~/.claude/projects/.../subagents/agent-*.jsonl` 파싱.
- [ ] N=1 한계 명시(또는 이참에 N≥3 검토 — 별도 결정).

## 6. 오픈 결정 / 리스크
- **scip-kotlin 빌드 가능 여부**가 최대 리스크. 안 되면 G 암 보류하고 H+M3 만 진행할지 결정.
- **ctags Kotlin 지원 수준**: 약하면 H 의 효과가 제한적 — 결과의 일부로 정직 기록.
- M3 채점 단위는 M1 과 동일 `(file:line)` 권장(함수 단위면 텍스트 암이 또 라인→함수 부담).
- **M3-W 범위 공정성(✅ A안으로 확정)**: 전 암(A~H) 인덱스에 Rust 크레이트 **포함하여 재인덱싱**. 기존 A~F 의 Kotlin-only
  인덱스는 재구축 필요. scip(Kotlin 전용)·serena 등 정밀암의 Rust 미커버는 결과의 일부(맹점)로 드러남 — 이게 M3-W 의 의도.
  남은 실무 리스크: 각 백엔드(codegraph/zoekt/scip 등)가 **Rust 파일을 실제로 인덱싱 가능한지** 세션에서 확인(불가한 백엔드는
  "범위 내이나 미지원"으로 정직 기록 — 이 역시 맹점 데이터의 일부).
- **B serena M3-W 부분 성공 시나리오(serena project.yml(현재 설정)에 rust 포함)**: serena 는 rust-analyzer 경유 Rust 함수 정의를 인덱싱/포착 가능. 단 Kotlin 측 `instance.export("...")` 문자열 리터럴 바인딩 포인트는 심볼 아니므로 미포착. 결과 해석 시 Kotlin 측 recall 과 Rust 측 recall 을 **분리 집계**하여 부분 실패 여부를 명확히 판정한다(serena 가 '전멸'로 나오면 project.yml 의 rust 설정 유효성을 세션에서 재확인).
- **M3-W 토큰 generic 함정**: `alloc`/`dealloc` 은 텍스트 오탐이 커 채점 noise — distinctive 한 `analyze_source`/
  `get_last_error` 중심으로 질문·gold 를 구성하거나, generic 토큰은 trap 으로 명시.
- (재정의) 이 리포는 진짜 모노레포가 아니라 **polyglot(Kotlin+Rust/WASM+다중 타깃언어)** 구조다. M3 는 모노레포 흉내가
  아니라 이 **언어 경계(seam)** 를 정조준한다. 진짜 멀티모듈 모노레포 스케일이 별도로 필요하면 그건 현 범위 밖(별도 논의).

## 7. 실행 순서 (권장)
> 범위 결정은 **A안(전 암 Rust 포함)으로 확정**됨 — 아래는 그 전제 위에서의 순서.

1. **전 암(A~H) Rust 포함 재인덱싱**(A안 확정): 기존 A~F Kotlin-only 인덱스 재구축 + G·H 신규 생성. 각 백엔드 Rust 지원 여부 확인·기록.
2. H(zoekt+ctags) 먼저 — 저비용, 즉시 C 와 비교 가능.
3. M3-W·M3-L 케이스 설계 확정 + gold 소스 산출(소스 직접 판독, 무오염).
4. G(scip-kotlin) 인덱싱 시도 — 막히면 보고 후 보류 결정.
5. 8 암 × 4 케이스 중 **신규 조합만** 실행: 기존 6암×2케이스(M1·M2)는 재사용,
   신규 = (G·H)×(M1·M2·M3-W·M3-L) + (A~F)×(M3-W·M3-L).
   - 즉 추가 실행 = G:4 + H:4 + (A~F)×2 = 4+4+12 = **20 서브에이전트** (기존 12 + 신규 20 = 총 32).
6. 채점 → 6종 문서 갱신 → transcript 격리 감사.
