#!/usr/bin/env python3
"""
stats_governance.py — workflow-combos 벤치마크 통계·거버넌스 집계기
=======================================================================

역할(차원: 통계·거버넌스):
  1) metrics-combos.csv(48행) 파싱 + N=3 조합×케이스 집계
     (중앙값 / 평균±SD / min-max — recall / precision / F1)
  2) 거버넌스 감사 (tool_use 블록 기반 — 프롬프트 텍스트 오탐 방지)
     - model = 'sonnet' 확인
     - 허용 도구 외 사용(viol_forbidden_tool) = 0
     - evals/ 스코프 위반(viol_scope_evals) = 0  ← Bash/rg/zoekt/Read 인자 경로 감사
     - 셀 내 인덱싱(viol_indexing_in_cell) = 0   ← tool_use input 에서 zoekt-index 탐지
     - C4 에서 serena 사용(viol_serena_in_c4) = 0 ← tool_use.name 감사
  3) 결정적 grader (file:line 거리 ≤1, Kuhn 최대이분매칭 — exact 우선, ±2 불허)
     ※ grader.py(_max_bipartite_match) 와 동일 알고리즘으로 통일해 두 채점기의
       frozen 수치 분기를 차단(테스트10b 교차검증). 그리디 2-패스는 ±1 충돌
       클러스터에서 과소산출하므로 폐기.
     - M3-W: Kotlin/Rust 분리 subscore 후 합산
     - M2: test_tail 별도 subscore
     - adjudication 필요 목록 분리 출력 (opus 판정 대상)
  4) CSV 스키마 참조 출력

사용:
  # A. 채점 — answers JSON + dataset gold 대조
  python3 stats_governance.py grade \
      --answers answers_C1_M1_r1.json \
      --case M1 --combo C1 --run 1

  # B. CSV 집계 + 거버넌스 감사
  python3 stats_governance.py aggregate \
      --csv metrics-combos.csv --out-dir .

  # C. CSV 스키마 출력
  python3 stats_governance.py schema

  # D. 빈 48행 CSV 템플릿 생성
  python3 stats_governance.py make-empty-csv --output metrics-combos.csv

표준 라이브러리만 사용(yaml/numpy/pandas 불필요).
dataset.yaml 의 gold 는 인라인 상수(GOLD_DATA)로 내장 — PyYAML 의존 없음.
"""

from __future__ import annotations

import argparse
import csv
import json
import math
import os
import re
import sys
from dataclasses import dataclass
from pathlib import Path
from typing import Any

# =============================================================================
# §0  CSV 컬럼 스키마 (확정)
# =============================================================================

CSV_COLUMNS = [
    # ── 식별자 ──────────────────────────────────────────────────────────────
    "combo",          # C1 | C2 | C3 | C4
    "case",           # M1 | M2 | M3-W | M3-L
    "run",            # 1 | 2 | 3  (N=3 반복 인덱스)
    # ── 품질 지표 ───────────────────────────────────────────────────────────
    "recall",         # float 0-1  (결정적 grader 산출, 동결)
    "precision",      # float 0-1  (동결; 제출 0건이면 NaN → 집계 제외)
    "f1",             # float 0-1  (2*P*R/(P+R), P=NaN 이면 NaN)
    "trap_incl",      # int  — 함정(traps_must_exclude) 포함 건수
    "halluc",         # int  — 환각(gold+trap 에 없는 답) 건수
    # ── 토큰/비용 ───────────────────────────────────────────────────────────
    "out_tok",        # int  — 출력 토큰
    "cacheR",         # int  — 캐시읽기 토큰
    "cacheC",         # int  — 캐시생성 토큰
    # ── 효율 ────────────────────────────────────────────────────────────────
    "tool_calls",     # int  — 도구 호출 수 (answers.tool_calls_count + 하니스 집계)
    "wall_sec",       # float — wall-clock(초)
    # ── 거버넌스 ────────────────────────────────────────────────────────────
    "model",          # str  — 서브에이전트 모델 (sonnet 이어야 함)
    "viol_indexing_in_cell",  # int 0|1 — 셀 내 zoekt-index 호출 여부 (tool_use input 기반)
    "viol_forbidden_tool",    # int 0|1 — 허용 도구 외 사용 여부 (tool_use name 기반)
    "viol_scope_evals",       # int 0|1 — evals/ 경로 검색 여부 (tool_use input 인자 기반)
    "viol_serena_in_c4",      # int 0|1 — C4 에서 serena 사용 여부 (tool_use name 기반)
]

# =============================================================================
# §1  gold 데이터 (dataset.yaml 인라인 상수 — PyYAML 불필요)
# =============================================================================
# 출처: evals/codegraph-vs-serena/dataset.yaml (git v1.12.1, 1daf879)
# 변경 시 이 상수를 직접 갱신하고 sha 를 기록한다.

# M1: file:line (callsite 33 + trap 2)
_M1_GOLD_CALLSITES: list[str] = [
    "src/main/kotlin/com/livteam/jsoninja/services/JsonFormatterService.kt:215",
    "src/main/kotlin/com/livteam/jsoninja/services/JsonDiffService.kt:102",
    "src/main/kotlin/com/livteam/jsoninja/extensions/JsoninjaPastePreProcessor.kt:48",
    "src/main/kotlin/com/livteam/jsoninja/services/typeConversion/TypeToJsonGenerationService.kt:43",
    "src/main/kotlin/com/livteam/jsoninja/diff/JsonDiffExtension.kt:412",
    "src/main/kotlin/com/livteam/jsoninja/actions/GenerateRandomJsonAction.kt:60",
    "src/main/kotlin/com/livteam/jsoninja/actions/editor/EditorPrettifyJsonAction.kt:13",
    "src/main/kotlin/com/livteam/jsoninja/actions/editor/EditorUglifyJsonAction.kt:13",
    "src/main/kotlin/com/livteam/jsoninja/actions/SortJsonDiffKeysOnceAction.kt:45",
    "src/main/kotlin/com/livteam/jsoninja/actions/SortJsonDiffKeysOnceAction.kt:46",
    "src/main/kotlin/com/livteam/jsoninja/ui/component/main/JsoninjaPanelPresenter.kt:74",
    "src/main/kotlin/com/livteam/jsoninja/ui/component/main/JsoninjaPanelPresenter.kt:92",
    "src/test/kotlin/com/livteam/jsoninja/services/JsonFormatterServiceTest.kt:18",
    "src/test/kotlin/com/livteam/jsoninja/services/JsonFormatterServiceTest.kt:32",
    "src/test/kotlin/com/livteam/jsoninja/services/JsonFormatterServiceTest.kt:38",
    "src/test/kotlin/com/livteam/jsoninja/services/JsonFormatterServiceTest.kt:43",
    "src/test/kotlin/com/livteam/jsoninja/services/JsonFormatterServiceTest.kt:108",
    "src/test/kotlin/com/livteam/jsoninja/services/JsonFormatterServiceTest.kt:119",
    "src/test/kotlin/com/livteam/jsoninja/services/JsonFormatterServiceTest.kt:120",
    "src/test/kotlin/com/livteam/jsoninja/services/JsonFormatterServiceTest.kt:139",
    "src/test/kotlin/com/livteam/jsoninja/services/JsonFormatterServiceTest.kt:149",
    "src/test/kotlin/com/livteam/jsoninja/services/JsonFormatterServiceTest.kt:162",
    "src/test/kotlin/com/livteam/jsoninja/services/JsonFormatterServiceTest.kt:169",
    "src/test/kotlin/com/livteam/jsoninja/services/JsonFormatterServiceTest.kt:181",
    "src/test/kotlin/com/livteam/jsoninja/services/JsonFormatterServiceTest.kt:189",
    "src/test/kotlin/com/livteam/jsoninja/services/JsonFormatterServiceTest.kt:201",
    "src/test/kotlin/com/livteam/jsoninja/services/JsonFormatterServiceTest.kt:204",
    "src/test/kotlin/com/livteam/jsoninja/services/JsonFormatterServiceTest.kt:217",
    "src/test/kotlin/com/livteam/jsoninja/services/JsonFormatterServiceTest.kt:229",
    "src/test/kotlin/com/livteam/jsoninja/services/JsonFormatterServiceTest.kt:246",
    "src/test/kotlin/com/livteam/jsoninja/services/JsonFormatterServiceTest.kt:257",
    "src/test/kotlin/com/livteam/jsoninja/services/JsonFormatterServiceTest.kt:270",
    "src/test/kotlin/com/livteam/jsoninja/services/JsonFormatterServiceTest.kt:285",
]
_M1_TRAPS: list[str] = [
    "src/main/kotlin/com/livteam/jsoninja/actions/PrettifyJsonAction.kt:18",
    "src/main/kotlin/com/livteam/jsoninja/actions/UglifyJsonAction.kt:20",
]

