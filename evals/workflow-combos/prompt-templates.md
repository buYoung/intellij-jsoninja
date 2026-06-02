# workflow-combos 벤치마크 — 프롬프트 템플릿 묶음

> **설계 기준**: `evals/codegraph-vs-serena/benchmark-design.md` §3.0 베이스 재사용 +
> `evals/workflow-combos/DESIGN.md` §4.1 골격 + `PROVENANCE-combos.txt` 실측 교정 반영.
> 인덱스 기준: git `v1.12.1` (`1daf879`), `.codegraph/zoekt-ctags-index` (symbols=5059, files=183 = kt 159 + rs 24).

---

## 0. 스코프 격리 핵심 사실 (반드시 먼저 읽을 것)

`.codegraph/zoekt-ctags-index` 단일 인덱스는 **Kotlin(.kt 159) + Rust(.rs 24) 전체 183파일**을 담는다.
즉 인덱스 전체 범위 = SCOPE-KT-RS(M3-W) 범위와 정확히 일치한다.

- **SCOPE-KT(M1·M2·M3-L)**: 인덱스가 Rust 를 자동 배제하지 못한다. 특히 `tree-sitter-wasm/src/language.rs` 에
  자체 `pub enum SupportedLanguage { Java, Kotlin, ... }` 와 16건의 `Kotlin` 참조(match 분기 포함)가 있어,
  무필터 검색 시 M3-L 의 미등재 false positive 로 precision 이 붕괴한다.
  → **zoekt 쿼리에 반드시 `f:\.kt$` 를 붙여 Rust(.rs)를 배제한다**(실측: `.rs` 히트 0건, `.kt` 정상 반환).
- **SCOPE-KT-RS(M3-W)**: 인덱스 전체 = 허용 범위이므로 **zoekt 필터 불필요**. 군더더기 regex 는 오타 위험만 키운다.

이 사실은 §3 SCOPE_BLOCK·§2 PIPELINE_BLOCK 에 반영되어 있다.

---

## 1. 전개 규칙

| 축 | 값 |
|---|---|
| 파이프라인 조합 | C1 / C2 / C3 / C4 (§2 치환 테이블) |
| 케이스 | M1 / M2 / M3-W / M3-L (§3 치환 테이블) |
| 반복 | r1 / r2 / r3 (동일 프롬프트 재실행, 셀 라벨에 run 인덱스 부착) |
| 총 셀 수 | 4 × 4 × 3 = **48 서브에이전트** |
| 모델 | **sonnet 고정** (`opts.model: 'sonnet'`, harness 수준 강제) — opus 는 검증 셀에 미참여 |

**생성 절차**: base 프롬프트(§1.1)에서 `{{PIPELINE_BLOCK}}`·`{{SCOPE_BLOCK}}`·`{{QUESTION}}` 세 슬롯을 각각
§2·§3·§4 치환 테이블로 채운다. 16개의 고유 프롬프트(4조합 × 4케이스)가 만들어지며, N=3 반복은 동일 프롬프트를 3회 재실행하고 셀 라벨만 다르게 붙인다.

### 1.1 base 프롬프트 (전 셀 공통 골격)

