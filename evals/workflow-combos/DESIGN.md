# 조합(workflow) 벤치마크 설계안 — 4 조합 × 4 케이스

> **데이터 출처**: gold·dataset·기존 단일도구 결과는 모두 `../codegraph-vs-serena/`(`dataset.yaml`,
> `results.md`, `benchmark-design.md`)에 있다. 본 폴더(`workflow-combos/`)는 그 gold 를 재사용해
> **도구 조합(파이프라인)** 을 측정하는 신규 벤치마크다. 산출물(`DESIGN.md`, 추후 `grader`,
> `metrics-combos.csv`, `results-combos.md`, `PROVENANCE-combos.txt`)은 본 폴더에 둔다.

> 목적: 기존 벤치마크(`../codegraph-vs-serena/results.md`)는 각 도구를 **단일 호출**로만 측정해 실제 개발 workflow 와 괴리가 있다.
> 이 설계는 Codex 가 제안한 **4개 도구 조합(파이프라인)** 을 동일한 gold·blind·±1줄 채점 체계로 측정해,
> 조합이 단일도구의 알려진 약점(serena DI 맹점, M2 생성자/람다 말단, M3 누락)을 실제로 메우는지와
> 정밀도·비용 trade-off 를 확인한다.
>
> **확정 사항(사용자 결정)**: ① 채점 케이스 = **M1·M2·M3-W·M3-L 4개 모두**. ② 텍스트/심볼 레이어 =
> **universal-ctags 인덱스 재빌드**(Rust 포함)로 `sym:` 기여분까지 측정. ③ 각 셀은 **3회 반복(N=3)** 측정.
> ④ 벤치마크(검증) 서브에이전트 모델 = **sonnet 고정**, **opus 는 검증에 비참여**(채점·리뷰 전용).
> ⑤ **토큰 사용량·품질 등 메트릭을 셀별·반복별로 반드시 기록**. ⑥ 이번 단계 = **설계안 확정만**(실행 전).

---

## 1. 측정할 4개 조합

전부 `write` 제외, 인덱스 기반 도구는 `zoekt+ctags` 하나만 사용. `read` = 수정 전 맥락 확인.

| ID | 조합 | 파이프라인 | 적용 상황 |
|----|------|-----------|-----------|
| **C1** 범용 기본값 | `zoekt+ctags/grep → serena → read` | 텍스트·심볼로 후보 광역 회수 → serena 로 심볼 관계 검증 → read 맥락 | 함수명·서비스명·설정키·문자열·enum·테스트 참조가 섞인 일반 케이스 |
| **C2** 심볼 선행 | `serena → zoekt+ctags/grep → read` | serena 가 정밀 후보 선제공 → 텍스트로 DI·미명시 수신자·문자열·축약 참조 누락 보정 → read | 단일 심볼이 명확할 때(메서드 참조, enum 상수 사용처) |
| **C3** 구조검색 보강 | `zoekt+ctags/grep → ast-grep → serena → read` | 텍스트 회수 → ast-grep 으로 코드 형태(인자 패턴·어노테이션·람다·생성자·when 분기) 정밀 축소 → serena 의미 확인 → read | 문자열이 아니라 코드 구조가 핵심일 때 |
| **C4** 최소 기준선 | `zoekt+ctags/grep → read` | 텍스트 매칭 → read | 정확 문자열·설정키·리소스 경로·로그/에러 메시지. 다른 조합의 비교 baseline |

> `zoekt+ctags/grep` 표기의 `/grep` 은 폴백. 기본은 `zoekt+ctags` 인덱스를 쓰되, 인덱스가 못 잡는
> 비정형 토큰은 같은 단계 안에서 `rg` 로 보정한다(아래 §3 스코프 규칙 동일 적용).

**실행 규모**: 4 조합 × 4 케이스 × **3회(N=3)** = **48 서브에이전트**(blind). 반복 간 분산까지 집계한다(§5·§6).

---

## 2. 실행 전 셋업 (전제조건)

### 2.1 universal-ctags 설치 ✅ 완료
- **설치됨**: `Universal Ctags 6.2.1`(`/opt/homebrew/bin/ctags`). PATH 우선순위 1순위라 `ctags` = Universal
  (Apple BSD `/usr/bin/ctags` 보다 앞 → 별도 조정 불필요).
- **Kotlin·Rust 둘 다 지원 확인**(`ctags --list-languages` 에 `Kotlin`·`Rust` 존재) → `sym:` 기여 측정 가능.
  기존 "Kotlin 지원 빈약" 우려는 일부 해소(단 추출 심볼 종류·완전성은 §2.2 재빌드 후 `sym:` 실검색으로 검증).
