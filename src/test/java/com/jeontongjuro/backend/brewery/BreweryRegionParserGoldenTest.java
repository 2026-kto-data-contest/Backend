package com.jeontongjuro.backend.brewery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeontongjuro.backend.brewery.BreweryRegionParser.RegionResult;
import java.io.IOException;
import java.io.InputStream;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 주소 파서 골든 대조(순수 함수, DB·Spring 불필요). 골든 brewery raw 픽스처(59행)의 주소를 파싱해
 * region 8칩 분포·광역권 폴드백이 골든 분포와 정확히 일치하는지, 파싱 성공률 100%(sido null 0)인지 검증한다.
 * 골든 분포는 재계산하지 않고 상수로 못박아 판정 기준선으로 쓴다(20260728 brewery raw 대상).
 * <p>
 * 광역권 6분류(부산·울산→경상 폴드백)는 8칩과의 교차검증 전용 — 컬럼으로 만들지 않고 이 테스트 안에서만 접는다.
 */
class BreweryRegionParserGoldenTest {

    private static final String BREWERY_FILE = "/golden/20260728_brewery_raw.json";

    /** 골든 8칩(region 최종 검증). 재계산 금지 — 이게 판정 기준선. */
    private static final Map<String, Integer> GOLDEN_CHIP = Map.of(
            "수도권", 13, "충청", 17, "전라", 8, "경상", 14, "강원", 3, "제주", 2, "부산", 1, "울산", 1);

    /** 골든 광역권 6분류(교차검증용, 부산·울산을 경상으로 접음). 8칩 경상14+부산1+울산1=16. */
    private static final Map<String, Integer> GOLDEN_METRO = Map.of(
            "충청", 17, "경상", 16, "수도권", 13, "전라", 8, "강원", 3, "제주", 2);

    private static final int GOLDEN_TOTAL = 59;

    /** 싱글턴 칩 핀 고정 — 카운트만으로는 약해서 상호명으로 못박는다. */
    private static final String BUSAN_PIN = "금정산성 토산주";
    private static final String ULSAN_PIN = "복순도가";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("3-1: region 8칩 분포 == 골든, 합 59, 파싱 성공률 100%(미분류 0)")
    void chipDistributionMatchesGolden() throws IOException {
        Map<String, Integer> chip = new TreeMap<>();
        int rows = 0;
        for (JsonNode brewery : readData()) {
            RegionResult r = BreweryRegionParser.parse(brewery.get("주소").asText());
            assertThat(r.sido()).as("파싱 성공률 100% — sido null 금지: %s", brewery.get("상호명").asText()).isNotNull();
            assertThat(r.region()).isNotNull();
            chip.merge(r.region(), 1, Integer::sum);
            rows++;
        }
        assertThat(rows).isEqualTo(GOLDEN_TOTAL);
        assertThat(chip.values().stream().mapToInt(Integer::intValue).sum()).isEqualTo(GOLDEN_TOTAL);
        assertThat(chip).as("8칩 region 분포 == 골든").containsExactlyInAnyOrderEntriesOf(GOLDEN_CHIP);
    }

    @Test
    @DisplayName("3-2: 광역권 6분류 폴드백(부산·울산→경상) == 골든 — 8칩과 교차검증")
    void metroFoldbackMatchesGolden() throws IOException {
        Map<String, Integer> metro = new TreeMap<>();
        for (JsonNode brewery : readData()) {
            RegionResult r = BreweryRegionParser.parse(brewery.get("주소").asText());
            String folded = ("부산".equals(r.region()) || "울산".equals(r.region())) ? "경상" : r.region();
            metro.merge(folded, 1, Integer::sum);
        }
        assertThat(metro).as("광역권 6분류 == 골든").containsExactlyInAnyOrderEntriesOf(GOLDEN_METRO);
    }

    @Test
    @DisplayName("3-3: 부산1·울산1 싱글턴 핀 고정 — 상호명으로 확인(카운트만으론 약함)")
    void busanAndUlsanSingletonsPinnedByBusinessName() throws IOException {
        Set<String> busan = new HashSet<>();
        Set<String> ulsan = new HashSet<>();
        for (JsonNode brewery : readData()) {
            RegionResult r = BreweryRegionParser.parse(brewery.get("주소").asText());
            String name = brewery.get("상호명").asText();
            if ("부산".equals(r.region())) {
                busan.add(name);
            }
            if ("울산".equals(r.region())) {
                ulsan.add(name);
            }
        }
        assertThat(busan).containsExactly(BUSAN_PIN);
        assertThat(ulsan).containsExactly(ULSAN_PIN);
    }

    @Test
    @DisplayName("3-4: 미분류 시도는 조용한 null이 아니라 예외로 시끄럽게 실패")
    void unclassifiedSidoFailsLoudly() {
        assertThatThrownBy(() -> BreweryRegionParser.parse("서귀포시 어딘가로 123"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("미분류 시도");
        assertThatThrownBy(() -> BreweryRegionParser.parse("  "))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> BreweryRegionParser.parse(null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    @DisplayName("3-5: 함정 — 앞 토큰만 앵커(광주광역시 vs 경기도 광주시), 세종은 시, 신·구 명칭 혼재")
    void anchorTrapsAreHandled() {
        // 광주광역시 → 전라 / 경기도 광주시 → 수도권. 문자열 중간의 '광주'에 걸리지 않는다.
        assertThat(BreweryRegionParser.parse("광주광역시 북구 첨단과기로 123"))
                .isEqualTo(new RegionResult("광주", "전라"));
        assertThat(BreweryRegionParser.parse("경기도 광주시 오포읍 123"))
                .isEqualTo(new RegionResult("경기", "수도권"));
        assertThat(BreweryRegionParser.parse("경기 광주 어딘가"))
                .isEqualTo(new RegionResult("경기", "수도권"));

        // 세종은 시(도 아님) → 충청
        assertThat(BreweryRegionParser.parse("세종특별자치시 조치원읍 1"))
                .isEqualTo(new RegionResult("세종", "충청"));

        // 신·구 명칭 혼재 — 같은 정규형으로 수렴
        assertThat(BreweryRegionParser.parse("강원도 춘천시 1")).isEqualTo(new RegionResult("강원", "강원"));
        assertThat(BreweryRegionParser.parse("강원특별자치도 춘천시 1")).isEqualTo(new RegionResult("강원", "강원"));
        assertThat(BreweryRegionParser.parse("전라북도 무주군 1")).isEqualTo(new RegionResult("전북", "전라"));
        assertThat(BreweryRegionParser.parse("전북특별자치도 무주군 1")).isEqualTo(new RegionResult("전북", "전라"));
        assertThat(BreweryRegionParser.parse("충청북도 옥천군 1")).isEqualTo(new RegionResult("충북", "충청"));

        // 부산·울산은 8칩에서 독립(경상으로 접지 않는다)
        assertThat(BreweryRegionParser.parse("부산 금정구 1")).isEqualTo(new RegionResult("부산", "부산"));
        assertThat(BreweryRegionParser.parse("울산광역시 울주군 1")).isEqualTo(new RegionResult("울산", "울산"));
    }

    private Iterable<JsonNode> readData() throws IOException {
        try (InputStream in = getClass().getResourceAsStream(BREWERY_FILE)) {
            assertThat(in).as("클래스패스 리소스 존재: %s", BREWERY_FILE).isNotNull();
            return objectMapper.readTree(in).get("data");
        }
    }
}
