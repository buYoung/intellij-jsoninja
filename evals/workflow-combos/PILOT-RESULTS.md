# 파일럿 결과 (대표 5셀, N=1) — 하니스 end-to-end 실증

> 목적: 48셀 본실행 전에 프롬프트→도구→스키마→grader→메트릭파서→조인→거버넌스 전 파이프라인을
> 실데이터로 검증. 2026-06-01 실행(workflow wf_f01bd8f1-252, sonnet 6에이전트=워밍업+5셀).

## 1. 품질·메트릭 (metrics-combos.csv)

| 셀 | recall | precision | out_tok | cacheR | tool_calls | wall(s) |
|----|--------|-----------|---------|--------|-----------|---------|
| C1×M3-L (serena) | 1.000 | 1.000 | 7114 | 699,661 | 27 | 129.5 |
| C2×M2 (serena FQN) | 0.875 (7/8) | 1.000 | 5145 | 860,857 | 32 | 158.2 |
| C3×M3-W (sg 조합) | 1.000 | 0.667 | 2852 | 113,660 | 10 | 53.9 |
| C4×M1 (텍스트) | 1.000 | 1.000 | 4880 | 123,651 | 14 | 73.5 |
| C4×M2 (텍스트 file:line) | 0.750 (6/8) | 1.000 | 3222 | 297,197 | 15 | 89.5 |

## 2. 검증된 사실 (PASS)
- **하니스 작동**: 실제 sonnet 셀이 `file` 필드 채운 answers 를 스키마대로 산출.
- **M2 span 해소 실증**: C4×M2(텍스트 file:line)·C2×M2(FQN) 모두 **adjudication 0** — 제출 (file:line)이 전부
  결정적으로 함수에 매핑됨(예: schedulePreview:92→span[91,131], syncLanguage:68→[67,85]). 누락은 둘 다
  depth-5 말단(ConvertTypeDialogPresenter.<init>·syncLanguage) = baseline 동일 약점(채점 아티팩트 아님).
- **거버넌스 전 항목 깨끗**: 전 셀 model=claude-sonnet-4-6(model_ok), opus 생성 0, advisor tool_use 0,
  zoekt-index 0(측정격리), evals/ 참조 0, 파일쓰기 0, isolation_violation 0.
- **advisor_present=annotation 은 양성**: `advisorModel:claude-opus-4-8` 어노테이션은 존재하나 opus 실호출 0
  → "opus 검증 비참여" 충족. 실질 거버넌스 신호 = model_ok + advisor tool_use 0.
- **메트릭 파서**: 워크플로 transcript 에서 토큰·모델·tool_calls·wall·격리 추출 정상.
- **transcript 지문매칭**: 5셀 전부 정확히 셀ID 로 매핑.
- **C1×M3-L recall/precision 1.0**: f:\.kt$ 필터로 Rust SupportedLanguage 오탐 0 — 스코프 격리 성공.

## 3. 드러난 핵심 리스크 (결정 필요)
**distinctive 도구 건너뜀 → 조합 퇴화**:
- C3×M3-W: sg **0회 호출**(tools=Bash,Read만) → C3(zoekt→sg→serena→read)가 C4 로 퇴화.
- C1×M3-L: serena **0회 호출** → C1(zoekt→serena→read)이 C4 로 퇴화.
- 원인: 단계 순서를 "권고"로 두니 zoekt+read 로 충분하면 에이전트가 sg·serena 를 건너뜀.
- 영향: 이대로 48셀을 돌리면 C1≡C3≡C4 로 수렴해 "도구 조합 비교"가 무의미해질 수 있음.

## 4. 확정된 해소 (사용자 의도 반영)
- **하이브리드 채점 확정**: 결정적 grader 가 캐논 수치 산출(파일럿 adjudication 0 = 전부 결정적),
  opus 는 모호 케이스 판정·리뷰·서술만. "opus는 채점·리뷰" 충족 + N=3 재현성 보존.
- **48셀 substrate = Workflow agent()**: 토큰·모델 기록 확인, Agent 폴백 불요.

## 5. 미결정 (48셀 진입 전)
- **distinctive 도구 정책**: §3 — 각 조합이 자기 distinctive 도구(C3=sg, C1/C2=serena)를 실제로 쓰게
  강제할지(조합 비교 의미 확보) vs 자유 에이전트로 두고 "건너뜀률(stickiness)"을 결과로 보고할지.
