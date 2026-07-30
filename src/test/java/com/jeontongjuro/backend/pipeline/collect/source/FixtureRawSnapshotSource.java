package com.jeontongjuro.backend.pipeline.collect.source;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeontongjuro.backend.pipeline.collect.RawDataset;
import java.io.IOException;
import java.io.InputStream;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * 골든 픽스처(src/test/resources/golden/*)를 수집 소스로 주입하는 테스트 구현.
 * 골든 raw 파일은 {_meta, data} 래퍼이며 data 배열이 이미 "원본 totalCount 순서의 전역 병합본"이므로
 * (수집기가 페이지 순서대로 병합해 봉인한 산출물) 배열 순서를 그대로 RawSnapshot 순서 계약으로 넘긴다.
 */
public class FixtureRawSnapshotSource implements RawSnapshotSource {

    private final ObjectMapper objectMapper;

    public FixtureRawSnapshotSource(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public static String fixturePath(RawDataset dataset) {
        return switch (dataset) {
            case BREWERY -> "/golden/20260728_brewery_raw.json";
            case PRODUCT -> "/golden/20260728_product_raw.json";
        };
    }

    @Override
    public RawSnapshot fetch(RawDataset dataset) {
        JsonNode doc = readFixture(fixturePath(dataset));
        JsonNode meta = doc.get("_meta");
        Instant collectedAt = Instant.parse(meta.get("collected_at").asText());
        long totalCount = meta.get("totalCount").asLong();
        List<JsonNode> rows = new ArrayList<>();
        doc.get("data").forEach(rows::add); // 배열 순서 그대로 = 원본 전역 순서
        return new RawSnapshot(dataset, collectedAt, totalCount, List.copyOf(rows));
    }

    private JsonNode readFixture(String classpathLocation) {
        try (InputStream in = FixtureRawSnapshotSource.class.getResourceAsStream(classpathLocation)) {
            if (in == null) {
                throw new IllegalStateException("골든 픽스처 없음: " + classpathLocation);
            }
            return objectMapper.readTree(in);
        } catch (IOException e) {
            throw new IllegalStateException("골든 픽스처 읽기 실패: " + classpathLocation, e);
        }
    }
}
