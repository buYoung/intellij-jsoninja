# 방법론 적대적 리뷰 — workflow-combos 벤치마크 설계 하더닝 (교정판)

> 역할: opus 리뷰 패널. 48 검증셀 비실행. 설계 빈틈을 열거하고 권고를 제시한다.
> 모든 수치·경로는 `DESIGN.md`, `dataset.yaml`, `PROVENANCE-combos.txt`, `results.md` 직접 판독 기준.
> ⚠️ **초판 정정**: 초판의 'ast-grep Kotlin 미지원' 주장은 **거짓으로 판명**되어 본 교정판에서 전면 반전했다(§1·§4-2 참조).

---

## 0. 실측 사전 확인 (이 리뷰 전 직접 검증)

| 항목 | 실측 결과 | 영향 |
|------|----------|------|
| `python3 -c "import yaml"` | **ModuleNotFoundError** — PyYAML 없음 | grader 를 PyYAML 의존 시 실행 불가. **frozen-gold.json 사전추출 또는 stdlib 파서 필수** |
| `sg --list-languages` | **그 플래그 자체가 없음** — `error: unexpected argument '--list-languages'` | 초판이 이 에러 출력을 'Kotlin 없음'으로 오독. 실제 지원 여부는 `sg run -l` 로 직접 검증해야 함 |
| `sg run -l kotlin -p 'val $N = $V'` (.kt) | **val 선언 라인만 매칭**(fun/class 라인 비매칭) | Kotlin grammar **정상 작동**(진짜 AST 변별). C3 sg 단계 유효 |
| `sg run -l rust -p 'fn $N() { $$$ }'` (.rs) | **fn 정의 매칭 성공** | Rust grammar **정상 작동** |
| `sg run -l klingon ...` (대조군) | `klingon is not supported!` 명시 거부 | kotlin/rust 가 'silent ignore' 가 아니라 진짜 지원됨을 확인 |
| `git diff --stat v1.12.1 -- src/main/kotlin src/test/kotlin tree-sitter-wasm/src` | **빈 결과** (변경 없음) | gold 라인 번호 유효, 인덱스-live tree 불일치 없음 |
| `zoekt -h` | `-index_dir`/`-jsonl`/`-sym` 존재, `-index` **없음** | DESIGN 의 `-index` 표기는 전부 `-index_dir` 로 교정 필수 |
| enclosing-function 매핑표 (`grep enclosing dataset.yaml`) | 참조만 있음(145줄 `"매핑표(아래)"`), **실제 표 미존재** | grader 가 기계 적용 불가 → design bug #3 |

---

## 1. ast-grep Kotlin·Rust 지원 — 초판 오류 정정 (최우선)

### 초판의 오류
초판은 design_bug 에 `major` 로 'ast-grep 0.42.1 에 Kotlin grammar 가 없다(실측 확인). C3 2단계는 Kotlin 에 대해 no-op' 을 기재했다. 이는 **거짓**이다. 근거였던 `sg --list-languages | grep -i kotlin` 은 그 플래그가 존재하지 않아 에러를 내고, 빈 grep 결과를 'Kotlin 미지원' 으로 오독한 것이다.

### 실측 반증 (이 빌드 0.42.1)
```
$ sg run -l kotlin -p 'val $N = $V' t.kt     # → val 선언 라인만 매칭 (fun/class 비매칭)
$ sg run -l kotlin -p 'class $N { $$$ }' t.kt # → 클래스만 매칭
$ sg run -l rust   -p 'fn $N() { $$$ }' t.rs  # → fn 정의 매칭
$ sg run -l klingon -p '...' t.kt             # → "klingon is not supported!" (명시 거부)
```
`val` 패턴이 fun/class 라인을 빼고 val 선언만 잡는다 = 텍스트 폴백이 아니라 **진짜 AST 변별**. 대조군 `klingon` 은 명시 거부되므로 kotlin/rust 는 '조용히 무시' 가 아니라 진짜 지원된다.

### 우려의 반전 (삭제가 아님)
C3 sg 단계의 **진짜 잔여 위험은 'grammar 부재' 가 아니라 '에이전트가 sg 단계를 건너뜀'** 이다. 근거: baseline(`results.md` §8 격리감사·`benchmark-design.md` §8)에서 **D암이 sg 를 한 번도 호출하지 않은 전례**가 있다. sg 를 건너뛰면 C3 의 파이프라인은 `zoekt → serena → read` 가 되어 **C1 으로 퇴화**한다. 이는 §4(파이프라인 비강제)의 C1≡C2 붕괴 위험과 같은 계열의 문제다.

