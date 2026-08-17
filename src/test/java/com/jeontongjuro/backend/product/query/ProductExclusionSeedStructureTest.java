package com.jeontongjuro.backend.product.query;

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
 * 제품 원본오류 제외 시드의 구조 봉인 — DB·네트워크 없이 리소스 JSON만 검증한다. 두 가지를 고정한다:
 * <ol>
 *   <li>entries 각 항목은 딱 3키(source_row_index·reason·decided_at)이고 source_row_index는 정수다.</li>
 *   <li>현재 제외 대상은 sr 1142(BRW-049 청명주) 1건이다 — 시드가 조용히 늘거나 줄면 실패시켜 검토를 강제한다.</li>
 * </ol>
 */
class ProductExclusionSeedStructureTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private static final Set<String> ALLOWED = Set.of("source_row_index", "reason", "decided_at");

    @Test
    @DisplayName("제외 시드: entries 키는 딱 3개 · source_row_index 정수 · 현재 제외 대상 = {1142}")
    void exclusionSeedShape() throws Exception {
        JsonNode entries = readEntries("/product_exclusion_seed.json");
        assertThat(entries.isArray()).isTrue();
        List<Integer> refs = new ArrayList<>();
        for (JsonNode e : entries) {
            List<String> keys = new ArrayList<>();
            e.fieldNames().forEachRemaining(keys::add);
            assertThat(keys).as("entries 키는 허용 3개뿐: %s", keys)
                    .containsExactlyInAnyOrderElementsOf(ALLOWED);
            assertThat(e.get("source_row_index").isInt()).as("source_row_index는 정수").isTrue();
            assertThat(e.get("reason").asText()).isNotBlank();
            refs.add(e.get("source_row_index").asInt());
        }
        assertThat(refs).as("현재 제외 대상은 sr 1142 1건").containsExactly(1142);
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
