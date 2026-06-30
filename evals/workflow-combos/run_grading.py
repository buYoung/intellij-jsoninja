#!/usr/bin/env python3
"""
run_grading.py — 채점 오케스트레이터 (단일 채점 정본)
====================================================
워크플로 결과(answers) + transcript 디렉터리를 받아:
  1) 셀별 answers JSON 파일 생성 (_answers/{label}.json, label=C{n}x{case}xr{run})
  2) transcript 지문매칭 → 셀↔transcript manifest
  3) grader.py 로 품질(recall/precision/F1) 채점 — 품질의 유일 산출처
  4) metrics_parser.py 로 토큰·모델·격리 추출 — 메트릭의 유일 산출처
  5) cell_id 로 조인 → metrics-combos.csv (품질+메트릭 단일 행)
  6) 거버넌스 요약(model_ok, advisor 실참여, 격리, C3 sg 호출)

품질은 grader.py 만, 메트릭은 metrics_parser.py 만 계산한다(이중 계산 금지 — 단일 정본).
"""
import argparse
import csv
import json
import re
import subprocess
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent


def fingerprint(prompt_text: str):
    """transcript 첫 user 메시지(프롬프트) → (combo, case) 지문매칭."""
    p = prompt_text
    if "formatJson(json, formatState" in p:
        case = "M1"
    elif "getOrCreate() 가" in p:
        case = "M2"
    elif "analyze_source, get_last_error, alloc, dealloc" in p:
        case = "M3-W"
    elif "SupportedLanguage.KOTLIN enum" in p:
        case = "M3-L"
    else:
        return None
    # C5/C6 는 센티널을 먼저 매칭한다(C5 가 "1단계(하드): [serena]"·"mcp__serena__*(읽기전용)" 를
    # 포함해 C2·C1 패턴과 충돌하므로 반드시 선행 검사).
    if "[조합 식별자: C5" in p:
        combo = "C5"
    elif "[조합 식별자: C6" in p:
        combo = "C6"
    elif "1단계(하드): [serena]" in p:
        combo = "C2"
    elif "sg run -p" in p:
        combo = "C3"
    elif "serena(mcp__serena__*) 는 이 조합에서 완전 금지" in p:
        combo = "C4"
    elif "mcp__serena__*(읽기전용), Read." in p:
        combo = "C1"
    else:
        return None
    return combo, case


def first_user_text(transcript_path: Path) -> str:
    for line in transcript_path.open():
        line = line.strip()
        if not line:
            continue
        try:
            obj = json.loads(line)
        except Exception:
            continue
        msg = obj.get("message") if isinstance(obj, dict) else None
        if isinstance(msg, dict) and msg.get("role") == "user":
            content = msg.get("content")
            if isinstance(content, str):
                return content
            if isinstance(content, list):
                return " ".join(b.get("text", "") for b in content if isinstance(b, dict))
    return ""


def build_manifest(transcript_dirs):
    """하나 이상 transcript_dir 의 agent-*.jsonl 을 지문매칭 → {label: path}. N=3 은 mtime 순 r1..r3.
    여러 디렉터리를 받으면(예: 기존 C1~C4 wf + 신규 C5/C6 wf) 합쳐서 매니페스트를 만든다.
    combo 가 디렉터리별로 분리(C1~C4 vs C5/C6)되므로 셀별 run 순서 오염은 없다."""
    if isinstance(transcript_dirs, (str, Path)):
        transcript_dirs = [transcript_dirs]
    paths_all = []
    for d in transcript_dirs:
        paths_all.extend(Path(d).glob("agent-*.jsonl"))
    by_cell = {}
    for tp in sorted(paths_all, key=lambda x: x.stat().st_mtime):
        if tp.name.endswith(".meta.json"):
            continue
        fp = fingerprint(first_user_text(tp))
        if not fp:
            continue  # 워밍업 등 측정 외
        cid = f"{fp[0]}x{fp[1]}"
        by_cell.setdefault(cid, []).append(tp)
    manifest = []
    for cid, paths in by_cell.items():
        for i, tp in enumerate(paths, 1):
            manifest.append({"cell_id": f"{cid}xr{i}", "transcript_path": str(tp)})
    return manifest


