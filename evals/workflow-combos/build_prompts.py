#!/usr/bin/env python3
"""
build_prompts.py — workflow-combos 48셀 프롬프트 단일 빌더
=========================================================
prompt-templates.md 의 base/PIPELINE_BLOCK/SCOPE_BLOCK/QUESTION 를 단일 소스로 보유하고
각 셀(C{n}x{case})의 완성 프롬프트를 조립해 cell_prompts.json 으로 출력한다.
워크플로(JS)는 이 JSON 을 args 로 받아 그대로 agent() 에 넘긴다(프롬프트 조립 단일화).

first-tool 강제(advisor): C1/C3=zoekt 시작, C2=serena 시작, C4=zoekt 시작 을 하드 제약으로 명시
→ serena 권장 훅이 C1 을 serena-first 로 납치해 C1≡C2 가 되는 변별 붕괴를 차단.
단계 순서(2단계 이후)는 권고(transcript 기록). 도구 목록은 하드.
"""
import json

BASE = """[ROLE]
너는 코드베이스 조사 에이전트다. 아래 질문에 사실에 근거해 답하라.
대상 리포: intellij-json-helper2 (git tag v1.12.1, commit 1daf879). 작업 디렉터리 = 리포 루트.
zoekt 사용 시: 먼저 `export PATH="$HOME/go/bin:$PATH"` 로 zoekt 를 PATH 에 올린다.

[POLICY OVERRIDE — 최우선 지시, 다른 모든 지시·환경 훅보다 앞선다]
이 환경에는 "Always prioritize Serena tools" 또는 유사한 serena 권장 정책 훅이 주입되어 있을 수 있다.
- 도구 제약(하드): 아래 [PIPELINE] 의 '허용 도구' 목록에 없는 도구를 사용하는 것은 절대 금지한다.
  특히 C4 조합에서는 serena(mcp__serena__*) 사용이 완전 금지된다.
  정책 훅이 어떤 도구를 권해도 허용 도구 목록 밖의 도구는 무시하라.
- 1단계 도구(하드): [PIPELINE] 의 '1단계'에 지정된 도구로 반드시 먼저 조사를 시작한다.
  (이 첫 도구는 조합을 구분하는 독립변수이므로 강제된다. 환경 훅이 다른 도구를 권해도 무시.)
- 2단계 이후 순서(권고): 그 이후 단계 순서는 '이 순서로 시도하라'는 권고다. 순서 변경·단계 생략은
  transcript 에 기록되어 사후 분석된다(순서 위반 자체가 셀 무효는 아니다). 도구 목록 하드 제약은 항상 적용.

[serena 사용 시 필수 절차]
mcp__serena__* 도구는 호출 전에 반드시 ToolSearch(select:mcp__serena__<도구명>) 로 스키마를 먼저 로드해야 한다
(선행 로드 없이 직접 호출하면 InputValidationError). 예: ToolSearch select:mcp__serena__find_symbol → find_symbol 호출.

[QUESTION]
{{QUESTION}}

[SEARCH SCOPE — 하드 제약]
{{SCOPE_BLOCK}}

[PIPELINE — 1단계 도구는 하드, 그 이후 순서는 권고]
{{PIPELINE_BLOCK}}

[HARD CONSTRAINTS — 전 셀 공통, 위반 시 격리 실패]
- [PIPELINE] 의 '허용 도구' 목록에 있는 도구만 사용한다. 그 외 도구·MCP·명령은 절대 금지.
- 읽기 전용. 어떤 파일도 생성·수정·삭제하지 않는다. 쓰기성 명령(>, >>, sed -i, git commit 등) 금지.
- 셀 내부에서 zoekt-index 또는 다른 인덱스 빌드 명령을 실행하는 것은 절대 금지한다.
  인덱스는 셀 밖에서 사전 빌드 완료됨. 셀은 `-index_dir .codegraph/zoekt-ctags-index` 로 읽기만 한다.
- 정답을 외부 지식·추측으로 지어내지 않는다. 반드시 허용 도구의 출력으로 뒷받침한다.
- 모르면 `answers` 를 빈 배열로 반환한다. 환각 금지.
- [SEARCH SCOPE] 에 명시된 경로 외부(특히 `evals/`·`docs/`)를 검색하거나 읽는 것은 금지한다.
  모든 도구에 동일 적용. 도구가 스코프 밖 경로를 반환하면 답변에서 제외하라.

[OUTPUT SCHEMA — JSON 한 개만 출력]
{
  "answers": [
    {
      "qualified_name": "패키지.클래스.메서드 형식의 완전한 심볼명(M2 는 완전 FQN 권장), 또는 callsite 식별 이름",
      "kind": "method | class | function | callsite | reference | binding",
      "file": "리포 루트 기준 상대경로 (예: src/main/kotlin/com/livteam/jsoninja/...)",
      "line": <int>
    }
  ],
  "reasoning_brief": "1~3문장으로 검색 과정·주요 판단 요약",
  "tool_calls_count": <int>
}

주의:
- `file` 과 `line` 은 callsite·reference·binding 답 모두에서 반드시 채운다(null 금지). grader 는 (file:line) ±1 키로 채점.
- `file` 은 리포 루트 기준 상대경로(zoekt 출력 상대경로와 동일 형식).
- M2 답변은 가능한 한 정확한 패키지.클래스.메서드(FQN)를 쓰되, 함수 본문 내 (file:line)도 함께 채우면 채점에 유리하다.
- JSON 외의 다른 텍스트를 출력하지 않는다."""

