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
 * brewery 채번원장(brewery_id_ledger.json)의 구조 봉인 — DB·네트워크 없이 리소스 JSON만 검증한다
 * (AddressFixSeedFileStructureTest 선례). ★파일명이 {@code *_seed.json}이 아니라서 클래스명에서
 * "Seed"를 뺐다 — {@code _meta.purpose}: "brewery 서비스 마스터 채번원장(불변 리소스)". 성격은 append-only
 * 시드가 아니라 BRW-xxx ↔ 자연키 봉인 매핑이지만, 로드되는 리소스 JSON이라는 점은 같다.
 * {@link BreweryMasterLoadConsistencyTest}는 DB 적재 정합(59행·연속·유니크)을 검증할 뿐 JSON 키 구조는
 * 봉인하지 않는다 — 이 테스트가 그 빈틈을 메운다.
 * <p>
 * {@code _meta.policy.immutability}: "append-only 봉인(골든과 동급). 파일이 이미 존재하면 재생성 절대
 * 금지 — 로드해 검증만." — 이 리소스가 가장 강한 불변 계약을 갖는다.
 * <p>
 * 두 가지를 고정한다:
 * <ol>
 *   <li>배열 키는 {@code entries}이고 59행, 각 항목은 딱 5키
 *       (brewery_id·business_name·natural_key·natural_key_type·norm).</li>
 *   <li>brewery_id는 BRW-001~BRW-059 연속·중복 없음, natural_key는 중복 없음,
 *       natural_key_type은 현재 전량 {@code business_name}이다(_meta.natural_key_reason 근거).</li>
 * </ol>
 */
class BreweryIdLedgerFileStructureTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** entries 각 항목 허용 키(딱 이 5개). */
    private static final Set<String> ALLOWED = Set.of(
            "brewery_id", "business_name", "natural_key", "natural_key_type", "norm");

    @Test
    @DisplayName("brewery 채번원장: entries 59행 · 키는 딱 5개 · brewery_id는 BRW-001~059 연속·중복 없음 · "
            + "natural_key 중복 없음 · natural_key_type=business_name")
    void breweryIdLedgerShape() throws Exception {
        JsonNode entries = readEntries("/brewery_id_ledger.json");
        assertThat(entries.isArray()).isTrue();
        assertThat(entries).hasSize(59);

        List<String> breweryIds = new ArrayList<>();
        List<String> naturalKeys = new ArrayList<>();
        for (JsonNode e : entries) {
            List<String> keys = new ArrayList<>();
            e.fieldNames().forEachRemaining(keys::add);
            assertThat(keys).as("entries 키는 허용 5개뿐: %s", keys)
                    .containsExactlyInAnyOrderElementsOf(ALLOWED);

            String breweryId = e.get("brewery_id").asText();
            assertThat(breweryId).as("brewery_id는 BRW-xxx 형식").matches("BRW-\\d{3}");
            assertThat(e.get("business_name").asText()).as("business_name 비어있지 않음").isNotBlank();
            assertThat(e.get("natural_key").asText()).as("natural_key 비어있지 않음").isNotBlank();
            assertThat(e.get("natural_key_type").asText()).as("natural_key_type은 현재 business_name")
                    .isEqualTo("business_name");
            breweryIds.add(breweryId);
            naturalKeys.add(e.get("natural_key").asText());
        }
        assertThat(breweryIds).as("brewery_id 중복 없음").doesNotHaveDuplicates();
        assertThat(naturalKeys).as("natural_key 중복 없음").doesNotHaveDuplicates();

        List<String> expectedIds = new ArrayList<>();
        for (int i = 1; i <= 59; i++) {
            expectedIds.add(String.format("BRW-%03d", i));
        }
        assertThat(breweryIds).as("BRW-001~059 연속·정확히 59개").containsExactlyInAnyOrderElementsOf(expectedIds);
    }

    private JsonNode readEntries(String classpath) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(classpath)) {
            assertThat(in).as("리소스 존재: %s", classpath).isNotNull();
            JsonNode entries = objectMapper.readTree(in).get("entries");
            assertThat(entries).as("entries 배열").isNotNull();
            return entries;
        }
    }
}