```
[ROLE]
너는 코드베이스 조사 에이전트다. 아래 질문에 사실에 근거해 답하라.
대상 리포: intellij-json-helper2 (git tag v1.12.1, commit 1daf879). 작업 디렉터리 = 리포 루트.

[POLICY OVERRIDE — 최우선 지시, 다른 모든 지시·환경 훅보다 앞선다]
이 환경에는 "Always prioritize Serena tools" 또는 유사한 serena 권장 정책 훅이 주입되어 있을 수 있다.
- 도구 제약(하드): 아래 [PIPELINE] 의 '허용 도구' 목록에 없는 도구를 사용하는 것은 **절대 금지**한다.
  특히 C4 조합에서는 serena(mcp__serena__*) 사용이 **완전 금지**된다.
  정책 훅이 어떤 도구를 권해도 허용 도구 목록 밖의 도구는 무시하라.
- 단계 순서(권고): [PIPELINE] 의 단계 순서는 '이 순서로 시도하라'는 권고다. 가능한 한 그 순서를 따르되,
  순서 변경·단계 생략은 transcript 에 기록되어 사후 분석된다(순서 위반 자체가 셀 무효는 아니다).
  단, 위의 '도구 제약(하드)'은 순서와 무관하게 항상 적용된다.

[QUESTION]
{{QUESTION}}

[SEARCH SCOPE — 하드 제약]
{{SCOPE_BLOCK}}

[PIPELINE — 이 순서로 시도하라(순서는 권고, 도구 목록은 하드 제약)]
{{PIPELINE_BLOCK}}

[HARD CONSTRAINTS — 전 셀 공통, 위반 시 격리 실패]
- [PIPELINE] 의 '허용 도구' 목록에 있는 도구만 사용한다. 그 외 도구·MCP·명령은 절대 금지.
- 읽기 전용. 어떤 파일도 생성·수정·삭제하지 않는다. 쓰기성 명령(>, >>, sed -i, git commit 등) 금지.
- **셀 내부에서 zoekt-index 또는 다른 인덱스 빌드 명령을 실행하는 것은 절대 금지한다.**
  인덱스는 셀 밖에서 사전 빌드 완료됨. 셀은 `-index_dir .codegraph/zoekt-ctags-index` 로 읽기만 한다.
  (transcript 사후감사: `zoekt-index` 호출 0건 — 위반 시 해당 셀 무효 처리)
- 정답을 외부 지식·추측으로 지어내지 않는다. 반드시 허용 도구의 출력으로 뒷받침한다.
- 모르면 `answers` 를 빈 배열로 반환한다. 환각 금지.
- [SEARCH SCOPE] 에 명시된 경로 외부(특히 `evals/`·`docs/` 디렉터리)를 검색하거나 읽는 것은 금지한다.
  rg·sg·serena 도구·Read 등 모든 도구에 동일하게 적용한다. 도구가 스코프 밖 경로를 반환하면 답변에서 제외하라.

[OUTPUT SCHEMA — JSON 한 개만 출력]
{
  "answers": [
    {
      "qualified_name": "패키지.클래스.메서드 형식의 완전한 심볼명, 또는 callsite 의 경우 식별 가능한 이름",
      "kind": "method | class | function | callsite | reference | binding",
      "file": "리포 루트 기준 상대경로 (예: src/main/kotlin/com/livteam/jsoninja/...)",
      "line": <int>
    }
  ],
  "reasoning_brief": "1~3문장으로 검색 과정·주요 판단 요약",
  "tool_calls_count": <int>
}

주의:
- `file` 과 `line` 은 callsite·reference·binding 답 모두에서 **반드시** 채워야 한다 (null 금지).
  grader 는 (file:line) ±1 키로 채점하므로 file 또는 line 이 빠지면 해당 항목은 실점 처리된다.
- `file` 은 리포 루트 기준 상대경로다(zoekt 출력 상대경로와 동일 형식; gold 와 정확히 일치 확인됨).
- M2(transitive 폐포) 답변은 `qualified_name` 이 채점 키이므로 정확한 패키지·클래스·메서드명을 포함하라.
- JSON 외의 다른 텍스트를 출력하지 않는다.
```

---

## 2. PIPELINE_BLOCK 치환 테이블 (조합별)

> **zoekt 필터 규칙(전 조합 공통)**: SCOPE-KT 케이스(M1·M2·M3-L)는 zoekt 쿼리에 **반드시 `f:\.kt$`** 를 붙인다
> (Rust 자동 배제 안 됨 — §0 참조). SCOPE-KT-RS 케이스(M3-W)는 필터를 붙이지 않는다(인덱스 전체 = 허용 범위).

### C1 — 범용 기본값: `zoekt+ctags → serena → read`

