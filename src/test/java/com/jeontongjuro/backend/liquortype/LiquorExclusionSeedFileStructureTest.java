package com.jeontongjuro.backend.liquortype;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 주종 판정 제외 시드(liquor_exclusion_seed.json)의 구조 봉인 — DB·네트워크 없이 리소스 JSON만 검증한다
 * (ProductExclusionSeedStructureTest 선례). append-only 제외 목록이라, 조용히 늘거나 줄거나 키가 바뀌면
 * 실패시켜 사람 검토를 강제한다.
 * <p>
 * 두 가지를 고정한다:
 * <ol>
 *   <li>배열 키는 {@code entries}이고 12행이다.</li>
 *   <li>각 항목은 딱 4키(source_row_ref·liquorType·reason·decided_at)이고, source_row_ref는 정수,
 *       liquorType은 {@link LiquorType} 6종 중 하나다.</li>
 * </ol>
 * ★{@code source_row_ref} 단독은 유니크하지 않다 — 같은 제품 행이 서로 다른 liquorType 오탐 2건으로
 * 각각 배제될 수 있다(예: source_row_ref=464가 과실주·청주 두 행으로 존재). 자연키는
 * {@code (source_row_ref, liquorType)} 쌍이며, 이 쌍의 중복만 금지한다.
 */
class LiquorExclusionSeedFileStructureTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** entries 각 항목 허용 키(딱 이 4개). */
    private static final Set<String> ALLOWED = Set.of("source_row_ref", "liquorType", "reason", "decided_at");

    @Test
    @DisplayName("주종 제외 시드: entries 12행 · 키는 딱 4개 · source_row_ref 정수 · liquorType은 6종 중 하나")
    void liquorExclusionSeedShape() throws Exception {
        JsonNode entries = readEntries("/liquor_exclusion_seed.json");
        assertThat(entries.isArray()).isTrue();
        assertThat(entries).hasSize(12);

        List<String> naturalKeys = new ArrayList<>();
        for (JsonNode e : entries) {
            List<String> keys = new ArrayList<>();
            e.fieldNames().forEachRemaining(keys::add);
            assertThat(keys).as("entries 키는 허용 4개뿐: %s", keys)
                    .containsExactlyInAnyOrderElementsOf(ALLOWED);

            assertThat(e.get("source_row_ref").isInt()).as("source_row_ref는 정수").isTrue();
            assertThat(e.get("liquorType").asText()).as("liquorType은 LiquorType 6종 중 하나")
                    .isIn("탁주", "약주", "청주", "증류주", "과실주", "기타");
            assertThat(e.get("reason").asText()).as("reason 비어있지 않음").isNotBlank();
            assertThat(e.get("decided_at").asText()).as("decided_at 비어있지 않음").isNotBlank();

            naturalKeys.add(e.get("source_row_ref").asInt() + "|" + e.get("liquorType").asText());
        }

        assertThat(naturalKeys).as("자연키 (source_row_ref, liquorType) 쌍은 중복 없음")
                .doesNotHaveDuplicates();
    }

    private JsonNode readEntries(String classpath) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(classpath)) {
            assertThat(in).as("시드 리소스 존재: %s", classpath).isNotNull();
            JsonNode entries = objectMapper.readTree(in).get("entries");
            assertThat(entries).as("entries 배열").isNotNull();
            return entries;
        }
    }
}
