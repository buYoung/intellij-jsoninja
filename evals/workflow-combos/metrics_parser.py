#!/usr/bin/env python3
"""
metrics_parser.py — 워크플로 조합 벤치마크 셀별 메트릭 추출기
============================================================

역할:
    서브에이전트 transcript(JSONL) 에서 셀당 메트릭을 추출한다.
    두 가지 substrate 를 투명하게 처리한다(경로 차이는 메인이 manifest 로 흡수):
      A) Agent 도구 서브에이전트 : .../<session-uuid>/subagents/agent-<hex>.jsonl
      B) 워크플로 서브에이전트   : .../<session-uuid>/subagents/workflows/wf_<id>/agent-<hex>.jsonl

실측으로 확정된 transcript 사실(두 substrate 비교):
    - assistant 이벤트는 message.usage 에 output_tokens / cache_read_input_tokens /
      cache_creation_input_tokens 를 담는다. 한 API 응답(=message.id 1개)이 스트리밍으로
      여러 줄에 기록되며, 같은 message.id 의 조각들은 같은 누적 usage 를 공유하다가 마지막
      조각에서 최종 누적치로 점프한다 → 그룹당 max 가 그 응답의 실제 값.
    - 단순 합산은 cacheR 을 크게 과대계상한다(실측 flat: naive 1,809,549 vs grouped 817,187,
      약 2.2배; 워크플로: naive 3,687,831 vs grouped 2,141,596). 반드시 그룹핑+max.
    - usage.iterations[] 항목은 부모 usage 를 그대로 미러한다(실측: out/cacheR/cacheC 동일).
      → 별도 합산 금지(이중계상). 이 파서는 top-level usage 키만 읽으므로 자동 회피.
    - requestId 와 message.id 는 실측상 정확히 1:1(flat 13:13, 워크플로 24:24). 그룹핑 키는
      message.id 우선(없으면 requestId fallback)을 쓴다 — message.id 가 'API 응답 1개=usage 1건'
      의 원칙적 단위이고, 재시도로 한 requestId 가 두 message.id 로 쪼개지면 max 가 과소계상되는
      위험을 message.id 키가 막는다(실측 동치라 캐논 수치는 불변: group-by-mid==group-by-rid).
    - message.model 이 주력(서브에이전트) 모델이다(실측: 워크플로 'claude-sonnet-4-6',
      flat 샘플 'claude-haiku-4-5' → 모델 감사가 실제로 비-sonnet 을 잡아야 함).
    - advisorModel 키는 'advisor 턴 표식'이 아니라 워크플로 substrate 의 셀 단위 어노테이션이다
      (워크플로 substrate 전 assistant 이벤트에 존재, 값=advisor 모델 'claude-opus-4-8';
      flat substrate 에는 키 자체가 없음). 이 키가 있으면 그 셀에 advisor(opus) 가 붙은 것이고,
      advisor 호출 토큰이 같은 message.id usage 에 접혀 들어가 message 단위 차감이 불가능하다.
      → 모델 식별에서 advisorModel 을 절대 skip 기준으로 쓰지 않는다. 대신 advisor_present
      거버넌스 신호로만 사용한다(opus 참여 오염 감지).

인터페이스 계약(메인 → 이 파서):
    메인은 셀→transcript 경로 매핑을 manifest 파일(JSON 또는 CSV)이나 CLI 인자로 넘긴다.
    [방법 1] manifest JSON : python3 metrics_parser.py --manifest manifest.json --output metrics-combos.csv
             형태: [{"cell_id":"C1xM1xr1","transcript_path":"/abs/path/agent-xxx.jsonl"}, ...]  (배열 또는 줄 구분 JSON)
    [방법 2] 개별 셀 CLI  : python3 metrics_parser.py --cell C1xM1xr1 /abs/a.jsonl --cell C1xM1xr2 /abs/b.jsonl
    [방법 3] stdin       : echo "C1xM1xr1\t/abs/path.jsonl" | python3 metrics_parser.py --stdin

출력 CSV 컬럼:
    cell_id, transcript_path, model, model_ok, advisor_present, output_tokens,
    cache_read_tokens, cache_creation_tokens, cache_creation_5m_tokens, cache_creation_1h_tokens,
    tool_calls, wall_clock_sec, wall_clock_trustworthy,
    isolation_violation, isolation_detail, requestid_missing

거버넌스 주의:
    - model_ok=True(message.model=sonnet) 라도 advisor_present=True 면 opus 가 토큰·추론에
      섞인 셀이므로 'sonnet 순수'가 아니다. 두 축을 분리해 본다.
    - wall_clock 의 '인덱싱 제외(검색만)' 성질은 인덱스가 셀 밖 사전빌드 + 격리강제 덕분이다.
      따라서 isolation_violation=True 면 셀 내부 인덱싱이 발생한 것이라 그 셀 wall_clock 은
      '검색만' 시간이 아니다 → wall_clock_trustworthy=False 로 보고한다.

의존성: 표준 라이브러리만 (json, re, csv, datetime, argparse, sys, pathlib)
"""

