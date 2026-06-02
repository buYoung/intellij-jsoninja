#!/usr/bin/env python3
"""
결정적 grader — workflow-combos 벤치마크 채점기 (수정 완성본)
=================================================================

입력:
  --answers-dir PATH   셀별 answers JSON 파일이 모인 디렉터리
                       파일명 규약: {combo}x{case}x{run}.json  예) C1xM1xr1.json
                       ※ metrics_parser.py cell_id 규약(x 구분자) 과 통일.
  --gold-json PATH     (선택) 외부 gold.json 으로 내장 gold 를 덮어쓴다.
                       지정하지 않으면 본 파일에 내장된 동결 gold(_FROZEN_GOLD)를 사용한다.
  --gold PATH          (선택) dataset.yaml 경로. PyYAML 이 설치된 경우에 한해
                       내장 gold 와 cross-check 만 수행(불일치 시 경고). 채점에는 미사용.
  --output PATH        결과 CSV 출력 파일 (기본: metrics-combos.csv)
  --adjudication PATH  판정 필요 목록 JSONL 출력 (기본: adjudication-needed.jsonl)
  --dump-gold PATH     내장 gold 를 JSON 으로 저장 후 종료 (gold.json 추출용).

[설계 결정 — gold 동결 방식]
  실행 환경에 PyYAML 이 없다(표준 라이브러리 아님). 따라서 dataset.yaml 재파싱에
  의존하지 않고, dataset.yaml(git v1.12.1 / 1daf879) 에서 추출한 gold 를 본 파일에
  Python dict 리터럴(_FROZEN_GOLD)로 내장한다. 이것이 캐논(frozen) gold 다.
  - 내장 gold 의 모든 (file:line) 75건과 M2 qualified_name 8건은 dataset.yaml 문자열과
    set-diff 로 완전 일치함을 검증함(전사 오류 0).
  - 외부 변경이 필요하면 --gold-json 으로 덮어쓸 수 있으나, 기본 동작은 내장 gold 다.
  - PyYAML 이 있으면 --gold 로 dataset.yaml 을 주어 내장 gold 와 cross-check 가능(채점 불사용).

answers JSON 스키마 (하나의 JSON 오브젝트):
  {
    "answers": [
      { "qualified_name": "...", "kind": "method|class|function|callsite",
        "file": "...", "line": <int|null> }, ...
    ],
    "reasoning_brief": "...",
    "tool_calls_count": <int>
  }
  - file 은 M1/M3-W/M3-L 채점에 필수. file 또는 line 누락 시 adjudication 으로 라우팅(전 케이스 공통).
  - 경로 정규화: 절대경로·"./" 접두 제거, "src/" 또는 "tree-sitter-wasm/" 이후만 사용.

[하이브리드 채점 원칙 — 교정사실 6]
  결정적 grader 는 명확한(exact) 매칭만 기계 채점하고, 모호한 케이스는 adjudication 목록으로
  분리해 opus 가 사후 판정한다. opus 는 기계 hit 를 재채점하지 않는다(N=3 재현성 보존).
  M2 qualified_name 은 "정확 FQN" 만 기계 hit/trap 으로 인정하고, 짧은형(short-form)·부분일치는
  adjudication 으로 보낸다(교정사실 6: "M2 qualified_name 부분일치"는 adjudication 케이스).

채점 규칙 요약:
  M1  : (file:line) 매칭, ±1, 1:1 최대이분매칭, dedup 후 trap 기계 산출.
        file/line 누락 또는 gold 파일 내 ±1 밖 FP → enclosing-function adjudication(분모 제외).
  M2  : qualified_name 정확 FQN 매칭(정규화 후), dedup 후.
        - 정확 FQN gold → 기계 hit
        - 정확 FQN trap(1~3번) → 기계 trap_inclusion
        - gold 닮은 짧은형/부분일치 → qualified_name_partial adjudication(분모 제외)
        - trap 닮은 짧은형/부분일치 → m2_trap_candidate adjudication(분모 제외) [수정: 기존엔 hallucination 오계상]
        - bare updateLanguage/schedulePreview(gold·trap 양쪽 충돌) → 양쪽 후보를 모두 명시한 adjudication
        - trap 4번(반대방향 분기) 경계 → m2_trap4_boundary adjudication
        - test_tail(통합테스트 3종) → 별도 subscore(메인 분모 제외)
  M3-W: (file:line), ±1, 1:1 최대이분매칭, dedup 후 kotlin/rust 분리 recall 합산. trap 기계 산출.
        file/line 누락 → m3w_missing_location adjudication(분모 제외). [수정: 기존엔 silently drop]
  M3-L: (file:line), ±1, 1:1 최대이분매칭, dedup 후.
        definition_site(:16) ±1 = 중립(분모·FP 모두 제외). non-gold 제출 → m3l_non_gold adjudication.
        file/line 누락 → m3l_missing_location adjudication. [수정: 기존엔 silently drop]

precision 분모 규약(전 케이스 공통):
  adjudication 으로 라우팅된 제출은 mechanical precision 분모에서 제외(교정사실 6: "맞는지 모름").
  trap 포함(trap_inclusion)은 진짜 FP 이므로 분모에 남긴다.
  false_positives 열은 mechanical FP(분모에 남은 비-hit 제출)만 계상해 precision 분모와 일관되게 한다.
  단 M3-L 은 예외: non-gold FP 전부가 adjudication 이지만 "제출 많음→precision 낮음" 신호 보존을 위해
  분모에 남긴다(설계 결정, 아래 grade_m3l 주석).

[결정 항목 — 사용자 승인 필요, 기본 OFF]
  M2_ACCEPT_CLASS_METHOD_AS_HIT = False
  True 로 켜면 'Class.method' 형태의 짧은형을 (충돌 없을 때) 기계 hit/trap 으로 인정한다.
  현재 gold 에서 Class.method 단축형은 충돌이 없음을 검증함(아래 _validate). 다만 이를 기계 hit 로
  인정하면 캐논(frozen) 수치가 바뀌고 교정사실 6("부분일치는 adjudication")과 충돌하므로 기본 OFF.
  켜려면 사용자 승인 후 플래그만 True 로.
"""

from __future__ import annotations

import argparse
import csv
import json
import os
import re
import sys
from collections import defaultdict
from dataclasses import dataclass, asdict
from pathlib import Path
from typing import Optional

# ---------------------------------------------------------------------------
# 결정 플래그 (사용자 승인 전까지 기본 OFF)
# ---------------------------------------------------------------------------
M2_ACCEPT_CLASS_METHOD_AS_HIT = False