# M2: qualified_name (폐포 8 + trap 4)
_M2_GOLD_FUNCTIONS: list[str] = [
    "com.livteam.jsoninja.services.typeConversion.TypeDeclarationAnalyzerService.analyzeSource",
    "com.livteam.jsoninja.services.typeConversion.TypeToJsonGenerationService.generate",
    "com.livteam.jsoninja.ui.dialog.convertType.TypeToJsonDialogPresenter.schedulePreview",
    "com.livteam.jsoninja.ui.dialog.convertType.TypeToJsonDialogPresenter.<init>",
    "com.livteam.jsoninja.ui.dialog.convertType.TypeToJsonDialogPresenter.updateLanguage",
    "com.livteam.jsoninja.ui.dialog.convertType.TypeToJsonDialogPresenter.bindView",
    "com.livteam.jsoninja.ui.dialog.convertType.ConvertTypeDialogPresenter.<init>",
    "com.livteam.jsoninja.ui.dialog.convertType.ConvertTypeDialogPresenter.syncLanguage",
]
_M2_TRAPS: list[str] = [
    "com.livteam.jsoninja.services.JsonDiffService.getOrCreateContext",
    "com.livteam.jsoninja.ui.dialog.convertType.TypeToJsonDialogView.updateLanguage",
    "com.livteam.jsoninja.ui.dialog.convertType.JsonToTypeDialogPresenter.schedulePreview",
    # 반대방향 분기 — exact 매칭 불가(범주형), adjudication 으로 처리
]
# M2 test_tail — 별도 subscore (통합테스트 3종, 주요 함수 경유 여부)
_M2_TEST_TAIL_FILES: list[str] = [
    "src/test/kotlin/com/livteam/jsoninja/services/typeConversion/TypeConversionWasmIntegrationTest.kt",
    "src/test/kotlin/com/livteam/jsoninja/services/typeConversion/TypeConversionWasmIntegrationV2Test.kt",
    "src/test/kotlin/com/livteam/jsoninja/services/typeConversion/TypeConversionWasmIntegrationV3Test.kt",
]

# M3-W: file:line (kotlin 4 + rust 4 + trap 2) — 분리 채점 후 합산
_M3W_GOLD_KOTLIN: list[str] = [
    "src/main/kotlin/com/livteam/jsoninja/services/treesitter/TreeSitterWasmRuntime.kt:61",
    "src/main/kotlin/com/livteam/jsoninja/services/treesitter/TreeSitterWasmRuntime.kt:62",
    "src/main/kotlin/com/livteam/jsoninja/services/treesitter/TreeSitterWasmRuntime.kt:63",
    "src/main/kotlin/com/livteam/jsoninja/services/treesitter/TreeSitterWasmRuntime.kt:64",
]
_M3W_GOLD_RUST: list[str] = [
    "tree-sitter-wasm/src/lib.rs:25",
    "tree-sitter-wasm/src/lib.rs:30",
    "tree-sitter-wasm/src/lib.rs:92",
    "tree-sitter-wasm/src/lib.rs:121",
]
_M3W_TRAPS: list[str] = [
    "src/main/kotlin/com/livteam/jsoninja/services/treesitter/WasmMemoryBridge.kt:19",
    "src/main/kotlin/com/livteam/jsoninja/services/treesitter/WasmMemoryBridge.kt:36",
]

# M3-L: file:line (29 = main 13 + test 16)
_M3L_GOLD_SITES: list[str] = [
    "src/main/kotlin/com/livteam/jsoninja/model/SupportedLanguage.kt:77",
    "src/main/kotlin/com/livteam/jsoninja/services/typeConversion/JsonToTypeNamingSupport.kt:11",
    "src/main/kotlin/com/livteam/jsoninja/services/typeConversion/JsonToTypeRenderer.kt:53",
    "src/main/kotlin/com/livteam/jsoninja/services/typeConversion/JsonToTypeRenderer.kt:86",
    "src/main/kotlin/com/livteam/jsoninja/services/typeConversion/JsonToTypeRenderer.kt:116",
    "src/main/kotlin/com/livteam/jsoninja/settings/JsoninjaSettingsState.kt:27",
    "src/main/kotlin/com/livteam/jsoninja/settings/JsoninjaSettingsState.kt:32",
    "src/main/kotlin/com/livteam/jsoninja/ui/dialog/convertType/JsonToTypeDialogView.kt:93",
    "src/main/kotlin/com/livteam/jsoninja/ui/dialog/convertType/TypeToJsonDialogView.kt:79",
    "src/main/kotlin/com/livteam/jsoninja/ui/dialog/convertType/TypeToJsonDialogView.kt:94",
    "src/main/kotlin/com/livteam/jsoninja/ui/dialog/convertType/TypeToJsonDialogView.kt:144",
    "src/main/kotlin/com/livteam/jsoninja/ui/dialog/convertType/model/JsonToTypeDialogConfig.kt:9",
    "src/main/kotlin/com/livteam/jsoninja/ui/dialog/convertType/model/TypeToJsonDialogConfig.kt:8",
    "src/test/kotlin/com/livteam/jsoninja/services/typeConversion/TypeConversionWasmIntegrationTest.kt:94",
    "src/test/kotlin/com/livteam/jsoninja/services/typeConversion/TypeConversionWasmIntegrationTest.kt:171",
    "src/test/kotlin/com/livteam/jsoninja/services/typeConversion/TypeConversionWasmIntegrationTest.kt:182",
    "src/test/kotlin/com/livteam/jsoninja/services/typeConversion/TypeConversionWasmIntegrationTest.kt:249",
    "src/test/kotlin/com/livteam/jsoninja/services/typeConversion/TypeConversionWasmIntegrationTest.kt:279",
    "src/test/kotlin/com/livteam/jsoninja/services/typeConversion/TypeConversionWasmIntegrationTest.kt:396",
    "src/test/kotlin/com/livteam/jsoninja/services/typeConversion/TypeConversionWasmIntegrationV2Test.kt:217",
    "src/test/kotlin/com/livteam/jsoninja/services/typeConversion/TypeConversionWasmIntegrationV2Test.kt:258",
    "src/test/kotlin/com/livteam/jsoninja/services/typeConversion/TypeConversionWasmIntegrationV2Test.kt:275",
    "src/test/kotlin/com/livteam/jsoninja/services/typeConversion/TypeConversionWasmIntegrationV2Test.kt:338",
    "src/test/kotlin/com/livteam/jsoninja/services/typeConversion/TypeConversionWasmIntegrationV2Test.kt:373",
    "src/test/kotlin/com/livteam/jsoninja/services/typeConversion/TypeConversionWasmIntegrationV2Test.kt:382",
    "src/test/kotlin/com/livteam/jsoninja/services/typeConversion/TypeConversionWasmIntegrationV3Test.kt:188",
    "src/test/kotlin/com/livteam/jsoninja/services/typeConversion/TypeConversionWasmIntegrationV3Test.kt:306",
    "src/test/kotlin/com/livteam/jsoninja/services/typeConversion/TypeConversionWasmIntegrationV3Test.kt:367",
    "src/test/kotlin/com/livteam/jsoninja/services/typeConversion/TypeConversionWasmIntegrationV3Test.kt:610",
]
# M3-L 정의 지점 (recall 분모 제외 + adjudication 대상)
_M3L_DEFINITION_SITE = "src/main/kotlin/com/livteam/jsoninja/model/SupportedLanguage.kt:16"

GOLD_DATA: dict[str, dict] = {
    "M1": {
        "gold": _M1_GOLD_CALLSITES,
        "traps": _M1_TRAPS,
        "unit": "file:line",
        "total": 33,
    },
    "M2": {
        "gold": _M2_GOLD_FUNCTIONS,
        "traps": _M2_TRAPS,
        "unit": "qualified_name",
        "total": 8,
    },
    "M3-W": {
        "gold": _M3W_GOLD_KOTLIN + _M3W_GOLD_RUST,
        "gold_kotlin": _M3W_GOLD_KOTLIN,
        "gold_rust": _M3W_GOLD_RUST,
        "traps": _M3W_TRAPS,
        "unit": "file:line",
        "total": 8,
    },
    "M3-L": {
        "gold": _M3L_GOLD_SITES,
        "traps": [],  # 범주형 함정 — adjudication 으로
        "unit": "file:line",
        "total": 29,
    },
}

# =============================================================================
# §2  거버넌스: 조합별 허용 도구
# =============================================================================
# 도구 이름은 tool_use 블록의 name 필드 기준 (접두사 또는 전체 이름)

ALLOWED_TOOL_PREFIXES: dict[str, list[str]] = {
    "C1": ["Bash", "mcp__serena__", "Read"],   # Bash 내부: zoekt / rg 만
    "C2": ["mcp__serena__", "Bash", "Read"],
    "C3": ["Bash", "mcp__serena__", "Read"],   # Bash 내부: zoekt / rg / sg
    "C4": ["Bash", "Read"],                    # serena 완전 금지
    "C5": ["mcp__serena__", "Read"],           # serena + 내장 Read 만 (Bash 금지)
    "C6": ["Bash", "Read", "Grep", "Glob"],    # Read + 셸 grep/find (rg·serena·index 금지)
}