def audit_tools(transcript_path: str) -> dict:
    """transcript 에서 도구 stickiness + 거버넌스 신호 추출."""
    c = {"zoekt": 0, "sg": 0, "serena": 0, "rg": 0, "read": 0,
         "bash": 0, "glob": 0, "grep": 0, "find": 0,
         "zoekt_index": 0, "evals_ref": 0, "opus_gen": 0, "advisor_tu": 0,
         "forbidden_path": 0}
    # gold/채점/결과 파일 접근 탐지(Read·Grep·Glob·Bash 전부) — 스코프·무결성 가드.
    # src 코드에는 등장하지 않는 토큰만 사용(오탐 방지).
    forbidden_substrs = ("evals/", "grader.py", "dataset.yaml", "results-combos",
                         "metrics-combos", "aggregate-combos", "benchmark-tasks", "_answers")
    for line in Path(transcript_path).open():
        line = line.strip()
        if not line:
            continue
        try:
            obj = json.loads(line)
        except Exception:
            continue
        m = obj.get("message")
        if not (isinstance(m, dict) and m.get("role") == "assistant"):
            continue
        if m.get("model") and "opus" in m["model"]:
            c["opus_gen"] += 1
        for b in (m.get("content") or []):
            if not (isinstance(b, dict) and b.get("type") == "tool_use"):
                continue
            name = b.get("name", "")
            if "advisor" in name.lower():
                c["advisor_tu"] += 1
            inp = b.get("input") or {}
            if name.startswith("mcp__serena__"):
                c["serena"] += 1
                # serena 도구도 경로 인자(relative_path 등)로 금지영역 접근 가능
                blob = " ".join(str(v) for v in inp.values())
                if any(s in blob for s in forbidden_substrs):
                    c["forbidden_path"] += 1
            elif name == "Read":
                c["read"] += 1
                if any(s in str(inp.get("file_path", "")) for s in forbidden_substrs):
                    c["forbidden_path"] += 1
            elif name == "Glob":
                c["glob"] += 1
                if any(s in (str(inp.get("path", "")) + str(inp.get("pattern", ""))) for s in forbidden_substrs):
                    c["forbidden_path"] += 1
            elif name == "Grep":
                c["grep"] += 1
                if any(s in (str(inp.get("path", "")) + str(inp.get("glob", ""))) for s in forbidden_substrs):
                    c["forbidden_path"] += 1
            elif name == "Bash":
                c["bash"] += 1
                cmd = inp.get("command", "")
                if "zoekt-index" in cmd:
                    c["zoekt_index"] += 1
                # 세그먼트별 선두 프로그램(간이)
                for seg in cmd.replace("&&", "\n").replace("||", "\n").replace("|", "\n").replace(";", "\n").split("\n"):
                    toks = seg.split()
                    prog = next((t for t in toks if "=" not in t and not t.startswith("-")), "")
                    base = prog.split("/")[-1]
                    if base == "zoekt":
                        c["zoekt"] += 1
                    elif base in ("sg", "ast-grep"):
                        c["sg"] += 1
                    elif base == "rg":
                        c["rg"] += 1
                    elif base == "grep":
                        c["grep"] += 1
                    elif base == "find":
                        c["find"] += 1
                if "evals/" in cmd:
                    c["evals_ref"] += 1
                # 제외 옵션(grep -v / --invert-match / --exclude*)의 인자는 무시한 뒤 검사.
                # (예: `grep -v "evals/"` 는 범위를 지킨 것이지 gold 접근이 아님 → 오탐 방지)
                scan = re.sub(r'(-v|--invert-match|--exclude(?:-dir)?(?:=|\s+))\s*("[^"]*"|\'[^\']*\'|\S+)',
                              ' ', cmd)
                if any(s in scan for s in forbidden_substrs):
                    c["forbidden_path"] += 1
    return c


