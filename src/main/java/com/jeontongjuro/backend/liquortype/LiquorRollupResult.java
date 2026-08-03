package com.jeontongjuro.backend.liquortype;

/**
 * 주종 롤업(6단계) 1회 실행 요약. MANUAL 시드 적재 → AUTO 추론 순서의 두 결과를 그대로 담는다(재계산 없음).
 * ★분포 수치는 보고용이지 골든이 아니다 — 봉인하지 않는다.
 */
public record LiquorRollupResult(LiquorManualSeedLoadService.LoadResult manual,
                                 LiquorInferenceService.InferResult infer) {
}
