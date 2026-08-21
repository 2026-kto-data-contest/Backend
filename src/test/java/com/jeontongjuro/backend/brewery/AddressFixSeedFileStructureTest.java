package com.jeontongjuro.backend.brewery;

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
 * 주소 보정 시드(address_fix_seed.json)의 구조 봉인 — DB·네트워크 없이 리소스 JSON만 검증한다
 * (KakaoPlaceSeedFileStructureTest·ProductExclusionSeedStructureTest 선례). 이 시드는 raw 주소 오류를
 * 보정하는 append-only 목록이라, 조용히 늘거나 줄거나 키가 바뀌면 실패시켜 사람 검토를 강제한다.
 * <p>
 * 세 가지를 고정한다:
 * <ol>
 *   <li>배열 키는 {@code fixes}(★{@code entries} 아님)이고 4행이다.</li>
 *   <li>각 fix는 딱 4키(brewery_id·raw_address·fixed_address·reason)이고 전부 비어있지 않으며
 *       brewery_id는 BRW-xxx 형식이다.</li>
 *   <li>현재 보정 대상 = {BRW-004, BRW-043, BRW-047, BRW-051} 정확히 이 4곳(중복 없음).</li>
 * </ol>
 */
class AddressFixSeedFileStructureTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** fixes 각 항목 허용 키(딱 이 4개). */
    private static final Set<String> ALLOWED =
            Set.of("brewery_id", "raw_address", "fixed_address", "reason");

    /** 현재 보정 대상 brewery_id(append-only 봉인 — 늘거나 줄면 실패). */
    private static final Set<String> EXPECTED_IDS =
            Set.of("BRW-004", "BRW-043", "BRW-047", "BRW-051");

    @Test
    @DisplayName("주소 보정 시드: fixes 4행 · 키는 딱 4개 · brewery_id는 BRW-xxx · 대상 = {004,043,047,051}")
    void addressFixSeedShape() throws Exception {
        JsonNode fixes = readFixes("/address_fix_seed.json");
        assertThat(fixes.isArray()).isTrue();
        assertThat(fixes).hasSize(4);

        List<String> ids = new ArrayList<>();
        for (JsonNode fix : fixes) {
            List<String> keys = new ArrayList<>();
            fix.fieldNames().forEachRemaining(keys::add);
            assertThat(keys).as("fixes 키는 허용 4개뿐: %s", keys)
                    .containsExactlyInAnyOrderElementsOf(ALLOWED);

            String breweryId = fix.get("brewery_id").asText();
            assertThat(breweryId).as("brewery_id는 BRW-xxx 형식").matches("BRW-\\d{3}");
            assertThat(fix.get("raw_address").asText()).as("raw_address 비어있지 않음").isNotBlank();
            assertThat(fix.get("fixed_address").asText()).as("fixed_address 비어있지 않음").isNotBlank();
            assertThat(fix.get("reason").asText()).as("reason 비어있지 않음").isNotBlank();
            ids.add(breweryId);
        }
        assertThat(ids).as("brewery_id 중복 없음").doesNotHaveDuplicates();
        assertThat(ids).as("현재 보정 대상 4곳").containsExactlyInAnyOrderElementsOf(EXPECTED_IDS);
    }

    private JsonNode readFixes(String classpath) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(classpath)) {
            assertThat(in).as("시드 리소스 존재: %s", classpath).isNotNull();
            JsonNode fixes = objectMapper.readTree(in).get("fixes");
            assertThat(fixes).as("fixes 배열").isNotNull();
            return fixes;
        }
    }
}