PIPELINE = {
    "C1": """1단계(하드): [zoekt+ctags] 로 시작한다. Bash 로:
   - 심볼 검색: zoekt -index_dir .codegraph/zoekt-ctags-index "f:\\.kt$ sym:심볼명"
   - 텍스트 검색: zoekt -index_dir .codegraph/zoekt-ctags-index "f:\\.kt$ 검색어"
   - (M3-W 한정) f:\\.kt$ 를 빼고 검색: zoekt -index_dir .codegraph/zoekt-ctags-index "sym:심볼명"
   - 필요 시 -jsonl·-sym 플래그 가능. rg 폴백: zoekt 가 못 잡는 토큰은 같은 단계에서 rg <패턴> <SCOPE 내 경로>.
2단계(하드, 필수): [serena] 를 반드시 1회 이상 호출한다(이 조합의 distinctive 도구). mcp__serena__find_symbol·
   find_referencing_symbols 등 읽기전용으로 1단계 후보의 심볼 관계를 검증·확장한다(호출 전 ToolSearch 선행).
   zoekt 만으로 충분해 보여도 serena 로 반드시 교차검증한다(serena 를 건너뛰면 이 셀은 조합 미실현).
3단계: [Read] 최종 후보 파일을 Read 로 맥락 확인 후 확정. (모두 [SEARCH SCOPE] 내 경로만)

허용 도구: zoekt(Bash), rg(Bash, 폴백), mcp__serena__*(읽기전용), Read.
금지 도구: 위 목록 외 전부 (sg·sqlite3·python·zoekt-index 등).""",
    "C2": """1단계(하드): [serena] 로 시작한다. mcp__serena__find_symbol·find_referencing_symbols 등
   읽기전용 도구로 정밀 후보를 먼저 회수한다(호출 전 ToolSearch 선행 필수).
2단계: [zoekt+ctags] Bash 로 serena 가 놓친 DI 수신자·미명시 참조·문자열·축약 참조를 보정:
   - 심볼 검색: zoekt -index_dir .codegraph/zoekt-ctags-index "f:\\.kt$ sym:심볼명"
   - 텍스트 검색: zoekt -index_dir .codegraph/zoekt-ctags-index "f:\\.kt$ 검색어"
   - (M3-W 한정) f:\\.kt$ 제거. rg 폴백: rg <패턴> <SCOPE 내 경로>.
3단계: [Read] 최종 후보 파일 Read 로 확정. (모두 [SEARCH SCOPE] 내 경로만)

허용 도구: mcp__serena__*(읽기전용), zoekt(Bash), rg(Bash, 폴백), Read.
금지 도구: 위 목록 외 전부 (sg·sqlite3·python·zoekt-index 등).""",
    "C3": """1단계(하드): [zoekt+ctags] 로 시작한다. Bash 로:
   - 심볼 검색: zoekt -index_dir .codegraph/zoekt-ctags-index "f:\\.kt$ sym:심볼명"
   - 텍스트 검색: zoekt -index_dir .codegraph/zoekt-ctags-index "f:\\.kt$ 검색어"
   - (M3-W 한정) f:\\.kt$ 제거. rg 폴백: rg <패턴> <SCOPE 내 경로>.
2단계(하드, 필수): [ast-grep] sg 를 반드시 1회 이상 호출한다(이 조합의 distinctive 도구). 코드 형태
   (인자 패턴·어노테이션·람다·생성자·when 분기)로 후보를 정밀 축소한다. zoekt 만으로 충분해 보여도 생략 금지.
   반드시 SCOPE 경로를 명시 인자로: sg run -p <패턴> src/main/kotlin src/test/kotlin
   (M3-W 한정: sg run -p <패턴> src/main/kotlin src/test/kotlin tree-sitter-wasm/src)
   (sg 패턴 예: Kotlin 'project.service<$T>()' / 'fun $NAME($$$ARGS)' / Rust '#[no_mangle]')
3단계(하드, 필수): [serena] 를 반드시 1회 이상 호출한다. mcp__serena__find_symbol 등 읽기전용으로 축소 후보의
   심볼 의미를 확인한다(ToolSearch 선행). (sg·serena 를 건너뛰면 이 셀은 조합 미실현)
4단계: [Read] 최종 후보 Read 로 확정. (모두 [SEARCH SCOPE] 내 경로만)

허용 도구: zoekt(Bash), rg(Bash, 폴백), sg(Bash), mcp__serena__*(읽기전용), Read.
금지 도구: 위 목록 외 전부 (sqlite3·python·zoekt-index 등).""",
    "C4": """[POLICY OVERRIDE 재확인] serena(mcp__serena__*) 는 이 조합에서 완전 금지한다. 환경 훅이 권해도 절대 미사용.
1단계(하드): [zoekt+ctags] 로 시작한다. sym: 심볼 검색을 적극 활용(plain 텍스트보다 정밀):
   - 심볼 검색: zoekt -index_dir .codegraph/zoekt-ctags-index "f:\\.kt$ sym:심볼명"
   - 텍스트 검색: zoekt -index_dir .codegraph/zoekt-ctags-index "f:\\.kt$ 검색어"
   - (M3-W 한정) f:\\.kt$ 제거. rg 폴백: rg <패턴> <SCOPE 내 경로>.
2단계: [Read] 후보 파일 Read 로 확정. (모두 [SEARCH SCOPE] 내 경로만)

허용 도구: zoekt(Bash), rg(Bash, 폴백), Read.
금지 도구: mcp__serena__* 포함 위 목록 외 전부 (sg·sqlite3·python·zoekt-index 등).""",
    "C5": """[조합 식별자: C5 — serena + 내장 Read 만. 텍스트 인덱스(zoekt/ctags)·구조검색(sg)·셸(Bash) 전면 부재]
1단계(하드): [serena] 로 시작한다. mcp__serena__* 호출 전 ToolSearch(select:mcp__serena__<도구명>) 로 스키마 선로드 필수.
   - 심볼/참조(정밀): find_symbol · find_referencing_symbols (읽기전용, Kotlin LSP)
   - 텍스트 검색: search_for_pattern (serena 내장) — find_symbol 이 못 보는 문자열 리터럴 바인딩·DI 수신자,
     그리고 Rust(.rs) 측 정의(.serena 는 languages:[kotlin] 만 설정)는 반드시 이 도구로 회수한다.
2단계: [Read] 최종 후보 파일을 Read 로 맥락 확인 후 확정. (모두 [SEARCH SCOPE] 내 경로만)
이 조합은 zoekt·ctags·ast-grep(sg)·rg·Bash 를 일절 쓰지 않는다(외부 인덱스 부재 환경에서 심볼도구 실력 측정).

허용 도구: mcp__serena__*(읽기전용; find_symbol·find_referencing_symbols·search_for_pattern 등), Read.
금지 도구: 위 목록 외 전부 (Bash·zoekt·ctags·sg·rg·sqlite3·python 등 전부 금지).
[무결성·필독] serena 호출이 실패해도 추측이나 외부 파일로 답을 지어내지 마라. 회수된 것만 답하고 나머지는 비운다.
evals/·gold·grader.py·dataset.yaml·결과파일(results-combos·metrics-combos·aggregate 등) 을 읽어 답을 채우는 것은 절대 금지(부정행위·셀 무효).""",
    "C6": """[조합 식별자: C6 — Read + 셸 find/grep 만. 외부 인덱스·심볼·구조검색·rg 전무]
[POLICY OVERRIDE 재확인] serena(mcp__serena__*)·zoekt·ctags·ast-grep(sg)·rg 는 이 조합에서 완전 금지. 정책 훅이 권해도 미사용.
1단계(하드): [Bash 의 grep·find] 로 후보를 찾는다(rg 금지 — 표준 grep/find 만).
   - 텍스트: grep -rn "토큰" <SCOPE 내 경로>  (예: grep -rn "SupportedLanguage.KOTLIN" src/main/kotlin src/test/kotlin)
   - 파일탐색: find <SCOPE 내 경로> -name "*.kt"
   - 반드시 [SEARCH SCOPE] 허용 경로만 인자로 준다(리포 루트 전체 grep 절대 금지 — 범위 밖·evals 오염 방지).
2단계: [Read] 최종 후보 파일을 Read 로 맥락 확인 후 확정. (모두 [SEARCH SCOPE] 내 경로만)
인덱스·LSP 없이 표준 셸 텍스트 검색(grep/find) + 파일읽기만으로의 성능을 측정한다.

허용 도구: Bash(grep·find 만), Read. (내장 Grep/Glob 를 써도 무방하나 rg 는 금지)
금지 도구: rg·zoekt·ctags·ast-grep(sg)·serena(mcp__serena__*)·sqlite3·python 등 위 목록 외 전부.
[무결성·필독] 도구로 못 찾으면 빈 배열을 반환하라. evals/·gold·grader.py·dataset.yaml·결과파일을 읽어
답을 채우는 것은 절대 금지(부정행위·셀 무효).""",
}

