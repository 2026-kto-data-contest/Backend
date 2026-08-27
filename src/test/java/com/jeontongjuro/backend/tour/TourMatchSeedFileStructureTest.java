package com.jeontongjuro.backend.tour;

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
 * TourAPI 매칭 시드(tour_match_seed.json)의 구조 봉인 — DB·네트워크 없이 리소스 JSON만 검증한다
 * (AddressFixSeedFileStructureTest·KakaoPlaceSeedFileStructureTest 선례).
 * <p>
 * ★이 파일은 다른 시드와 최상위 구조가 다르다: {@code _meta}·{@code _removed}가 객체가 아니라 <b>문자열</b>이고,
 * 배열 필드명도 {@code entries}가 아니라 <b>{@code seeds}</b>다. 이 테스트는 {@code seeds}만 읽는다.
 * <p>
 * 두 가지를 고정한다:
 * <ol>
 *   <li>배열 키는 {@code seeds}이고 19행, 각 항목은 딱 3키(brewery_id·content_id·source_title).</li>
 *   <li>entries에 <b>mapx·mapy·lat·lng·latitude·longitude·addr1·addr2·address·title·dist·distance
 *       키가 하나도 없다</b> — 좌표·주소 재도입 차단(200m 접지는 라이브 조회로만 한다).</li>
 * </ol>
 */
class TourMatchSeedFileStructureTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** seeds 각 항목 허용 키(딱 이 3개). */
    private static final Set<String> ALLOWED = Set.of("brewery_id", "content_id", "source_title");

    /** seeds에 절대 있으면 안 되는 키(좌표·주소 재도입 차단 — 200m 접지는 라이브 조회 전용). */
    private static final Set<String> FORBIDDEN = Set.of(
            "mapx", "mapy", "lat", "lng", "latitude", "longitude",
            "addr1", "addr2", "address", "title", "dist", "distance");

    @Test
    @DisplayName("TourAPI 매칭 시드: seeds 19행 · 키는 딱 3개 · 금지 키 0건 · brewery_id는 BRW-xxx·중복 없음")
    void tourMatchSeedShape() throws Exception {
        JsonNode seeds = readSeeds("/tour_match_seed.json");
        assertThat(seeds.isArray()).isTrue();
        assertThat(seeds).hasSize(19);

        List<String> breweryIds = new ArrayList<>();
        for (JsonNode seed : seeds) {
            List<String> keys = new ArrayList<>();
            seed.fieldNames().forEachRemaining(keys::add);
            assertThat(keys).as("seeds 키는 허용 3개뿐: %s", keys)
                    .containsExactlyInAnyOrderElementsOf(ALLOWED);
            for (String forbidden : FORBIDDEN) {
                assertThat(seed.has(forbidden)).as("금지 키 '%s'는 없어야", forbidden).isFalse();
            }

            String breweryId = seed.get("brewery_id").asText();
            assertThat(breweryId).as("brewery_id는 BRW-xxx 형식").matches("BRW-\\d{3}");
            assertThat(seed.get("content_id").asText()).as("content_id 비어있지 않음").isNotBlank();
            assertThat(seed.get("source_title").asText()).as("source_title 비어있지 않음").isNotBlank();
            breweryIds.add(breweryId);
        }
        assertThat(breweryIds).as("brewery_id 중복 없음").doesNotHaveDuplicates();
    }

    private JsonNode readSeeds(String classpath) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(classpath)) {
            assertThat(in).as("시드 리소스 존재: %s", classpath).isNotNull();
            JsonNode root = objectMapper.readTree(in);
            assertThat(root.get("_meta").isTextual()).as("_meta는 문자열(다른 시드와 다름)").isTrue();
            assertThat(root.get("_removed").isTextual()).as("_removed는 문자열(다른 시드와 다름)").isTrue();
            JsonNode seeds = root.get("seeds");
            assertThat(seeds).as("seeds 배열(★entries 아님)").isNotNull();
            return seeds;
        }
    }
}
