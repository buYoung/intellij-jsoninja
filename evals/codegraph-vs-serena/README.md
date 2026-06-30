# 코드 탐색 백엔드 벤치마크 (codegraph · serena · zoekt · rg+sg · bare · cocoindex · zoekt+ctags)

JSONinja 리포에서 "실제 코드 수정 직전 엔지니어가 던지는 질문"을 7개 코드탐색 백엔드에 동일 프롬프트로 묻고,
소스에서 독립 산출한 정답(gold)과 대조해 정확도·효율을 측정한 벤치마크다. 각 백엔드는 sonnet 서브에이전트가
구동하며, 차이는 오직 **허용 도구**뿐이다.

| 암 | 백엔드 |
|----|--------|
| A. codegraph | `.codegraph/codegraph.db` 정적 코드그래프(SQLite, @colbymchenry/codegraph) |
| B. serena | LSP 기반 심볼 탐색 MCP (kotlin+rust) |
| C. zoekt | 트라이그램 코드검색 인덱스 (**ctags 없음**) |
| D. rg+sg | ripgrep(텍스트) + ast-grep(구조) |
| E. bare | 내장 텍스트검색(grep) + 파일읽기 (추가 도구 없음) |
| F. cocoindex | tree-sitter 청킹 + 임베딩 시맨틱 벡터검색 ([cocoindex/](./cocoindex/)) |
| H. zoekt+ctags | 트라이그램 + **ctags 심볼 메타**(`sym:` 검색) |

> A~E·H 는 *정확-매칭형*, F(cocoindex) 만 *시맨틱 임베딩 top-k* 다.
> **C(plain) 와 H(ctags) 를 둘 다 둬** ctags 메타데이터의 순효과를 비교한다(H 는 C 를 덮어쓰지 않는 신규 암).
> M1·M2 외 **polyglot 경계 케이스 M3-W·M3-L** 을 추가했다(아래). 전 암 인덱스는 v1.12.1+Rust 포함으로 재인덱싱(A안).

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
- **M3-W — 언어 간 WASM ABI seam**: Kotlin 호스트가 Rust WASM export 를 **문자열 이름으로** 바인딩하는 지점과
  대응 Rust `#[no_mangle] pub extern "C"` 정의. gold 8(Kotlin `TreeSitterWasmRuntime.kt:61-64` + Rust `lib.rs:25/30/92/121`).
- **M3-L — 다중 타깃언어 enum 참조**: `SupportedLanguage.KOTLIN` enum 상수를 **심볼로서** 참조하는 모든 지점(when 분기 포함).
  gold 29(main 13 + test 16). 함정 = 문자열 'kotlin'·`KOTLIN_` 접두·리소스 경로(텍스트 'kotlin' 표면 173L/42F).

## 핵심 결론

- **(M3 polyglot) "정밀>텍스트" 단순 서열은 성립하지 않는다 — 비단조.** 언어 간 seam(M3-W)에선 텍스트군(C/D/E/H)이 1.00, 순수 그래프 **codegraph 만 0.50**(Kotlin 문자열 바인딩 맹점); serena 는 내장 텍스트툴(`search_for_pattern`)로 1.00. 언어 내 enum 참조(M3-L)에선 serena 1.00 정밀이지만 텍스트군도 qualified 토큰 검색으로 0.966, 반대로 **codegraph 0.00**(graph 가 enum 상수 참조를 색인 안 함). → 갈림의 축은 "정밀 vs 텍스트"가 아니라 **백엔드가 무엇을 색인 가능한가**(문자열 리터럴/enum 참조/cross-language 링크/라인 정밀도). 상세 [`results.md`](./results.md).
- **(M3) ctags 효과**: H(zoekt+ctags)는 plain C 대비 M2 transitive 에서 0.625→**0.75** 개선(`sym:` 심볼검색이 람다·DI홉 회수). M1·M3-W·M3-L 은 C 와 동급.
- **단일 지배 도구 없음.** codegraph=전수성·효율 강점이나 동명 매칭 오탐(타입 맹점, M1 precision 0.94).
  serena=타입 정밀하나 **명시적 타입 없는 DI 수신자(`service<T>()`/`getService(class.java)`) 호출을 일관 누락**
  (M1 재현율 0.76, raw 출력 직접 검증). 텍스트(rg/bare)=경계·컨텍스트 강점이나 다중홉 폐포 말단(생성자·람다 콜백)에서 누락.