from __future__ import annotations

import argparse
import csv
import json
import re
import sys
from collections import defaultdict
from datetime import datetime
from pathlib import Path


# ---------------------------------------------------------------------------
# 격리 감사 — 셀 내부 인덱싱(zoekt-index / ctags 빌드) 호출 감지
# ---------------------------------------------------------------------------
# 감지 대상(측정격리 위반):
#   - zoekt-index 바이너리 직접/래퍼/명령치환 실행
#   - -require_ctags 플래그
#   - ctags 빌드(ctags 바이너리 + 빌드 플래그 -R/-e/-f/-a 동반)
# 허용(위반 아님):
#   - zoekt -index_dir .codegraph/zoekt-ctags-index "..."   (읽기 전용 쿼리 — 올바른 구문)
#   - ls/grep/echo/cat 등에서 zoekt-index·ctags 문자열 언급
# ⚠️ '0 위반'은 '알려진 패턴 미발견'이며 완전 보증이 아니다(트랜스파일·동적 빌더 등 우회 가능).
_REQUIRE_CTAGS_RE = re.compile(r"-require_ctags\b")
_CTAGS_BUILD_FLAG_RE = re.compile(r"\s-(?:R|e|f|a)\b")
_WRAPPER_RE = re.compile(r"^(?:time|nohup|xargs|env|stdbuf|nice|ionice)\s+(?:-\S+\s+)*")


def _detect_isolation_violation(bash_command: str) -> tuple[bool, str]:
    """Bash 명령에서 셀 내부 인덱싱을 감지. 반환: (위반여부, 위반사유)."""
    if _REQUIRE_CTAGS_RE.search(bash_command):
        return True, "-require_ctags"

    # `;` `&&` `||` `|` 와 명령치환 $(...) / 백틱 경계로 세그먼트 분리(우회 차단)
    segments = re.split(r"[;&|]+|\$\(|\)|`", bash_command)
    for segment in segments:
        segment = segment.strip()
        if not segment:
            continue
        # 환경변수 인라인 할당 제거: VAR=value prog → prog
        segment = re.sub(r"^(?:\w+=\S*\s+)+", "", segment)
        # 래퍼 벗기기: time/nohup/xargs/env/... prog → prog (반복)
        prev = None
        while prev != segment:
            prev = segment
            segment = _WRAPPER_RE.sub("", segment).lstrip()
            segment = re.sub(r"^(?:\w+=\S*\s+)+", "", segment)
        tokens = segment.split()
        if not tokens:
            continue
        first_token = tokens[0]
        basename = first_token.split("/")[-1] if "/" in first_token else first_token
        if basename == "zoekt-index":
            return True, "zoekt-index"
        if basename == "ctags":
            # ctags 는 빌드 플래그(-R/-e/-f/-a) 동반 시에만 인덱스 빌드로 간주.
            # (플래그 없는 'ctags --version' 류 점검은 위반 아님)
            if _CTAGS_BUILD_FLAG_RE.search(segment):
                return True, "ctags-build"
    return False, ""


# ---------------------------------------------------------------------------
# 타임스탬프 파싱
# ---------------------------------------------------------------------------
def _parse_ts(ts_str: str) -> float | None:
    """ISO8601 타임스탬프를 Unix 초(float)로 변환. 실패 시 None."""
    if not ts_str:
        return None
    ts_str = ts_str.replace("Z", "+00:00")
    try:
        return datetime.fromisoformat(ts_str).timestamp()
    except ValueError:
        return None


# ---------------------------------------------------------------------------
# usage 추출 헬퍼
# ---------------------------------------------------------------------------
def _extract_usage(event: dict) -> dict | None:
    """이벤트에서 usage dict 추출(최상위 또는 message.usage). 없으면 None."""
    usage = event.get("usage")
    if usage is None:
        msg = event.get("message")
        if isinstance(msg, dict):
            usage = msg.get("usage")
    return usage if isinstance(usage, dict) else None


