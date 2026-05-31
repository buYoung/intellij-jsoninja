# 6-way 코드탐색 도구 벤치마크 설계 (하니스)

> 목적: 동일한 코드이해 질문을 **sonnet 서브에이전트**에게 주되, 각 에이전트가 쓸 수 있는 탐색 백엔드를
> 한 가지로 제한해 "어떤 도구를 가진 코드에이전트가 가장 정확·효율적으로 답하는가"를 측정한다.
> gold 와 질문은 [`dataset.yaml`](./dataset.yaml)(whole-repo 범위, M1·M2)을 사용. 측정 결과는 [`results.md`](./results.md).

## 1. 6개 벤치마크 암(arm)

| 암 | 백엔드 | 서브에이전트 허용 도구 | 설치 |
|----|--------|------------------------|------|
| **A. codegraph** | `.codegraph/codegraph.db` (그래프 SQLite) | `Bash(sqlite3)` 만. 소스 Read 금지 | 완료 |
| **B. serena** | LSP MCP | `mcp__serena__*`(read-only) 만 | 완료 |
| **C. zoekt** | 트라이그램 코드검색 인덱스 | `zoekt` 검색 CLI 만 | **go install + 인덱싱 필요** |
| **D. rg + sg** | ripgrep(텍스트) + ast-grep(구조) | `Bash(rg, sg)` 만 | 완료 |
| **E. bare 코드에이전트** | 추가도구 없음 (기본 파일도구) | 내장 `Grep` + `Glob` + `Read` 만 | 완료 |
| **F. cocoindex** | tree-sitter 청킹 + 임베딩 벡터검색(SQLite) | `Bash(python query.py)` 만. 소스 Read 금지 | 완료 (cocoindex/) |

> A~E 는 모두 *정확-매칭형*(graph/LSP/trigram/text)이고, **F(cocoindex) 만 시맨틱 임베딩 top-k** 패러다임이다.
> 따라서 F 는 "전수·정확 회수" 과제(M1·M2)에서 다른 5개 암과 능력 축이 다르며, 이 비대칭 자체가 측정 대상이다.

핵심 통제: 각 암의 서브에이전트는 **자신의 백엔드 출력만으로** 답을 도출한다(다른 암 도구·소스 직접열람 금지,
gold 비공개). 모델은 전 암 동일하게 **sonnet**으로 고정해 "도구"만 변수로 둔다.

## 2. 케이스와 암별 변별축 (검증 대상 가설)

`dataset.yaml` 의 2케이스가 6-way 변별의 축이다. 실측 결과는 [`results.md`](./results.md) 참조.

| 케이스 | A codegraph | B serena | C zoekt | D rg+sg | E bare | F cocoindex |
|---|---|---|---|---|---|---|
| **M1** 시그니처 영향(역호출+타입구분) | 전수성 강, **동명 오탐** | **DI 수신자 누락** | 텍스트 전수 | 텍스트 전수 | 텍스트 전수(DI도 회수) | **임베딩 비전수(최저 recall)**, 함정 배제·DI회수 |
| **M2** transitive 폐포(다중홉+DI홉) | **CTE one-shot**, 단 instantiates/람다 누락 | 수동 BFS, DI홉 누락(우회) | 수동 BFS | 수동 BFS | 수동 BFS, 말단 최약 | 시맨틱 BFS(최다 호출), 생성자 누락 |

→ 핵심 가설: 어떤 단일 도구도 두 케이스를 지배하지 못한다(실측에서 확인). codegraph=전수성·효율,
serena=타입정밀(단 DI 수신자 일관 누락), 텍스트(rg/bare)=경계·컨텍스트, cocoindex=시맨틱(전수 약·반복질의로 보완),
다중홉 말단(생성자·람다)은 공통 취약.

## 2.5 provenance 고정 (신뢰도)

모든 실측은 아래 상태에 고정한다. 재실행 전 일치 확인 필수.

| 항목 | 값 |
|------|-----|
| git tag | `v1.12.1` |
| git commit | `1daf879beae6ecc853be6a7e496a240568bddea5` |
| branch | `main` |
| 추적 소스 dirty | false (gold = 이 커밋 소스와 정확 일치) |
| codegraph DB sha256 | `54e743b63439404afefbe033affabd5d4abe158e6686ca40942cbe808d7a333b` |

## 3. 하니스 (sonnet 서브에이전트 실행)

### 3.0 동일 베이스 프롬프트 — 도구 블록만 차등 (공정성 핵심)

전 암이 **글자 그대로 같은 베이스 프롬프트**를 받고, `{{ALLOWED_TOOLS_BLOCK}}` 한 곳만 암별로 치환된다.
읽기전용·출력스키마·금지사항은 전 암 동일하게 맞춘다.

