# 코드 탐색 백엔드 벤치마크 (codegraph · serena · zoekt · rg+sg · bare)

JSONinja 리포에서 "실제 코드 수정 직전 엔지니어가 던지는 질문"을 5개 코드탐색 백엔드에 동일 프롬프트로 묻고,
소스에서 독립 산출한 정답(gold)과 대조해 정확도·효율을 측정한 벤치마크다. 각 백엔드는 sonnet 서브에이전트가
구동하며, 차이는 오직 **허용 도구**뿐이다.

| 암 | 백엔드 |
|----|--------|
| A. codegraph | `.codegraph/codegraph.db` 정적 코드그래프(SQLite) |
| B. serena | LSP 기반 심볼 탐색 MCP |
| C. zoekt | 트라이그램 코드검색 인덱스 |
| D. rg+sg | ripgrep(텍스트) + ast-grep(구조) |
| E. bare | 내장 텍스트검색(grep) + 파일읽기 (추가 도구 없음) |

## 문서

| 파일 | 내용 |
|------|------|
| [`dataset.yaml`](./dataset.yaml) | **신뢰 데이터셋** — 2케이스(M1·M2)의 질문·소스 독립 gold·홉별 도달 경로·함정·provenance 고정 |
| [`results.md`](./results.md) | **측정 결과** — 스코어보드(recall/precision)·토큰·효율·hit/miss·핵심 발견·격리 감사 |
| [`report.html`](./report.html) | 결과 대시보드(단일 파일, 차트·표) |
| [`benchmark-design.md`](./benchmark-design.md) | 하니스 설계 — 동일 베이스 프롬프트(`{{ALLOWED_TOOLS_BLOCK}}`만 차등), 암별 도구 블록 |

## 케이스 (whole-repo 범위)

- **M1 — 시그니처 변경 영향(blast radius)**: `JsonFormatterService.formatJson(...)` 시그니처를 바꿀 때 고쳐야 할
  모든 호출지점. gold 33(prod 12 + test 21), 함정 = 동명 `JsoninjaPanelPresenter.formatJson` 호출 2건.
- **M2 — 동작 변경 전파(transitive 폐포)**: `TreeSitterWasmRuntime.getOrCreate()` 동작을 바꿀 때 영향받는
  프로덕션 함수 전부. gold 8함수(treesitter→typeConversion→ui/dialog 3모듈 종단, DI 홉 2개 포함).

## 핵심 결론

- **단일 지배 도구 없음.** codegraph=전수성·효율 강점이나 동명 매칭 오탐(타입 맹점, M1 precision 0.94).
  serena=타입 정밀하나 **명시적 타입 없는 DI 수신자(`service<T>()`/`getService(class.java)`) 호출을 일관 누락**
  (M1 재현율 0.76, raw 출력 직접 검증). 텍스트(rg/bare)=경계·컨텍스트 강점이나 다중홉 폐포 말단(생성자·람다 콜백)에서 누락.
- **bare(텍스트+읽기)는 rg 와 동일 능력군.** rg 가 근소 우위(M1 1.00 vs 0.97, M2 0.75 vs 0.625).
  단순 역호출엔 충분하나 깊은 transitive 에선 최약체 — 추가 도구 없는 baseline 의 한계가 과제 유형에 따라 드러난다.
- **효율**: 전수 조회는 인덱스/그래프가 압도적(zoekt M1 1콜·37s). 다중홉 BFS 는 호출수·토큰 폭증(serena M2 캐시읽기 1.42M).

## 방법론

- **gold 는 소스에서 독립 산출** — 측정 대상 5개 도구 중 어느 것도 정답 산출에 쓰지 않음(순환논리 차단).
  홉별 도달 경로를 `dataset.yaml` 에 감사 가능하게 기록.
- **blind 채점** — 에이전트에 gold 비공개. recall = 찾은 gold/전체, precision = 맞은 답/제출 답. 라인 ±1 정규화.
- **provenance 고정** — git `v1.12.1`(`1daf879`), codegraph DB sha256 `54e743b6…`. 재실행 전 일치 확인.
- **인덱스 범위 정렬** — gold 에 테스트 호출지점이 포함되므로 zoekt 를 src(main+test, 296파일)로 재인덱싱해 공정성 확보.
- **격리 감사** — 각 서브에이전트 transcript 로 도구사용 사후 점검(하드 샌드박스 아님).

## 재현

`.codegraph/` 의 인덱스(db·zoekt)는 대용량 바이너리라 커밋하지 않는다. 아래로 재생성한다.

```bash
# codegraph 인덱스: codegraph 도구로 리포 인덱싱 → .codegraph/codegraph.db
# zoekt 인덱스 (src = main + test)
export PATH="$HOME/go/bin:$PATH"
zoekt-index -index .codegraph/zoekt-index src
```

이후 `benchmark-design.md` 의 베이스 프롬프트에 `dataset.yaml` 의 각 케이스 질문과 암별 도구 블록을 끼워
sonnet 서브에이전트로 실행하고, 산출물을 gold 와 `(qualified_name)`/`(file:line)` 키로 대조해 채점한다.

## 한계

- 각 케이스 1회 실행(N=1) — 샘플링 분산 미측정.
- D 암은 실제로 `sg`(ast-grep) 미사용(rg-only) — 구조검색 변별은 미측정.
- E(bare) 암은 grep 을 `Bash grep` 으로 호출(내장 Grep 도구 미사용). 동일 ripgrep 엔진이라 능력 동등하나,
  내장 도구로의 엄격 강제까지 원하면 MCP·Bash 를 비활성화한 샌드박스가 필요하다(능력은 안 바뀜).