def _cache_creation_split(usage: dict) -> tuple[int, int]:
    """cache_creation 의 ephemeral 5m / 1h 분리값 추출(없으면 0,0)."""
    cc = usage.get("cache_creation")
    if isinstance(cc, dict):
        return (
            cc.get("ephemeral_5m_input_tokens", 0) or 0,
            cc.get("ephemeral_1h_input_tokens", 0) or 0,
        )
    return 0, 0


# ---------------------------------------------------------------------------
# 단일 transcript 파싱
# ---------------------------------------------------------------------------
def parse_transcript(transcript_path: str | Path) -> dict:
    """하나의 JSONL transcript 를 파싱해 메트릭 dict 를 반환한다."""
    path = Path(transcript_path)
    events: list[dict] = []
    with path.open(encoding="utf-8") as fh:
        for raw_line in fh:
            raw_line = raw_line.strip()
            if not raw_line:
                continue
            try:
                events.append(json.loads(raw_line))
            except json.JSONDecodeError:
                continue

    # -----------------------------------------------------------------------
    # 1) usage 집계 — message.id(없으면 requestId) 그룹핑 + 그룹당 max
    # -----------------------------------------------------------------------
    # 같은 message.id 의 스트리밍 조각은 누적 usage 를 공유하다 마지막에 최종치로 점프하므로
    # 그룹당 max 가 그 API 응답의 실제 값. 그룹 간 합산 = transcript 총량.
    # 키 부재 이벤트는 폴백(연속 동일 cacheR/cacheC 접기) + requestid_missing 플래그.
    keyed_groups: dict[str, list[dict]] = defaultdict(list)
    keyed_order: list[str] = []
    no_key_usages: list[dict] = []  # message.id·requestId 둘 다 없는 assistant usage
    requestid_missing = False

    for event in events:
        if event.get("type") != "assistant":
            continue
        msg = event.get("message") if isinstance(event.get("message"), dict) else {}
        group_key = msg.get("id") or event.get("requestId")
        if group_key is None:
            usage = _extract_usage(event)
            if usage is not None:
                no_key_usages.append(usage)
            requestid_missing = True
            continue
        if group_key not in keyed_groups:
            keyed_order.append(group_key)
        keyed_groups[group_key].append(event)

    total_output_tokens = 0
    total_cache_read = 0
    total_cache_creation = 0
    total_cc_5m = 0
    total_cc_1h = 0

    for group_key in keyed_order:
        usages = [
            u
            for event in keyed_groups[group_key]
            if (u := _extract_usage(event)) is not None
        ]
        if not usages:
            continue
        # 그룹당 max(=최종 누적치). iterations[] 는 top-level 만 읽으므로 자동 제외.
        total_output_tokens += max(u.get("output_tokens", 0) for u in usages)
        total_cache_read += max(u.get("cache_read_input_tokens", 0) for u in usages)
        total_cache_creation += max(u.get("cache_creation_input_tokens", 0) for u in usages)
        # cache_creation 5m/1h 분리: 동일 그룹에서 cache_creation_input_tokens 가 최대인 usage 의 분리값 채택
        best = max(usages, key=lambda u: u.get("cache_creation_input_tokens", 0))
        cc5m, cc1h = _cache_creation_split(best)
        total_cc_5m += cc5m
        total_cc_1h += cc1h

    # 폴백: 키 없는 usage(실측상 발생 안 함 — best-effort). 연속 동일 (cacheR,cacheC) 접기.
    if no_key_usages:
        current_key: tuple | None = None
        current_usages: list[dict] = []

        def _flush(usages: list[dict], key: tuple | None) -> None:
            nonlocal total_output_tokens, total_cache_read, total_cache_creation
            nonlocal total_cc_5m, total_cc_1h
            if not usages or key is None:
                return
            total_output_tokens += max(u.get("output_tokens", 0) for u in usages)
            total_cache_read += key[0]
            total_cache_creation += key[1]
            best = max(usages, key=lambda u: u.get("cache_creation_input_tokens", 0))
            cc5m, cc1h = _cache_creation_split(best)
            total_cc_5m += cc5m
            total_cc_1h += cc1h

        for usage in no_key_usages:
            key = (
                usage.get("cache_read_input_tokens", 0),
                usage.get("cache_creation_input_tokens", 0),
            )
            if key != current_key:
                _flush(current_usages, current_key)
                current_key = key
                current_usages = [usage]
            else:
                current_usages.append(usage)
        _flush(current_usages, current_key)

    # -----------------------------------------------------------------------
    # 2) 모델 추출 — assistant 이벤트의 message.model 직독
    # -----------------------------------------------------------------------
    # advisorModel 은 모델 식별에 절대 쓰지 않는다(셀 어노테이션이지 advisor 턴 표식 아님).
    # tool_use 를 가진 첫 턴의 model 을 주력으로 확정, 없으면 첫/마지막 fallback.
    primary_model = "unknown"
    for event in events:
        if event.get("type") != "assistant":
            continue
        msg = event.get("message")
        if not isinstance(msg, dict):
            continue
        model_val = msg.get("model")
        if not model_val:
            continue
        content = msg.get("content", [])
        has_tool_use = any(
            isinstance(c, dict) and c.get("type") == "tool_use"
            for c in (content if isinstance(content, list) else [])
        )
        if has_tool_use:
            primary_model = model_val
            break
        if primary_model == "unknown":
            primary_model = model_val
    if primary_model == "unknown":
        for event in reversed(events):
            msg = event.get("message")
            if isinstance(msg, dict) and msg.get("model"):
                primary_model = msg["model"]
                break

    model_ok = "sonnet" in primary_model.lower()

    # -----------------------------------------------------------------------
    # 3) advisor 참여 감지 — advisorModel 키 또는 advisor content
    # -----------------------------------------------------------------------
    advisor_present = False
    advisor_model = ""
    for event in events:
        if event.get("type") != "assistant":
            continue
        if event.get("advisorModel"):
            advisor_present = True
            advisor_model = event.get("advisorModel", "")
            break
        msg = event.get("message")
        if isinstance(msg, dict):
            content = msg.get("content", [])
            if isinstance(content, list):
                for c in content:
                    if isinstance(c, dict) and c.get("type") in (
                        "server_tool_use",
                        "advisor_tool_result",
                    ):
                        advisor_present = True
                        break
        if advisor_present:
            break

    # -----------------------------------------------------------------------
    # 4) tool_calls — 고유 tool_use id 기준
    # -----------------------------------------------------------------------
    seen_tool_ids: set[str] = set()
    unique_tool_calls = 0
    for event in events:
        msg = event.get("message")
        if not isinstance(msg, dict):
            continue
        content = msg.get("content", [])
        if not isinstance(content, list):
            continue
        for item in content:
            if not isinstance(item, dict) or item.get("type") != "tool_use":
                continue
            uid = item.get("id")
            if uid:
                if uid not in seen_tool_ids:
                    seen_tool_ids.add(uid)
                    unique_tool_calls += 1
            else:
                unique_tool_calls += 1

    # -----------------------------------------------------------------------
    # 5) wall_clock_sec — 전체 이벤트 min/max timestamp 차이
    # -----------------------------------------------------------------------
    timestamps: list[float] = []
    for event in events:
        ts = _parse_ts(event.get("timestamp", ""))
        if ts is not None:
            timestamps.append(ts)
    wall_clock_sec = max(timestamps) - min(timestamps) if len(timestamps) >= 2 else None

    # -----------------------------------------------------------------------
    # 6) 격리 감사 — Bash 명령에서 셀 내부 인덱싱 감지
    # -----------------------------------------------------------------------
    isolation_violation = False
    isolation_detail = ""
    for event in events:
        msg = event.get("message")
        if not isinstance(msg, dict):
            continue
        content = msg.get("content", [])
        if not isinstance(content, list):
            continue
        for item in content:
            if not isinstance(item, dict) or item.get("type") != "tool_use":
                continue
            if item.get("name") != "Bash":
                continue
            tool_input = item.get("input")
            if not isinstance(tool_input, dict):
                continue
            bash_cmd = tool_input.get("command", "")
            if not isinstance(bash_cmd, str):
                continue
            violated, reason = _detect_isolation_violation(bash_cmd)
            if violated:
                isolation_violation = True
                isolation_detail = f"[{reason}] {bash_cmd[:180]}"
                break
        if isolation_violation:
            break

    # wall_clock 신뢰성: 격리 위반 셀은 셀 내부 인덱싱이 시간에 섞여 '검색만'이 아님.
    wall_clock_trustworthy = (wall_clock_sec is not None) and (not isolation_violation)

    return {
        "model": primary_model,
        "model_ok": model_ok,
        "advisor_present": advisor_present,
        "advisor_model": advisor_model,
        "output_tokens": total_output_tokens,
        "cache_read_tokens": total_cache_read,
        "cache_creation_tokens": total_cache_creation,
        "cache_creation_5m_tokens": total_cc_5m,
        "cache_creation_1h_tokens": total_cc_1h,
        "tool_calls": unique_tool_calls,
        "wall_clock_sec": wall_clock_sec,
        "wall_clock_trustworthy": wall_clock_trustworthy,
        "isolation_violation": isolation_violation,
        "isolation_detail": isolation_detail,
        "requestid_missing": requestid_missing,
    }