def required_tool_ok(combo: str, tools: dict) -> tuple[bool, str]:
    """distinctive 도구 준수 + 무결성. 전 조합 공통: gold/evals/채점·결과파일 접근 시 즉시 실패."""
    # 범용 무결성 게이트(모든 조합) — gold/grader/결과파일 접근은 셀 무효.
    if tools.get("forbidden_path", 0) > 0:
        return (False, f"무결성 위반: 금지경로(gold/evals/grader/결과파일) 접근 {tools['forbidden_path']}회")
    if combo == "C1":
        return (tools["serena"] >= 1, "serena>=1 필요" if tools["serena"] < 1 else "")
    if combo == "C2":
        return (tools["serena"] >= 1, "serena>=1 필요" if tools["serena"] < 1 else "")
    if combo == "C3":
        ok = tools["sg"] >= 1 and tools["serena"] >= 1
        miss = []
        if tools["sg"] < 1:
            miss.append("sg>=1")
        if tools["serena"] < 1:
            miss.append("serena>=1")
        return (ok, ("필요: " + ",".join(miss)) if miss else "")
    if combo == "C4":
        return (tools["serena"] == 0, "C4 serena 금지 위반" if tools["serena"] > 0 else "")
    if combo == "C5":
        # serena≥1 필수 + zoekt/sg/rg/bash 전면 금지(텍스트 인덱스·셸 부재 환경)
        miss = []
        if tools.get("serena", 0) < 1:
            miss.append("serena>=1")
        for t in ("zoekt", "sg", "rg", "bash"):
            if tools.get(t, 0) > 0:
                miss.append(f"{t}=0위반")
        return (not miss, ("필요: " + ",".join(miss)) if miss else "")
    if combo == "C6":
        # Read + 셸 find/grep 만. rg·zoekt·sg·serena 금지(rg 금지로 C7≡rg 와 구분). grep|find 최소 1회.
        miss = [f"{t}=0위반" for t in ("serena", "zoekt", "sg", "rg") if tools.get(t, 0) > 0]
        if tools.get("grep", 0) < 1 and tools.get("find", 0) < 1:
            miss.append("grep|find>=1")
        return (not miss, ("필요: " + ",".join(miss)) if miss else "")
    return (True, "")


def aggregate_n3(joined: list, out_path: Path):
    """combo x case 로 묶어 recall/precision/f1 의 중앙값·평균±SD 출력(유효 n 표기)."""
    import statistics
    groups = {}
    for r in joined:
        cid = r["cell_id"]
        # C{n}x{case}xr{run}
        parts = cid.rsplit("xr", 1)
        key = parts[0]
        groups.setdefault(key, []).append(r)

    def vals(rows, col):
        out = []
        for r in rows:
            v = r.get(col)
            if v in (None, "", "N/A", "None"):
                continue
            try:
                out.append(float(v))
            except ValueError:
                pass
        return out

    lines = ["combo_case,n,recall_median,recall_mean,recall_sd,prec_median,prec_mean,prec_sd,f1_median,f1_mean,f1_sd,prec_valid_n"]
    print("\n[7] N=3 집계 (combo×case):")
    for key in sorted(groups):
        rows = groups[key]
        rec = vals(rows, "q_recall")
        prec = vals(rows, "q_precision")
        f1 = vals(rows, "q_f1")

        def stat(xs):
            if not xs:
                return ("", "", "")
            md = statistics.median(xs)
            mn = statistics.mean(xs)
            sd = statistics.stdev(xs) if len(xs) >= 2 else ""
            return (f"{md:.3f}", f"{mn:.3f}", (f"{sd:.3f}" if sd != "" else "n/a"))
        rm, ra, rs = stat(rec)
        pm, pa, ps = stat(prec)
        fm, fa, fs = stat(f1)
        lines.append(f"{key},{len(rows)},{rm},{ra},{rs},{pm},{pa},{ps},{fm},{fa},{fs},{len(prec)}")
        pflag = " ⚠P결측" if len(prec) < len(rec) else ""
        print(f"      {key:10s} n={len(rows)} recall중앙={rm}(±{rs}) prec중앙={pm}(±{ps}, n={len(prec)}){pflag}")
    out_path.write_text("\n".join(lines) + "\n")
    print(f"      → {out_path}")


