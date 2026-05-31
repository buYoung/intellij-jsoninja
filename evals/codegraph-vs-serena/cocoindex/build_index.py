#!/usr/bin/env python3
"""cocoindex 암(F) 인덱스 빌더.

cocoindex 의 tree-sitter 청킹(RecursiveSplitter, Kotlin 지원)으로 소스를 청크로 쪼개고,
sentence-transformers(all-MiniLM-L6-v2, normalize=True)로 임베딩해 SQLite 에 저장한다.
벡터 검색은 청크 수가 수천 개 수준이라 query.py 가 numpy 코사인 brute-force 로 처리한다
(pgvector/Docker 불필요). 이는 cocoindex 의 코드검색 '능력'(구문인지 청킹 + 시맨틱 임베딩)을
재현 가능하게 박제하기 위한 라이브러리 사용 방식이며, 선언적 flow/incremental 갱신 기능은
1회성 정적 인덱스에 불필요하므로 쓰지 않는다(README 의 설계 노트 참조).

사용:
    python build_index.py --src-root <repo-src-checkout> --out index.sqlite

provenance(v1.12.1) 고정은 --src-root 가 v1.12.1 소스를 가리키게 해서 보장한다.
"""
from __future__ import annotations

import argparse
import os
import sqlite3
import struct
import sys
from pathlib import Path

import numpy as np
from cocoindex.ops.text import RecursiveSplitter, detect_code_language

# 다른 암과 동일 인덱싱 범위: src/main/kotlin + src/test/kotlin
DEFAULT_SUBDIRS = ["src/main/kotlin", "src/test/kotlin"]
DEFAULT_MODEL = "sentence-transformers/all-MiniLM-L6-v2"
DEFAULT_CHUNK_SIZE = 1000  # bytes (tree-sitter 구문경계 우선, 이 값 근처에서 분할)


def iter_kotlin_files(src_root: Path, subdirs: list[str]):
    for sub in subdirs:
        base = src_root / sub
        if not base.is_dir():
            continue
        for path in sorted(base.rglob("*.kt")):
            yield path


def chunk_file(splitter: RecursiveSplitter, path: Path, src_root: Path,
               chunk_size: int):
    text = path.read_text(encoding="utf-8", errors="replace")
    lang = detect_code_language(filename=path.name) or "kotlin"
    rel = path.relative_to(src_root).as_posix()
    for ch in splitter.split(text, chunk_size, language=lang):
        body = ch.text.strip()
        if not body:
            continue
        yield {
            "file": rel,
            "start_line": ch.start.line,
            "end_line": ch.end.line,
            "start_byte": ch.start.byte_offset,
            "end_byte": ch.end.byte_offset,
            "text": ch.text,
        }


def main() -> int:
    ap = argparse.ArgumentParser()
    ap.add_argument("--src-root", required=True,
                    help="인덱싱할 소스 루트 (v1.12.1 체크아웃 권장)")
    ap.add_argument("--out", default="index.sqlite")
    ap.add_argument("--model", default=DEFAULT_MODEL)
    ap.add_argument("--chunk-size", type=int, default=DEFAULT_CHUNK_SIZE)
    ap.add_argument("--subdirs", nargs="*", default=DEFAULT_SUBDIRS)
    ap.add_argument("--git-commit", default="", help="provenance 기록용 커밋 해시")
    args = ap.parse_args()

    src_root = Path(args.src_root).resolve()
    if not src_root.is_dir():
        print(f"src-root 없음: {src_root}", file=sys.stderr)
        return 1

    splitter = RecursiveSplitter()
    rows: list[dict] = []
    n_files = 0
    for path in iter_kotlin_files(src_root, args.subdirs):
        n_files += 1
        rows.extend(chunk_file(splitter, path, src_root, args.chunk_size))
    print(f"files={n_files} chunks={len(rows)}")
    if not rows:
        print("청크 0개 — 경로 확인", file=sys.stderr)
        return 1

    # 임베딩: cocoindex 가 래핑하는 것과 동일 모델/정규화로 직접 인코딩.
    from sentence_transformers import SentenceTransformer
    model = SentenceTransformer(args.model)
    embs = model.encode([r["text"] for r in rows], normalize_embeddings=True,
                         convert_to_numpy=True, show_progress_bar=True,
                         batch_size=64).astype(np.float32)
    dim = int(embs.shape[1])
    print(f"embedded dim={dim}")

    out = Path(args.out)
    if out.exists():
        out.unlink()
    con = sqlite3.connect(out)
    con.execute("CREATE TABLE meta (key TEXT PRIMARY KEY, value TEXT)")
    con.execute(
        "CREATE TABLE chunks (id INTEGER PRIMARY KEY, file TEXT, start_line INT, "
        "end_line INT, start_byte INT, end_byte INT, text TEXT, embedding BLOB)")
    for k, v in {
        "model": args.model,
        "dim": str(dim),
        "chunk_size": str(args.chunk_size),
        "n_files": str(n_files),
        "n_chunks": str(len(rows)),
        "git_commit": args.git_commit,
        "subdirs": ",".join(args.subdirs),
    }.items():
        con.execute("INSERT INTO meta VALUES (?,?)", (k, v))
    for r, e in zip(rows, embs):
        con.execute(
            "INSERT INTO chunks (file,start_line,end_line,start_byte,end_byte,text,embedding) "
            "VALUES (?,?,?,?,?,?,?)",
            (r["file"], r["start_line"], r["end_line"], r["start_byte"],
             r["end_byte"], r["text"],
             struct.pack(f"<{dim}f", *e.tolist())))
    con.commit()
    con.close()
    print(f"wrote {out} ({len(rows)} chunks, dim={dim})")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