# ---------------------------------------------------------------------------
# manifest / CLI 입력 파싱
# ---------------------------------------------------------------------------
def _load_cell_mappings(args: argparse.Namespace) -> list[tuple[str, str]]:
    mappings: list[tuple[str, str]] = []
    if args.manifest:
        raw = Path(args.manifest).read_text(encoding="utf-8").strip()
        if raw.startswith("["):
            records = json.loads(raw)
        else:
            records = [json.loads(line) for line in raw.splitlines() if line.strip()]
        for rec in records:
            mappings.append((rec["cell_id"], rec["transcript_path"]))
    elif args.cell:
        for pair in args.cell:
            mappings.append((pair[0], pair[1]))
    elif args.stdin:
        for raw_line in sys.stdin:
            raw_line = raw_line.strip()
            if not raw_line:
                continue
            if "\t" in raw_line:
                cell_id, path = raw_line.split("\t", 1)
            else:
                cell_id, path = raw_line.split(",", 1)
            mappings.append((cell_id.strip(), path.strip()))
    return mappings


# ---------------------------------------------------------------------------
# CSV 출력
# ---------------------------------------------------------------------------
CSV_FIELDNAMES = [
    "cell_id",
    "transcript_path",
    "model",
    "model_ok",
    "advisor_present",
    "output_tokens",
    "cache_read_tokens",
    "cache_creation_tokens",
    "cache_creation_5m_tokens",
    "cache_creation_1h_tokens",
    "tool_calls",
    "wall_clock_sec",
    "wall_clock_trustworthy",
    "isolation_violation",
    "isolation_detail",
    "requestid_missing",
]


