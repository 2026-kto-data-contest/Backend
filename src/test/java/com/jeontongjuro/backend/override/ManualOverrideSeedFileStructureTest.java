package com.jeontongjuro.backend.override;

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
 * 수동 오버라이드 시드(manual_override_seed.json)의 구조 봉인 — DB·네트워크 없이 리소스 JSON만 검증한다
 * (AddressFixSeedFileStructureTest 선례). {@link ManualOverrideSeedLoadTest}는 DB 적재 정합(FK·멱등)을
 * 검증할 뿐 JSON 키 구조는 봉인하지 않는다 — 이 테스트가 그 빈틈을 메운다.
 * <p>
 * ★{@code _meta.schema}는 7키만 선언하지만(seq 누락) 실제 엔트리는 14행 전부 <b>8키</b>다.
 * 봉인은 {@code _meta}가 아니라 실파일 8키를 기준으로 한다.
 * <p>
 * 두 가지를 고정한다:
 * <ol>
 *   <li>배열 키는 {@code entries}이고 14행, 각 항목은 딱 8키
 *       (brewery_id·match_key·match_key_kind·override_type·reason·recheck_flag·seq·source_raw_name).</li>
 *   <li>seq는 1~14를 중복 없이 채우는 정수, match_key_kind는 {@link MatchKeyKind} 2종,
 *       override_type은 {@link OverrideType} 2종, recheck_flag는 boolean, brewery_id는 BRW-xxx.</li>
 * </ol>
 */
class ManualOverrideSeedFileStructureTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** entries 각 항목 허용 키(딱 이 8개 — ★_meta.schema의 7키와 다름, seq 포함). */
    private static final Set<String> ALLOWED = Set.of(
            "brewery_id", "match_key", "match_key_kind", "override_type",
            "reason", "recheck_flag", "seq", "source_raw_name");

    @Test
    @DisplayName("수동 오버라이드 시드: entries 14행 · 키는 딱 8개(seq 포함, _meta.schema와 다름) · "
            + "seq 1~14 중복 없음 · match_key_kind/override_type 2종 · brewery_id는 BRW-xxx")
    void manualOverrideSeedShape() throws Exception {
        JsonNode entries = readEntries("/manual_override_seed.json");
        assertThat(entries.isArray()).isTrue();
        assertThat(entries).hasSize(14);

        List<Integer> seqs = new ArrayList<>();
        for (JsonNode e : entries) {
            List<String> keys = new ArrayList<>();
            e.fieldNames().forEachRemaining(keys::add);
            assertThat(keys).as("entries 키는 허용 8개뿐: %s", keys)
                    .containsExactlyInAnyOrderElementsOf(ALLOWED);

            assertThat(e.get("seq").isInt()).as("seq는 정수").isTrue();
            assertThat(e.get("match_key_kind").asText()).as("match_key_kind는 BREWERY_NORM 또는 PRODUCT_NAME")
                    .isIn("BREWERY_NORM", "PRODUCT_NAME");
            assertThat(e.get("override_type").asText()).as("override_type은 NAME_MAP 또는 ROW_PIN")
                    .isIn("NAME_MAP", "ROW_PIN");
            assertThat(e.get("recheck_flag").isBoolean()).as("recheck_flag는 boolean").isTrue();
            assertThat(e.get("brewery_id").asText()).as("brewery_id는 BRW-xxx 형식").matches("BRW-\\d{3}");
            assertThat(e.get("match_key").asText()).as("match_key 비어있지 않음").isNotBlank();
            assertThat(e.get("reason").asText()).as("reason 비어있지 않음").isNotBlank();
            assertThat(e.get("source_raw_name").asText()).as("source_raw_name 비어있지 않음").isNotBlank();
            seqs.add(e.get("seq").asInt());
        }
        assertThat(seqs).as("seq는 1~14 중복 없이").containsExactlyInAnyOrder(
                1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14);
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