```
PIPELINE_BLOCK:
1) [zoekt+ctags] Bash 로 다음 명령을 사용해 후보를 광역 회수한다:
   - 심볼 검색: zoekt -index_dir .codegraph/zoekt-ctags-index "f:\.kt$ sym:심볼명"
   - 텍스트 검색: zoekt -index_dir .codegraph/zoekt-ctags-index "f:\.kt$ 검색어"
   - (M3-W 한정) f:\.kt$ 를 빼고 검색한다: zoekt -index_dir .codegraph/zoekt-ctags-index "sym:심볼명"
     M3-W 는 Rust(.rs)도 허용 범위이므로 필터를 붙이지 않는다.
   - 필요 시 -jsonl·-sym 플래그 추가 가능.
   - rg 폴백: zoekt 인덱스가 포착하지 못하는 비정형 토큰은 같은 단계 안에서
     rg <패턴> <SEARCH SCOPE 내 경로> 로 보정한다 (경로 제한 필수).
2) [serena] mcp__serena__find_symbol·mcp__serena__find_referencing_symbols 등 읽기전용 도구로
   심볼 관계를 검증·확장한다. [SEARCH SCOPE] 외 경로(특히 evals/·docs/)가 나오면 답변에서 제외한다.
3) [Read] 최종 후보 파일을 Read 로 맥락 확인 후 답변을 확정한다.
   Read 도 [SEARCH SCOPE] 내 경로만 열람한다.

허용 도구: zoekt(Bash), rg(Bash, 폴백), mcp__serena__*(읽기전용), Read.
금지 도구: 위 목록 외 전부 (sg·sqlite3·python·zoekt-index 등).
```

### C2 — 심볼 선행: `serena → zoekt+ctags → read`

```
PIPELINE_BLOCK:
1) [serena] mcp__serena__find_symbol·mcp__serena__find_referencing_symbols 등 읽기전용 도구로
   정밀 후보를 먼저 회수한다. [SEARCH SCOPE] 외 경로가 나오면 답변에서 제외한다.
2) [zoekt+ctags] Bash 로 다음 명령을 사용해 serena 가 놓친 DI 수신자·미명시 참조·문자열·축약
   참조를 보정한다:
   - 심볼 검색: zoekt -index_dir .codegraph/zoekt-ctags-index "f:\.kt$ sym:심볼명"
   - 텍스트 검색: zoekt -index_dir .codegraph/zoekt-ctags-index "f:\.kt$ 검색어"
   - (M3-W 한정) f:\.kt$ 를 빼고 검색한다(Rust 도 허용 범위): "sym:심볼명"
   - rg 폴백: zoekt 가 못 잡는 비정형 토큰은 rg <패턴> <SEARCH SCOPE 내 경로> 로 보정
     (경로 제한 필수).
3) [Read] 최종 후보 파일을 Read 로 맥락 확인 후 답변을 확정한다.
   Read 도 [SEARCH SCOPE] 내 경로만 열람한다.

허용 도구: mcp__serena__*(읽기전용), zoekt(Bash), rg(Bash, 폴백), Read.
금지 도구: 위 목록 외 전부 (sg·sqlite3·python·zoekt-index 등).
```

### C3 — 구조검색 보강: `zoekt+ctags → ast-grep → serena → read`

```
PIPELINE_BLOCK:
1) [zoekt+ctags] Bash 로 다음 명령을 사용해 후보를 광역 회수한다:
   - 심볼 검색: zoekt -index_dir .codegraph/zoekt-ctags-index "f:\.kt$ sym:심볼명"
   - 텍스트 검색: zoekt -index_dir .codegraph/zoekt-ctags-index "f:\.kt$ 검색어"
   - (M3-W 한정) f:\.kt$ 를 빼고 검색한다(Rust 도 허용 범위): "sym:심볼명"
   - rg 폴백: zoekt 가 못 잡는 비정형 토큰은 rg <패턴> <SEARCH SCOPE 내 경로> 로 보정
     (경로 제한 필수).
2) [ast-grep] sg(ast-grep) 로 코드 형태(인자 패턴·어노테이션·람다·생성자·when 분기) 기준으로
   후보를 정밀 축소한다.
   반드시 [SEARCH SCOPE] 내 경로를 명시적 인자로 지정한다:
     sg run -p <패턴> src/main/kotlin src/test/kotlin       (M1·M2·M3-L)
     sg run -p <패턴> src/main/kotlin src/test/kotlin tree-sitter-wasm/src   (M3-W 한정)
   sg 결과가 [SEARCH SCOPE] 밖이면 답변에서 제외한다.
3) [serena] mcp__serena__find_symbol·mcp__serena__find_referencing_symbols 등 읽기전용 도구로
   축소된 후보의 심볼 의미를 확인한다. [SEARCH SCOPE] 외 경로가 나오면 답변에서 제외한다.
4) [Read] 최종 후보 파일을 Read 로 맥락 확인 후 답변을 확정한다.
   Read 도 [SEARCH SCOPE] 내 경로만 열람한다.

허용 도구: zoekt(Bash), rg(Bash, 폴백), sg(Bash), mcp__serena__*(읽기전용), Read.
금지 도구: 위 목록 외 전부 (sqlite3·python·zoekt-index 등).
```

