# workflow-combos 벤치마크 — 진행 상태 (compact 대비 resume 가이드)

> 이 문서 하나로 compact 이후 작업을 정확히 이어갈 수 있다. 상세설계 [`DESIGN.md`], 프롬프트 [`prompt-templates.md`],
> gold·baseline 은 `../codegraph-vs-serena/`. **현재 상태: 하니스 완성·파일럿 검증 끝. 48셀 본실행 대기(사용자 "시작" 신호 + compact 후).**

## 0. 한 줄
도구 단일호출 벤치마크(`../codegraph-vs-serena`)와 달리 **도구 조합(파이프라인) 4종 C1~C4 × 4케이스(M1·M2·M3-W·M3-L) × N=3 = 48셀**을 같은 gold·blind·±1 채점으로 측정. sonnet 검증·opus 채점/리뷰.

## 1. 확정 결정 (전부 반영됨)
- **채점 = 하이브리드**: 결정적 grader.py 가 캐논 recall/precision/F1 산출(파일럿 adjudication 0 = 전부 결정적), opus 는 모호 케이스 판정 + 리뷰/서술만. "opus는 채점·리뷰" 충족 + N=3 재현성.
- **distinctive 도구 강제**(사용자 결정): C1=serena≥1, C2=serena-first, C3=sg≥1 & serena≥1, C4=zoekt-only. first-tool 도 하드. → 조합 차별화 보장(파일럿에서 미강제 시 C1≡C3≡C4 퇴화 확인). stickiness(실호출수)도 기록.
- **substrate = Workflow agent()**: 토큰·모델 transcript 기록 확인. model:'sonnet' 강제. opus 검증 비참여(advisorModel 어노테이션은 양성 — opus 생성·advisor tool_use 0 실측).
- **측정 격리**: zoekt+ctags 인덱스 사전빌드(셀 밖). 셀 내 인덱싱 금지. serena 워밍업 셀(측정 제외) 선행.
- **N=3**: 셀당 3회, combo×case 중앙값+평균±SD.

## 2. ▶ 본실행 절차 (사용자 "시작" 시 — compact 후 여기부터)

> **★ 완료(2026-06-01)**: 48셀 본실행·채점·결과문서 끝. 결과 = [`results-combos.md`].
> - Run ID: `wf_5e31d1d3-f29` (49 agent, 524초, 1.41M 토큰, 700 tool-use).
> - 거버넌스 깨끗(48/48): model_ok 전부 sonnet, opus_gen 0, advisor_tu 0, iso 0, 필수도구 미준수 0.
> - 핵심: 조합 순이득 = M2 recall 0.75→0.875, M3-L precision 0.76→1.00 뿐. serena 끼우면 M3-W 정밀도 하락.
>   최단순 C4(zoekt+ctags→read)가 비용·속도 최고이며 품질 동급. 상세 [`results-combos.md`].
> - adjudication 18건(C2×M1×r3 line_boundary_pm1)은 §9 에서 양성 해소(Kuhn 1:1 매칭 bijection 확인, 점수 무변동).
> - 산출: metrics-combos.csv(48행), aggregate-combos.csv(N=3), results-combos.md, ../codegraph-vs-serena/results.md 에 링크.

```
# (1) 48셀 실행 — Workflow 도구로 launch (백그라운드, 완료 알림 옴)
Workflow({scriptPath: "evals/workflow-combos/_full_workflow.js"})
#   = serena 워밍업 1셀(측정제외) + 48 측정셀(16 unique x N=3) sonnet 병렬.
#   주의: distinctive 강제로 셀당 도구호출↑ → 토큰·시간 파일럿보다 큼.

# (2) 완료되면 채점 (출력파일·transcript dir 은 완료 알림에 나옴)
cd evals/workflow-combos
python3 run_grading.py --result <48run_output_file> --transcript-dir <48run_transcript_dir>
#   → metrics-combos.csv(48행: 품질+토큰+stickiness+준수) + aggregate-combos.csv(N=3) + 거버넌스/준수 요약 + adjudication-needed.jsonl

# (3) 거버넌스 확인: 필수도구 미준수 셀 0, opus_gen 0, iso 0, model_ok 전부 True.
#     미준수 셀 있으면 해당 셀만 재실행(_full_workflow.js resume 또는 부분 워크플로).

# (4) opus 리뷰/adjudication: adjudication-needed.jsonl(있으면) 판정 + results-combos.md 작성
#     (스코어보드 N=3 + 단일도구 baseline 대비 delta + 비용 + stickiness + 안정성). 채점은 opus 가 재계산 금지(grader 캐논 동결).

# (5) ../codegraph-vs-serena/results.md 에 조합 섹션 링크.
```

## 3. 파일럿 결과 (검증 완료 — [`PILOT-RESULTS.md`])
대표 5셀 N=1 실측: C1×M3-L 1.0/1.0, C2×M2 0.875/1.0, C3×M3-W 1.0/0.667, C4×M1 1.0/1.0, C4×M2 0.75/1.0.
파이프라인 end-to-end 작동, M2 span 해소 실증(텍스트 file:line→함수 결정매핑, adjudication 0), 거버넌스 전항목 깨끗.

## 4. 하니스 파일 (전부 작성·검증 완료)
- `build_prompts.py` → `cell_prompts.json` : 16 프롬프트 단일소스(distinctive 강제·first-tool·ToolSearch 선행·f:\.kt$·file 스키마).
- `gen_workflow.py` : cell_prompts → 워크플로 JS 생성기. `python3 gen_workflow.py full > _full_workflow.js` (48셀), `pilot`(5셀).
- `_full_workflow.js` : ▶ 본실행 launch 대상 (48셀, node --check 통과, distinctive 12회씩 확인).
- `grader.py` : 품질 단독 채점기. 내장 _FROZEN_GOLD(PyYAML 불요). **M2 span 해소 추가**(M2_GOLD_SPANS/M2_TRAP_SPANS, serena 실측 v1.12.1 범위). `--answers-dir DIR`.
- `metrics_parser.py` : 토큰·모델·tool_calls·wall·격리 추출. `--manifest`(배열 [{cell_id,transcript_path}]).
- `run_grading.py` : 채점 오케스트레이터(answers→grader→metrics→cell_id 조인→stickiness/준수→N=3 집계). transcript 지문매칭.
- `stats_governance.py` : 보조(self-test 12 통과). **미완: 품질 재계산 제거** — 현재 run_grading 가 단일 정본(grader=품질, metrics_parser=메트릭)이라 stats_governance.grade 는 미사용. 필요시 aggregate 만 쓰거나 정리.
- `PROVENANCE-combos.txt` : 인덱스(combos-src_v16, sha f15863a6…, 5059심볼) 기록.
- 인덱스: `.codegraph/zoekt-ctags-index` (사전빌드 완료, 재빌드 금지).

## 5. 미해결/주의
- (사소) stats_governance.py 품질 재계산 로직 잔존 — run_grading 가 정본이라 무해하나 정리 권장.
- 48셀 비용 큼(distinctive 강제). ultracode 라 진행하되 사용자에게 규모 사전고지함.
- 필수도구 미준수 셀은 재실행(조합 미실현). run_grading 가 자동 플래그.