- **bare(텍스트+읽기)는 rg 와 동일 능력군.** rg 가 근소 우위(M1 1.00 vs 0.97, M2 0.75 vs 0.625).
  단순 역호출엔 충분하나 깊은 transitive 에선 최약체 — 추가 도구 없는 baseline 의 한계가 과제 유형에 따라 드러난다.
- **cocoindex(시맨틱 임베딩)는 전수 회수에 부적합하나 무너지지 않는다.** M1 recall 0.67 로 6개 암 중 최저
  (임베딩 top-k 가 33개 호출지점을 전수 열거 못 함) — 단 함정·환각 0 이고 serena 가 놓친 DI 호출지점은 회수.
  M2 는 반복 시맨틱 BFS 로 recall 0.75(최상위 동률)까지 끌어올렸으나 호출수 최다(M2 30콜). "임베딩은 정확-탐색에 약하다"는
  예상은 M1 에서 확인됐고, M2 는 유능한 에이전트가 약한 검색 원시도구를 추론으로 보완하는 양상(serena 의 raw-vs-agent 교훈 재현).
- **효율**: 전수 조회는 인덱스/그래프가 압도적(zoekt M1 1콜·37s). 다중홉 BFS 는 호출수·토큰 폭증(serena M2 캐시읽기 1.42M).
  cocoindex 는 의미상 'near' 결과를 'exact' 로 좁히려 질의를 반복 재구성해 호출수가 가장 많다(M1 17콜·M2 30콜).

## 방법론

- **gold 는 소스에서 독립 산출** — 측정 대상 6개 도구 중 어느 것도 정답 산출에 쓰지 않음(순환논리 차단).
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

# cocoindex 인덱스 (tree-sitter 청킹 + MiniLM 임베딩 → SQLite) — 상세는 cocoindex/README.md
cd cocoindex && uv venv -p 3.13 .venv && . .venv/bin/activate && uv pip install -r requirements.txt
git archive v1.12.1 src/main/kotlin src/test/kotlin | tar -x -C .src-v1.12.1
python build_index.py --src-root .src-v1.12.1 --out index.sqlite --git-commit 1daf879beae6ecc853be6a7e496a240568bddea5
```

**M3 확장 재현(7암×4케이스, A안 = Rust 포함)**: 도구 설치 — codegraph `npm i -g @colbymchenry/codegraph@latest`,
zoekt `go install github.com/sourcegraph/zoekt/cmd/{zoekt-index,zoekt}@latest`, `universal-ctags`(brew; zoekt 가
`universal-ctags` 이름을 찾으므로 `ln -sf $(command -v ctags) $HOME/go/bin/universal-ctags`). 인덱싱 입력은
`git archive v1.12.1 src/main/kotlin src/test/kotlin tree-sitter-wasm/src` 추출본(`.codegraph/src-v1.12.1`). 그 추출본에
codegraph(`codegraph init . && codegraph index .`), `zoekt-index -disable_ctags`(C), `zoekt-index -require_ctags`(H),
cocoindex(`--subdirs ... tree-sitter-wasm/src`)를 각각 빌드한다. sha·파일수는 `PROVENANCE-m3.txt`. (`.codegraph/` 는 gitignore.)

이후 `benchmark-design.md` 의 베이스 프롬프트에 `dataset.yaml` 의 각 케이스 질문과 암별 도구 블록을 끼워
sonnet 서브에이전트로 실행하고, 산출물을 gold 와 `(qualified_name)`/`(file:line)` 키로 대조해 채점한다.

## 한계

- 각 케이스 1회 실행(N=1) — 샘플링 분산 미측정.
- D 암은 실제로 `sg`(ast-grep) 미사용(rg-only) — 구조검색 변별은 미측정.
- F(cocoindex)는 임베딩 모델·청크크기·top-k 가 새 변수(본 실행 = all-MiniLM-L6-v2·1000B 고정). 출력이 청크라
  gold 의 (file:line) 와는 청크 라인추정으로 환산해 채점 — 정밀도 하락 일부는 라인추정 오차다(함정/환각과 구분).
- E(bare) 암은 grep 을 `Bash grep` 으로 호출(내장 Grep 도구 미사용). 동일 ripgrep 엔진이라 능력 동등하나,
  내장 도구로의 엄격 강제까지 원하면 MCP·Bash 를 비활성화한 샌드박스가 필요하다(능력은 안 바뀜).
