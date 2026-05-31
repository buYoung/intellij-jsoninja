# F. cocoindex 암 (시맨틱 임베딩 코드검색)

기존 5-way 벤치마크(codegraph · serena · zoekt · rg+sg · bare)에 추가된 **6번째 암**.
다른 5개 암이 모두 *정확 매칭형*(graph / LSP / trigram / text)인 반면, cocoindex 는
**tree-sitter 구문인지 청킹 + 트랜스포머 임베딩 + 벡터 유사도검색**이라는 *시맨틱* 패러다임이다.

> 핵심 가설: M1(호출지점 전수)·M2(transitive 폐포)처럼 **전수·정확** 회수가 필요한 과제에서
> 임베딩 top-k 검색이 정확-매칭 암 대비 얼마나 무너지는지(또는 의외로 버티는지)를 박제한다.

## 구성 요소

| 파일 | 역할 |
|------|------|
| `requirements.txt` | cocoindex 1.0.7 + sentence-transformers + numpy |
| `build_index.py` | src/main+src/test 의 `.kt` 를 cocoindex `RecursiveSplitter`(tree-sitter, Kotlin) 로 청킹 → `all-MiniLM-L6-v2`(normalize) 임베딩 → SQLite 저장 |
| `query.py` | 질의 텍스트를 동일 모델로 임베딩 → numpy 코사인 top-k 청크를 JSON 출력 (서브에이전트가 Bash 로만 호출) |

## 설계 노트 (왜 선언적 flow 를 안 쓰나)

cocoindex 1.0.x 의 정식 사용법은 `App`/`mount` 기반 선언적 flow 로 pgvector 등에 증분 인덱싱하는 것이다.
하지만 본 벤치마크는 **v1.12.1 단일 커밋의 1회성 정적 인덱스**만 필요하므로, 증분 갱신·실시간 동기화
기능은 무의미하다. 그래서 cocoindex 가 제공하는 코드검색의 *능력 핵심* 두 가지 —
(1) tree-sitter 구문인지 청킹(`cocoindex.ops.text.RecursiveSplitter`, 엔진에 Kotlin `.kt`/`.kts` 내장),
(2) sentence-transformers 임베딩 — 을 **라이브러리로 직접 호출**해 결정론적·재현 가능하게 박제한다.
벡터스토어도 청크 수가 수천 개라 numpy brute-force 코사인으로 충분해 pgvector/Docker 를 쓰지 않는다.
즉 "cocoindex 의 코드 청킹+임베딩 능력"은 그대로 측정하되, 인프라 변수는 최소화한 설계다.

## 격리 조건 (다른 암과 동일 원칙)

- 서브에이전트 허용 도구: **`Bash` 로 `python query.py "..."` 실행만**. 소스 직접 Read/grep/MCP 금지.
- 모델·온도 등은 다른 암과 동일 sonnet 고정. 유일한 독립변수는 탐색 백엔드(=cocoindex 벡터검색).

## 재현

```bash
cd evals/codegraph-vs-serena/cocoindex
uv venv -p 3.13 .venv && . .venv/bin/activate
uv pip install -r requirements.txt

# v1.12.1 소스 추출(인덱싱 입력) — provenance 고정
git archive v1.12.1 src/main/kotlin src/test/kotlin | tar -x -C .src-v1.12.1

# 인덱스 빌드
python build_index.py --src-root .src-v1.12.1 --out index.sqlite \
    --git-commit 1daf879beae6ecc853be6a7e496a240568bddea5

# 질의 예시
python query.py "JsonFormatterService.formatJson 호출지점" --top-k 25
```

## provenance

| 항목 | 값 |
|------|-----|
| git tag / commit | `v1.12.1` / `1daf879beae6ecc853be6a7e496a240568bddea5` |
| 인덱싱 범위 | `src/main/kotlin` + `src/test/kotlin` 의 `.kt` (146 + 13 = 159 파일) |
| 청킹 | cocoindex `RecursiveSplitter`, chunk_size=1000B, language=kotlin |
| 임베딩 | `sentence-transformers/all-MiniLM-L6-v2` (384-dim, normalize=True) |
| 검색 | numpy 코사인 top-k (정규화 벡터 내적) |

> `index.sqlite`·`.venv`·`.src-v1.12.1` 은 커밋하지 않는다(`.gitignore`). 위 절차로 재생성한다.

## 한계 (공정성 고지)

- **패러다임 비대칭**: 임베딩 검색은 "유사 청크 top-k"를 주지 "X 를 호출하는 곳 전부"를 주지 않는다.
  M1/M2 의 전수·정확 회수에는 구조적으로 불리하며, 낮은 recall 은 도구 결함이 아니라 용도 차이다.
- **출력 단위**: 결과가 청크(파일+라인범위)라, gold 의 `(file:line)`/`(qualified_name)` 와는
  enclosing-chunk 포함 여부로 환산해 채점한다(라인 ±1 정규화는 기존 채점기와 동일).
- **새 변수**: 임베딩 모델·청크크기·top-k 가 결과에 영향. 본 실행은 위 표 값으로 고정.
- **N=1**: 다른 암과 마찬가지로 케이스당 1회 실행.