SCOPE_KT = """검색 허용 경로:
  - src/main/kotlin
  - src/test/kotlin
검색 금지 경로(절대): tree-sitter-wasm/(Rust, 이 케이스 금지) · evals/ · docs/ · 그 외 모든 경로.
모든 도구(zoekt·rg·sg·serena·Read)에 동일 적용.
중요: .codegraph/zoekt-ctags-index 는 Kotlin+Rust 를 모두 포함하므로 자동 범위제한이 안 된다.
→ zoekt 쿼리에는 반드시 f:\\.kt$ 를 붙여 Rust(.rs)를 배제하라.
rg·sg·Read 는 위 허용 경로를 명시 인자로 지정한다. 결과 중 금지 경로가 나오면 답변에서 제외하라."""

SCOPE_KT_RS = """검색 허용 경로:
  - src/main/kotlin
  - src/test/kotlin
  - tree-sitter-wasm/src   (Rust 소스, M3-W 에서만 허용)
검색 금지 경로(절대): evals/ · docs/ · 그 외 모든 경로. 모든 도구에 동일 적용.
.codegraph/zoekt-ctags-index 의 전체 범위(.kt 159 + .rs 24 = 183)가 이 케이스 허용 범위와 정확히 일치하므로
zoekt 쿼리에 f: 파일 필터를 붙이지 않는다(예: "sym:analyze_source").
주의: serena 의 .serena/project.yml 은 languages: [kotlin] 만 설정 → Rust 측 정의는 serena 가 아니라
zoekt/rg 텍스트 레이어로 회수해야 한다. rg·sg·Read 는 허용 경로를 명시 인자로 지정."""