# M2 (file:line) → 함수 결정적 해소용 동결 span (provenance: serena LSP @ git v1.12.1 / 1daf879).
# 텍스트 파이프라인(C4 등)이 FQN 대신 (file:line)/단축형으로 답해도 in-span line 이면 캐논 FQN 으로
# 결정 매핑한다(모델 판정 불요). 동명 충돌(schedulePreview·updateLanguage gold↔trap)은 파일이 달라 분해됨.
# 키 = dataset.yaml 정확 FQN, 값 = [(파일 basename, start_line, end_line), ...]. 생성자는 ctor+init 구간 통합.
M2_RESOLVE_VIA_SPAN = True
M2_GOLD_SPANS: dict[str, list[tuple[str, int, int]]] = {
    "com.livteam.jsoninja.services.typeConversion.TypeDeclarationAnalyzerService.analyzeSource": [("TypeDeclarationAnalyzerService.kt", 19, 58)],
    "com.livteam.jsoninja.services.typeConversion.TypeToJsonGenerationService.generate": [("TypeToJsonGenerationService.kt", 17, 43)],
    "com.livteam.jsoninja.ui.dialog.convertType.TypeToJsonDialogPresenter.schedulePreview": [("TypeToJsonDialogPresenter.kt", 91, 131)],
    "com.livteam.jsoninja.ui.dialog.convertType.TypeToJsonDialogPresenter.<init>": [("TypeToJsonDialogPresenter.kt", 13, 32)],
    "com.livteam.jsoninja.ui.dialog.convertType.TypeToJsonDialogPresenter.updateLanguage": [("TypeToJsonDialogPresenter.kt", 37, 42)],
    "com.livteam.jsoninja.ui.dialog.convertType.TypeToJsonDialogPresenter.bindView": [("TypeToJsonDialogPresenter.kt", 68, 83)],
    "com.livteam.jsoninja.ui.dialog.convertType.ConvertTypeDialogPresenter.<init>": [("ConvertTypeDialogPresenter.kt", 8, 29)],
    "com.livteam.jsoninja.ui.dialog.convertType.ConvertTypeDialogPresenter.syncLanguage": [("ConvertTypeDialogPresenter.kt", 67, 85)],
}
M2_TRAP_SPANS: dict[str, list[tuple[str, int, int]]] = {
    "com.livteam.jsoninja.services.JsonDiffService.getOrCreateContext": [("JsonDiffService.kt", 155, 174)],
    "com.livteam.jsoninja.ui.dialog.convertType.TypeToJsonDialogView.updateLanguage": [("TypeToJsonDialogView.kt", 140, 148)],
    "com.livteam.jsoninja.ui.dialog.convertType.JsonToTypeDialogPresenter.schedulePreview": [("JsonToTypeDialogPresenter.kt", 111, 151)],
}

# ---------------------------------------------------------------------------
# 내장 동결 gold (provenance: ../codegraph-vs-serena/dataset.yaml @ git v1.12.1 / 1daf879)
# (file:line) 75건·M2 qname 8건 모두 dataset.yaml 문자열과 set-diff 일치 검증 완료.
# ---------------------------------------------------------------------------
_FROZEN_GOLD: dict = {
    "M1": {
        "gold_sites": [
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
        ],
        "traps": [
            "src/main/kotlin/com/livteam/jsoninja/actions/PrettifyJsonAction.kt:18",
            "src/main/kotlin/com/livteam/jsoninja/actions/UglifyJsonAction.kt:20",
        ],
    },
    "M2": {
        "gold_functions": [
            {"qualified_name": "com.livteam.jsoninja.services.typeConversion.TypeDeclarationAnalyzerService.analyzeSource", "depth": 1},
            {"qualified_name": "com.livteam.jsoninja.services.typeConversion.TypeToJsonGenerationService.generate", "depth": 2},
            {"qualified_name": "com.livteam.jsoninja.ui.dialog.convertType.TypeToJsonDialogPresenter.schedulePreview", "depth": 3},
            {"qualified_name": "com.livteam.jsoninja.ui.dialog.convertType.TypeToJsonDialogPresenter.<init>", "depth": 4},
            {"qualified_name": "com.livteam.jsoninja.ui.dialog.convertType.TypeToJsonDialogPresenter.updateLanguage", "depth": 4},
            {"qualified_name": "com.livteam.jsoninja.ui.dialog.convertType.TypeToJsonDialogPresenter.bindView", "depth": 4},
            {"qualified_name": "com.livteam.jsoninja.ui.dialog.convertType.ConvertTypeDialogPresenter.<init>", "depth": 5},
            {"qualified_name": "com.livteam.jsoninja.ui.dialog.convertType.ConvertTypeDialogPresenter.syncLanguage", "depth": 5},
        ],
        "traps_explicit": [
            "com.livteam.jsoninja.services.JsonDiffService.getOrCreateContext",
            "com.livteam.jsoninja.ui.dialog.convertType.TypeToJsonDialogView.updateLanguage",
            "com.livteam.jsoninja.ui.dialog.convertType.JsonToTypeDialogPresenter.schedulePreview",
        ],
        "test_tail_basenames": [
            "TypeConversionWasmIntegrationTest.kt",
            "TypeConversionWasmIntegrationV2Test.kt",
            "TypeConversionWasmIntegrationV3Test.kt",
        ],
    },
    "M3-W": {
        "kotlin_sites": [
            "src/main/kotlin/com/livteam/jsoninja/services/treesitter/TreeSitterWasmRuntime.kt:61",
            "src/main/kotlin/com/livteam/jsoninja/services/treesitter/TreeSitterWasmRuntime.kt:62",
            "src/main/kotlin/com/livteam/jsoninja/services/treesitter/TreeSitterWasmRuntime.kt:63",
            "src/main/kotlin/com/livteam/jsoninja/services/treesitter/TreeSitterWasmRuntime.kt:64",
        ],
        "rust_sites": [
            "tree-sitter-wasm/src/lib.rs:25",
            "tree-sitter-wasm/src/lib.rs:30",
            "tree-sitter-wasm/src/lib.rs:92",
            "tree-sitter-wasm/src/lib.rs:121",
        ],
        "traps": [
            "src/main/kotlin/com/livteam/jsoninja/services/treesitter/WasmMemoryBridge.kt:19",
            "src/main/kotlin/com/livteam/jsoninja/services/treesitter/WasmMemoryBridge.kt:36",
        ],
    },
    "M3-L": {
        "gold_sites": [
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
        ],
        "definition_site": "src/main/kotlin/com/livteam/jsoninja/model/SupportedLanguage.kt:16",
    },
}