def _write_csv(rows: list[dict], output_path: str | None) -> None:
    fh = open(output_path, "w", newline="", encoding="utf-8") if output_path else sys.stdout
    try:
        writer = csv.DictWriter(fh, fieldnames=CSV_FIELDNAMES)
        writer.writeheader()
        for row in rows:
            writer.writerow(row)
    finally:
        if output_path:
            fh.close()


def _error_row(cell_id: str, transcript_path: str, model: str, detail: str) -> dict:
    return {
        "cell_id": cell_id,
        "transcript_path": transcript_path,
        "model": model,
        "model_ok": False,
        "advisor_present": False,
        "output_tokens": 0,
        "cache_read_tokens": 0,
        "cache_creation_tokens": 0,
        "cache_creation_5m_tokens": 0,
        "cache_creation_1h_tokens": 0,
        "tool_calls": 0,
        "wall_clock_sec": "",
        "wall_clock_trustworthy": False,
        "isolation_violation": False,
        "isolation_detail": detail,
        "requestid_missing": False,
    }


# ---------------------------------------------------------------------------
# 메인
# ---------------------------------------------------------------------------
def main() -> None:
    parser = argparse.ArgumentParser(
        description="워크플로 벤치마크 셀별 메트릭 추출기 (metrics_parser.py)",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
사용 예:
  python3 metrics_parser.py --manifest manifest.json --output metrics-combos.csv
  python3 metrics_parser.py --cell C1xM1xr1 /abs/a.jsonl --cell C1xM1xr2 /abs/b.jsonl
  cat mapping.tsv | python3 metrics_parser.py --stdin

manifest.json 형식:
  [{"cell_id":"C1xM1xr1","transcript_path":"/abs/path/agent-xxx.jsonl"}, ...]
""",
    )
    input_group = parser.add_mutually_exclusive_group(required=True)
    input_group.add_argument("--manifest", metavar="FILE",
                             help="셀→경로 매핑 JSON 파일 (배열 또는 줄 구분 JSON)")
    input_group.add_argument("--cell", metavar=("CELL_ID", "PATH"), nargs=2,
                             action="append", help="셀 ID 와 transcript 경로 쌍 (반복 가능)")
    input_group.add_argument("--stdin", action="store_true",
                             help="stdin 에서 'cell_id\\tpath' 또는 'cell_id,path' 줄 읽기")
    parser.add_argument("--output", "-o", metavar="FILE", default=None,
                        help="CSV 출력 파일 경로 (생략 시 stdout)")
    parser.add_argument("--sonnet-pattern", metavar="PATTERN", default="sonnet",
                        help="sonnet 으로 판정할 모델명 부분 문자열 (기본: 'sonnet')")

    args = parser.parse_args()

    mappings = _load_cell_mappings(args)
    if not mappings:
        print("[ERROR] 처리할 셀이 없습니다.", file=sys.stderr)
        sys.exit(1)

    sonnet_pattern = args.sonnet_pattern.lower()
    rows: list[dict] = []

    for cell_id, transcript_path in mappings:
        if not Path(transcript_path).exists():
            print(f"[WARN] transcript 없음: cell={cell_id} path={transcript_path}", file=sys.stderr)
            rows.append(_error_row(cell_id, transcript_path, "FILE_NOT_FOUND", ""))
            continue
        try:
            metrics = parse_transcript(transcript_path)
        except Exception as exc:  # noqa: BLE001
            print(f"[ERROR] 파싱 실패: cell={cell_id} path={transcript_path} err={exc}", file=sys.stderr)
            rows.append(_error_row(cell_id, transcript_path, "PARSE_ERROR", str(exc)[:200]))
            continue

        model_ok = sonnet_pattern in metrics["model"].lower()
        rows.append({
            "cell_id": cell_id,
            "transcript_path": transcript_path,
            "model": metrics["model"],
            "model_ok": model_ok,
            "advisor_present": metrics["advisor_present"],
            "output_tokens": metrics["output_tokens"],
            "cache_read_tokens": metrics["cache_read_tokens"],
            "cache_creation_tokens": metrics["cache_creation_tokens"],
            "cache_creation_5m_tokens": metrics["cache_creation_5m_tokens"],
            "cache_creation_1h_tokens": metrics["cache_creation_1h_tokens"],
            "tool_calls": metrics["tool_calls"],
            "wall_clock_sec": (f"{metrics['wall_clock_sec']:.2f}"
                               if metrics["wall_clock_sec"] is not None else ""),
            "wall_clock_trustworthy": metrics["wall_clock_trustworthy"],
            "isolation_violation": metrics["isolation_violation"],
            "isolation_detail": metrics["isolation_detail"],
            "requestid_missing": metrics["requestid_missing"],
        })

        if metrics["isolation_violation"]:
            print(f"[ISOLATION VIOLATION] cell={cell_id} detail={metrics['isolation_detail'][:120]} "
                  "→ 셀 내부 인덱싱 의심. 이 셀 wall_clock 도 무효.", file=sys.stderr)
        if not model_ok:
            print(f"[MODEL WARN] cell={cell_id} model={metrics['model']} (sonnet 아님)", file=sys.stderr)
        if metrics["advisor_present"]:
            print(f"[ADVISOR WARN] cell={cell_id} advisor={metrics['advisor_model'] or 'detected'} "
                  "→ opus 참여 의심(토큰·추론 오염). message.model=sonnet 이어도 순수 아님.", file=sys.stderr)
        if metrics["requestid_missing"]:
            print(f"[KEY WARN] cell={cell_id}: message.id/requestId 없는 이벤트 → 폴백 집계. "
                  "cacheR/cacheC 보수적 해석.", file=sys.stderr)

    _write_csv(rows, args.output)

    advisor_count = sum(1 for r in rows if r.get("advisor_present"))
    violation_count = sum(1 for r in rows if r["isolation_violation"])
    non_sonnet_count = sum(1 for r in rows if not r["model_ok"])
    rid_missing_count = sum(1 for r in rows if r.get("requestid_missing"))
    print(f"\n[요약] 처리 셀={len(rows)}  격리위반={violation_count}  비-sonnet={non_sonnet_count}  "
          f"advisor참여={advisor_count}  키-폴백={rid_missing_count}", file=sys.stderr)
    if violation_count:
        print("[경고] 격리 위반 셀 존재 — transcript 확인 + 해당 셀 wall_clock 무효.", file=sys.stderr)
    if non_sonnet_count:
        print("[경고] 비-sonnet 모델 셀 존재 — 거버넌스 위반.", file=sys.stderr)
    if advisor_count:
        print(f"[경고] advisor(opus) 참여 의심 셀 {advisor_count}개 — 48 측정셀은 advisor 비활성으로 "
              "spawn 해야 함(opus 오염 금지). 측정셀이면 재실행 검토.", file=sys.stderr)


if __name__ == "__main__":
    main()