→ **권고**: P16 을 '미지원 명시' 가 아니라 '파일럿이 sg 실제 호출·구조 필터 기여를 실증; 미호출 시 C3≡C1 으로 결과 보고에 기록' 으로 재정의. C3 PIPELINE_BLOCK 에 'sg 로 구조 축소를 반드시 1회 이상 시도하라(Kotlin·Rust 모두 지원됨)' 를 명시.

---

## 2. 파일럿 셀 게이팅 (Pilot Gating) 설계

### 현황·문제
DESIGN §8 에 파일럿 단계가 없다(48셀 직행). 파이프라인 1개라도 프롬프트 결함(file 누락, `-index` 오타, serena 훅 납치, sg 미호출)이 있으면 해당 조합 12셀 전체가 무효이고, 사후 발견 시 재실행 비용이 배증한다.

### 파일럿 대상 (커버리지 확장 — task 지적 반영)
초판 파일럿(C3×M3-W + C4×M1)은 M1·M3-W 만 경유해 **grader 의 가장 모델의존적 경로(M2 FQN 충돌·adjudication, M3-L trap·definition_site)** 가 48셀 전에 한 번도 실행되지 않는다. 따라서:

- **C3×M3-W**: zoekt 구문, sg 구조매칭(Kotlin·Rust 둘 다), serena 훅 오버라이드, file 필드 스키마, ±1 채점, trap 2개 전 경유.
- **C4×M1**: serena 금지 격리·도구 납치 0 실증.
- **C2×M2 (신규 추가)**: serena 선행 + **FQN 충돌·adjudication 라우팅·test_tail 분리** 경로 실증. M2 가 결정적으로 채점되는지(adjudication 비중이 낮은지) 게이팅.
- **합성 M3-L dry-run (셀 아님)**: definition_site(:16) neutral 처리·trap(KOTLIN_ 접두·문자열) 배제·소문자 과대매칭 회피를 grader 합성 입력으로 검증.

### 게이팅 통과 기준 (사전 정의)
- (a) 출력에 `file` 필드 존재, (b) grader 가 hit/miss 기계 산출 완료, (c) `evals/`·`docs/`·`report.html` 접근 0, (d) 허용 도구 외 0, (e) **transcript 의 model 필드 값이 sonnet**(§6 참조), (f) C3 에서 sg 1회 이상 실제 호출, (g) M2 dry-run 에서 adjudication_needed 가 의도한 충돌만 라우팅.
- 파일럿은 **sonnet 고정**, **N=3 카운트 제외**(버그 발견 시 수정 전 런 혼입 금지).

---

## 3. serena 워밍업 셀 절차 (3층 검증)

DESIGN §4.2 의 'serena 사전점검 1셀' 을 세 목표로 분해해 각각 독립 실증·기록한다.

```
워밍업 셀 목표:
  (a) ToolSearch select:mcp__serena__find_symbol 성공 → 스키마 로드 확인
      (현 세션 ToolSearch select:mcp__serena__* 빈 결과였음 — 서브에이전트 런타임 재확인 필수)
  (b) mcp__serena__find_symbol("JsonFormatterService") 응답 확인 → LSP 프라임
  (c) 재호출 응답시간이 첫 호출 대비 유의미 감소 → 캐시 영속 실증
  (d) .serena/ 인덱스 파일 변경시각을 측정 셀 직전 기록 → 측정 셀 종료 후 동일하면 재인덱싱 없음 확인
```
- 층 1(스키마 로드) 실패 시 C1·C2·C3 36셀 전부 serena 미사용으로 무효 → 먼저 해소.
- (c)·(d) 실패 시 C1·C2·C3 wall-clock 은 '검색+인덱싱 혼합' 으로 주석 처리, **정확도(recall/precision) 수치만 valid 로 보고**.

---

## 4. 측정 격리 + 파이프라인 비강제 결과해석

### 4-1. 검색 스코프 누수 3종
- **rg/sg 의 evals/ 접근**: `evals/` 문서가 gold 토큰(`SupportedLanguage.KOTLIN` 5파일, `formatJson` 다수)을 포함 → 무스코프 `rg` 가 evals 를 훑으면 M3-L 정밀도 붕괴. 프롬프트 강제 단독은 불충분, transcript 감사 필수.
- **Read 의 gold 백도어**: 에이전트가 `evals/codegraph-vs-serena/dataset.yaml` 을 Read 하면 gold 전체 노출 → blind 붕괴. HARD CONSTRAINTS 에 `evals/·docs/·report.html 내 어떤 파일도 Read·검색 금지` 추가.
- **인덱스 내부 빌드 금지 사후감사**: `grep "zoekt-index" <transcript>` → 0 이어야 pass(PROVENANCE-combos.txt §측정격리). 감사 스크립트에 명시.

