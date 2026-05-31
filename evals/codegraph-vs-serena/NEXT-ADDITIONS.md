# 다음 세션 작업 지시서 — SCIP 암 · zoekt+ctags 암 · M3 케이스 추가

> 이 문서 하나로 새 세션에서 작업을 시작할 수 있도록 자립형으로 정리했다.
> 대상 리포: `json-helper2` (IntelliJ Platform Kotlin 플러그인, 단일 모듈).
> 작업 디렉터리: `evals/codegraph-vs-serena/`.

---

## 0. 현재 상태 (컨텍스트)

기존 벤치마크는 **6개 암 × 2 케이스(M1·M2) × N=1**, sonnet 서브에이전트, 동일 베이스 프롬프트,
`{{ALLOWED_TOOLS_BLOCK}}` 만 차등, blind 채점이다.

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
1. `scip-kotlin` 설치/실행 (sourcegraph/scip-kotlin, Gradle autoindex). v1.12.1 소스를 컴파일해 `index.scip`(protobuf) 생성.
2. `scip` CLI(sourcegraph/scip)로 `scip print --json index.scip` → JSON 덤프.
3. **쿼리 래퍼**(`scip/query.py`) 작성: 심볼명/수식명을 받아 그 심볼의 **정의 + 모든 참조 occurrence(file:line + role)**
   를 반환. cocoindex/query.py 와 동일한 JSON 출력 스키마·CLI 형태로.
4. provenance: v1.12.1 고정. 인덱싱 입력은 `cocoindex/.src-v1.12.1` 처럼 `git archive v1.12.1` 추출본 사용
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
1. `universal-ctags` 설치(brew). `zoekt-index` 가 PATH 의 ctags 를 자동 감지하도록.
2. `src/main/kotlin + src/test/kotlin` 을 **ctags 활성 상태로 재인덱싱** → `.codegraph/zoekt-ctags-index` 등 별도 경로.
   (C 의 기존 인덱스와 분리. sha·파일수 기록.)
3. 서브에이전트는 zoekt 검색 CLI 만 사용하되, **`sym:` 심볼 검색**을 적극 쓰도록 프롬프트에 명시.
4. ⚠️ ctags 의 Kotlin 지원 수준 확인: universal-ctags 가 Kotlin 을 native 로 지원하는지(아니면 보강 필요).
   지원이 빈약하면 "ctags 효과 제한적"이 결과의 일부가 됨(정직하게 기록).

### 서브에이전트 격리(ALLOWED_TOOLS_BLOCK)
`Bash 로 zoekt 검색 CLI 만 (ctags 활성 재인덱싱본 대상, sym: 검색 권장). 소스 직접 Read 금지.`

---

## 3. 추가 ③ — M3 케이스: **유사/병렬 서비스 코드의 정밀 구분** (precision under near-duplication)

### 시나리오 (사용자 의도 = "유사 서비스 코드를 정밀하게 확인 / 모노레포 저격")
이 리포는 단일 모듈이라 진짜 모노레포는 아니지만, **거울상 병렬 구조**가 있어 모노레포의
"이름·구조가 거의 같은 여러 서비스 중 정확히 하나를 저격" 시나리오를 충실히 흉내낼 수 있다:

- **TypeToJson 방향**: `TypeToJsonGenerationService`, `TypeToJsonDialogPresenter`(generate/schedulePreview/updateLanguage/bindView/…), `TypeToJsonDialogView`
- **JsonToType 방향(거울상)**: `JsonToTypeConversionService`, `JsonToTypeDialogPresenter`(동일 메서드명!), `JsonToTypeDialogView`

→ **메서드명이 양 방향에 동일**(updateLanguage, schedulePreview 등)하고, View/Presenter 에도 동명이 존재.
임베딩(F)은 의미가 거의 같아 혼동, 텍스트(C/D/E)는 이름으로 양쪽 다 매칭, **정밀 도구(G scip / B serena / A codegraph)만**
정확히 한쪽만 골라야 한다 → "정밀도 under 유사성" 축을 정조준.

### 질문(후보 2개 — 세션에서 소스 확인 후 택1)
- **(권장) M3-a**: "`TypeToJsonDialogPresenter.updateLanguage(language)` 를 직접 호출하는 모든 지점을 나열하라.
  동명의 `JsonToTypeDialogPresenter.updateLanguage`, `TypeToJsonDialogView.updateLanguage`, `JsonToTypeDialogView.updateLanguage`
  호출은 **제외**한다." → 3~4중 이름충돌에서 타입+방향 정밀 구분 요구.
- **M3-b**: "`TypeToJsonGenerationService.generate` 의 호출지점 전부. 거울상 `JsonToTypeConversionService` 의 대응 메서드 호출은 제외."