# ---------------------------------------------------------------------------
# gold count / collision assertions
# ---------------------------------------------------------------------------
_GOLD_COUNTS = {
    "M1_total": 33, "M1_prod": 12, "M1_test": 21,
    "M2": 8, "M3W_kotlin": 4, "M3W_rust": 4, "M3W_total": 8, "M3L": 29,
}


def _validate_gold_counts(gold: dict) -> None:
    m1_sites = gold.get("M1", {}).get("gold_sites", [])
    m1_prod = [s for s in m1_sites if "src/main" in s]
    m1_test = [s for s in m1_sites if "src/test" in s]
    m2_fns = gold.get("M2", {}).get("gold_functions", [])
    m3w_kt = gold.get("M3-W", {}).get("kotlin_sites", [])
    m3w_rs = gold.get("M3-W", {}).get("rust_sites", [])
    m3l = gold.get("M3-L", {}).get("gold_sites", [])
    errors = []
    if len(m1_sites) != _GOLD_COUNTS["M1_total"]:
        errors.append(f"M1 gold_sites={len(m1_sites)} (기대={_GOLD_COUNTS['M1_total']})")
    if len(m1_prod) != _GOLD_COUNTS["M1_prod"]:
        errors.append(f"M1 prod_sites={len(m1_prod)} (기대={_GOLD_COUNTS['M1_prod']})")
    if len(m1_test) != _GOLD_COUNTS["M1_test"]:
        errors.append(f"M1 test_sites={len(m1_test)} (기대={_GOLD_COUNTS['M1_test']})")
    if len(m2_fns) != _GOLD_COUNTS["M2"]:
        errors.append(f"M2 gold_functions={len(m2_fns)} (기대={_GOLD_COUNTS['M2']})")
    if len(m3w_kt) != _GOLD_COUNTS["M3W_kotlin"]:
        errors.append(f"M3-W kotlin_sites={len(m3w_kt)} (기대={_GOLD_COUNTS['M3W_kotlin']})")
    if len(m3w_rs) != _GOLD_COUNTS["M3W_rust"]:
        errors.append(f"M3-W rust_sites={len(m3w_rs)} (기대={_GOLD_COUNTS['M3W_rust']})")
    if len(m3l) != _GOLD_COUNTS["M3L"]:
        errors.append(f"M3-L gold_sites={len(m3l)} (기대={_GOLD_COUNTS['M3L']})")
    if errors:
        raise RuntimeError(
            "[gold count assert 실패] 내장 gold 불일치 → 전사 오류 의심:\n"
            + "\n".join(f"  {e}" for e in errors)
        )


def _validate_m2_traps_not_in_gold(gold: dict) -> None:
    gold_qnames = {fn["qualified_name"] for fn in gold.get("M2", {}).get("gold_functions", [])}
    trap_set = set(gold.get("M2", {}).get("traps_explicit", []))
    overlap = gold_qnames & trap_set
    if overlap:
        raise RuntimeError(f"[치명] M2 traps_explicit 에 gold 함수 포함 → 수치 오염: {overlap}")


# ---------------------------------------------------------------------------
# gold 로딩 (내장 우선, --gold-json 덮어쓰기, --gold 는 cross-check 만)
# ---------------------------------------------------------------------------

def _load_gold_via_json(json_path: str) -> dict:
    with open(json_path, "r", encoding="utf-8") as f:
        return json.load(f)


def _crosscheck_with_yaml(gold: dict, yaml_path: str) -> None:
    """PyYAML 이 있으면 dataset.yaml 과 내장 gold 의 (file:line)·qname 을 대조해 경고만 낸다(채점 불사용)."""
    try:
        import yaml  # type: ignore
    except ImportError:
        print("[정보] PyYAML 미설치 — dataset.yaml cross-check 생략(내장 gold 사용).", file=sys.stderr)
        return
    try:
        with open(yaml_path, "r", encoding="utf-8") as f:
            data = yaml.safe_load(f)
    except Exception as exc:
        print(f"[경고] dataset.yaml 로드 실패, cross-check 생략: {exc}", file=sys.stderr)
        return
    yaml_sites: set[str] = set()
    yaml_qnames: set[str] = set()
    for case in data.get("cases", []):
        g = case.get("gold", {})
        for key in ("production_sites", "test_sites", "kotlin_sites", "rust_sites", "use_sites", "traps_must_exclude"):
            for item in g.get(key, []) or []:
                if isinstance(item, str) and re.search(r":\d+$", item.strip()):
                    yaml_sites.add(item.strip())
        if isinstance(g.get("definition_site"), str):
            yaml_sites.add(g["definition_site"].strip())
        for entry in g.get("production_closure", []) or []:
            if isinstance(entry, dict) and "qualified_name" in entry:
                yaml_qnames.add(entry["qualified_name"])
    embedded_sites: set[str] = set()
    for case in ("M1", "M3-W", "M3-L"):
        c = gold.get(case, {})
        for k in ("gold_sites", "kotlin_sites", "rust_sites", "traps"):
            for s in c.get(k, []) or []:
                embedded_sites.add(s)
        if c.get("definition_site"):
            embedded_sites.add(c["definition_site"])
    embedded_qnames = {fn["qualified_name"] for fn in gold.get("M2", {}).get("gold_functions", [])}
    missing_q = embedded_qnames - yaml_qnames
    if missing_q:
        print(f"[경고] cross-check: 내장 M2 qname 이 dataset.yaml 에 없음: {missing_q}", file=sys.stderr)
    drift = embedded_sites - yaml_sites
    if drift:
        print(f"[경고] cross-check: 내장 (file:line) 이 dataset.yaml 과 불일치: {sorted(drift)}", file=sys.stderr)
    if not drift and not missing_q:
        print("[정보] cross-check OK — 내장 gold 가 dataset.yaml 과 일치.", file=sys.stderr)