→ transcript 감사 항목: 모든 rg/sg/read path 인자에 `evals/`·`docs/`·`report.html` 없음 + `zoekt-index` 호출 0 + `evals/` 접근 0. 위반 1건도 해당 셀 무효.

### 4-2. C3 sg 단계 — 미호출이 진짜 위험 (§1 연결)
sg grammar 는 작동하므로(§1), C3 의 위험은 'sg 건너뜀 → C3≡C1 퇴화' 다. transcript 에서 sg 호출 횟수를 파싱해 'C3 중 N회 중 K회가 sg 미사용' 으로 보고.

### 4-3. 단계 순서 비강제 → C1≡C2 붕괴 위험
C1(zoekt→serena)·C2(serena→zoekt)의 유일한 독립변수는 **단계 순서**다. 비강제면 두 조합이 같은 행동으로 수렴해 변별이 사라진다. → transcript 에서 각 단계 도구의 첫 등장 순서를 파싱해 **PIPELINE 준수율을 메트릭으로 집계**('C1 N회 중 K회 순서 역행'). 결과 §7 한계에 '이 결과는 조합의 이론적 상한이 아니라 이 파이프라인 지시를 받은 sonnet 에이전트의 실측 행동' 명시.

---

## 5. C1~C4 vs 단일도구 delta 비교의 공정성

- **분모 재정의(권고)**: '단일도구 best' 분모는 합집합 조합에 자명하게 유리(DESIGN §6 도 '재현율 기계적 상승' 인정). 부분집합 arm 으로 재정의 — C4(zoekt+read)↔H(zoekt+ctags 단독), C1·C2(serena 포함)↔B(serena 단독), C3↔H·B union 이론치. 이래야 '조합이 단순 합집합보다 실제 더 나은가' 측정 가능.
- **N=1 vs N=3**: 단일도구 N=1, 조합 N=3 median. 단일도구 분산 미지 → delta 신뢰구간 정의 불가. 보고서에 명시.
- **substrate 불일치**: 단일도구 Agent 도구, 조합 Workflow(또는 폴백 Agent). 토큰·wall-clock 비용 delta 가 apples-to-oranges → 동일 substrate 실행 또는 한계 명시.
- **serena DI 맹점(M1) 해소 판정**: 최종 recall 만 보면 DI 호출을 zoekt 가 건졌는지 serena 가 건졌는지 불명. grader 출력에 '단계별 최초 회수 도구' 귀속 필드 추가 또는 transcript 파싱으로 각 gold hit 를 최초 반환 도구에 귀속.

---

## 6. 48셀 substrate + sonnet 고정 검증

### sonnet 고정 — 사후가 아닌 파일럿 게이트로 승격 (task 지적 반영)
초판 메트릭 파서의 `model` 감사 열은 사후(48셀 소진 후) 확인이다. `opts.model:'sonnet'` 설정 여부가 아니라 **transcript 가 실제 model=sonnet 을 기록하는지**를 파일럿 게이팅 조건으로 올린다. 파일럿에서 model 미기록·opus 기록 시 즉시 **Agent substrate 폴백**(원 벤치마크가 model 기록 확인된 substrate)으로 전환하고 결정을 실행 로그에 기록.

### 기타 substrate 리스크
- transcript 경로 패턴 확인 → 메트릭 파서가 Workflow/Agent 양 substrate 분기 처리.
- C1·C2·C3 serena 셀 병렬 시 LSP 경합 → 직렬 실행 또는 경합 감지. 응답 지연이 wall-clock 에 섞이면 조합 간 시간 비교 오염.

---

## 7. 기타 누락 항목