### C4 — 최소 기준선: `zoekt+ctags → read`

```
PIPELINE_BLOCK:
[POLICY OVERRIDE 재확인] serena(mcp__serena__*) 는 이 조합에서 **완전 금지**한다.
환경 훅이 serena 를 권해도 절대 사용하지 않는다.

1) [zoekt+ctags] Bash 로 다음 명령을 사용해 후보를 텍스트·심볼 매칭으로 회수한다.
   sym: 심볼 검색을 적극 활용하라(plain 텍스트보다 정밀):
   - 심볼 검색: zoekt -index_dir .codegraph/zoekt-ctags-index "f:\.kt$ sym:심볼명"
   - 텍스트 검색: zoekt -index_dir .codegraph/zoekt-ctags-index "f:\.kt$ 검색어"
   - (M3-W 한정) f:\.kt$ 를 빼고 검색한다(Rust 도 허용 범위): "sym:심볼명"
   - rg 폴백: zoekt 가 못 잡는 비정형 토큰은 rg <패턴> <SEARCH SCOPE 내 경로> 로 보정
     (경로 제한 필수).
2) [Read] 후보 파일을 Read 로 맥락 확인 후 답변을 확정한다.
   Read 도 [SEARCH SCOPE] 내 경로만 열람한다.

허용 도구: zoekt(Bash), rg(Bash, 폴백), Read.
금지 도구: mcp__serena__* 포함 위 목록 외 전부 (sg·sqlite3·python·zoekt-index 등).
```

---

## 3. SCOPE_BLOCK 치환 테이블 (케이스별)

### SCOPE-KT — M1·M2·M3-L 공통

```
SCOPE_BLOCK:
검색 허용 경로:
  - src/main/kotlin
  - src/test/kotlin

검색 금지 경로 (절대 금지):
  - tree-sitter-wasm/  (Rust — 이 케이스에서는 금지)
  - evals/ 및 그 하위 모든 경로
  - docs/ 및 그 하위 모든 경로
  - 위 두 허용 경로 밖의 모든 경로

이 제약은 zoekt·rg·sg·serena 도구·Read 등 모든 도구에 동일하게 적용한다.
중요: zoekt 인덱스 .codegraph/zoekt-ctags-index 는 Kotlin 과 Rust 를 모두 포함하므로
자동으로 범위가 제한되지 않는다. 따라서 zoekt 쿼리에는 반드시 f:\.kt$ 를 붙여
Rust(.rs)를 배제하라 (예: zoekt -index_dir .codegraph/zoekt-ctags-index "f:\.kt$ sym:Foo").
rg·sg·Read 는 반드시 위 허용 경로를 명시적 인자로 지정해야 한다.
serena·rg·sg·Read 결과 중 위 금지 경로(특히 evals/·docs/·tree-sitter-wasm/)가 나오면
답변에서 제외하라.
```

### SCOPE-KT-RS — M3-W 전용

```
SCOPE_BLOCK:
검색 허용 경로:
  - src/main/kotlin
  - src/test/kotlin
  - tree-sitter-wasm/src   (Rust 소스, M3-W 에서만 허용)

검색 금지 경로 (절대 금지):
  - evals/ 및 그 하위 모든 경로
  - docs/ 및 그 하위 모든 경로
  - 위 세 허용 경로 밖의 모든 경로

이 제약은 zoekt·rg·sg·serena 도구·Read 등 모든 도구에 동일하게 적용한다.
zoekt 인덱스 .codegraph/zoekt-ctags-index 의 전체 범위(.kt 159 + .rs 24 = 183파일)가
이 케이스의 허용 범위와 정확히 일치하므로, zoekt 쿼리에 f: 파일 필터를 붙이지 않는다
(예: zoekt -index_dir .codegraph/zoekt-ctags-index "sym:analyze_source").
rg·sg·Read 는 반드시 위 허용 경로를 명시적 인자로 지정해야 한다.
serena·rg·sg·Read 결과 중 위 금지 경로(특히 evals/·docs/)가 나오면 답변에서 제외하라.

주의: serena 의 .serena/project.yml 은 languages: [kotlin] 만 설정되어 있으므로,
Rust 측 심볼·정의는 serena 가 아니라 zoekt / rg 텍스트 레이어로 회수해야 한다.
```

