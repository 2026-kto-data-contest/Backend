package com.jeontongjuro.backend.experience;

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
 * 체험 매칭 시드(experience_match_seed.json)의 구조 봉인 — DB·네트워크 없이 리소스 JSON만 검증한다
 * (AddressFixSeedFileStructureTest·KakaoPlaceSeedFileStructureTest 선례). 이 시드는 기준일 2021-09-17
 * 체험 원본과 브루어리를 잇는 매칭 키만 보관한다({@code _meta.note}: "매칭 키만 보관한다. 체험 상세값은
 * API에서 읽는다. 주소·연락처 등은 마스터가 진실원천이다") — 옛 기준일 속성이 슬쩍 들어오는 것을 구조로 차단한다.
 * <p>
 * 두 가지를 고정한다:
 * <ol>
 *   <li>배열 키는 {@code matches}이고 30행, 각 항목은 딱 4키(breweryId·experienceBreweryName·matchMethod·evidence).</li>
 *   <li>entries에 <b>address·phone·homepage·liquorType·visitReservation·소재지·주소·연락처·홈페이지·주종·
 *       양조장주소·상시방문가능여부·예약방문가능여부 키가 하나도 없다</b> — 체험 원본의 상세값 재도입을 구조적으로 차단.</li>
 * </ol>
 */
class ExperienceMatchSeedFileStructureTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** matches 각 항목 허용 키(딱 이 4개). */
    private static final Set<String> ALLOWED =
            Set.of("breweryId", "experienceBreweryName", "matchMethod", "evidence");

    /** matches에 절대 있으면 안 되는 키(체험 원본 상세값 재도입 차단 — 마스터가 진실원천). */
    private static final Set<String> FORBIDDEN = Set.of(
            "address", "phone", "homepage", "liquorType", "visitReservation",
            "소재지", "주소", "연락처", "홈페이지", "주종", "양조장주소", "상시방문가능여부", "예약방문가능여부");

    @Test
    @DisplayName("체험 매칭 시드: matches 30행 · 키는 딱 4개 · 금지 키 0건 · breweryId는 BRW-xxx·중복 없음")
    void experienceMatchSeedShape() throws Exception {
        JsonNode matches = readMatches("/experience_match_seed.json");
        assertThat(matches.isArray()).isTrue();
        assertThat(matches).hasSize(30);

        List<String> breweryIds = new ArrayList<>();
        for (JsonNode m : matches) {
            List<String> keys = new ArrayList<>();
            m.fieldNames().forEachRemaining(keys::add);
            assertThat(keys).as("matches 키는 허용 4개뿐: %s", keys)
                    .containsExactlyInAnyOrderElementsOf(ALLOWED);
            for (String forbidden : FORBIDDEN) {
                assertThat(m.has(forbidden)).as("금지 키 '%s'는 없어야", forbidden).isFalse();
            }

            String breweryId = m.get("breweryId").asText();
            assertThat(breweryId).as("breweryId는 BRW-xxx 형식").matches("BRW-\\d{3}");
            assertThat(m.get("experienceBreweryName").asText()).as("experienceBreweryName 비어있지 않음").isNotBlank();
            assertThat(m.get("matchMethod").asText()).as("matchMethod는 NAME 또는 ADDRESS")
                    .isIn("NAME", "ADDRESS");
            breweryIds.add(breweryId);
        }
        assertThat(breweryIds).as("breweryId 중복 없음").doesNotHaveDuplicates();
    }

    private JsonNode readMatches(String classpath) throws Exception {
        try (InputStream in = getClass().getResourceAsStream(classpath)) {
            assertThat(in).as("시드 리소스 존재: %s", classpath).isNotNull();
            JsonNode matches = objectMapper.readTree(in).get("matches");
            assertThat(matches).as("matches 배열").isNotNull();
            return matches;
        }
    }
}