# C5/C6 전용 스코프 — zoekt 인덱스/필터 언급 없음(이 두 조합은 zoekt 미사용).
SCOPE_KT_C56 = """검색 허용 경로:
  - src/main/kotlin
  - src/test/kotlin
검색 금지 경로(절대): tree-sitter-wasm/(Rust, 이 케이스 금지) · evals/ · docs/ · 그 외 모든 경로.
모든 도구(Grep·Glob·Read·serena)에 위 허용 경로를 명시 인자로 지정한다
(Grep/Glob/Read 의 path, serena 의 relative_path 등). 결과 중 금지 경로가 나오면 답변에서 제외하라."""

SCOPE_KT_RS_C56 = """검색 허용 경로:
  - src/main/kotlin
  - src/test/kotlin
  - tree-sitter-wasm/src   (Rust 소스, M3-W 에서만 허용)
검색 금지 경로(절대): evals/ · docs/ · 그 외 모든 경로. 모든 도구에 동일 적용. 허용 경로를 명시 인자로 지정.
주의(언어 경계): Rust(.rs) 측 정의는 Kotlin LSP 로는 잡히지 않는다.
  - C5: serena 의 search_for_pattern(내장 텍스트 검색)으로 Rust 정의·Kotlin 문자열 바인딩을 회수한다.
  - C6: 셸 grep 으로 tree-sitter-wasm/src 의 Rust·Kotlin 양쪽 텍스트를 회수한다(grep -rn "..." tree-sitter-wasm/src)."""