### SCOPE 매핑 요약

| 케이스 | SCOPE_BLOCK 변형 | zoekt 필터 |
|--------|-----------------|-----------|
| M1 | SCOPE-KT | `f:\.kt$` 필수 |
| M2 | SCOPE-KT | `f:\.kt$` 필수 |
| M3-W | SCOPE-KT-RS | 필터 없음(인덱스 전체 = 허용 범위) |
| M3-L | SCOPE-KT | `f:\.kt$` 필수 |

---

## 4. QUESTION 치환 테이블 (dataset.yaml 원문 그대로)

### M1 — 시그니처 변경 영향 (blast radius)

```
QUESTION:
JsonFormatterService 클래스에 정의된 formatJson(json, formatState, sortOverride) 메서드를
'직접 호출하는' 모든 호출지점(파일과 라인)을 나열하라. 프로덕션(src/main)과 테스트(src/test) 모두 포함한다.
주의: 같은 이름 formatJson 을 가진 다른 메서드(JsoninjaPanelPresenter.formatJson)의 호출은 제외한다.
```

### M2 — 동작 전파 (transitive impact closure)

```
QUESTION:
TreeSitterWasmRuntime.getOrCreate() 가 (직접 또는 다른 함수를 거쳐 간접적으로) 호출되는,
프로덕션 코드(src/main)의 모든 함수를 호출 깊이와 함께 나열하라. 즉 getOrCreate 의 transitive
역호출 폐포다. IntelliJ 진입점(AnAction.actionPerformed / DialogWrapper 생성) 위쪽은 제외한다.
```

### M3-W — 언어 간 WASM ABI seam

```
QUESTION:
Kotlin 호스트가 Rust WASM 모듈의 export 를 이름으로 바인딩하는 모든 지점과, 그에 대응하는 Rust 측
#[no_mangle] pub extern "C" 정의를 모두 나열하라. 대상 ABI: analyze_source, get_last_error, alloc, dealloc.
```

### M3-L — enum 참조 (SupportedLanguage.KOTLIN)

```
QUESTION:
SupportedLanguage.KOTLIN enum 상수를 심볼로서 참조하는 모든 지점(when 분기 포함)을 나열하라.
단순 문자열 "kotlin"/"kt", 리소스 경로(tree-sitter/queries/kotlin/…), Kotlin* 식별자(KOTLIN_SERIAL_NAME 등),
코루틴/플랫폼 문맥의 kotlin 은 제외한다.
```

---

## 5. 16개 고유 프롬프트 조합 전개표

| 셀 ID | 파이프라인 | 케이스 | PIPELINE_BLOCK | SCOPE_BLOCK | zoekt 필터 | QUESTION |
|-------|-----------|--------|---------------|-------------|-----------|---------|
| C1×M1 | C1 | M1 | C1 블록 | SCOPE-KT | `f:\.kt$` | M1 질문 |
| C1×M2 | C1 | M2 | C1 블록 | SCOPE-KT | `f:\.kt$` | M2 질문 |
| C1×M3-W | C1 | M3-W | C1 블록 | SCOPE-KT-RS | 없음 | M3-W 질문 |
| C1×M3-L | C1 | M3-L | C1 블록 | SCOPE-KT | `f:\.kt$` | M3-L 질문 |
| C2×M1 | C2 | M1 | C2 블록 | SCOPE-KT | `f:\.kt$` | M1 질문 |
| C2×M2 | C2 | M2 | C2 블록 | SCOPE-KT | `f:\.kt$` | M2 질문 |
| C2×M3-W | C2 | M3-W | C2 블록 | SCOPE-KT-RS | 없음 | M3-W 질문 |
| C2×M3-L | C2 | M3-L | C2 블록 | SCOPE-KT | `f:\.kt$` | M3-L 질문 |
| C3×M1 | C3 | M1 | C3 블록 | SCOPE-KT | `f:\.kt$` | M1 질문 |
| C3×M2 | C3 | M2 | C3 블록 | SCOPE-KT | `f:\.kt$` | M2 질문 |
| C3×M3-W | C3 | M3-W | C3 블록 | SCOPE-KT-RS | 없음 | M3-W 질문 |
| C3×M3-L | C3 | M3-L | C3 블록 | SCOPE-KT | `f:\.kt$` | M3-L 질문 |
| C4×M1 | C4 | M1 | C4 블록 | SCOPE-KT | `f:\.kt$` | M1 질문 |
| C4×M2 | C4 | M2 | C4 블록 | SCOPE-KT | `f:\.kt$` | M2 질문 |
| C4×M3-W | C4 | M3-W | C4 블록 | SCOPE-KT-RS | 없음 | M3-W 질문 |
| C4×M3-L | C4 | M3-L | C4 블록 | SCOPE-KT | `f:\.kt$` | M3-L 질문 |