# Bash 명령 중 허용 프로그램 (첫 토큰 기준). C5 는 Bash 금지(빈 집합), C6 은 grep/find 만.
ALLOWED_BASH_COMMANDS: dict[str, set[str]] = {
    "C1": {"zoekt", "rg"},
    "C2": {"zoekt", "rg"},
    "C3": {"zoekt", "rg", "sg"},
    "C4": {"zoekt", "rg"},
    "C5": set(),
    "C6": {"grep", "find"},
}

# 셀 내 인덱싱 금지 패턴 (Bash input 에서)
INDEXING_FORBIDDEN_CMD_PATTERNS = [
    "zoekt-index",
    "zoekt_index",
]

# evals/ 스코프 위반: 검색 도구 인자에서 탐지
EVALS_SCOPE_PATTERN = re.compile(r"\bevals[/\\]")

# 셸 명령 분리자 — 각 세그먼트의 선두 프로그램을 감사하기 위함
_BASH_SEGMENT_SPLIT = re.compile(r"&&|\|\||[|;\n]")


def _extract_bash_programs(command_str: str) -> list[str]:
    """
    Bash 명령 문자열에서 실행 프로그램(각 파이프/체인 세그먼트의 선두 토큰)을 추출.
    예: 'rg foo && cat src/x' → ['rg', 'cat'],  'zoekt ... | grep' → ['zoekt', 'grep'].
    선두 env 할당(FOO=bar cmd ...)은 건너뛰고 실제 프로그램을 잡는다.
    완전한 셸 파서는 아니나(서브셸·따옴표 안 연산자는 한계) 첫 토큰만 보던
    기존 방식의 밀반입 구멍을 막는 보수적 감사다.
    """
    programs: list[str] = []
    for segment in _BASH_SEGMENT_SPLIT.split(command_str):
        tokens = segment.strip().split()
        if not tokens:
            continue
        # 선두 env 할당(KEY=VALUE) 스킵
        program = None
        for token in tokens:
            if re.match(r"^[A-Za-z_][A-Za-z0-9_]*=", token):
                continue
            program = token
            break
        if program:
            programs.append(program)
    return programs


# =============================================================================
# §3  헬퍼 함수
# =============================================================================


def _parse_file_line(entry: str) -> tuple[str, int] | None:
    """'path/to/file.kt:42' → ('path/to/file.kt', 42). 실패 시 None."""
    match = re.match(r"^(.+):(\d+)$", entry.strip())
    if not match:
        return None
    return match.group(1), int(match.group(2))


def _normalize_path(path: str, repo_root: str = "") -> str:
    """
    경로 정규화: 절대경로/'./' 프리픽스 제거, repo root 기준 상대경로로.
    gold 는 'src/...' 형태 상대경로가 정본.
    """
    path = path.strip()
    # leading ./ 제거
    if path.startswith("./"):
        path = path[2:]
    # repo_root 가 주어지면 절대경로 → 상대경로 변환
    if repo_root and os.path.isabs(path):
        try:
            path = os.path.relpath(path, repo_root)
        except ValueError:
            pass
    return path


def _file_line_distance(submitted_key: str, gold_key: str, repo_root: str = "") -> int | None:
    """
    두 'path:line' 키 간 라인 거리를 반환.
    경로가 다르면 None, 같은 경로면 abs(line_s - line_g).
    경로 정규화 후 비교.
    """
    parsed_s = _parse_file_line(submitted_key)
    parsed_g = _parse_file_line(gold_key)
    if parsed_s is None or parsed_g is None:
        return None
    path_s = _normalize_path(parsed_s[0], repo_root)
    path_g = _normalize_path(parsed_g[0], repo_root)
    # suffix 매칭: 절대경로 vs 상대경로 대응
    if path_s != path_g:
        # 한쪽이 다른 쪽의 suffix 인지 확인
        if not (path_s.endswith(path_g) or path_g.endswith(path_s)):
            return None
    return abs(parsed_s[1] - parsed_g[1])


def _f1(precision: float | None, recall: float) -> float | None:
    """F1 산출. precision=None(제출 0건) 이면 None."""
    if precision is None:
        return None
    if precision + recall == 0:
        return 0.0
    return 2 * precision * recall / (precision + recall)


