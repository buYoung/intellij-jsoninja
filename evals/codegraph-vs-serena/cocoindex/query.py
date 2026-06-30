#!/usr/bin/env python3
"""cocoindex 암(F) 벡터검색 쿼리 CLI.

build_index.py 가 만든 SQLite 인덱스에 대해 질의 텍스트를 동일 모델로 임베딩하고
코사인 유사도 top-k 청크를 JSON 으로 출력한다. 서브에이전트는 이 스크립트를 Bash 로만
호출해 코드를 탐색한다(소스 직접 Read 금지가 이 암의 격리 조건).

사용:
    python query.py "JsonFormatterService.formatJson 를 호출하는 곳" --top-k 25
"""
from __future__ import annotations

import argparse
import json
import sqlite3
import struct
import sys
from pathlib import Path

import numpy as np


def load_index(path: Path):
    con = sqlite3.connect(path)
    meta = dict(con.execute("SELECT key,value FROM meta").fetchall())
    dim = int(meta["dim"])
    rows = con.execute(
        "SELECT id,file,start_line,end_line,text,embedding FROM chunks").fetchall()
    con.close()
    mat = np.zeros((len(rows), dim), dtype=np.float32)
    info = []
    for i, (cid, file, sl, el, text, blob) in enumerate(rows):
        mat[i] = struct.unpack(f"<{dim}f", blob)
        info.append({"file": file, "start_line": sl, "end_line": el, "text": text})
    return meta, mat, info


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("query")
    ap.add_argument("--index", default=str(Path(__file__).parent / "index.sqlite"))
    ap.add_argument("--top-k", type=int, default=20)
    ap.add_argument("--preview-chars", type=int, default=240)
    ap.add_argument("--full-text", action="store_true",
                    help="청크 전체 텍스트 출력(기본은 미리보기만)")
    args = ap.parse_args()

    idx = Path(args.index)
    if not idx.exists():
        print(f"인덱스 없음: {idx} (build_index.py 먼저 실행)", file=sys.stderr)
        return 1

    meta, mat, info = load_index(idx)
    from sentence_transformers import SentenceTransformer
    model = SentenceTransformer(meta["model"])
    q = model.encode([args.query], normalize_embeddings=True,
                     convert_to_numpy=True)[0].astype(np.float32)
    scores = mat @ q  # 정규화된 벡터라 내적 = 코사인
    top = np.argsort(-scores)[: args.top_k]

    results = []
    for rank, i in enumerate(top, 1):
        c = info[int(i)]
        text = c["text"]
        results.append({
            "rank": rank,
            "score": round(float(scores[i]), 4),
            "file": c["file"],
            "start_line": c["start_line"],
            "end_line": c["end_line"],
            "preview": text if args.full_text else text[: args.preview_chars],
        })
    print(json.dumps({
        "query": args.query,
        "model": meta["model"],
        "n_chunks": meta["n_chunks"],
        "results": results,
    }, ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
