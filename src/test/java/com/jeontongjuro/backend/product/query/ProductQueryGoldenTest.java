package com.jeontongjuro.backend.product.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 실데이터 스냅샷 골든 회귀 — 파싱 로직(설명 되돌림·수상 뱃지)이 정규식/조건 미세 변경만으로 조용히 회귀하는 것을
 * 막는다. {@code product_query_golden.json}은 운영 DB에서 뽑은 원문(input)과 스펙 기준 기대값(expected)을 담고
 * 있고(생성 근거는 파일 _meta), 이 테스트는 순수 함수에 input을 넣어 expected와 대조한다. DB 불필요.
 * <p>
 * ★골든을 코드 출력에 맞추지 않는다 — expected는 스펙으로 사람이 산정했고 독립 파이썬 레퍼런스로 교차검증했다.
 */
class ProductQueryGoldenTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private JsonNode golden() throws Exception {
        try (InputStream in = getClass().getResourceAsStream("/product_query_golden.json")) {
            assertThat(in).as("골든 리소스 존재").isNotNull();
            return MAPPER.readTree(in);
        }
    }

    @Test
    @DisplayName("설명 되돌림: 골든 input에 정책 적용 → expected(문장끝 6건 불변 · 소수점 2건 컷)")
    void descriptionGolden() throws Exception {
        JsonNode rows = golden().get("descriptions");
        assertThat(rows).isNotNull().isNotEmpty();
        for (JsonNode row : rows) {
            int idx = row.get("sourceRowIndex").asInt();
            String input = row.get("input").asText();
            String expected = row.get("expected").asText();
            assertThat(DescriptionTruncationPolicy.apply(input))
                    .as("sr%d 설명 되돌림", idx)
                    .isEqualTo(expected);
        }
    }

    @Test
    @DisplayName("수상 뱃지: 골든 input에 파서 적용 → expected(대회명오탐·음역·절단·괄호/연도 쉼표 전부)")
    void awardBadgeGolden() throws Exception {
        JsonNode rows = golden().get("awardBadges");
        assertThat(rows).isNotNull().isNotEmpty();
        for (JsonNode row : rows) {
            int idx = row.get("sourceRowIndex").asInt();
            String input = row.get("input").asText();
            String expected = row.get("expected").asText();
            assertThat(AwardGradeParser.parse(input))
                    .as("sr%d 수상 뱃지", idx)
                    .isEqualTo(expected);
        }
    }
}
