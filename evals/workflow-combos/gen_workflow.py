#!/usr/bin/env python3
"""
gen_workflow.py — cell_prompts.json 으로부터 자기완결 워크플로 JS 를 생성한다.
프롬프트 수기 전사 오류 방지 + 단일 소스 유지. 파일럿/48런 공용.

usage:
  python3 gen_workflow.py pilot   > _pilot_workflow.js   (대표 5셀, N=1)
  python3 gen_workflow.py full    > _full_workflow.js    (16셀 x N=3 = 48, + serena 워밍업)
  python3 gen_workflow.py new     > _new_workflow.js     (C5/C6 x N=3 = 24, + serena 워밍업)
"""
import json
import sys

PROMPTS = json.load(open("cell_prompts.json"))
CELLS = PROMPTS["cells"]
WARMUP = PROMPTS["warmup_serena"]

PILOT_CELLS = ["C4xM1", "C2xM2", "C4xM2", "C3xM3-W", "C1xM3-L"]
COMBOS = ["C1", "C2", "C3", "C4"]
NEW_COMBOS = ["C5", "C6"]  # 증분 실행 대상(C1~C4 는 기존 결과 재사용)
CASES = ["M1", "M2", "M3-W", "M3-L"]

ANSWERS_SCHEMA = {
    "type": "object", "additionalProperties": False,
    "properties": {
        "answers": {"type": "array", "items": {
            "type": "object", "additionalProperties": False,
            "properties": {
                "qualified_name": {"type": "string"},
                "kind": {"type": "string"},
                "file": {"type": "string"},
                "line": {"type": "integer"},
            },
            "required": ["qualified_name", "kind", "file", "line"],
        }},
        "reasoning_brief": {"type": "string"},
        "tool_calls_count": {"type": "integer"},
    },
    "required": ["answers", "reasoning_brief", "tool_calls_count"],
}


def build_runlist(mode):
    """[(cell_id, run_label, prompt_key)] 생성. run_label 은 transcript→셀 매핑용."""
    runs = []
    if mode == "pilot":
        for cid in PILOT_CELLS:
            runs.append((cid, f"{cid}xr1", cid))
    else:  # full(C1~C4) / new(C5~C6): 각 조합 x case x r1..r3
        combos = NEW_COMBOS if mode == "new" else COMBOS
        for combo in combos:
            for case in CASES:
                cid = f"{combo}x{case}"
                for r in (1, 2, 3):
                    runs.append((cid, f"{cid}xr{r}", cid))
    return runs


def gen(mode):
    runs = build_runlist(mode)
    runmap = {label: CELLS[key] for (cid, label, key) in runs}
    name = {"pilot": "combos-pilot", "new": "combos-c5c6-24run"}.get(mode, "combos-48run")
    desc = {"pilot": "파일럿: 대표 5셀(C4xM1,C2xM2,C4xM2,C3xM3-W,C1xM3-L) sonnet 실행 — 하니스 end-to-end 실증",
            "new": "C5(serena+read)·C6(내장 Read/Glob/Grep) x 4케이스 x N=3 = 24셀 sonnet blind 증분 실행"}.get(
        mode, "48셀(16 unique x N=3) sonnet blind 실행 — 도구 조합 벤치마크 본실행")
    labels = [label for (_, label, _) in runs]
    js = f"""export const meta = {{
  name: {json.dumps(name)},
  description: {json.dumps(desc)},
  phases: [
    {{ title: 'Warmup', detail: 'serena LSP 선기동(측정 제외)' }},
    {{ title: 'Cells', detail: '{len(runs)}개 측정 셀 sonnet 병렬' }},
  ],
}}

const WARMUP = {json.dumps(WARMUP, ensure_ascii=False)}
const RUNMAP = {json.dumps(runmap, ensure_ascii=False)}
const LABELS = {json.dumps(labels, ensure_ascii=False)}
const ANSWERS_SCHEMA = {json.dumps(ANSWERS_SCHEMA, ensure_ascii=False)}

phase('Warmup')
const warmup = await agent(WARMUP, {{ label: 'warmup-serena', phase: 'Warmup', model: 'sonnet' }})
log(`warmup done: ${{String(warmup).slice(0,80)}}`)

phase('Cells')
const results = await parallel(LABELS.map((label) => () =>
  agent(RUNMAP[label], {{ label, phase: 'Cells', model: 'sonnet', schema: ANSWERS_SCHEMA }})
    .then((a) => ({{ cell_label: label, answers: a }}))
    .catch((e) => ({{ cell_label: label, error: String(e) }}))
))

return {{ warmup: String(warmup).slice(0, 200), n_cells: results.length, results }}
"""
    return js


if __name__ == "__main__":
    mode = sys.argv[1] if len(sys.argv) > 1 else "pilot"
    sys.stdout.write(gen(mode))