def load_gold(yaml_path: Optional[str], json_path: Optional[str]) -> dict:
    """
    gold 로딩:
      1. --gold-json 지정 → 그 파일을 gold 로 사용(외부 덮어쓰기).
      2. 미지정 → 내장 _FROZEN_GOLD 사용(기본, PyYAML 불필요).
      3. --gold(dataset.yaml) 지정 + PyYAML 있으면 cross-check 경고만(채점 불사용).
    """
    if json_path and os.path.exists(json_path):
        gold = _load_gold_via_json(json_path)
        src = f"외부 gold.json: {json_path}"
    else:
        gold = json.loads(json.dumps(_FROZEN_GOLD))  # deep copy
        src = "내장 _FROZEN_GOLD"
    print(f"[정보] gold 출처 = {src}", file=sys.stderr)
    if yaml_path and os.path.exists(yaml_path):
        _crosscheck_with_yaml(gold, yaml_path)
    return gold


# ---------------------------------------------------------------------------
# 유틸리티
# ---------------------------------------------------------------------------

def _normalize_path(path_str: str) -> str:
    p = path_str.strip()
    for marker in ("tree-sitter-wasm/", "src/"):
        idx = p.find(marker)
        if idx != -1:
            return p[idx:]
    if p.startswith("./"):
        p = p[2:]
    return p


def _normalize_qname(qname: str) -> str:
    return re.sub(r"\s+", " ", qname.strip())


def _parse_file_line(site: str) -> tuple[str, int]:
    parts = site.rsplit(":", 1)
    if len(parts) != 2:
        raise ValueError(f"file:line 형식 아님: {site!r}")
    return _normalize_path(parts[0]), int(parts[1])


def _resolve_m2_via_span(file_raw, line_raw) -> Optional[str]:
    """M2: (file:line) 이 gold/trap 함수 span 안이면 그 캐논 FQN 반환(없으면 None).
    파일은 basename, line 은 [start,end] 포함 매칭. 둘 이상 함수에 동시 매칭되면 None(모호)."""
    if not M2_RESOLVE_VIA_SPAN or not file_raw or line_raw is None:
        return None
    try:
        line = int(line_raw)
    except (TypeError, ValueError):
        return None
    basename = Path(_normalize_path(str(file_raw))).name
    matches: list[str] = []
    for table in (M2_GOLD_SPANS, M2_TRAP_SPANS):
        for fqn, spans in table.items():
            if any(bn == basename and start <= line <= end for (bn, start, end) in spans):
                matches.append(fqn)
    uniq = list(dict.fromkeys(matches))
    return uniq[0] if len(uniq) == 1 else None


def _is_within_one(line_a: int, line_b: int) -> bool:
    return abs(line_a - line_b) <= 1


def _dedup_file_line(items: list[tuple[str, int]]) -> list[tuple[str, int]]:
    seen: set[tuple[str, int]] = set()
    result: list[tuple[str, int]] = []
    for item in items:
        if item not in seen:
            seen.add(item)
            result.append(item)
    return result


def _dedup_qnames(items: list[str]) -> list[str]:
    seen: set[str] = set()
    result: list[str] = []
    for item in items:
        if item not in seen:
            seen.add(item)
            result.append(item)
    return result


# ---------------------------------------------------------------------------
# 1:1 bipartite ±1 매칭 (Kuhn 최대이분매칭)
# ---------------------------------------------------------------------------

def _max_bipartite_match(g_lines: list[int], s_lines: list[int]) -> list[tuple[int, int, bool]]:
    """Kuhn 증강경로 최대이분매칭 — |g-s|<=1 간선만. exact 우선(인접리스트 앞 배치).

    검증: gold=[61,62,63,64], sub=[60,61,62,63] → 4매칭(균일 -1 시프트). greedy 는 2.
    """
    n_g = len(g_lines)
    n_s = len(s_lines)
    adj: list[list[tuple[int, bool]]] = [[] for _ in range(n_g)]
    for gi, gl in enumerate(g_lines):
        for si, sl in enumerate(s_lines):
            if gl == sl:
                adj[gi].insert(0, (si, True))
            elif abs(gl - sl) == 1:
                adj[gi].append((si, False))
    match_s: list[int] = [-1] * n_s
    match_exact: list[bool] = [False] * n_s

    def _augment(gi: int, visited: list[bool]) -> bool:
        for (si, is_ex) in adj[gi]:
            if visited[si]:
                continue
            visited[si] = True
            if match_s[si] == -1 or _augment(match_s[si], visited):
                match_s[si] = gi
                match_exact[si] = is_ex
                return True
        return False

    for gi in range(n_g):
        visited = [False] * n_s
        _augment(gi, visited)
    result: list[tuple[int, int, bool]] = []
    for si, gi in enumerate(match_s):
        if gi != -1:
            result.append((gi, si, match_exact[si]))
    return result


def _bipartite_match_file_line(
    gold_sites: list[str],
    submitted_sites: list[tuple[str, int]],
) -> tuple[list[tuple[str, int, bool]], list[str], list[tuple[str, int]]]:
    gold_parsed: list[tuple[str, int]] = [_parse_file_line(s) for s in gold_sites]
    gold_by_file: dict[str, list[int]] = defaultdict(list)
    for (f, ln) in gold_parsed:
        gold_by_file[f].append(ln)
    sub_by_file: dict[str, list[int]] = defaultdict(list)
    for (f, ln) in submitted_sites:
        sub_by_file[f].append(ln)
    hits: list[tuple[str, int, bool]] = []
    unmatched_gold: list[tuple[str, int]] = []
    unmatched_sub: list[tuple[str, int]] = []
    all_files = sorted(set(gold_by_file.keys()) | set(sub_by_file.keys()))
    for f in all_files:
        g_lines = sorted(gold_by_file.get(f, []))
        s_lines = sorted(sub_by_file.get(f, []))
        matched_pairs = _max_bipartite_match(g_lines, s_lines)
        matched_g: set[int] = {gi for (gi, si, _) in matched_pairs}
        matched_s: set[int] = {si for (gi, si, _) in matched_pairs}
        for (gi, si, is_exact) in matched_pairs:
            hits.append((f, g_lines[gi], is_exact))
        for gi, gl in enumerate(g_lines):
            if gi not in matched_g:
                unmatched_gold.append((f, gl))
        for si, sl in enumerate(s_lines):
            if si not in matched_s:
                unmatched_sub.append((f, sl))
    gold_miss = [f"{f}:{ln}" for (f, ln) in unmatched_gold]
    return hits, gold_miss, unmatched_sub


# ---------------------------------------------------------------------------
# 케이스별 채점 함수
# ---------------------------------------------------------------------------