N=3 반복: 각 셀을 동일 프롬프트로 3회 재실행. 셀 라벨: `C1×M1×r1`, `C1×M1×r2`, `C1×M1×r3` 형식.

---

## 6. 사전 실행 체크리스트 (하니스 진입 전 확인 필수)

| 항목 | 확인 방법 | 상태 |
|------|-----------|------|
| zoekt+ctags 인덱스 존재 | `ls .codegraph/zoekt-ctags-index/*.zoekt` | ✅ `combos-src_v16.00000.zoekt` 확인됨 |
| 인덱스 = 재빌드 금지 | PROVENANCE-combos.txt sha256 `f15863a6...bccbf030` 참조 | ✅ 사전 빌드 완료 — 재빌드 금지(측정 격리) |
| zoekt 필터(SCOPE-KT) 검증 | `zoekt -index_dir .codegraph/zoekt-ctags-index 'f:\.kt$ sym:SupportedLanguage'` → `.rs` 0건 | ✅ 실측 확인 (.rs 0 / .kt 정상) |
| serena 연결 상태 | `claude mcp list` → `serena ✓ Connected` | 확인 요 |
| serena 런타임 로드 | C1/C2/C3 실행 전 워밍업 셀 1개(측정 제외) | 워밍업 셀 별도 실행 필요 |
| zoekt 바이너리 위치 | `~/go/bin/zoekt` (PATH 외, 절대경로 or `export PATH="$HOME/go/bin:$PATH"`) | ✅ 존재 확인 |
| universal-ctags 심볼릭 링크 | `~/go/bin/universal-ctags -> /opt/homebrew/bin/ctags` | ✅ 존재(인덱스 빌드 시 필요, 검색엔 불필요) |
| sg(ast-grep) 가용 | `which sg` | ✅ `/opt/homebrew/bin/sg` |
| rg 가용 | `which rg` | ✅ `/opt/homebrew/bin/rg` |

> **serena 워밍업 셀**: C1·C2·C3 실행 직전 아래 단일 셀을 **측정 제외**로 먼저 실행해 LSP 를 선기동한다.
> 셀 라벨을 `warmup-serena`(r1~r3 아님)로 붙여 48셀 집계에서 배제한다.
> 워밍업 프롬프트 예시:
> ```
> mcp__serena__find_symbol 로 "JsonFormatterService" 를 찾아 결과를 한 줄로 출력하라.
> 이 셀은 LSP 워밍업 전용이며 측정에 포함되지 않는다.
> ```

---

## 7. 채점 인터페이스 계약 (grader 와의 정렬)

모든 셀의 출력은 아래 스키마를 엄수해야 grader 가 기계적으로 처리할 수 있다.

```json
{
  "answers": [
    {
      "qualified_name": "string (패키지.클래스.메서드 또는 식별 가능 이름)",
      "kind": "method | class | function | callsite | reference | binding",
      "file": "string (리포 루트 기준 상대경로, 예: src/main/kotlin/com/livteam/jsoninja/...)",
      "line": "int (1-based, null 금지 — callsite·reference·binding 모두 필수)"
    }
  ],
  "reasoning_brief": "string (1~3문장)",
  "tool_calls_count": "int"
}
```

