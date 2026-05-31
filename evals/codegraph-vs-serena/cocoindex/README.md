# cocoindex 암 — 시맨틱 벡터검색 백엔드 (벤치마크 F 암)

tree-sitter 청킹 + 트랜스포머 임베딩으로 코드 청크를 벡터화하고, 쿼리와의 코사인 유사도 top-k 를 반환하는
**시맨틱 검색** 백엔드다. codegraph/serena/zoekt/rg/bare 가 *정확-매칭* 인 것과 달리 cocoindex 는 *의미 근접* 을 돌려준다.

## 역할

5-way 벤치마크(M1·M2)에 6번째 암으로 추가됐다. "임베딩 기반 시맨틱 검색이 전수·정확 회수 과제에서
어떻게 거동하는가"를 측정한다. 동일 베이스 프롬프트·blind 채점·동일 gold 를 그대로 적용한다.

## 구성

| 파일 | 내용 |
|------|------|
| `build_index.py` | 소스 트리 → tree-sitter 청킹 → MiniLM 임베딩 → SQLite(`index.sqlite`) |
| `query.py` | 쿼리 문자열 → 임베딩 → 코사인 top-k 청크 반환 (벤치마크 격리: 이 스크립트만 호출) |
| `requirements.txt` | cocoindex, sentence-transformers, torch 등 |
| `.src-v1.12.1/` | v1.12.1 소스 체크아웃(인덱싱 입력, git archive 추출) |

## 재현 (M1·M2, Kotlin 전용)

```bash
uv venv -p 3.13 .venv && . .venv/bin/activate
uv pip install -r requirements.txt
git archive v1.12.1 src/main/kotlin src/test/kotlin | tar -x -C .src-v1.12.1
python build_index.py --src-root .src-v1.12.1 --out index.sqlite --git-commit 1daf879beae6ecc853be6a7e496a240568bddea5
python query.py "포맷 옵션을 적용해 JSON 을 정렬·정형화" --top-k 8
```

## 재현 (M3 확장 — Rust 포함, A안)

M3-W 가 Rust `tree-sitter-wasm/src` 측 정의를 포함하므로, M3 인덱스는 Rust 를 포함해 재빌드한다
(`build_index.py` 의 `--subdirs` 로 범위 지정 + `.rs` 확장자 수집).

```bash
. .venv/bin/activate
git archive v1.12.1 src/main/kotlin src/test/kotlin tree-sitter-wasm/src | tar -x -C .src-v1.12.1
python build_index.py --src-root .src-v1.12.1 \
  --subdirs src/main/kotlin src/test/kotlin tree-sitter-wasm/src \
  --out index.sqlite --git-commit 1daf879beae6ecc853be6a7e496a240568bddea5
# 결과: 1249 청크 (kt 1053 + rs 196)
```

## 동작 원리 / 채점 시 주의

- 청크 = tree-sitter 로 자른 코드 조각(파일+라인범위). gold 의 (file:line) 와 대조 시 청크 라인범위로 환산.
- 임베딩 비결정성: 모델·버전 고정(all-MiniLM-L6-v2, 384d). 재현 시 동일 모델 사용.
- 결과는 `../results.md` 의 F(cocoindex) 행 참조. M3 에서 cocoindex 는 라인-정밀 회수에 약해 M3-W 0.125 / M3-L ~0.07 로 최약(임베딩의 정확-위치 한계).

> 주: 이 파일은 이전 커밋에서 끊긴 heredoc 으로 말미에 `EOF` 가 수천 줄 덧붙어 손상돼 있었고, M3 문서화 중 정상 내용으로 복구했다.
