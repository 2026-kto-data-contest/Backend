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
 * 상세 편입 시드(#50)의 구조 봉인 — DB·네트워크 없이 리소스 JSON만 검증한다. 두 가지를 못 흔들게 고정한다:
 * <ol>
 *   <li>카카오 전화 시드: 59행·PHONE 51(합집합 52 근거 = TOUR 19 ∪ KAKAO 51, 교집합 18).</li>
 *   <li>농림부 시드: 36행이고 <b>entries에 소재지·주종·업체명 키가 하나도 없다</b> — 2019 기준일 오염을
 *       구조적으로 차단(컬럼 이전에 시드에 값이 없어야 한다는 전제 #12).</li>
 * </ol>
 */
class DetailSeedFileStructureTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 농림부 entries에 절대 있으면 안 되는 키(2019 값이 마스터 오염). */
    private static final Set<String> FORBIDDEN = Set.of("소재지", "주종", "업체명",
            "address", "liquorType", "businessName", "sojaeji");

    /** 농림부 entries 허용 키(딱 이 5개). */
    private static final Set<String> ALLOWED = Set.of(
            "breweryId", "foundedYear", "representativeName", "designatedYear", "designationNote");

    @Test
    @DisplayName("카카오 전화 시드: 59행 · status=PHONE 51 · PHONE 행은 전화 보유")
    void kakaoPhoneSeedShape() throws Exception {
        JsonNode entries = readEntries("/kakao_phone_seed.json");
        assertThat(entries).hasSize(59);
        int phone = 0;
        for (JsonNode e : entries) {
            if ("PHONE".equals(e.get("status").asText())) {
                phone++;
                assertThat(e.get("phone").asText()).as("PHONE 행은 전화 보유").isNotBlank();
            }
        }
        assertThat(phone).isEqualTo(51);
    }

    @Test
    @DisplayName("농림부 시드: 36행 · entries 키는 딱 5개 · 소재지·주종·업체명 키 0건(오염 구조 차단)")
    void nonglimSeedHasNoForbiddenColumns() throws Exception {
        JsonNode entries = readEntries("/nonglim_seed.json");
        assertThat(entries).hasSize(36);
        for (JsonNode e : entries) {
            List<String> keys = new ArrayList<>();
            e.fieldNames().forEachRemaining(keys::add);
            assertThat(keys).as("entries 키는 허용 5개뿐: %s", keys)
                    .containsExactlyInAnyOrderElementsOf(ALLOWED);
            for (String forbidden : FORBIDDEN) {
                assertThat(e.has(forbidden)).as("금지 키 '%s'는 없어야", forbidden).isFalse();
            }
            // 설립연도·선정연도는 4자리 정수
            assertThat(e.get("foundedYear").isInt()).isTrue();
            assertThat(e.get("foundedYear").asInt()).isBetween(1800, 2026);
            assertThat(e.get("designatedYear").isInt()).isTrue();
        }
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
