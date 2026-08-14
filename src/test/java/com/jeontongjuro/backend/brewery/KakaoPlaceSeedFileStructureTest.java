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
 * 카카오 place 시드(#54)의 구조 봉인 — DB·네트워크 없이 리소스 JSON만 검증한다(DetailSeedFileStructureTest 선례).
 * 두 가지를 못 흔들게 고정한다:
 * <ol>
 *   <li>59행 · status=PLACE 55(placeUrl 보유) · NO_MATCH 4(placeUrl null) — 로더 적용 대상(55) 근거.</li>
 *   <li>entries에 <b>phone·address·brewery_address·homepage 키가 하나도 없다</b> — place 시드가 전화/주소를
 *       재도입해 다른 파생을 오염시키는 것을 구조적으로 차단(로더는 breweryId/status/placeUrl만 읽는다).</li>
 * </ol>
 */
class KakaoPlaceSeedFileStructureTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** place entries 허용 키(딱 이 6개). */
    private static final Set<String> ALLOWED = Set.of(
            "breweryId", "searchName", "status", "placeUrl", "kakaoPlaceName", "distanceM");

    /** place entries에 절대 있으면 안 되는 키(다른 파생 오염 방지 — 명시적 봉인). */
    private static final Set<String> FORBIDDEN = Set.of("phone", "address", "brewery_address", "homepage");

    @Test
    @DisplayName("카카오 place 시드: 59행 · PLACE 55(URL 보유) · NO_MATCH 4(URL null) · 키는 딱 6개 · 금지 키 0건")
    void kakaoPlaceSeedShape() throws Exception {
        JsonNode entries = readEntries("/kakao_place_seed.json");
        assertThat(entries).hasSize(59);

        int place = 0;
        int noMatch = 0;
        for (JsonNode e : entries) {
            // 허용 키만 존재(6개 정확)
            List<String> keys = new ArrayList<>();
            e.fieldNames().forEachRemaining(keys::add);
            assertThat(keys).as("entries 키는 허용 6개뿐: %s", keys)
                    .containsExactlyInAnyOrderElementsOf(ALLOWED);
            // 금지 키 부재(전화·주소·홈페이지 재도입 차단)
            for (String forbidden : FORBIDDEN) {
                assertThat(e.has(forbidden)).as("금지 키 '%s'는 없어야", forbidden).isFalse();
            }

            String status = e.get("status").asText();
            if ("PLACE".equals(status)) {
                place++;
                assertThat(e.get("placeUrl").asText()).as("PLACE 행은 placeUrl 보유").isNotBlank();
            } else {
                assertThat(status).as("status는 PLACE 또는 NO_MATCH").isEqualTo("NO_MATCH");
                noMatch++;
                assertThat(e.get("placeUrl").isNull()).as("NO_MATCH 행은 placeUrl null").isTrue();
            }
        }
        assertThat(place).as("PLACE 55").isEqualTo(55);
        assertThat(noMatch).as("NO_MATCH 4").isEqualTo(4);
    }

    private JsonNode readEntries(String classpath) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(classpath)) {
            assertThat(in).as("시드 리소스 존재: %s", classpath).isNotNull();
            JsonNode entries = objectMapper.readTree(in).get("entries");
            assertThat(entries).as("entries 배열").isNotNull();
            assertThat(entries.isArray()).isTrue();
            return entries;
        }
    }
}