@dataclass
class CellResult:
    combo: str
    case: str
    run: str
    recall: Optional[float]
    precision: Optional[float]
    f1: Optional[float]
    hits: int
    gold_size: int
    submitted: int
    false_positives: int
    trap_inclusion_count: int
    hallucination_raw_count: int
    m3w_kotlin_recall: Optional[float] = None
    m3w_rust_recall: Optional[float] = None
    m2_test_tail_hits: int = 0
    m2_test_tail_total: int = 0
    tool_calls_count: int = 0
    adjudication_count: int = 0

    def as_row(self) -> dict:
        d = asdict(self)
        return {k: ("" if v is None else v) for k, v in d.items()}


def _safe_f1(recall: Optional[float], precision: Optional[float]) -> Optional[float]:
    if recall is None or precision is None:
        return None
    if recall + precision == 0:
        return 0.0
    return 2 * recall * precision / (recall + precision)


def _safe_precision(hits: int, submitted: int) -> Optional[float]:
    if submitted == 0:
        return None
    return hits / submitted


def _count_adj(adjudications: list[dict], combo: str, case: str, run: str) -> int:
    return sum(1 for a in adjudications if a["combo"] == combo and a["case"] == case and a["run"] == run)


# ── M1 ──────────────────────────────────────────────────────────────────────

def grade_m1(answers, gold, combo, run, adjudications, tool_calls_count):
    gold_sites: list[str] = gold["M1"]["gold_sites"]
    trap_sites: list[str] = gold["M1"]["traps"]

    gold_files: set[str] = set()
    for s in gold_sites:
        try:
            gf, _ = _parse_file_line(s)
            gold_files.add(gf)
        except ValueError:
            pass

    submitted_fl_raw: list[tuple[str, int]] = []
    missing_loc_count = 0  # file/line 누락 adjudication 수(분모 제외)
    for ans in answers:
        file_raw = ans.get("file") or ""
        line_raw = ans.get("line")
        if file_raw and line_raw is not None:
            try:
                submitted_fl_raw.append((_normalize_path(file_raw), int(line_raw)))
            except (ValueError, TypeError):
                pass
        else:
            adjudications.append({
                "combo": combo, "case": "M1", "run": run,
                "kind": "enclosing_function",
                "item": str(ans.get("qualified_name") or ans.get("file") or ans),
                "reason": "file 또는 line 누락 — enclosing-function 동치 여부 판정 필요",
            })
            missing_loc_count += 1

    submitted_fl = _dedup_file_line(submitted_fl_raw)
    hits_list, _, fp_sub = _bipartite_match_file_line(gold_sites, submitted_fl)

    for (f, gold_line, is_exact) in hits_list:
        if not is_exact:
            adjudications.append({
                "combo": combo, "case": "M1", "run": run,
                "kind": "line_boundary_pm1",
                "item": f"{f}:{gold_line}",
                "reason": "제출 라인이 gold 와 정확히 일치하지 않고 ±1 범위로 매칭됨",
            })

    trap_set: set[str] = set()
    for trap_str in trap_sites:
        try:
            tf, tl = _parse_file_line(trap_str)
            trap_set.add(f"{tf}:{tl}")
        except ValueError:
            pass

    trap_hit_count = 0
    enclosing_adj_count = 0
    hallucination_count = 0
    for (f, ln) in fp_sub:
        key = f"{f}:{ln}"
        if key in trap_set:
            trap_hit_count += 1
        elif f in gold_files:
            adjudications.append({
                "combo": combo, "case": "M1", "run": run,
                "kind": "enclosing_function",
                "item": key,
                "reason": "gold 파일 내 ±1 범위 밖 FP — 둘러싼 함수 라인 후보. 판정 필요.",
            })
            enclosing_adj_count += 1
        else:
            hallucination_count += 1

    hits = len(hits_list)
    gold_size = len(gold_sites)
    # precision 분모: adjudication(enclosing_function) 제외, trap 은 포함
    precision_submitted = len(submitted_fl) - enclosing_adj_count
    # false_positives 열: mechanical FP = trap + hallucination (precision 분모와 일관, adj 제외)
    false_positives = trap_hit_count + hallucination_count
    recall = hits / gold_size if gold_size > 0 else 0.0
    precision = _safe_precision(hits, precision_submitted)
    f1 = _safe_f1(recall, precision)
    return CellResult(
        combo=combo, case="M1", run=run,
        recall=recall, precision=precision, f1=f1,
        hits=hits, gold_size=gold_size,
        submitted=len(submitted_fl),
        false_positives=false_positives,
        trap_inclusion_count=trap_hit_count,
        hallucination_raw_count=hallucination_count,
        tool_calls_count=tool_calls_count,
        adjudication_count=_count_adj(adjudications, combo, "M1", run),
    )


# ── M2 ──────────────────────────────────────────────────────────────────────