```bash
ctags --version | head -1        # Universal Ctags 6.2.1 (확인 완료)
ctags --list-languages | grep -iE "kotlin|rust"   # Kotlin·Rust (확인 완료)
```

### 2.2 zoekt+ctags 인덱스 재빌드 (Rust 포함)
현재 인덱스 `.codegraph/zoekt-index/src_v16.00000.zoekt` 는 `HasSymbols:false`·`Source:"src"`(Rust 미포함)
**plain zoekt** 라 그대로 못 쓴다. ctags 활성 + Rust 포함으로 **별도 경로**에 신규 빌드(기존 plain 인덱스 보존).
```bash
export PATH="$HOME/go/bin:$PATH"           # zoekt, zoekt-index
zoekt-index -index .codegraph/zoekt-ctags-index \
  src/main/kotlin src/test/kotlin tree-sitter-wasm/src
# 검증: HasSymbols:true 확인 (false 면 ctags 미감지 → PATH/ctags 종류 점검)
zoekt -index .codegraph/zoekt-ctags-index "sym:SupportedLanguage" | head
```
- 검증 항목: `HasSymbols:true`, LanguageMap 에 Kotlin·Rust 존재, `sym:` 검색 동작.
- **provenance 기록**: 인덱스 sha256, 인덱싱 파일 수(kt/rs), `ctags --version`, 빌드 시각 → `PROVENANCE-combos.txt`.
- ⚠️ universal-ctags 의 Kotlin 지원이 빈약하면 `sym:` 효과가 제한적 → "ctags 효과 제한적"을 결과의 일부로 정직 기록.

### 2.3 serena 연결 확인
- `claude mcp list` 기준 `serena ✓ Connected` 확인됨. 단 `.serena/project.yml` 의 `languages: [kotlin]` 뿐
  → **M3-W 의 Rust 측 정의는 serena 가 못 본다**(기존 벤치마크와 동일 조건; Rust 측은 텍스트 레이어가 회수).
- ⚠️ **세션 리스크**: 현 세션의 `ToolSearch select:mcp__serena__*` 가 빈 결과였다. 연결돼 있어도 서브에이전트가
  런타임에 `mcp__serena__*` 스키마를 ToolSearch 로 로드하는지 **C1·C2·C3 실행 직전 1셀로 사전 점검**한다(로드 실패 시
  serena 의존 조합 전체가 무효이므로 먼저 해소).

### 2.4 provenance 고정
- `src` / `tree-sitter-wasm` 는 `v1.12.1`(1daf879) 이후 **변경 없음**(확인 완료) → gold 줄 번호 유효.
- 워킹트리는 `v1.12.1+8`(evals 문서 추가분)이라 **§3 스코프 강제가 필수**(gold 토큰이 evals 문서에 누출됨).

---

## 3. 검색 스코프 강제 (가장 중요한 방법론 규칙)

워킹트리의 `evals/` 문서가 gold 토큰(`SupportedLanguage.KOTLIN`, `analyze_source` 등)을 그대로 포함한다
(실측: `SupportedLanguage.KOTLIN` 이 evals 문서 5개에 존재). 무스코프 검색 시 이들이 오탐이 되어
**특히 M3-L 정밀도가 붕괴**한다. 따라서 모든 조합·모든 단계의 검색을 다음 범위로 **고정**한다.

| 케이스 | 허용 검색 범위 |
|--------|----------------|
| M1·M2·M3-L | `src/main/kotlin` + `src/test/kotlin` |
| M3-W | 위 + `tree-sitter-wasm/src` (Rust) |

- zoekt+ctags 인덱스는 위 범위로만 빌드(§2.2) → 자연히 스코프 제한.
- `rg` 폴백·`serena search_for_pattern`·`read` 는 **경로를 명시적으로 위 범위로 제한**(프롬프트 하드 제약).
- `evals/`·`docs/`·`report.html` 등은 검색 대상에서 제외.

---

## 4. 하니스 (서브에이전트 실행)

### 4.1 동일 베이스 프롬프트 — 파이프라인 블록만 차등
기존 설계(`benchmark-design.md` §3.0)의 베이스 프롬프트를 재사용하되, `{{ALLOWED_TOOLS_BLOCK}}` 을
**조합별 파이프라인 지시**로 치환한다. 출력 스키마·읽기전용·환각금지·blind 는 전 셀 동일.