```
[ROLE]
너는 코드베이스 조사 에이전트다. 아래 질문에 사실에 근거해 답하라.
대상 리포: intellij-json-helper2 (git tag v1.12.1, commit 1daf879). 작업 디렉터리 = 리포 루트.

[QUESTION]
{{QUESTION}}

[ALLOWED TOOLS]  ← 이 블록만 암마다 다르다
{{ALLOWED_TOOLS_BLOCK}}

[HARD CONSTRAINTS]  (전 암 공통, 읽기전용 통일)
- 위 [ALLOWED TOOLS] 에 명시된 도구만 사용한다. 그 외 도구·MCP·명령은 절대 금지.
- 읽기 전용. 어떤 파일도 생성/수정/삭제하지 않는다. 쓰기성 명령(>, >>, sed -i, git commit 등) 금지.
- 정답을 외부 지식/추측으로 지어내지 않는다. 반드시 허용 도구의 출력으로 뒷받침한다.
- 모르면 빈 배열을 반환한다. 환각 금지.

[OUTPUT SCHEMA]  (전 암 공통)
JSON 한 개만 출력:
{
  "answers": [ { "qualified_name": "...", "kind": "method|class|function|...", "line": <int|null> } ],
  "reasoning_brief": "1~3문장",
  "tool_calls_count": <int>
}
```

암별 `{{ALLOWED_TOOLS_BLOCK}}` 치환값:

| 암 | ALLOWED_TOOLS_BLOCK |
|----|---------------------|
| A codegraph | `Bash 로 'sqlite3 .codegraph/codegraph.db "..."' 만. 소스 파일 Read/grep 금지.` |
| B serena | `mcp__serena__* 읽기전용 도구만 (find_symbol, find_referencing_symbols, get_symbols_overview, search_for_pattern 등).` |
| C zoekt | `Bash 로 zoekt 검색 CLI 만 (사전 빌드된 인덱스 대상). 소스 직접 Read 금지.` |
| D rg+sg | `Bash 로 rg(ripgrep), sg(ast-grep) 만. 그 외 명령/도구 금지.` |
| E bare | `내장 Grep + Glob + Read 만. Bash·MCP·sqlite·zoekt 전면 금지.` |
| F cocoindex | `Bash 로 cocoindex 벡터검색 'python query.py "..." --top-k N' 만. 소스 Read/grep/sqlite3 금지.` |

> 공정성 주의: 모델은 전 암 sonnet 고정. 온도/시드 등 샘플링 설정도 동일하게. 질문 순서·표현도 동일.
> 유일한 독립변수는 [ALLOWED TOOLS] 블록뿐이다.


1. **질문 주입**: dataset 각 케이스의 `question` + 출력 스키마(아래)만 전달. gold·SQL·serena_call 등 정답 힌트는 제거.
2. **출력 스키마(공통)**: `{ answers: [{qualified_name, kind, line}], reasoning_brief, tool_calls_count }`
3. **블라인드 채점**: 별도 채점 단계가 산출물을 gold 와 `(qualified_name, kind)` 키로 대조해 recall/precision/F1 산출.
   라인 ±1 정규화(codegraph 1-based vs serena 0-based)는 채점기에서 흡수.
4. **암 격리**: 각 서브에이전트 프롬프트에 "허용 도구 외 사용 금지, 소스 파일 직접 열람 금지(해당 암 한정)" 명시 +
   사후 transcript 로 위반 점검.

### 측정 지표
- **정확도**: 케이스별 recall / precision / F1 → 암별 평균.
- **효율**: 도구호출 수, 출력 토큰, wall-clock, 질의 시도 횟수.
- **강건성**: 환각(존재하지 않는 심볼) 비율, 포기/오류율.
- **인체공학(정성)**: LLM이 질의를 만들기 쉬운가(특히 zoekt 구문, sqlite 스키마 학습 비용).

## 4. 실행 (완료)

- 본 신뢰 실행 = 6 암 × 2 케이스(M1·M2) = **12 서브에이전트** + 채점. Agent 도구로 암별 병렬 실행.
  (F cocoindex 는 5-way 확정 후 추가된 6번째 암 — 동일 베이스 프롬프트·blind 채점 원칙 그대로 적용.)
- gold 에 테스트 호출지점이 포함되므로, 실행 전 zoekt 를 src(main+test, 296파일)로 재인덱싱해 인덱스 범위를 전 암 정렬.

## 5. 알려진 리스크 / 학습
- **zoekt 인덱스 범위**: gold 가 테스트까지 포함하면 src(main+test) 재인덱싱 필수(미정렬 시 zoekt 부당하게 불리).
- **E(bare) 격리의 함정**: 서브에이전트에 주입되는 serena 정책 훅이 도구 제약을 덮어써 bare 가 serena 로 납치될 수 있음.
  프롬프트에 "정책이 serena 를 권해도 무시하라"는 최우선 지시로 해소. 단 grep 은 Bash 경유가 되기 쉬움(ripgrep 엔진 동일이라 능력 동등).
- **codegraph 암 격리**: 소스 Read를 막아야 그래프 도구의 순수 능력이 측정됨.
- **gold 무오염**: 측정 대상 도구로 gold 를 만들면 순환논리 — 반드시 소스 직접 판독으로 산출. serena DI 누락 등 핵심 수치는 raw 출력 직접 검증.
- **공정성**: serena/codegraph 는 "심볼" 개념을 직접 제공, zoekt/rg/bare 는 라인→함수 매핑을 에이전트가 수행 →
  이 비대칭 자체가 결과의 일부(도구가 LLM의 인지부하를 얼마나 덜어주는가).