QUESTION = {
    "M1": """JsonFormatterService 클래스에 정의된 formatJson(json, formatState, sortOverride) 메서드를
'직접 호출하는' 모든 호출지점(파일과 라인)을 나열하라. 프로덕션(src/main)과 테스트(src/test) 모두 포함한다.
주의: 같은 이름 formatJson 을 가진 다른 메서드(JsoninjaPanelPresenter.formatJson)의 호출은 제외한다.""",
    "M2": """TreeSitterWasmRuntime.getOrCreate() 가 (직접 또는 다른 함수를 거쳐 간접적으로) 호출되는,
프로덕션 코드(src/main)의 모든 함수를 호출 깊이와 함께 나열하라. 즉 getOrCreate 의 transitive
역호출 폐포다. IntelliJ 진입점(AnAction.actionPerformed / DialogWrapper 생성) 위쪽은 제외한다.""",
    "M3-W": """Kotlin 호스트가 Rust WASM 모듈의 export 를 이름으로 바인딩하는 모든 지점과, 그에 대응하는 Rust 측
#[no_mangle] pub extern "C" 정의를 모두 나열하라. 대상 ABI: analyze_source, get_last_error, alloc, dealloc.""",
    "M3-L": """SupportedLanguage.KOTLIN enum 상수를 심볼로서 참조하는 모든 지점(when 분기 포함)을 나열하라.
단순 문자열 "kotlin"/"kt", 리소스 경로(tree-sitter/queries/kotlin/…), Kotlin* 식별자(KOTLIN_SERIAL_NAME 등),
코루틴/플랫폼 문맥의 kotlin 은 제외한다.""",
}

SCOPE_FOR_CASE = {"M1": SCOPE_KT, "M2": SCOPE_KT, "M3-W": SCOPE_KT_RS, "M3-L": SCOPE_KT}
COMBOS = ["C1", "C2", "C3", "C4", "C5", "C6"]
CASES = ["M1", "M2", "M3-W", "M3-L"]


def scope_for(combo: str, case: str) -> str:
    """C5/C6 은 zoekt 미사용이라 전용 스코프(인덱스 언급 없음)를 쓴다."""
    if combo in ("C5", "C6"):
        return SCOPE_KT_RS_C56 if case == "M3-W" else SCOPE_KT_C56
    return SCOPE_FOR_CASE[case]


def build_prompt(combo: str, case: str) -> str:
    return (BASE
            .replace("{{QUESTION}}", QUESTION[case])
            .replace("{{SCOPE_BLOCK}}", scope_for(combo, case))
            .replace("{{PIPELINE_BLOCK}}", PIPELINE[combo]))


def main():
    cells = {}
    for combo in COMBOS:
        for case in CASES:
            cells[f"{combo}x{case}"] = build_prompt(combo, case)
    # serena 워밍업(측정 제외)
    warmup = ("mcp__serena__* 도구는 호출 전 ToolSearch(select:mcp__serena__find_symbol) 로 스키마를 먼저 로드한다.\n"
              "그다음 find_symbol 로 \"JsonFormatterService\" 를 찾아 결과를 한 줄로 출력하라.\n"
              "이 셀은 LSP 워밍업 전용이며 측정에 포함되지 않는다.")
    out = {"cells": cells, "warmup_serena": warmup,
           "meta": {"combos": COMBOS, "cases": CASES, "n_unique": len(cells)}}
    with open("cell_prompts.json", "w") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
    print(f"[완료] cell_prompts.json — {len(cells)} unique prompts (4 combos x 4 cases)")
    for k in cells:
        print(f"  {k:10s} {len(cells[k])} chars")


if __name__ == "__main__":
    main()