```
[ROLE] 코드베이스 조사 에이전트. git v1.12.1(1daf879) 기준. 작업 디렉터리 = 리포 루트.
[QUESTION] {{QUESTION}}
[SEARCH SCOPE] (하드 제약) {{SCOPE_BLOCK}}   ← §3 범위. 이 밖(특히 evals/)은 검색 금지.
[PIPELINE] (이 순서로 진행) {{PIPELINE_BLOCK}}   ← 조합별 차등
[HARD CONSTRAINTS] 파이프라인 명시 도구만. 읽기전용. gold 추측 금지, 빈배열 허용.
[OUTPUT SCHEMA] { answers:[{qualified_name, kind, line}], reasoning_brief, tool_calls_count }
```

조합별 `{{PIPELINE_BLOCK}}`:

| ID | PIPELINE_BLOCK |
|----|----------------|
| C1 | 1) `zoekt -index .codegraph/zoekt-ctags-index`(+필요시 `rg`)로 후보 광역 회수 → 2) `mcp__serena__*`(find_symbol / find_referencing_symbols)로 심볼 관계 검증·확장 → 3) `Read`로 최종 후보 맥락 확인 후 확정 |
| C2 | 1) `mcp__serena__*` 로 정밀 후보 선제 회수 → 2) `zoekt+ctags`(+`rg`)로 DI·미명시 수신자·문자열·축약 참조 누락 보정 → 3) `Read` 확인 |
| C3 | 1) `zoekt+ctags`(+`rg`) 후보 회수 → 2) `sg`(ast-grep)로 코드 형태(인자/어노테이션/람다/생성자/when) 정밀 축소 → 3) `mcp__serena__*` 의미 확인 → 4) `Read` 확인 |
| C4 | 1) `zoekt+ctags`(+`rg`) 텍스트 회수 → 2) `Read` 확인. (serena·sg 미사용) |

> 파이프라인은 권고 순서이되 강제 차단이 아니라 "이 순서로 시도하라"의 형태(에이전트가 단계 생략·역행하면
> transcript 로 기록). 단 **PIPELINE_BLOCK 에 없는 도구**(예: C4 의 serena)는 금지.

### 4.2 오케스트레이션
- Workflow 도구로 48셀(16 × 3회) fan-out. 각 셀 격리·blind.
- serena 사전점검 1셀(§2.3)을 C1·C2·C3 앞에 배치.
- 반복 3회는 동일 프롬프트로 실행하되, 셀 라벨에 `run` 인덱스를 붙여 구분(`C1×M1×r1` 등).

---

## 4.5 모델 정책 (검증/채점 분리)

| 단계 | 모델 | 근거 |
|------|------|------|
| **벤치마크(검증) 서브에이전트** 48셀 | **sonnet 고정** | 기존 벤치마크가 sonnet 고정이므로 비교 일관성 유지. 도구만 변수. |
| **채점(grader)** | **모델 없음 — 결정적 스크립트** | 아래 ⚠️ 참조 |
| **리뷰/해석/합성** | **opus** | delta 분석·약점 해소 판정·서술 합성 등 정성 작업에만 |

- Workflow `agent()` 의 `opts.model: 'sonnet'` 으로 48셀 전부 강제. **opus 는 검증 셀에 절대 미사용**(오염 방지).
- ⚠️ **채점은 opus 가 아니라 결정적 grader 스크립트로 하길 권장**(아래 §5). gold 가 `(file:line)` 정확 매칭이라
  모델 채점은 재현성·일관성이 떨어지고 N=3×16 = 48건에서 모델 변동이 품질 지표에 노이즈로 섞인다.
  opus 는 "채점 결과의 해석·리뷰"에 쓰는 게 사용자 의도(검증 비참여, 품질 데이터 신뢰성)에 더 부합한다.

---

## 5. 채점 (기존 체계와 apples-to-apples)

기존 벤치마크에 자동 채점 스크립트는 없고 gold 대조가 수동/에이전트 기반이었다. 48셀 일관성·재현성을 위해
**결정적 grader 스크립트**를 신규 작성(모델 비사용).

- 입력: 각 셀의 `answers` JSON + `dataset.yaml` 의 케이스별 gold/traps.
- 매칭: `(file:line)` 키, **±1줄 정규화**. enclosing-function 동치표(M1 notes) 반영.
- 산출: 셀·반복별 recall / precision / F1. `traps_must_exclude` 포함 시 precision 페널티.
- **N=3 집계**: 조합×케이스별 recall/precision/F1 의 **중앙값 + 평균 ± 표준편차**(또는 min/max)로 분산 표기.
- 출력: `results-combos.md` 스코어보드 + 셀별 hit/miss 상세 + 반복 간 분산.
- opus 리뷰: grader 출력을 입력으로 받아 delta·약점 해소·서술 합성(채점 자체는 안 함).