**grader 채점 키 요약**:

| 케이스 | 채점 키 | gold 수 | 함정 |
|--------|--------|---------|------|
| M1 | `(file:line)` ±1, callsite | 33 (prod 12 + test 21) | 2 (presenter.formatJson 호출지점 2건: PrettifyJsonAction.kt:18, UglifyJsonAction.kt:20) |
| M2 | `qualified_name` 정확 매칭 | 8 함수 (폐포) | 4 (getOrCreateContext·TypeToJsonDialogView.updateLanguage·JsonToType 반대방향 등) |
| M3-W | `(file:line)` ±1, Kotlin 4 + Rust 4 분리채점 후 합산 | 8 | 2 (WasmMemoryBridge.kt:19/36 alloc/dealloc 2차사용) |
| M3-L | `(file:line)` ±1, reference | 29 (main 13 + test 16) | 문자열/리소스/KOTLIN_ 접두/코루틴 + **Rust SupportedLanguage::Kotlin(미등재 오탐, f:\.kt$ 로 차단)** |

> M1 enclosing-function 동치: dataset.yaml M1 notes 의 'enclosing-function 매핑표'로 둘러싼 함수 응답도 동치 처리 가능(adjudication 대상).
> M1 정의/자기호출 경계: :215(formatJsonOnDefault 내부 자기호출)는 gold 포함, :210(formatJsonOnDefault 정의)·JsonDiffExtension.formatJsonInBackground 정의는 제외.
> M3-L 정의 SupportedLanguage.kt:16(KOTLIN())은 정의이므로 recall 분모 제외.

**하이브리드 채점 정책**:
결정적 grader 스크립트가 기계적 `(file:line)` ±1 / M2 `qualified_name` 매칭으로 캐논 수치를 산출(동결).
opus 는 스크립트가 "모호(adjudication_needed)" 플래그로 분류한 케이스만 판정한다:
- M2 `qualified_name` 부분일치 (패키지 접두 차이 등)
- M1 enclosing-function 동치 (에이전트가 호출지점 대신 둘러싼 함수로 답한 경우)
- ±1 경계 케이스
- M2/M3 함정 경계 (해석 분기가 필요한 경우)

opus 가 기계적 hit 를 재채점하는 것은 N=3 재현성을 깨뜨리므로 금지한다.
→ grader 스크립트는 `adjudication_needed` 리스트를 캐논 수치와 분리해 별도 출력해야 한다.

---

## 8. 거버넌스 감사 항목 (transcript 사후 확인)

| 감사 항목 | 기준 | 위반 처리 |
|-----------|------|-----------|
| zoekt-index 호출 수 | **0** — 셀 내부 인덱스 빌드 절대 금지 | 해당 셀 무효 처리 |
| zoekt f:\.kt$ 필터(SCOPE-KT) | **부착 확인** — M1/M2/M3-L 의 zoekt 쿼리에 `f:\.kt$` 존재 | 미부착 시 Rust 오탐 가능성 표기 |
| evals/·docs/ 검색·읽기 | **0** | 해당 셀 precision 수치 별도 표기 |
| tree-sitter-wasm/ 검색(SCOPE-KT) | **0** (M1/M2/M3-L) | 해당 셀 precision 별도 표기 |
| 허용 도구 외 사용 | **0** | 해당 셀 격리 실패 표기 |
| sg 무경로 실행 | **0** — sg 는 SCOPE 경로를 명시 인자로 받아야 함 | 무경로 실행 시 스코프 위반 표기 |
| 사용 모델 | **sonnet** 확인 (harness opts 수준) | 결과 표기 시 명시 |
| serena 사용 (C4) | **0** | C4 셀 격리 실패 표기 |
| 파일 쓰기 | **0** | 해당 셀 즉시 무효 |

---

## 9. 실측 교정 사항 (DESIGN.md 기준 변경점)

