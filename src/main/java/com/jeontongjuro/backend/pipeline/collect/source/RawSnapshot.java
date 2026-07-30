package com.jeontongjuro.backend.pipeline.collect.source;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

/**
 * 한 데이터셋의 수집 스냅샷.
 *
 * 계약(순서 보장 — source_row_index의 근거):
 * rows는 원본 data 배열을 페이지 오름차순으로 이어붙인 전역 병합 리스트여야 하며,
 * 리스트 인덱스 == 원본 totalCount 순서의 전역 0-based 위치다.
 * 페이지 내 순서·페이지 간 순서 모두 원본과 일치해야 하고, 수집 도착 순서로 채우면 안 된다.
 *
 * @param dataset     수집 대상
 * @param collectedAt 수집 시각(UTC Instant) — collected_at 컬럼으로 보존된다.
 *                    snapshot_date는 별개의 논리 라벨(주입 우선, 미주입 시 이 값의 UTC 날짜)
 * @param totalCount  원천이 신고한 totalCount
 * @param rows        원본 순서 전역 병합 행 목록(각 원소 = 원본 JSON 객체 그대로)
 */
public record RawSnapshot(
        com.jeontongjuro.backend.pipeline.collect.RawDataset dataset,
        Instant collectedAt,
        long totalCount,
        List<JsonNode> rows
) {
}