def main():
    ap = argparse.ArgumentParser()
    ap.add_argument("--result", required=True, help="워크플로 출력 파일(.output, {result:{results:[...]}})")
    ap.add_argument("--transcript-dir", required=True, action="append",
                    help="agent-*.jsonl 디렉터리. 여러 번 지정 가능(기존 C1~C4 wf + 신규 C5/C6 wf 병합).")
    ap.add_argument("--answers-dir", default="_answers")
    ap.add_argument("--csv", default="metrics-combos.csv")
    args = ap.parse_args()

    raw = json.loads(Path(args.result).read_text())
    res = raw.get("result", raw)
    if isinstance(res, str):
        res = json.loads(res)
    cells = res.get("results", [])

    adir = HERE / args.answers_dir
    adir.mkdir(exist_ok=True)
    n_written = 0
    for c in cells:
        label = c.get("cell_label")
        ans = c.get("answers")
        if not label or ans is None:
            continue
        # label 예: C4xM1xr1 — grader 가 파싱하는 {combo}x{case}x{run}.json 형식
        (adir / f"{label}.json").write_text(json.dumps(ans, ensure_ascii=False))
        n_written += 1
    print(f"[1] answers 파일 {n_written}개 → {adir}")

    manifest = build_manifest([Path(d) for d in args.transcript_dir])
    mpath = HERE / "_manifest.json"
    mpath.write_text(json.dumps(manifest, ensure_ascii=False, indent=1))
    print(f"[2] transcript manifest {len(manifest)}개 → {mpath}")
    for entry in manifest:
        print(f"      {entry['cell_id']:14s} {Path(entry['transcript_path']).name}")

    # 3) grader (품질 단독)
    q_csv = HERE / "_quality.csv"
    subprocess.run([sys.executable, str(HERE / "grader.py"),
                    "--answers-dir", str(adir), "--output", str(q_csv),
                    "--adjudication", str(HERE / "adjudication-needed.jsonl")], check=True)
    print(f"[3] 품질 채점 → {q_csv}")

    # 4) metrics_parser (메트릭 단독)
    m_csv = HERE / "_metrics.csv"
    subprocess.run([sys.executable, str(HERE / "metrics_parser.py"),
                    "--manifest", str(mpath), "--output", str(m_csv)], check=True)
    print(f"[4] 메트릭 추출 → {m_csv}")

    # 5) cell_id 조인
    def load_csv(p):
        with open(p) as f:
            return list(csv.DictReader(f))
    q_rows = {}
    for r in load_csv(q_csv):
        cid = f"{r.get('combo')}x{r.get('case')}x{r.get('run')}"
        q_rows[cid] = r
    m_rows = {r.get("cell_id"): r for r in load_csv(m_csv)}
    man_map = {e["cell_id"]: e["transcript_path"] for e in manifest}
    all_ids = sorted(set(q_rows) | set(m_rows))
    joined = []
    for cid in all_ids:
        row = {"cell_id": cid}
        row.update({f"q_{k}": v for k, v in q_rows.get(cid, {}).items()})
        row.update({f"m_{k}": v for k, v in m_rows.get(cid, {}).items()})
        # stickiness + 필수도구 준수
        combo = cid.split("x")[0]
        tools = audit_tools(man_map[cid]) if cid in man_map else {}
        if tools:
            row.update({f"use_{k}": v for k, v in tools.items()})
            ok, why = required_tool_ok(combo, tools)
            row["required_tool_ok"] = ok
            row["required_tool_note"] = why
        joined.append(row)
    if joined:
        cols = []
        for r in joined:
            for k in r:
                if k not in cols:
                    cols.append(k)
        with open(HERE / args.csv, "w", newline="") as f:
            w = csv.DictWriter(f, fieldnames=cols)
            w.writeheader()
            w.writerows(joined)
    print(f"[5] 조인 → {HERE / args.csv} ({len(joined)}행)")

    # 6) 거버넌스 + stickiness/준수 요약
    print("[6] 거버넌스·stickiness 요약 (recall/prec | model_ok opus_gen adv_tu iso | zoekt/sg/serena/read | 준수):")
    noncompliant = []
    for r in joined:
        cid = r["cid"] if "cid" in r else r["cell_id"]
        rec = r.get("q_recall"); prec = r.get("q_precision")
        og = r.get("use_opus_gen"); atu = r.get("use_advisor_tu"); iso = r.get("m_isolation_violation")
        z, s, se, rd = r.get("use_zoekt"), r.get("use_sg"), r.get("use_serena"), r.get("use_read")
        ok = r.get("required_tool_ok"); note = r.get("required_tool_note", "")
        flag = "" if ok in (True, "True") else f"  ❌미준수({note})"
        if flag:
            noncompliant.append(cid)
        print(f"      {cid:13s} R={rec} P={prec} | ok={r.get('m_model_ok')} opus={og} adv={atu} iso={iso} | z{z}/sg{s}/se{se}/rd{rd}{flag}")
    if noncompliant:
        print(f"   ⚠ 필수도구 미준수 셀 {len(noncompliant)}개(조합 미실현 → 재실행 검토): {noncompliant}")

    # 7) N=3 집계
    aggregate_n3(joined, HERE / "aggregate-combos.csv")


if __name__ == "__main__":
    main()