def grade_m2(answers, gold, combo, run, adjudications, tool_calls_count):
    gold_functions: list[dict] = gold["M2"]["gold_functions"]
    trap_qnames: list[str] = gold["M2"]["traps_explicit"]
    test_tail_basenames: list[str] = gold["M2"]["test_tail_basenames"]

    gold_qname_set: set[str] = {_normalize_qname(fn["qualified_name"]) for fn in gold_functions}
    trap_qname_set: set[str] = {_normalize_qname(t) for t in trap_qnames}
    overlap = gold_qname_set & trap_qname_set
    if overlap:
        raise RuntimeError(f"[치명] M2 traps_explicit 에 gold 함수 포함: {overlap}")

    # suffix → {"gold":[...], "trap":[...]} 충돌 맵 (bare 메서드명 모호성 판정용)
    suffix_map: dict[str, dict[str, list[str]]] = defaultdict(lambda: {"gold": [], "trap": []})
    for gq in gold_qname_set:
        suffix_map[gq.split(".")[-1]]["gold"].append(gq)
    for tq in trap_qname_set:
        suffix_map[tq.split(".")[-1]]["trap"].append(tq)

    # Class.method 단축형(2-segment) → FQN 역참조 맵 (결정 플래그용)
    class_method_to_gold: dict[str, list[str]] = defaultdict(list)
    class_method_to_trap: dict[str, list[str]] = defaultdict(list)
    for gq in gold_qname_set:
        segs = gq.split(".")
        if len(segs) >= 2:
            class_method_to_gold[".".join(segs[-2:])].append(gq)
    for tq in trap_qname_set:
        segs = tq.split(".")
        if len(segs) >= 2:
            class_method_to_trap[".".join(segs[-2:])].append(tq)

    test_tail_basename_set: set[str] = set(test_tail_basenames)

    def _is_test_tail_answer(ans: dict) -> bool:
        file_raw = ans.get("file") or ""
        if file_raw:
            bn = Path(_normalize_path(file_raw)).name
            if bn in test_tail_basename_set:
                return True
        qname = ans.get("qualified_name") or ""
        for basename in test_tail_basenames:
            stem = Path(basename).stem
            if stem.lower() in qname.lower():
                return True
        return False

    matched_test_tail_basenames: set[str] = set()
    for ans in answers:
        file_raw = ans.get("file") or ""
        if file_raw:
            bn = Path(_normalize_path(file_raw)).name
            if bn in test_tail_basename_set:
                matched_test_tail_basenames.add(bn)
        qname = ans.get("qualified_name") or ""
        for basename in test_tail_basenames:
            stem = Path(basename).stem
            if stem.lower() in qname.lower():
                matched_test_tail_basenames.add(basename)
    test_tail_hits = len(matched_test_tail_basenames)
    test_tail_total = len(test_tail_basenames)

    submitted_qnames_raw: list[str] = []
    for ans in answers:
        if _is_test_tail_answer(ans):
            continue
        qname = _normalize_qname(ans.get("qualified_name") or "")
        # span 해소: 이미 정확 FQN 이 아니면 in-span (file:line) 으로 캐논 FQN 결정 매핑
        if qname not in gold_qname_set and qname not in trap_qname_set:
            span_fqn = _resolve_m2_via_span(ans.get("file"), ans.get("line"))
            if span_fqn:
                qname = span_fqn
        if qname:
            submitted_qnames_raw.append(qname)
    submitted_qnames = _dedup_qnames(submitted_qnames_raw)

    hits = 0
    trap_hit_count = 0
    hallucination_count = 0
    adj_routed_count = 0
    matched_gold: set[str] = set()

    for sq in submitted_qnames:
        # 1) 정확 FQN gold
        if sq in gold_qname_set:
            if sq not in matched_gold:
                hits += 1
                matched_gold.add(sq)
            continue
        # 2) 정확 FQN trap (1~3번)
        if sq in trap_qname_set:
            trap_hit_count += 1
            continue
        # 3) 결정 플래그: Class.method 단축형을 기계 hit/trap 으로 인정(기본 OFF)
        if M2_ACCEPT_CLASS_METHOD_AS_HIT:
            g_cm = class_method_to_gold.get(sq, [])
            t_cm = class_method_to_trap.get(sq, [])
            if len(g_cm) == 1 and not t_cm:
                if g_cm[0] not in matched_gold:
                    hits += 1
                    matched_gold.add(g_cm[0])
                continue
            if len(t_cm) == 1 and not g_cm:
                trap_hit_count += 1
                continue
        # 4) trap 4번(반대방향 분기) 경계 — 우선 판정.
        #    [수정] 'syncLanguage' 단독 조건 제거: ConvertTypeDialogPresenter.syncLanguage 는
        #    gold(depth 5) 이므로 trap4 로 보내면 opus 가 정답을 trap 으로 오인한다.
        #    trap 4번은 "반대방향 jsonToTypePresenter.updateLanguage 분기"뿐이므로 그 조건만 남긴다.
        if "updateLanguage" in sq and "jsontotype" in sq.lower():
            adjudications.append({
                "combo": combo, "case": "M2", "run": run,
                "kind": "m2_trap4_boundary", "item": sq,
                "reason": "M2 trap 4번(반대방향 분기 jsonToType updateLanguage) 경계 — 기계 판정 불가",
            })
            adj_routed_count += 1
            continue
        # 5) bare 메서드명 또는 부분일치 → suffix 충돌 판정
        last_seg = sq.split(".")[-1]
        sm = suffix_map.get(last_seg)
        if sm and (sm["gold"] or sm["trap"]):
            gold_cands = sm["gold"]
            trap_cands = sm["trap"]
            if gold_cands and trap_cands:
                # 충돌(bare updateLanguage / schedulePreview): 양쪽 후보 모두 명시
                adjudications.append({
                    "combo": combo, "case": "M2", "run": run,
                    "kind": "qualified_name_ambiguous", "item": sq,
                    "reason": (
                        f"메서드명 '{last_seg}' 가 gold·trap 양쪽에 존재 — "
                        f"gold 후보={gold_cands} / trap 후보={trap_cands}. 어느 쪽인지 판정 필요."
                    ),
                })
            elif gold_cands:
                adjudications.append({
                    "combo": combo, "case": "M2", "run": run,
                    "kind": "qualified_name_partial", "item": sq,
                    "reason": f"suffix '{last_seg}' gold 닮음(부분일치) — gold 후보={gold_cands}",
                })
            else:
                adjudications.append({
                    "combo": combo, "case": "M2", "run": run,
                    "kind": "m2_trap_candidate", "item": sq,
                    "reason": f"suffix '{last_seg}' trap 닮음(부분일치) — trap 후보={trap_cands}",
                })
            adj_routed_count += 1
            continue
        # 6) <init>/constructor 표기 차이
        if "<init>" in sq or "constructor" in sq.lower():
            routed = False
            for gq in sorted(gold_qname_set):
                if ".<init>" in gq:
                    candidate = sq.replace("<init>", "").replace("constructor", "").strip(". ")
                    if candidate and candidate in gq:
                        adjudications.append({
                            "combo": combo, "case": "M2", "run": run,
                            "kind": "qualified_name_partial", "item": sq,
                            "reason": f"<init>/constructor 표기 차이 가능성 — gold: {gq}",
                        })
                        adj_routed_count += 1
                        routed = True
                        break
            if routed:
                continue
        # 7) 어디에도 안 닮음 → 진짜 hallucination
        hallucination_count += 1

    gold_size = len(gold_qname_set)
    precision_submitted = len(submitted_qnames) - adj_routed_count
    # false_positives 열: mechanical FP = trap + hallucination (precision 분모와 일관, adj 제외)
    false_positives = trap_hit_count + hallucination_count
    recall = hits / gold_size if gold_size > 0 else 0.0
    precision = _safe_precision(hits, precision_submitted)
    f1 = _safe_f1(recall, precision)
    return CellResult(
        combo=combo, case="M2", run=run,
        recall=recall, precision=precision, f1=f1,
        hits=hits, gold_size=gold_size,
        submitted=len(submitted_qnames),
        false_positives=false_positives,
        trap_inclusion_count=trap_hit_count,
        hallucination_raw_count=hallucination_count,
        m2_test_tail_hits=test_tail_hits,
        m2_test_tail_total=test_tail_total,
        tool_calls_count=tool_calls_count,
        adjudication_count=_count_adj(adjudications, combo, "M2", run),
    )