def _stats(values: list[float]) -> dict[str, float | int]:
    """중앙값 / 평균 / SD / min / max."""
    if not values:
        return {"median": float("nan"), "mean": float("nan"), "sd": float("nan"),
                "min": float("nan"), "max": float("nan"), "n": 0}
    n = len(values)
    sorted_v = sorted(values)
    median = sorted_v[n // 2] if n % 2 == 1 else (sorted_v[n // 2 - 1] + sorted_v[n // 2]) / 2
    mean = sum(values) / n
    variance = sum((v - mean) ** 2 for v in values) / n if n > 1 else 0.0
    sd = math.sqrt(variance)
    return {"median": median, "mean": mean, "sd": sd, "min": min(values), "max": max(values), "n": n}


# =============================================================================
# §4  결정적 grader
# =============================================================================

@dataclass
class GradeResult:
    combo: str
    case: str
    run: int
    recall: float
    precision: float | None   # None = 제출 0건 (undefined, 집계 제외)
    f1: float | None
    trap_incl: int
    halluc: int
    hit_count: int
    miss_list: list[str]
    trap_hit_list: list[str]
    halluc_list: list[str]
    adjudication_needed: list[dict]  # opus 판정 대상
    # 케이스별 subscore
    subscore: dict  # M2: test_tail_hit / M3-W: kotlin_hit/rust_hit


def _augment_match(
    gold_index: int,
    adjacency: list[list[int]],
    submitted_match: list[int],
    visited: list[bool],
) -> bool:
    """
    Kuhn 증강경로 한 단계. gold_index 를 미배정/재배정 가능한 제출 슬롯에 연결한다.
    submitted_match[s] = 그 제출 슬롯에 현재 배정된 gold 인덱스(-1 = 미배정).
    """
    for submitted_index in adjacency[gold_index]:
        if visited[submitted_index]:
            continue
        visited[submitted_index] = True
        if (
            submitted_match[submitted_index] == -1
            or _augment_match(
                submitted_match[submitted_index], adjacency, submitted_match, visited
            )
        ):
            submitted_match[submitted_index] = gold_index
            return True
    return False


def _match_file_line_optimal(
    submitted_keys: list[str],
    gold_list: list[str],
    trap_list: list[str],
    repo_root: str = "",
) -> tuple[set[str], set[int], list[str], list[str], list[dict]]:
    """
    file:line 최대 1:1 배정 (exact 우선, ±1 허용, ±2 이상 불허).

    그리디 2-패스(舊 _match_file_line_greedy)는 두 제출이 동시에 두 gold 의 ±1
    창에 들어가는 충돌 클러스터에서 최대 cardinality 를 보장하지 못해 hit 를
    과소 산출했다(예: gold[61,62,63,64] / 제출[62,63,64,65] → greedy 3, 최적 4).
    이는 같은 케이스를 grader.py(_max_bipartite_match, Kuhn) 와 다른 frozen 수치로
    채점하게 만들어 N=3 재현성·단일 정본 계약을 깬다.
    → grader.py 와 동일하게 Kuhn 최대이분매칭으로 통일한다.

    - 각 gold 최대 1개 제출, 각 제출 최대 1개 gold (1:1).
    - 인접리스트에 exact(거리=0) 간선을 ±1 간선보다 앞에 둬, 동일 cardinality
      해 중에서 exact 를 더 많이 고르도록 결정적으로 유도한다.

    반환:
      (hit_gold_canonical_set, used_submitted_index_set,
       trap_hit_list, halluc_list, adjudication_needed)
    """
    n_gold = len(gold_list)
    n_submitted = len(submitted_keys)

    # 인접리스트: gold_index → [submitted_index, ...] (exact 먼저)
    adjacency: list[list[int]] = [[] for _ in range(n_gold)]
    pm1_pairs: set[tuple[int, int]] = set()  # 거리=1 간선(±1 경계 adjudication 기록용)
    for gold_index, gold_canonical in enumerate(gold_list):
        exact_edges: list[int] = []
        near_edges: list[int] = []
        for submitted_index, submitted_key in enumerate(submitted_keys):
            distance = _file_line_distance(submitted_key, gold_canonical, repo_root)
            if distance is None:
                continue
            if distance == 0:
                exact_edges.append(submitted_index)
            elif distance == 1:
                near_edges.append(submitted_index)
                pm1_pairs.add((gold_index, submitted_index))
        adjacency[gold_index] = exact_edges + near_edges  # exact 우선

    # Kuhn 최대이분매칭
    submitted_match: list[int] = [-1] * n_submitted  # 제출 → gold (-1=미배정)
    for gold_index in range(n_gold):
        visited = [False] * n_submitted
        _augment_match(gold_index, adjacency, submitted_match, visited)

    hit_gold: set[str] = set()
    used_submitted: set[int] = set()
    adjudication_needed: list[dict] = []
    for submitted_index, gold_index in enumerate(submitted_match):
        if gold_index == -1:
            continue
        hit_gold.add(gold_list[gold_index])
        used_submitted.add(submitted_index)
        # ±1 경계로만 붙은 쌍(exact 아님)은 엄밀 검증 권장 항목으로 기록
        if (gold_index, submitted_index) in pm1_pairs:
            distance = _file_line_distance(
                submitted_keys[submitted_index], gold_list[gold_index], repo_root
            )
            if distance == 1:
                adjudication_needed.append({
                    "reason": "±1 경계 매칭 — 엄밀 검증 권장",
                    "submitted": submitted_keys[submitted_index],
                    "gold_canonical": gold_list[gold_index],
                })

    # 미사용 제출 → 함정/환각 분류
    trap_hit_list: list[str] = []
    halluc_list: list[str] = []
    for submitted_index, submitted_key in enumerate(submitted_keys):
        if submitted_index in used_submitted:
            continue
        is_trap = False
        for trap_key in trap_list:
            dist = _file_line_distance(submitted_key, trap_key, repo_root)
            if dist is not None and dist <= 1:
                trap_hit_list.append(submitted_key)
                is_trap = True
                break
        if not is_trap:
            halluc_list.append(submitted_key)

    return hit_gold, used_submitted, trap_hit_list, halluc_list, adjudication_needed


def grade_answers(
    case_id: str,
    combo: str,
    run: int,
    answers: list[dict],
    repo_root: str = "",
) -> GradeResult:
    """
    answers 리스트 (각 원소: {"qualified_name":..., "kind":..., "file":..., "line":...})
    를 GOLD_DATA 와 대조해 recall/precision/F1 을 산출한다.

    - file:line 단위(M1/M3-W/M3-L): 거리 ≤1, exact 우선, 1:1 최적 배정
    - qualified_name 단위(M2): 정확 매칭; 부분일치 → adjudication
    """
    gold_info = GOLD_DATA[case_id]
    gold_list: list[str] = gold_info["gold"]
    trap_list: list[str] = gold_info["traps"]
    unit: str = gold_info["unit"]
    total: int = gold_info["total"]

    adjudication_needed: list[dict] = []
    subscore: dict = {}

    # ── 제출 키 추출 ──────────────────────────────────────────────────────
    submitted_keys: list[str] = []
    for ans in answers:
        if unit == "file:line":
            file_val = _normalize_path(ans.get("file", ""), repo_root)
            line_val = ans.get("line")
            if not file_val or line_val is None:
                adjudication_needed.append({
                    "reason": "file 또는 line 누락 — 출력 스키마 위반(file 필드 필수, [실측 교정 §3] 참조)",
                    "answer": ans,
                })
                continue
            submitted_keys.append(f"{file_val}:{line_val}")
        else:  # M2 qualified_name
            submitted_key = ans.get("qualified_name", "").strip()
            if not submitted_key:
                adjudication_needed.append({"reason": "qualified_name 누락", "answer": ans})
                continue
            submitted_keys.append(submitted_key)

    # ── 매칭 ─────────────────────────────────────────────────────────────
    if unit == "file:line":
        hit_gold, _used, trap_hit_list, halluc_list, adj = _match_file_line_optimal(
            submitted_keys, gold_list, trap_list, repo_root
        )
        adjudication_needed.extend(adj)

        # M3-L: definition_site 제출 → adjudication (precision 분모 포함 여부)
        if case_id == "M3-L":
            for submitted_key in submitted_keys:
                dist = _file_line_distance(submitted_key, _M3L_DEFINITION_SITE, repo_root)
                if dist is not None and dist <= 1:
                    adjudication_needed.append({
                        "reason": "M3-L definition_site 제출(:16) — recall 분모 제외. precision 분모 포함 여부 opus 판정 필요",
                        "submitted": submitted_key,
                        "definition_site": _M3L_DEFINITION_SITE,
                    })

        # M3-W: Kotlin / Rust 분리 subscore
        if case_id == "M3-W":
            kotlin_hit, _, _, _, _ = _match_file_line_optimal(
                submitted_keys, _M3W_GOLD_KOTLIN, [], repo_root
            )
            rust_hit, _, _, _, _ = _match_file_line_optimal(
                submitted_keys, _M3W_GOLD_RUST, [], repo_root
            )
            subscore = {
                "kotlin_hit": len(kotlin_hit),
                "kotlin_total": len(_M3W_GOLD_KOTLIN),
                "kotlin_recall": len(kotlin_hit) / len(_M3W_GOLD_KOTLIN),
                "rust_hit": len(rust_hit),
                "rust_total": len(_M3W_GOLD_RUST),
                "rust_recall": len(rust_hit) / len(_M3W_GOLD_RUST),
            }

    else:  # M2 qualified_name
        hit_gold = set()
        trap_hit_list = []
        halluc_list = []

        for submitted_key in submitted_keys:
            if submitted_key in gold_list:
                hit_gold.add(submitted_key)
            else:
                # 함정 exact 매칭
                is_trap = any(submitted_key == trap for trap in trap_list)
                if is_trap:
                    trap_hit_list.append(submitted_key)
                    continue
                # 부분일치 → adjudication
                partial_candidates = [g for g in gold_list if (submitted_key in g or g in submitted_key)]
                if partial_candidates:
                    adjudication_needed.append({
                        "reason": "M2 qualified_name 부분일치 — opus 판정 필요",
                        "submitted": submitted_key,
                        "gold_candidates": partial_candidates,
                    })
                else:
                    # M2 <init> 동치 후보
                    init_candidates = [
                        g for g in gold_list
                        if "<init>" in g and g.rsplit(".", 1)[0] in submitted_key
                    ]
                    if init_candidates:
                        adjudication_needed.append({
                            "reason": "M2 <init> 동치 후보 — 에이전트가 생성자를 부모 클래스 이름으로 제출",
                            "submitted": submitted_key,
                            "gold_init_candidates": init_candidates,
                        })
                    else:
                        halluc_list.append(submitted_key)

        # M2 test_tail subscore
        test_tail_mentioned = []
        for ans in answers:
            file_val = ans.get("file", "") or ""
            qn = ans.get("qualified_name", "") or ""
            for tail_file in _M2_TEST_TAIL_FILES:
                tail_stem = os.path.splitext(os.path.basename(tail_file))[0]
                if tail_stem in file_val or tail_stem in qn:
                    if tail_file not in test_tail_mentioned:
                        test_tail_mentioned.append(tail_file)
        subscore = {
            "test_tail_mentioned": test_tail_mentioned,
            "test_tail_hit": len(test_tail_mentioned),
            "test_tail_total": 3,
        }

    # ── 산출 ──────────────────────────────────────────────────────────────
    hit_count = len(hit_gold)
    recall = hit_count / total if total > 0 else 0.0
    submitted_total = len(submitted_keys)
    if submitted_total == 0:
        precision = None  # undefined (집계에서 NaN 처리)
    else:
        # [정책: frozen precision 분모 확정]
        # M2 adjudication-pending 항목(부분일치/<init> 동치 후보)도 submitted_total 분모에 포함된다.
        # 즉 "frozen precision = pending을 miss로 간주"한 값으로 동결(frozen)한다.
        # opus 가 pending을 hit로 판정하면 adjudicated_precision 을 별도 산출하고,
        # 이 frozen 수치는 변경하지 않는다 (N=3 재현성 보호).
        precision = hit_count / submitted_total

    computed_f1 = _f1(precision, recall)
    miss_list = [g for g in gold_list if g not in hit_gold]

    return GradeResult(
        combo=combo,
        case=case_id,
        run=run,
        recall=round(recall, 6),
        precision=round(precision, 6) if precision is not None else None,
        f1=round(computed_f1, 6) if computed_f1 is not None else None,
        trap_incl=len(trap_hit_list),
        halluc=len(halluc_list),
        hit_count=hit_count,
        miss_list=miss_list,
        trap_hit_list=trap_hit_list,
        halluc_list=halluc_list,
        adjudication_needed=adjudication_needed,
        subscore=subscore,
    )


# =============================================================================
# §5  거버넌스 감사 (tool_use 블록 기반)
# =============================================================================

@dataclass
class ToolUseEvent:
    """transcript 에서 파싱된 도구 호출 한 건."""
    tool_name: str
    input_text: str  # JSON 직렬화 문자열


def extract_tool_use_events(transcript_text: str) -> list[ToolUseEvent]:
    """
    jsonl transcript 에서 tool_use 블록을 추출.
    반환: ToolUseEvent 리스트 (도구명 + input 직렬화 문자열)

    두 가지 형태 지원:
    1) Agent 도구 transcript (jsonl, content 배열 내 type=tool_use)
    2) Workflow transcript (유사 구조)
    """
    events: list[ToolUseEvent] = []
    for line in transcript_text.splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            obj = json.loads(line)
        except json.JSONDecodeError:
            continue

        content_blocks = (
            obj.get("content")
            or obj.get("message", {}).get("content")
            or []
        )
        if not isinstance(content_blocks, list):
            continue
        for block in content_blocks:
            if not isinstance(block, dict):
                continue
            if block.get("type") == "tool_use":
                tool_name = block.get("name", "")
                tool_input = block.get("input") or {}
                input_text = json.dumps(tool_input, ensure_ascii=False)
                events.append(ToolUseEvent(tool_name=tool_name, input_text=input_text))

    return events


@dataclass
class GovernanceResult:
    combo: str
    case: str
    run: int
    model: str
    viol_indexing_in_cell: int   # 0 | 1
    viol_forbidden_tool: int     # 0 | 1
    viol_scope_evals: int        # 0 | 1
    viol_serena_in_c4: int       # 0 | 1
    model_ok: bool
    details: list[str]           # 위반 상세 메시지


def audit_governance(
    combo: str,
    case: str,
    run: int,
    model: str,
    tool_use_events: list[ToolUseEvent],
) -> GovernanceResult:
    """
    tool_use_events: extract_tool_use_events() 로 파싱된 도구 호출 목록.
    raw transcript 텍스트가 아닌 tool_use 블록만 감사 → 프롬프트 텍스트 오탐 방지.
    """
    details: list[str] = []

    model_ok = "sonnet" in model.lower()
    if not model_ok:
        details.append(f"모델 비준수: {model!r} (sonnet 이어야 함)")

    viol_indexing = 0
    viol_forbidden = 0
    viol_scope = 0
    viol_serena_c4 = 0

    allowed_prefixes = ALLOWED_TOOL_PREFIXES.get(combo, [])
    allowed_bash_cmds = ALLOWED_BASH_COMMANDS.get(combo, set())

    for event in tool_use_events:
        name = event.tool_name
        input_text = event.input_text

        # ① 셀 내 인덱싱 금지 — Bash 명령에서 zoekt-index 탐지
        if name == "Bash":
            for forbidden_cmd in INDEXING_FORBIDDEN_CMD_PATTERNS:
                if forbidden_cmd in input_text:
                    viol_indexing = 1
                    details.append(f"셀 내 인덱싱 호출 감지: '{forbidden_cmd}' (Bash input)")
                    break

        # ② 허용 도구 외 사용
        is_allowed = any(name.startswith(prefix) for prefix in allowed_prefixes)
        if not is_allowed:
            viol_forbidden = 1
            details.append(f"금지 도구 사용: tool_name={name!r} (combo={combo})")

        # Bash 내부 허용 명령 감사 — 모든 세그먼트의 선두 프로그램을 검사한다.
        # 첫 토큰만 보면 `rg foo && cat src/...`, `zoekt ...; sg ...`, `zoekt ... | grep`
        # 처럼 셸 연산자로 금지 프로그램(특히 소스 직접열람 cat/sed)을 밀반입할 수 있다.
        if name == "Bash" and allowed_bash_cmds:
            try:
                cmd_input = json.loads(event.input_text)
                command_str = cmd_input.get("command", "") or ""
            except (json.JSONDecodeError, AttributeError):
                command_str = input_text
            for program in _extract_bash_programs(command_str):
                basename_token = os.path.basename(program)
                if program not in allowed_bash_cmds and basename_token not in allowed_bash_cmds:
                    viol_forbidden = 1
                    details.append(
                        f"Bash 금지 명령 감지: program={program!r} "
                        f"(combo={combo} 허용={allowed_bash_cmds})"
                    )

        # ③ evals/ 스코프 위반 — Bash/Read/rg/zoekt 인자에서 탐지
        if EVALS_SCOPE_PATTERN.search(input_text):
            viol_scope = 1
            details.append(f"evals/ 스코프 위반 감지: tool={name!r}")

        # ④ C4 에서 serena 사용
        if combo == "C4" and name.startswith("mcp__serena__"):
            viol_serena_c4 = 1
            details.append(f"C4 에서 serena 사용 감지: tool={name!r}")

    return GovernanceResult(
        combo=combo,
        case=case,
        run=run,
        model=model,
        viol_indexing_in_cell=viol_indexing,
        viol_forbidden_tool=viol_forbidden,
        viol_scope_evals=viol_scope,
        viol_serena_in_c4=viol_serena_c4,
        model_ok=model_ok,
        details=details,
    )


# =============================================================================
# §6  transcript 파서 (토큰 + tool_use)
# =============================================================================

def parse_transcript(transcript_path: str) -> tuple[dict[str, int], list[ToolUseEvent]]:
    """
    서브에이전트 transcript(jsonl)에서 토큰 사용량 + tool_use 이벤트 추출.
    반환: ({"out_tok": int, "cacheR": int, "cacheC": int, "tool_calls": int}, [ToolUseEvent, ...])
    """
    token_result = {"out_tok": 0, "cacheR": 0, "cacheC": 0, "tool_calls": 0}
    events: list[ToolUseEvent] = []

    if not os.path.isfile(transcript_path):
        return token_result, events

    with open(transcript_path, encoding="utf-8", errors="replace") as fp:
        content = fp.read()

    for line in content.splitlines():
        line = line.strip()
        if not line:
            continue
        try:
            obj = json.loads(line)
        except json.JSONDecodeError:
            continue

        # 토큰 집계
        usage = obj.get("usage") or obj.get("message", {}).get("usage") or {}
        if usage:
            token_result["out_tok"] += usage.get("output_tokens", 0)
            token_result["cacheR"] += usage.get("cache_read_input_tokens", 0)
            token_result["cacheC"] += usage.get("cache_creation_input_tokens", 0)

        # tool_use 추출
        content_blocks = (
            obj.get("content")
            or obj.get("message", {}).get("content")
            or []
        )
        if isinstance(content_blocks, list):
            for block in content_blocks:
                if isinstance(block, dict) and block.get("type") == "tool_use":
                    tool_name = block.get("name", "")
                    tool_input = block.get("input") or {}
                    input_text = json.dumps(tool_input, ensure_ascii=False)
                    events.append(ToolUseEvent(tool_name=tool_name, input_text=input_text))
                    token_result["tool_calls"] += 1

    return token_result, events


# =============================================================================
# §7  CSV 행 병합 + 집계
# =============================================================================

@dataclass
class CsvRow:
    combo: str
    case: str
    run: int
    recall: float
    precision: float    # NaN = undefined (제출 0건)
    f1: float           # NaN = undefined
    trap_incl: int
    halluc: int
    out_tok: int
    cacheR: int
    cacheC: int
    tool_calls: int
    wall_sec: float
    model: str
    viol_indexing_in_cell: int
    viol_forbidden_tool: int
    viol_scope_evals: int
    viol_serena_in_c4: int

    def to_dict(self) -> dict:
        row = {col: getattr(self, col) for col in CSV_COLUMNS}
        # NaN → 빈 문자열로 CSV 직렬화 (집계 시 NaN 유지)
        for float_col in ["recall", "precision", "f1", "wall_sec"]:
            val = row[float_col]
            if isinstance(val, float) and math.isnan(val):
                row[float_col] = ""
        return row


def merge_to_csv_row(
    grade: GradeResult,
    gov: GovernanceResult,
    out_tok: int = 0,
    cacheR: int = 0,
    cacheC: int = 0,
    tool_calls: int = 0,
    wall_sec: float = 0.0,
) -> CsvRow:
    """GradeResult + GovernanceResult + 토큰 메트릭 → CsvRow."""
    return CsvRow(
        combo=grade.combo,
        case=grade.case,
        run=grade.run,
        recall=grade.recall,
        precision=grade.precision if grade.precision is not None else float("nan"),
        f1=grade.f1 if grade.f1 is not None else float("nan"),
        trap_incl=grade.trap_incl,
        halluc=grade.halluc,
        out_tok=out_tok,
        cacheR=cacheR,
        cacheC=cacheC,
        tool_calls=tool_calls,
        wall_sec=wall_sec,
        model=gov.model,
        viol_indexing_in_cell=gov.viol_indexing_in_cell,
        viol_forbidden_tool=gov.viol_forbidden_tool,
        viol_scope_evals=gov.viol_scope_evals,
        viol_serena_in_c4=gov.viol_serena_in_c4,
    )


def write_csv(rows: list[CsvRow], output_path: str) -> None:
    with open(output_path, "w", newline="", encoding="utf-8") as fp:
        writer = csv.DictWriter(fp, fieldnames=CSV_COLUMNS)
        writer.writeheader()
        for row in rows:
            writer.writerow(row.to_dict())
    print(f"[CSV] {len(rows)}행 → {output_path}")


def read_csv(input_path: str) -> list[dict]:
    rows: list[dict] = []
    with open(input_path, newline="", encoding="utf-8") as fp:
        reader = csv.DictReader(fp)
        for row in reader:
            for int_col in ["run", "trap_incl", "halluc", "out_tok", "cacheR", "cacheC",
                             "tool_calls", "viol_indexing_in_cell", "viol_forbidden_tool",
                             "viol_scope_evals", "viol_serena_in_c4"]:
                try:
                    row[int_col] = int(row[int_col])
                except (ValueError, KeyError):
                    row[int_col] = 0
            for float_col in ["recall", "precision", "f1", "wall_sec"]:
                val = row.get(float_col, "")
                if val == "" or val is None:
                    row[float_col] = float("nan")
                else:
                    try:
                        row[float_col] = float(val)
                    except ValueError:
                        row[float_col] = float("nan")
            rows.append(row)
    return rows


def aggregate_n3(rows: list[dict]) -> dict:
    """
    조합×케이스별 N=3 집계.
    NaN(precision undefined) 은 집계에서 제외.
    """
    groups: dict[tuple, list[dict]] = {}
    for row in rows:
        key = (row["combo"], row["case"])
        groups.setdefault(key, []).append(row)

    result: dict = {}
    for (combo, case), group_rows in sorted(groups.items()):
        def _valid(col: str) -> list[float]:
            return [r[col] for r in group_rows if not math.isnan(r.get(col, float("nan")))]

        gov_violations = sum(
            r.get("viol_indexing_in_cell", 0) + r.get("viol_forbidden_tool", 0)
            + r.get("viol_scope_evals", 0) + r.get("viol_serena_in_c4", 0)
            for r in group_rows
        )
        result[(combo, case)] = {
            "recall": _stats(_valid("recall")),
            "precision": _stats(_valid("precision")),
            "f1": _stats(_valid("f1")),
            "governance_violations": gov_violations,
            "model_violations": sum(
                1 for r in group_rows if "sonnet" not in r.get("model", "").lower()
            ),
            "runs": len(group_rows),
            "rows": group_rows,
        }
    return result


def print_aggregate_table(aggregated: dict) -> None:
    """N=3 집계 표를 텍스트로 출력."""
    combos = ["C1", "C2", "C3", "C4", "C5", "C6"]
    cases = ["M1", "M2", "M3-W", "M3-L"]

    sep = "=" * 108
    print(f"\n{sep}")
    print("  N=3 집계 스코어보드 (중앙값 / 평균±SD / 거버넌스 위반)")
    print("  ※ 표본=3 은 매우 작다 → 평균±SD 는 '서술용'일 뿐 추론 통계 아님.")
    print("  ※ 각 지표 옆 (n=…) 은 해당 지표가 집계된 유효 표본 수. precision/f1 은")
    print("     제출 0건(NaN) 셀이 제외되므로 recall 의 n=3 과 달라질 수 있다(결측 표기).")
    print(sep)
    hdr = (
        f"{'조합':4s} {'케이스':6s} {'runs':4s}  "
        f"{'recall 중앙(n)':16s} {'recall 평균±SD':16s}  "
        f"{'precision 중앙(n)':18s} {'precision 평균±SD':16s}  "
        f"{'F1 중앙(n)':12s}  {'거버넌스':10s}"
    )
    print(hdr)
    print("-" * 108)

    for combo in combos:
        for case in cases:
            key = (combo, case)
            if key not in aggregated:
                print(f"{combo:4s} {case:6s}  —  (데이터 없음)")
                continue
            data = aggregated[key]
            r = data["recall"]
            p = data["precision"]
            f = data["f1"]
            gov = data["governance_violations"]
            model_v = data["model_violations"]
            runs = data["runs"]  # 총 반복 수(셀 수). 지표별 유효 n 은 아래 stats["n"].

            def _fmt_med(stats_dict: dict) -> str:
                v = stats_dict.get("median", float("nan"))
                med = f"{v:.3f}" if not math.isnan(v) else "–"
                return f"{med}(n={stats_dict.get('n', 0)})"

            def _fmt_meansd(stats_dict: dict) -> str:
                mean = stats_dict.get("mean", float("nan"))
                if math.isnan(mean):
                    return "–"
                # n<=1 이면 SD 추정 불가 → '±n/a' 로 명시(0.000 이 '완전일치'로 오독되지 않도록)
                if stats_dict.get("n", 0) <= 1:
                    return f"{mean:.3f}±n/a"
                return f"{mean:.3f}±{stats_dict.get('sd', float('nan')):.3f}"

            r_med, r_meansd = _fmt_med(r), _fmt_meansd(r)
            p_med, p_meansd = _fmt_med(p), _fmt_meansd(p)
            f_med = _fmt_med(f)
            gov_str = f"{gov}{'⚠' if gov > 0 else ''} (model:{model_v})"

            # 결측 경고: precision/f1 의 유효 n 이 recall 의 유효 n 보다 작으면 표시
            missing_flag = ""
            if p.get("n", 0) < r.get("n", 0):
                missing_flag = " ⚠P결측"

            print(
                f"{combo:4s} {case:6s} {runs:4d}  "
                f"{r_med:16s} {r_meansd:16s}  "
                f"{p_med:18s} {p_meansd:16s}  "
                f"{f_med:12s}  {gov_str}{missing_flag}"
            )

    print("-" * 108)
    print("  SD = 모표준편차(population, ddof=0), n≤1 은 '±n/a'. 표본=3 한계상 서술 통계로만 해석.")
    print()


def print_governance_summary(rows: list[dict]) -> None:
    """거버넌스 감사 요약."""
    total = len(rows)
    viol_model = [r for r in rows if "sonnet" not in r.get("model", "").lower()]
    viol_index = [r for r in rows if r.get("viol_indexing_in_cell", 0)]
    viol_tool = [r for r in rows if r.get("viol_forbidden_tool", 0)]
    viol_scope = [r for r in rows if r.get("viol_scope_evals", 0)]
    viol_serena = [r for r in rows if r.get("viol_serena_in_c4", 0)]

    print("\n" + "=" * 60)
    print("  거버넌스 감사 요약 (tool_use 블록 기반)")
    print("=" * 60)
    print(f"전체 셀: {total}행")
    _report_violation("model≠sonnet", viol_model)
    _report_violation("셀 내 인덱싱(viol_indexing_in_cell)", viol_index)
    _report_violation("금지 도구(viol_forbidden_tool)", viol_tool)
    _report_violation("evals/ 스코프(viol_scope_evals)", viol_scope)
    _report_violation("C4 serena 사용(viol_serena_in_c4)", viol_serena)
    print()


def _report_violation(label: str, violations: list[dict]) -> None:
    if not violations:
        print(f"  ✓ {label}: 0")
    else:
        print(f"  ✗ {label}: {len(violations)}건")
        for v in violations:
            print(f"      → combo={v.get('combo')} case={v.get('case')} run={v.get('run')}")


# =============================================================================
# §8  CLI 진입점
# =============================================================================

def cmd_grade(args: argparse.Namespace) -> None:
    """단일 셀 채점 — answers JSON 파일 입력."""
    with open(args.answers, encoding="utf-8") as fp:
        payload = json.load(fp)

    answers = payload.get("answers", [])
    repo_root = args.repo_root or ""
    result = grade_answers(
        case_id=args.case,
        combo=args.combo,
        run=args.run,
        answers=answers,
        repo_root=repo_root,
    )

    # f-string 포맷 스펙 안에서 조건식을 쓰면 ValueError → 미리 계산
    prec_str = f"{result.precision:.4f}" if result.precision is not None else "NaN(undefined)"
    f1_str = f"{result.f1:.4f}" if result.f1 is not None else "NaN"

    print(f"\n[채점 결과] combo={result.combo} case={result.case} run={result.run}")
    print(f"  recall={result.recall:.4f}  precision={prec_str}  F1={f1_str}")
    print(f"  적중={result.hit_count}/{GOLD_DATA[args.case]['total']}  "
          f"함정포함={result.trap_incl}  환각={result.halluc}")

    if result.subscore:
        print(f"  subscore: {result.subscore}")
    if result.miss_list:
        print(f"  누락 gold({len(result.miss_list)}건): {result.miss_list[:5]}"
              f"{'...' if len(result.miss_list) > 5 else ''}")
    if result.adjudication_needed:
        print(f"\n[adjudication 필요 — opus 판정 대상] {len(result.adjudication_needed)}건:")
        for item in result.adjudication_needed:
            print(f"  • {item}")


def cmd_aggregate(args: argparse.Namespace) -> None:
    """metrics-combos.csv 읽어 N=3 집계 + 거버넌스 감사 출력."""
    rows = read_csv(args.csv)
    if not rows:
        print(f"[오류] {args.csv} 가 비어있거나 읽을 수 없음.")
        sys.exit(1)

    aggregated = aggregate_n3(rows)
    print_aggregate_table(aggregated)
    print_governance_summary(rows)

    if args.out_dir:
        out_path = os.path.join(args.out_dir, "aggregate-combos.json")

        def _nan_to_none(obj: Any) -> Any:
            if isinstance(obj, float) and math.isnan(obj):
                return None
            if isinstance(obj, dict):
                return {k: _nan_to_none(v) for k, v in obj.items()}
            if isinstance(obj, list):
                return [_nan_to_none(v) for v in obj]
            return obj

        serializable: dict = {}
        for (combo, case), data in aggregated.items():
            key_str = f"{combo}×{case}"
            serializable[key_str] = _nan_to_none({k: v for k, v in data.items() if k != "rows"})
        with open(out_path, "w", encoding="utf-8") as fp:
            json.dump(serializable, fp, ensure_ascii=False, indent=2)
        print(f"[집계 JSON] → {out_path}")


def cmd_schema(_args: argparse.Namespace) -> None:
    """metrics-combos.csv 스키마 출력."""
    print("\nmetrics-combos.csv 컬럼 스키마 (48행 — 4조합 × 4케이스 × 3회)")
    print("=" * 72)
    descriptions = {
        "combo": "C1|C2|C3|C4",
        "case": "M1|M2|M3-W|M3-L",
        "run": "1|2|3 — N=3 반복 인덱스",
        "recall": "float 0-1 (결정적 grader 산출, 동결)",
        "precision": "float 0-1 (동결); 제출 0건이면 빈 셀(NaN, 집계 제외)",
        "f1": "float 0-1; precision=NaN 이면 빈 셀",
        "trap_incl": "int — 함정 포함 건수",
        "halluc": "int — 환각 건수",
        "out_tok": "int — 출력 토큰",
        "cacheR": "int — 캐시읽기 토큰",
        "cacheC": "int — 캐시생성 토큰",
        "tool_calls": "int — 도구 호출 수 합계",
        "wall_sec": "float — wall-clock(초)",
        "model": "str — 서브에이전트 모델 ('sonnet' 포함이어야 합격)",
        "viol_indexing_in_cell": "0|1 — 셀 내 zoekt-index 호출 (tool_use Bash input 기반)",
        "viol_forbidden_tool": "0|1 — 허용 도구 외 사용 (tool_use name 기반)",
        "viol_scope_evals": "0|1 — evals/ 경로 검색 (tool_use input 인자 기반)",
        "viol_serena_in_c4": "0|1 — C4 에서 serena 사용 (tool_use name 기반)",
    }
    for col in CSV_COLUMNS:
        print(f"  {col:<30s} {descriptions.get(col, '')}")
    print()
    print("채점 방침:")
    print("  - file:line ±1 정확 매칭(거리 ≤1, exact 우선, 1:1 배정) → 수치 동결(frozen)")
    print("  - M2 qualified_name 정확 매칭 → 수치 동결")
    print("  - adjudication 대상: M2 부분일치 / M1 enclosing-function / ±1 경계 / M3-L definition_site")
    print("  - opus 는 adjudication 케이스만 판정; 기계적 hit 재채점 금지(N=3 재현성)")
    print("  - M3-W: Kotlin/Rust 분리 subscore 후 합산")
    print("  - M2: test_tail 별도 subscore (통합테스트 3종)")
    print("  - 거버넌스 감사는 tool_use 블록 기반(프롬프트 텍스트 오탐 방지)")
    print()


def cmd_make_empty_csv(args: argparse.Namespace) -> None:
    """빈 48행 CSV 템플릿 생성."""
    combos = ["C1", "C2", "C3", "C4", "C5", "C6"]
    cases = ["M1", "M2", "M3-W", "M3-L"]
    rows: list[CsvRow] = []
    for combo in combos:
        for case in cases:
            for run in [1, 2, 3]:
                rows.append(CsvRow(
                    combo=combo, case=case, run=run,
                    recall=float("nan"), precision=float("nan"), f1=float("nan"),
                    trap_incl=0, halluc=0,
                    out_tok=0, cacheR=0, cacheC=0,
                    tool_calls=0, wall_sec=0.0,
                    model="",
                    viol_indexing_in_cell=0,
                    viol_forbidden_tool=0,
                    viol_scope_evals=0,
                    viol_serena_in_c4=0,
                ))
    out_path = args.output or "metrics-combos.csv"
    write_csv(rows, out_path)
    print(f"[템플릿] 빈 48행 CSV → {out_path}")


# =============================================================================
# §9  self-test (간이)
# =============================================================================

def _run_self_tests() -> None:
    """핵심 매칭 로직 자가 검증."""
    print("[자가 검증 시작]")
    errors: list[str] = []

    # 테스트 1: ±1 이 아닌 ±2 는 miss 여야 한다
    submitted = [
        {"file": "src/main/kotlin/com/livteam/jsoninja/services/JsonFormatterService.kt",
         "line": 217, "qualified_name": "x", "kind": "callsite"}
    ]
    result = grade_answers("M1", "C1", 1, submitted)
    # line 215 gold, submitted 217 → 거리=2 → miss
    if result.hit_count != 0:
        errors.append(f"테스트1 실패: ±2 거리가 hit 됨 (hit_count={result.hit_count}, 기대=0)")
    else:
        print("  ✓ 테스트1: ±2 거리는 miss")

    # 테스트 2: 인접 gold (SortJsonDiffKeysOnceAction:45 / :46) — 둘 다 제출 → 둘 다 hit
    submitted2 = [
        {"file": "src/main/kotlin/com/livteam/jsoninja/actions/SortJsonDiffKeysOnceAction.kt",
         "line": 45, "qualified_name": "x", "kind": "callsite"},
        {"file": "src/main/kotlin/com/livteam/jsoninja/actions/SortJsonDiffKeysOnceAction.kt",
         "line": 46, "qualified_name": "x", "kind": "callsite"},
    ]
    result2 = grade_answers("M1", "C1", 1, submitted2)
    if result2.hit_count != 2:
        errors.append(
            f"테스트2 실패: 인접 gold :45/:46 둘 다 hit 기대 → hit_count={result2.hit_count}, "
            f"halluc={result2.halluc}"
        )
    else:
        print("  ✓ 테스트2: 인접 gold :45/:46 둘 다 적중")

    # 테스트 3: trap 제출 → trap_incl=1, halluc=0
    submitted3 = [
        {"file": "src/main/kotlin/com/livteam/jsoninja/actions/PrettifyJsonAction.kt",
         "line": 18, "qualified_name": "x", "kind": "callsite"}
    ]
    result3 = grade_answers("M1", "C1", 1, submitted3)
    if result3.trap_incl != 1 or result3.halluc != 0:
        errors.append(
            f"테스트3 실패: trap 제출 → trap_incl={result3.trap_incl} halluc={result3.halluc} "
            f"(기대 trap_incl=1 halluc=0)"
        )
    else:
        print("  ✓ 테스트3: 함정 정확 분류")

    # 테스트 4: M3-W Kotlin/Rust 분리 subscore
    submitted4 = [
        {"file": "src/main/kotlin/com/livteam/jsoninja/services/treesitter/TreeSitterWasmRuntime.kt",
         "line": 61, "qualified_name": "x", "kind": "callsite"},
        {"file": "tree-sitter-wasm/src/lib.rs",
         "line": 25, "qualified_name": "x", "kind": "callsite"},
    ]
    result4 = grade_answers("M3-W", "C1", 1, submitted4)
    if result4.subscore.get("kotlin_hit") != 1 or result4.subscore.get("rust_hit") != 1:
        errors.append(
            f"테스트4 실패: M3-W kotlin_hit={result4.subscore.get('kotlin_hit')} "
            f"rust_hit={result4.subscore.get('rust_hit')} (기대 각 1)"
        )
    else:
        print("  ✓ 테스트4: M3-W Kotlin/Rust 분리 subscore 정상")

    # 테스트 5: 거버넌스 — C4 에서 serena 호출 → viol_serena_c4=1
    events5 = [ToolUseEvent(tool_name="mcp__serena__find_symbol", input_text='{"name": "foo"}')]
    gov5 = audit_governance("C4", "M1", 1, "claude-sonnet-4-6", events5)
    if gov5.viol_serena_in_c4 != 1:
        errors.append(f"테스트5 실패: C4 serena 감지 → viol_serena_in_c4={gov5.viol_serena_in_c4} (기대=1)")
    else:
        print("  ✓ 테스트5: C4 serena 사용 감지")

    # 테스트 6: 거버넌스 — evals/ 경로가 프롬프트에 있어도 tool_use 가 없으면 위반 아님
    events6: list[ToolUseEvent] = []  # tool_use 없음
    gov6 = audit_governance("C1", "M1", 1, "claude-sonnet-4-6", events6)
    if gov6.viol_scope_evals != 0:
        errors.append(f"테스트6 실패: tool_use 없을 때 viol_scope_evals={gov6.viol_scope_evals} (기대=0)")
    else:
        print("  ✓ 테스트6: tool_use 없으면 evals/ 오탐 없음")

    # 테스트 7: cmd_grade CLI 경로 스모크 (f-string 버그 재발 방지)
    import tempfile
    smoke_answers = {
        "answers": [
            {"file": "src/main/kotlin/com/livteam/jsoninja/services/JsonDiffService.kt",
             "line": 102, "qualified_name": "x", "kind": "callsite"}
        ],
        "reasoning_brief": "smoke test",
        "tool_calls_count": 1,
    }
    with tempfile.NamedTemporaryFile(
        mode="w", suffix=".json", delete=False, encoding="utf-8"
    ) as tmp:
        json.dump(smoke_answers, tmp)
        tmp_path = tmp.name

    import io
    from contextlib import redirect_stdout
    import argparse as _ap
    fake_args = _ap.Namespace(
        answers=tmp_path, case="M1", combo="C1", run=1, repo_root=""
    )
    buf = io.StringIO()
    try:
        with redirect_stdout(buf):
            cmd_grade(fake_args)
        output = buf.getvalue()
        if "recall=" not in output:
            errors.append("테스트7 실패: cmd_grade 출력에 'recall=' 없음")
        else:
            print("  ✓ 테스트7: cmd_grade CLI 경로 정상 실행(f-string 크래시 없음)")
    except Exception as exc:  # noqa: BLE001
        errors.append(f"테스트7 실패: cmd_grade 크래시 → {exc}")
    finally:
        os.unlink(tmp_path)

    # precision=None 경로 스모크 (제출 0건)
    fake_args_empty = _ap.Namespace(
        answers="/dev/null", case="M1", combo="C1", run=1, repo_root=""
    )
    try:
        # /dev/null 은 빈 JSON 이 아님 → 직접 answers=[] 로 호출
        result_empty = grade_answers("M1", "C1", 1, [], "")
        prec_str_e = f"{result_empty.precision:.4f}" if result_empty.precision is not None else "NaN(undefined)"
        f1_str_e = f"{result_empty.f1:.4f}" if result_empty.f1 is not None else "NaN"
        if prec_str_e != "NaN(undefined)":
            errors.append(f"테스트8 실패: precision=None 시 prec_str={prec_str_e!r}")
        else:
            print("  ✓ 테스트8: precision=None → 'NaN(undefined)' 포맷 정상")
    except Exception as exc:  # noqa: BLE001
        errors.append(f"테스트8 실패: {exc}")

    # 테스트 9: 그리디 과소산출 반례 — gold[11,12] / 제출[12,13] → 최적 2 hit
    base9 = "src/main/kotlin/com/livteam/jsoninja/services/JsonFormatterService.kt"
    sub9 = [
        {"file": base9, "line": 215, "qualified_name": "x", "kind": "callsite"},  # exact gold:215
    ]
    # JsonFormatterService.kt 의 유일 gold 는 :215. 충돌 클러스터를 직접 자극하려면
    # _match_file_line_optimal 을 합성 입력으로 호출한다.
    hit9, used9, _, _, _ = _match_file_line_optimal(
        ["f.kt:12", "f.kt:13"], ["f.kt:11", "f.kt:12"], [], ""
    )
    if len(hit9) != 2:
        errors.append(
            f"테스트9 실패: 충돌 클러스터 gold[11,12]/제출[12,13] → hit={len(hit9)} (기대 2, "
            f"그리디 회귀)"
        )
    else:
        print("  ✓ 테스트9: ±1 충돌 클러스터 최적매칭(그리디 과소산출 반례 2건 적중)")

    # 테스트 10: 실제 M3-W 연속 4줄 gold[61-64] / 제출[62-65] → kotlin_hit=4 +
    #            grader.py 와 수치 일치(가능 시 교차검증)
    base10 = "src/main/kotlin/com/livteam/jsoninja/services/treesitter/TreeSitterWasmRuntime.kt"
    sub10 = [
        {"file": base10, "line": L, "qualified_name": "x", "kind": "binding"}
        for L in (62, 63, 64, 65)
    ]
    r10 = grade_answers("M3-W", "C1", 1, sub10)
    if r10.subscore.get("kotlin_hit") != 4:
        errors.append(
            f"테스트10 실패: M3-W 연속줄[62-65] kotlin_hit={r10.subscore.get('kotlin_hit')} "
            f"(기대 4, 그리디면 3)"
        )
    else:
        print("  ✓ 테스트10: M3-W 연속 4줄 최적매칭(kotlin_hit=4)")
    # grader.py 교차검증(있으면) — 두 채점기 frozen 수치 일치 보증
    try:
        import grader as _grader  # type: ignore
        if hasattr(_grader, "_max_bipartite_match"):
            g_count = len(_grader._max_bipartite_match([61, 62, 63, 64], [62, 63, 64, 65]))
            if g_count != r10.subscore.get("kotlin_hit"):
                errors.append(
                    f"테스트10b 실패: grader.py={g_count} ≠ stats={r10.subscore.get('kotlin_hit')} "
                    f"(두 채점기 frozen 수치 분기)"
                )
            else:
                print(f"  ✓ 테스트10b: grader.py 교차검증 일치(둘 다 {g_count})")
    except Exception:  # noqa: BLE001 — grader 미가용(PyYAML 등)이면 교차검증 생략
        print("  · 테스트10b: grader.py 미가용 → 교차검증 생략(스킵)")

    # 테스트 11: Bash 밀반입 차단 — 'rg foo && cat src/x' 는 금지 프로그램(cat) 감지
    events11 = [ToolUseEvent(
        tool_name="Bash",
        input_text=json.dumps({"command": "rg foo src/main && cat src/main/X.kt"}),
    )]
    gov11 = audit_governance("C1", "M1", 1, "claude-sonnet-4-6", events11)
    if gov11.viol_forbidden_tool != 1:
        errors.append(
            "테스트11 실패: 'rg && cat' 체인의 금지 프로그램(cat) 미감지 "
            f"(viol_forbidden_tool={gov11.viol_forbidden_tool}, 기대 1)"
        )
    else:
        print("  ✓ 테스트11: Bash 체인 밀반입(cat) 감지")

    # 테스트 12: 정상 Bash 체인 'zoekt ... | rg' 는 위반 아님(허용 프로그램만)
    events12 = [ToolUseEvent(
        tool_name="Bash",
        input_text=json.dumps({"command": "zoekt -index_dir .codegraph/zoekt-ctags-index 'sym:Foo' | rg KOTLIN"}),
    )]
    gov12 = audit_governance("C1", "M1", 1, "claude-sonnet-4-6", events12)
    if gov12.viol_forbidden_tool != 0:
        errors.append(
            f"테스트12 실패: 허용 프로그램만 쓴 'zoekt|rg' 가 오탐 "
            f"(viol_forbidden_tool={gov12.viol_forbidden_tool}, 기대 0)"
        )
    else:
        print("  ✓ 테스트12: 허용 프로그램 체인(zoekt|rg) 오탐 없음")

    if errors:
        print("\n[자가 검증 실패]")
        for e in errors:
            print(f"  ✗ {e}")
        sys.exit(1)
    else:
        print("[자가 검증 완료 — 전체 통과]\n")


# =============================================================================
# §10  main
# =============================================================================

def main() -> None:
    parser = argparse.ArgumentParser(
        description="workflow-combos 벤치마크 통계·거버넌스 집계기",
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    subparsers = parser.add_subparsers(dest="command", required=True)

    # grade
    p_grade = subparsers.add_parser("grade", help="단일 셀 채점")
    p_grade.add_argument("--answers", required=True, help="answers JSON 파일 경로")
    p_grade.add_argument("--case", required=True, choices=["M1", "M2", "M3-W", "M3-L"])
    p_grade.add_argument("--combo", required=True, choices=["C1", "C2", "C3", "C4", "C5", "C6"])
    p_grade.add_argument("--run", type=int, default=1)
    p_grade.add_argument("--repo-root", default="", help="repo root 절대경로 (경로 정규화 용)")
    p_grade.set_defaults(func=cmd_grade)

    # aggregate
    p_agg = subparsers.add_parser("aggregate", help="metrics-combos.csv 집계 + 거버넌스 감사")
    p_agg.add_argument("--csv", required=True)
    p_agg.add_argument("--out-dir", default=".")
    p_agg.set_defaults(func=cmd_aggregate)

    # schema
    p_schema = subparsers.add_parser("schema", help="CSV 컬럼 스키마 출력")
    p_schema.set_defaults(func=cmd_schema)

    # make-empty-csv
    p_empty = subparsers.add_parser("make-empty-csv", help="빈 48행 CSV 템플릿 생성")
    p_empty.add_argument("--output", default="metrics-combos.csv")
    p_empty.set_defaults(func=cmd_make_empty_csv)

    # self-test
    p_test = subparsers.add_parser("self-test", help="핵심 매칭·거버넌스 로직 자가 검증")
    p_test.set_defaults(func=lambda _: _run_self_tests())

    args = parser.parse_args()
    args.func(args)


if __name__ == "__main__":
    main()