| 항목 | DESIGN.md 원문 | 이 템플릿에서 교정된 내용 |
|------|---------------|--------------------------|
| zoekt 플래그 | `-index PATH` | `-index_dir .codegraph/zoekt-ctags-index` (실측 검증) |
| zoekt 스코프 필터 | 언급 없음(자동 제한 가정) | **SCOPE-KT 는 `f:\.kt$` 필수**(인덱스가 Rust 포함 → 자동 제한 안 됨). M3-W 만 필터 없음. 실측 검증 |
| 출력 스키마 | `{qualified_name, kind, line}` (file 없음) | `{qualified_name, kind, file, line}` (file 필수 추가, dataset.yaml 정본) |
| serena 정책 훅 | 언급 없음 | [POLICY OVERRIDE] 블록 — 도구 금지는 하드, 순서는 권고로 분리(§4.1 비강제 준수) |
| 셀 내부 인덱싱 | 언급 없음 | [HARD CONSTRAINTS] 에 명시 + transcript 감사 대상 |
| non-zoekt 도구 경로 제약 | §3 에만 서술 | 각 PIPELINE_BLOCK·SCOPE_BLOCK 에 도구별 경로 제한 + 스코프 밖 결과 사후필터 명시 |

---

## 10. 검증된 실측 명령 부록 (실행자 참조용)

```bash
export PATH="$HOME/go/bin:$PATH"

# SCOPE-KT (M1/M2/M3-L): f:\.kt$ 필수 — Rust 배제 확인
zoekt -index_dir .codegraph/zoekt-ctags-index 'f:\.kt$ sym:SupportedLanguage'
#   → src/main/kotlin/.../SupportedLanguage.kt:7 만 반환, .rs 0건 (실측 확인)

# SCOPE-KT-RS (M3-W): 필터 없음 — Kotlin+Rust 모두
zoekt -index_dir .codegraph/zoekt-ctags-index 'sym:analyze_source'
#   → src/main/kotlin/.../TreeSitterWasmRuntime.kt + tree-sitter-wasm/src/lib.rs:92 (실측 확인)

# 무필터 위험 예시(SCOPE-KT 에서 금지): Rust SupportedLanguage::Kotlin 16건 오탐 유입
zoekt -index_dir .codegraph/zoekt-ctags-index 'sym:SupportedLanguage'
#   → language.rs:7, language.rs:14 (Rust) 포함 — M3-L 오탐 (f:\.kt$ 로 차단해야 함)
```

---

## 11. 미해결 결정사항 (설계자 판단 필요)

1. **PIPELINE 순서: 권고 vs 강제**
   본 템플릿은 과제 검토 기준 7(PIPELINE 비강제) 및 DESIGN §4.1·§7 을 따라 단계 순서를 **권고**로 두고
   도구 금지만 하드 제약으로 둔다. trade-off: 엄격 비강제 하에서 serena 권장 훅이 C1 에서 serena-first 를
   유발하면 C1(zoekt→serena) vs C2(serena→zoekt) 변별이 약화될 수 있다. 변별을 우선해 순서를 강제하려면
   DESIGN §4.1·§7 을 함께 갱신해야 한다(현 설계 명시와 충돌하므로 설계자 결정 필요).

2. **rg 폴백 경로 인자 구체화 수준**
   현재 `rg <패턴> <SEARCH SCOPE 내 경로>` 로 두었다. 에이전트가 경로를 생략하는 것을 막으려면
   `rg --glob "*.kt" <패턴> src/main/kotlin src/test/kotlin` 처럼 플래그까지 강제할지 결정 필요
   (과도한 구체화는 실제 workflow 와 괴리).

3. **serena search_for_pattern 스코프 집행**
   serena 도구는 경로 화이트리스트 전달이 도구마다 다를 수 있다. 본 템플릿은 '스코프 밖 결과 사후필터'
   지시 + transcript 감사(§8)로 보강했으나, serena 가 evals/ 포함 전체를 탐색하는지 실행 transcript 에서
   케이스별 확인이 필요하다.

4. **M2 depth 채점 여부**
   output_schema 에 depth 가 없어 grader 는 qualified_name 만 채점한다. dataset.yaml M2 gold 의 depth 를
   채점에 반영하려면 schema 확장이 필요하다(현 설계는 미반영).

5. **48셀 substrate 와 메트릭 파서 호환**
   Workflow agent transcript 가 토큰/모델을 노출하지 않으면 Agent 도구 폴백이 필요하다. 메트릭 파서는
   두 transcript 형태를 모두 처리하도록 설계해야 한다(이 차원 밖).