- **7-1. git diff 사전 게이트 자동화**: 실측 빈 출력 확인됨(수동). 48셀 직전 스크립트에 `git diff --stat v1.12.1 -- src/main/kotlin src/test/kotlin tree-sitter-wasm/src` → 비어있지 않으면 실행 중단 삽입.
- **7-2. M2 test_tail 분리**: `test_tail` 3종(TypeConversionWasmIntegration{,V2,V3}Test)은 별도 분모(3). 폐포 8 에 산입 금지. grader 가 `test_tail_recall = hit_test_files / 3` 별도 출력.
- **7-3. M2 depth 비채점**: matching_key 는 qualified_name, depth 는 참조 전용. grader 가 depth 로 키잉·감점 금지 명기.
- **7-4. frozen-gold.json 사전추출**: PyYAML 없음 → `dataset.yaml` 을 1회 JSON 추출(canonical 동결 겸용) 후 grader 는 `import json` 만 사용. 추출은 PyYAML 가능 환경 또는 수동 변환으로 1회.
- **7-5. C4 serena 납치 실증**: 환경의 'Always prioritize Serena tools' 훅이 서브에이전트 전파(benchmark-design §5 의 E(bare) 가 겪은 문제). C4 프롬프트 오버라이드 효과를 파일럿 C4×M1 의 transcript 감사(serena 호출 0)로 게이팅. 납치 시 오버라이드 강화 후 전체 C4 실행.
- **7-6. N=3 이상치 처리**: 무효 셀(격리 위반·모델 오염) 발생 시 해당 run 제외 + 사유 기록. N=1 로 줄면 '단일 측정(분산 없음)' 명시, N=0 이면 재실행.

---

## 8. 체크리스트 요약 (교정 반영)

| # | 항목 | 상태 | 비고 |
|---|------|------|------|
| P1 | `frozen-gold.json` 사전추출, grader 가 JSON 만 읽음 | 미완 | PyYAML 없음 실측 |
| P2 | grader ±1 매칭 1:1 bipartite | 미완 | blocker |
| P3 | grader M2 불충분 qualifier → adjudication 라우팅 | 미완 | blocker |
| P4 | grader `adjudication_needed[]` 출력 필드 | 미완 | 하이브리드 전제 |
| P5 | M1 enclosing-function 동치표 구축 또는 adjudication 라우팅 | 미완 | 표 미존재 실측 |
| P6 | definition_site(M3-L:16, M1:210) neutral 규칙 명시 | 미완 | |
| P7 | M2 test_tail 폐포 8 과 분리 집계 | 미완 | |
| P8 | M2 depth 채점 키 사용 금지 명기 | 미완 | |
| P9 | OUTPUT SCHEMA 에 `file` 추가 (전 조합) | 미완 | DESIGN §4.1 결함 |
| P9b | **OUTPUT SCHEMA qualified_name = package+class+method FQN 강제** | 미완 | **신규 — M2 결정적 채점 회복** |
| P10 | PIPELINE_BLOCK `-index` → `-index_dir` (전 조합·§2.2) | 미완 | DESIGN 결함 |
| P11 | HARD CONSTRAINTS 'evals/ Read 금지' 추가 | 미완 | |
| P12 | serena 훅 오버라이드 문구 (C4 효과 실증) | 일부 | |
| P13 | 파일럿 = C3×M3-W + C4×M1 + **C2×M2 + M3-L dry-run** | 미완 | **커버리지 확장** |
| P14 | serena 워밍업 (a)~(d) 3층 검증 | 미완 | |
| P15 | `git diff` 사전 게이트 자동화 | 미완 | 수동만 완료 |
| P16 | **C3 sg = grammar 지원됨(실측). 파일럿이 sg 실제 호출 실증, 미호출 시 C3≡C1 기록** | 미완 | **초판 정정** |
| P17 | transcript 감사: rg/sg/read path + zoekt-index 0 + evals 0 | 미완 | |
| P18 | 단계 순서 준수율 메트릭·파서 | 미완 | |
| P19 | delta 분모를 부분집합 arm 으로 재정의 | 권고 | |
| P20 | **substrate·model=sonnet 파일럿 게이트(transcript 기록값 확인)** | 미완 | **사후→게이트 승격** |

---

## 9. 인터페이스 계약 (요약 — 상세는 interface_contract 필드)

- **grader**: 입력 `frozen-gold.json`(JSON 동결) + `cell-answers.jsonl`. 출력 `cell-scores.jsonl`(frozen 수치 + `adjudication_needed[]`). ±1 = exact 1:1 → 동일 file ±1 1:1 greedy → file 다르면 금지. M2 = 완전 FQN exact 우선, 충돌만 adjudication. definition_site = neutral(별도 카운터). test_tail = /3 별도. depth = 비채점.
- **opus adjudication**: `adjudication_needed[]` → `adjudication-decisions.jsonl`. 기계 hit 재채점 금지(N=3 보호), 결정 동결, 모호 시 miss 보수 처리.
- **메트릭 파서**: Workflow/Agent transcript 자동 분기. 열에 model(=sonnet 게이트)·evals_access_count(=0)·zoekt_index_call_count(=0)·sg_call_count(C3)·pipeline_stage_order 포함.