> grader 작성도 코드 변경이므로, 사용자 승인 후 실행 단계에서 만든다(이 설계안 범위 밖).

---

## 5.5 메트릭 기록 (필수)

셀별·반복별로 아래를 **모두 기록**하고 조합×케이스 단위로 집계한다(N=3 분산 포함).

| 분류 | 항목 |
|------|------|
| **품질** | recall, precision, F1 (반복별 + 중앙값/평균±SD), hit/miss 상세, trap 포함 여부, 환각 건수 |
| **토큰** | 출력(out), 캐시읽기(cacheR), 캐시생성(cacheCreate) — 서브에이전트 transcript 에서 분리 집계 |
| **비용/효율** | 도구 호출 수(`tool_calls_count` + 하니스 집계), wall-clock(초), 파이프라인 단계별 사용 도구 |
| **거버넌스** | 사용 모델(=sonnet 확인), 허용 도구 외 사용 0 감사, 스코프 위반(evals/ 검색) 0 감사 |

- 토큰은 `~/.claude/projects/.../subagents/agent-*.jsonl` transcript 파싱(기존 §6 방식 동일).
- 산출물: 셀별 원시 메트릭 `metrics-combos.csv`(48행) + 집계표(`results-combos.md` §효율).
- ⚠️ 48셀이라 토큰·시간 총량이 기존(28셀)보다 큼 — 실행 비용을 사전 인지(특히 serena 다중홉 cacheR 폭증 경향).

---

## 6. 보고 (조합 vs 단일도구 delta 중심)

조합은 도구 합집합이라 **재현율은 기계적으로 상승**한다. 따라서 절대값이 아니라 다음을 핵심으로 보고한다.

1. **스코어보드**: C1~C4 × M1/M2/M3-W/M3-L 의 recall/precision (N=3 중앙값 + 분산).
2. **delta 표**: 각 조합 vs 해당 케이스 단일도구 best(`results.md`) 대비 recall·precision 증감.
3. **약점 해소 여부**: serena DI 맹점(M1), M2 생성자/람다 말단, M3-W Kotlin 바인딩·M3-L enum 누락이 조합에서 메워지는가.
4. **비용**: `tool_calls_count`, 토큰(out/cacheR/cacheCreate), wall-clock. "정밀도·비용 대비 회수 이득"이 실제 관전 축.
5. **안정성**: 반복 3회 간 recall/precision 변동(분산) — 조합이 일관되게 이득을 주는지 vs 운에 좌우되는지.

---

## 7. 공정성 / 한계 (사전 명시)

- **N=3**: 셀당 3회 측정으로 분산 표기(기존 N=1 한계 일부 완화). 단 3회는 여전히 작은 표본 — 평균±SD 로만 해석.
- **모델 고정**: 검증 셀 전부 sonnet. opus 는 채점/검증에 비참여(결정적 grader + opus 정성 리뷰만).
- **serena Rust 미설정**: M3-W Rust 측은 serena 가 못 봄(텍스트 레이어가 회수) — 조건 명시.
- **ctags Kotlin 지원 수준**: 약하면 `sym:` 기여 제한 — 결과의 일부로 정직 기록.
- **파이프라인 비강제**: 에이전트가 단계를 생략/역행할 수 있어 "조합의 상한"이 아니라 "이 워크플로를 준 에이전트의 실측"이다.
- **비용 수치는 서브에이전트 usage 추정치.**

---

## 8. 실행 순서 (승인 후)

1. universal-ctags 설치 + zoekt+ctags(Rust 포함) 인덱스 재빌드·검증·provenance 기록(§2.1–2.2).
2. serena 런타임 로드 사전점검 1셀(§2.3).
3. 결정적 grader 스크립트 + 메트릭 수집기(transcript 토큰 파서) 작성(§5·§5.5).
4. **48셀(4 조합 × 4 케이스 × 3회) blind 실행 — Workflow fan-out, 전 셀 모델 sonnet 고정**.
5. grader 결정적 채점 → 메트릭 집계(`metrics-combos.csv` 48행 + N=3 분산).
6. **opus 리뷰**: grader/메트릭 출력 해석 → `results-combos.md` 작성(스코어보드·delta·비용·안정성).
7. transcript 격리 감사(허용 도구 외 0, evals/ 스코프 위반 0, 모델=sonnet 확인).
8. `results.md`/`report.html` 에 조합 섹션 링크.