# ── M3-W ────────────────────────────────────────────────────────────────────

def grade_m3w(answers, gold, combo, run, adjudications, tool_calls_count):
    kotlin_sites: list[str] = gold["M3-W"]["kotlin_sites"]
    rust_sites: list[str] = gold["M3-W"]["rust_sites"]
    trap_sites: list[str] = gold["M3-W"]["traps"]

    submitted_fl_raw: list[tuple[str, int]] = []
    for ans in answers:
        file_raw = ans.get("file") or ""
        line_raw = ans.get("line")
        if file_raw and line_raw is not None:
            try:
                submitted_fl_raw.append((_normalize_path(file_raw), int(line_raw)))
            except (ValueError, TypeError):
                pass
        else:
            # 수정: file/line 누락을 silently drop 하지 않고 adjudication 으로(M1 parity)
            adjudications.append({
                "combo": combo, "case": "M3-W", "run": run,
                "kind": "m3w_missing_location",
                "item": str(ans.get("qualified_name") or ans.get("file") or ans),
                "reason": "file 또는 line 누락 — (file:line) 채점 불가, 판정 필요",
            })

    submitted_fl = _dedup_file_line(submitted_fl_raw)
    submitted_kotlin = [(f, ln) for (f, ln) in submitted_fl if f.endswith(".kt")]
    submitted_rust = [(f, ln) for (f, ln) in submitted_fl if f.endswith(".rs")]

    kotlin_hits, _, kotlin_fp = _bipartite_match_file_line(kotlin_sites, submitted_kotlin)
    rust_hits, _, rust_fp = _bipartite_match_file_line(rust_sites, submitted_rust)
    other_fp = [(f, ln) for (f, ln) in submitted_fl if not f.endswith(".kt") and not f.endswith(".rs")]
    all_fp = kotlin_fp + rust_fp + other_fp

    for (f, gold_line, is_exact) in kotlin_hits + rust_hits:
        if not is_exact:
            adjudications.append({
                "combo": combo, "case": "M3-W", "run": run,
                "kind": "line_boundary_pm1", "item": f"{f}:{gold_line}",
                "reason": "제출 라인이 gold 와 정확히 일치하지 않고 ±1 범위로 매칭됨",
            })

    trap_set: set[str] = set()
    for t in trap_sites:
        try:
            tf, tl = _parse_file_line(t)
            trap_set.add(f"{tf}:{tl}")
        except ValueError:
            pass
    trap_hit_count = 0
    for (f, ln) in all_fp:
        if f"{f}:{ln}" in trap_set:
            trap_hit_count += 1

    kotlin_size = len(kotlin_sites)
    rust_size = len(rust_sites)
    gold_size = kotlin_size + rust_size
    total_hits = len(kotlin_hits) + len(rust_hits)
    submitted = len(submitted_fl)
    false_positives = len(all_fp)
    hallucination_count = false_positives - trap_hit_count
    kotlin_recall = len(kotlin_hits) / kotlin_size if kotlin_size > 0 else 0.0
    rust_recall = len(rust_hits) / rust_size if rust_size > 0 else 0.0
    recall = total_hits / gold_size if gold_size > 0 else 0.0
    precision = _safe_precision(total_hits, submitted)
    f1 = _safe_f1(recall, precision)
    return CellResult(
        combo=combo, case="M3-W", run=run,
        recall=recall, precision=precision, f1=f1,
        hits=total_hits, gold_size=gold_size, submitted=submitted,
        false_positives=false_positives,
        trap_inclusion_count=trap_hit_count,
        hallucination_raw_count=hallucination_count,
        m3w_kotlin_recall=kotlin_recall,
        m3w_rust_recall=rust_recall,
        tool_calls_count=tool_calls_count,
        adjudication_count=_count_adj(adjudications, combo, "M3-W", run),
    )


# ── M3-L ────────────────────────────────────────────────────────────────────

def grade_m3l(answers, gold, combo, run, adjudications, tool_calls_count):
    gold_sites: list[str] = gold["M3-L"]["gold_sites"]
    definition_site: str = gold["M3-L"].get("definition_site", "")

    def_file: Optional[str] = None
    def_line: Optional[int] = None
    if definition_site:
        try:
            def_file, def_line = _parse_file_line(definition_site)
        except ValueError:
            pass

    submitted_fl_raw: list[tuple[str, int]] = []
    for ans in answers:
        file_raw = ans.get("file") or ""
        line_raw = ans.get("line")
        if file_raw and line_raw is not None:
            try:
                nf = _normalize_path(file_raw)
                nl = int(line_raw)
                if def_file is not None and nf == def_file and _is_within_one(nl, def_line):
                    continue  # 중립: enum 선언(:16) — 계상 제외
                submitted_fl_raw.append((nf, nl))
            except (ValueError, TypeError):
                pass
        else:
            # 수정: file/line 누락을 silently drop 하지 않고 adjudication 으로(M1 parity)
            adjudications.append({
                "combo": combo, "case": "M3-L", "run": run,
                "kind": "m3l_missing_location",
                "item": str(ans.get("qualified_name") or ans.get("file") or ans),
                "reason": "file 또는 line 누락 — (file:line) 채점 불가, 판정 필요",
            })

    submitted_fl = _dedup_file_line(submitted_fl_raw)
    hits_list, _, fp_sub = _bipartite_match_file_line(gold_sites, submitted_fl)

    for (f, gold_line, is_exact) in hits_list:
        if not is_exact:
            adjudications.append({
                "combo": combo, "case": "M3-L", "run": run,
                "kind": "line_boundary_pm1", "item": f"{f}:{gold_line}",
                "reason": "제출 라인이 gold 와 정확히 일치하지 않고 ±1 범위로 매칭됨",
            })

    for (f, ln) in fp_sub:
        adjudications.append({
            "combo": combo, "case": "M3-L", "run": run,
            "kind": "m3l_non_gold", "item": f"{f}:{ln}",
            "reason": "gold 에 없는 제출 — trap(문자열/리소스/KOTLIN_접두/코루틴) 여부 기계 판정 불가",
        })

    hits = len(hits_list)
    gold_size = len(gold_sites)
    submitted = len(submitted_fl)
    false_positives = len(fp_sub)
    recall = hits / gold_size if gold_size > 0 else 0.0
    # [설계 결정] M3-L 은 non-gold FP 가 전부 adjudication 이지만, "제출 많음→precision 낮음"
    # 신호 보존을 위해 precision 분모를 전체 submitted 로 유지한다(M1/M2 와 다른 예외).
    # opus 가 adjudication 판정 후 최종 precision 을 재계산한다.
    precision = _safe_precision(hits, submitted)
    f1 = _safe_f1(recall, precision)
    return CellResult(
        combo=combo, case="M3-L", run=run,
        recall=recall, precision=precision, f1=f1,
        hits=hits, gold_size=gold_size, submitted=submitted,
        false_positives=false_positives,
        trap_inclusion_count=0,
        hallucination_raw_count=false_positives,
        tool_calls_count=tool_calls_count,
        adjudication_count=_count_adj(adjudications, combo, "M3-L", run),
    )