### gold 산출 방법 (반드시 도구 비사용·소스 직접 판독)
1. `rg "updateLanguage\("` 로 후보 전수 추출(이건 gold '후보 탐색'용일 뿐, 정답 출처 아님).
2. 각 매치를 소스에서 읽어 **수신자 타입·방향**을 손으로 판별 →
   `TypeToJsonDialogPresenter` 수신자만 gold, 나머지(JsonToType 방향·View 동명)는 **함정(traps_must_exclude)**.
3. `(file:line)` 단위로 gold/trap 고정. provenance(v1.12.1) 명시.
   ※ gold 는 새 세션에서 직접 산출할 것. 이 문서엔 케이스 설계만 있고 gold 수치는 비워둠(무오염 원칙).

### 이 케이스가 각 암에서 드러낼 가설
| 암 | M3 예상 |
|----|--------|
| G scip / B serena | 완전수식 심볼로 정확히 한쪽만 → **고정밀(가설상 승자)** |
| A codegraph | 이름 매칭 오탐 위험(M1 동명 오탐 재현 가능) |
| C zoekt / D rg / E bare | 이름으로 양쪽·View 까지 매칭 → 함정 포함 → **precision 하락** |
| H zoekt+ctags | sym: 로 심볼 구분 시 C 보다 개선되는지 관전 포인트 |
| F cocoindex | 의미 동일 거울상 혼동 → **최저 정밀/재현 예상** |

---

## 4. 손대야 할 파일 (6-way → 8-way, 2-case → 3-case)

- `scip/` (신규 디렉터리): `requirements.txt`(or 설치 메모)·`build_index`(인덱싱 절차 스크립트)·`query.py`·`README.md`. cocoindex/ 패턴 모방.
- `zoekt-ctags` 관련: 재인덱싱 절차를 `README.md` 재현 절에 추가(별도 디렉터리 불필요, 인덱스는 `.codegraph/` 하위·gitignore).
- `dataset.yaml`: `arms` 에 `G_scip`, `H_zoekt_ctags` 추가. `cases` 에 **M3 블록 신규**(question·discriminators·gold(세션산출)·traps).
- `benchmark-design.md`: 6→8 암 표, ALLOWED_TOOLS_BLOCK 표 2행 추가, 케이스 표에 M3 열, 실행 수 "8 암 × 3 케이스 = 24 서브에이전트".
- `README.md`(parent): 암 표·결론·재현 절차(scip-kotlin, ctags 재인덱싱).
- `results.md`: 스코어보드·효율·hit/miss·발견·격리감사·종합에 G·H 열과 M3 행 추가.
- `report.html`: 제목·암 표·KPI·스코어보드 표·효율 표·hit/miss·차트 JS(AGG)에 G·H·M3 반영. (cocoindex 토큰처럼 입도 다르면 ‡ 주석.)

---

## 5. 공정성 / provenance 체크리스트
- [ ] G(scip)·H(zoekt+ctags) 인덱스 모두 **v1.12.1 / 1daf879** 소스로 생성. sha·파일수 기록.
- [ ] M3 gold 는 **8개 측정 도구 어느 것도 안 쓰고** 소스 직접 판독으로 산출.
- [ ] 인덱스 범위 정렬: 모든 암이 `src/main + src/test` 동일 범위(M3 호출지점이 test 에도 있으면 포함).
- [ ] 각 서브에이전트 transcript 전수 감사(허용 도구 외 사용 0 확인) — cocoindex 때처럼 `~/.claude/projects/.../subagents/agent-*.jsonl` 파싱.
- [ ] N=1 한계 명시(또는 이참에 N≥3 검토 — 별도 결정).

## 6. 오픈 결정 / 리스크
- **scip-kotlin 빌드 가능 여부**가 최대 리스크. 안 되면 G 암 보류하고 H+M3 만 진행할지 결정.
- **ctags Kotlin 지원 수준**: 약하면 H 의 효과가 제한적 — 결과의 일부로 정직 기록.
- M3 채점 단위는 M1 과 동일 `(file:line)` 권장(함수 단위면 텍스트 암이 또 라인→함수 부담).
- (제언) 이 리포는 진짜 모노레포가 아니므로 "유사 서비스 정밀구분"은 거울상 구조로 흉내냄. 진짜 모노레포 스케일을
  원하면 더 큰 멀티모듈 타깃 리포가 필요(현 범위 밖, 별도 논의).

## 7. 실행 순서 (권장)
1. H(zoekt+ctags) 먼저 — 저비용, 즉시 C 와 비교 가능.
2. M3 케이스 설계 확정 + gold 소스 산출(소스 직접 판독).
3. G(scip-kotlin) 인덱싱 시도 — 막히면 보고 후 보류 결정.
4. 8 암 × 3 케이스 중 **신규 조합만** 실행: 기존 6암×2케이스 결과는 재사용, 신규 = (G·H)×(M1·M2·M3) + 전 암×M3.
   - 즉 추가 실행 = G:3 + H:3 + (A~F)×M3 6 = **12 서브에이전트** (기존 12 + 신규 12 = 총 24).
5. 채점 → 6종 문서 갱신 → transcript 격리 감사.