# ---------------------------------------------------------------------------
# 파일명 파싱
# ---------------------------------------------------------------------------

_VALID_COMBOS = {"C1", "C2", "C3", "C4", "C5", "C6"}
_VALID_CASES = {"M1", "M2", "M3-W", "M3-L"}
_VALID_RUNS = {"r1", "r2", "r3"}


def _parse_filename(filename: str) -> Optional[tuple[str, str, str]]:
    """{combo}x{case}x{run}.json → (combo, case, run). x 구분자(maxsplit=2)."""
    stem = Path(filename).stem
    parts = stem.split("x", 2)
    if len(parts) != 3:
        return None
    combo, case, run = parts
    if combo not in _VALID_COMBOS:
        return None
    if case not in _VALID_CASES:
        return None
    if run not in _VALID_RUNS:
        return None
    return combo, case, run


_GRADERS = {"M1": grade_m1, "M2": grade_m2, "M3-W": grade_m3w, "M3-L": grade_m3l}


def grade_cell(answers_path, gold, combo, case, run, adjudications):
    with open(answers_path, "r", encoding="utf-8") as f:
        cell_data = json.load(f)
    answers = cell_data.get("answers", [])
    tool_calls_count = cell_data.get("tool_calls_count", 0)
    grader = _GRADERS.get(case)
    if grader is None:
        raise ValueError(f"알 수 없는 케이스: {case!r}")
    return grader(
        answers=answers, gold=gold, combo=combo, run=run,
        adjudications=adjudications, tool_calls_count=tool_calls_count,
    )


CSV_FIELDS = [
    "combo", "case", "run",
    "recall", "precision", "f1",
    "hits", "gold_size", "submitted", "false_positives",
    "trap_inclusion_count", "hallucination_raw_count",
    "m3w_kotlin_recall", "m3w_rust_recall",
    "m2_test_tail_hits", "m2_test_tail_total",
    "tool_calls_count", "adjudication_count",
]


def main(argv: list[str] | None = None) -> None:
    parser = argparse.ArgumentParser(description="workflow-combos 벤치마크 결정적 채점기")
    parser.add_argument("--answers-dir", required=False,
                        help="셀별 answers JSON 디렉터리. 파일명: {combo}x{case}x{run}.json")
    parser.add_argument("--gold-json", default=None,
                        help="외부 gold.json (지정 시 내장 gold 덮어쓰기). 미지정 시 내장 _FROZEN_GOLD 사용.")
    parser.add_argument("--gold", default=None,
                        help="dataset.yaml 경로 (PyYAML 있으면 cross-check 만, 채점 불사용).")
    parser.add_argument("--dump-gold", default=None, metavar="PATH",
                        help="gold 를 JSON 으로 저장 후 종료 (gold.json 추출용).")
    parser.add_argument("--output", default="metrics-combos.csv", help="결과 CSV 출력 경로")
    parser.add_argument("--adjudication", default="adjudication-needed.jsonl", help="판정 필요 JSONL 출력 경로")
    args = parser.parse_args(argv)

    gold = load_gold(args.gold, args.gold_json)
    _validate_gold_counts(gold)
    _validate_m2_traps_not_in_gold(gold)

    if args.dump_gold:
        dump_path = Path(args.dump_gold)
        with open(dump_path, "w", encoding="utf-8") as f:
            json.dump(gold, f, ensure_ascii=False, indent=2)
        print(f"[완료] gold.json → {dump_path}")
        sys.exit(0)

    if not args.answers_dir:
        print("[오류] --answers-dir 필요(--dump-gold 외).", file=sys.stderr)
        sys.exit(1)

    answers_dir = Path(args.answers_dir)
    if not answers_dir.is_dir():
        print(f"[오류] --answers-dir 가 디렉터리가 아님: {answers_dir}", file=sys.stderr)
        sys.exit(1)

    json_files = sorted(answers_dir.glob("*.json"))
    if not json_files:
        print(f"[경고] {answers_dir} 에 JSON 파일이 없음", file=sys.stderr)

    results: list[CellResult] = []
    adjudications: list[dict] = []
    for json_file in json_files:
        parsed = _parse_filename(json_file.name)
        if parsed is None:
            print(f"[건너뜀] 파일명 규약 불일치: {json_file.name} (규약: {{combo}}x{{case}}x{{run}}.json)", file=sys.stderr)
            continue
        combo, case, run = parsed
        if case not in gold:
            print(f"[건너뜀] gold 에 케이스 없음: {case} (파일: {json_file.name})", file=sys.stderr)
            continue
        try:
            cell_result = grade_cell(str(json_file), gold, combo, case, run, adjudications)
            results.append(cell_result)
            prec_str = f"{cell_result.precision:.3f}" if cell_result.precision is not None else "N/A"
            f1_str = f"{cell_result.f1:.3f}" if cell_result.f1 is not None else "N/A"
            print(f"  {json_file.name:32s}  recall={cell_result.recall:.3f}  precision={prec_str:5s}  F1={f1_str}")
        except Exception as exc:
            print(f"[오류] {json_file.name}: {exc}", file=sys.stderr)

    output_path = Path(args.output)
    with open(output_path, "w", newline="", encoding="utf-8") as csvfile:
        writer = csv.DictWriter(csvfile, fieldnames=CSV_FIELDS)
        writer.writeheader()
        for r in results:
            row = r.as_row()
            writer.writerow({k: row[k] for k in CSV_FIELDS})
    print(f"\n[완료] CSV → {output_path}  ({len(results)} 행)")

    adj_path = Path(args.adjudication)
    with open(adj_path, "w", encoding="utf-8") as f:
        for item in adjudications:
            f.write(json.dumps(item, ensure_ascii=False) + "\n")
    print(f"[완료] adjudication → {adj_path}  ({len(adjudications)} 건)")


if __name__ == "__main__":
    main()
