package com.jeontongjuro.backend.geo;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 좌표 골든 파일 대조(#28 검증 단정 1~4). DB·Spring·카카오 무관 — 실기동으로 봉인한
 * {@code 20260806_brewery_coord.tsv}(brewery_id \t lat \t lng \t coord_source, 59행)를 읽어
 * 내부 정합을 회귀 검증한다. (단정 5·6은 {@link BreweryGeocodingGoldenTest}에서 오케스트레이터로 검증.)
 */
class BreweryCoordGoldenTest {

    private static final String GOLDEN_TSV = "/golden/20260806_brewery_coord.tsv";

    private static final double LAT_MIN = 33.0;
    private static final double LAT_MAX = 39.0;
    private static final double LNG_MIN = 124.0;
    private static final double LNG_MAX = 132.0;

    /** coord_source 실측 분포. */
    private static final Map<String, Integer> EXPECTED_SOURCE_DIST = Map.of(
            "KAKAO_ADDRESS", 53,
            "KAKAO_ADDRESS_NORMALIZED", 2,
            "KAKAO_ADDRESS_SEED", 4);

    /** 시드 4건 실측 좌표(사람이 카카오에서 직접 받은 값 — 소수 4자리까지 일치해야 한다). */
    private static final Map<String, double[]> SEED_REF = Map.of(
            "BRW-004", new double[]{37.4883, 128.1973},
            "BRW-043", new double[]{37.5219, 126.7089},
            "BRW-047", new double[]{36.6770, 127.4801},
            "BRW-051", new double[]{35.3452, 126.8129});

    private record Row(String breweryId, String lat, String lng, String source) {
    }

    private List<Row> loadGolden() throws IOException {
        List<Row> rows = new ArrayList<>();
        try (InputStream in = getClass().getResourceAsStream(GOLDEN_TSV)) {
            assertThat(in).as("골든 TSV 리소스 존재: %s (실기동→골든 생성 후 test 순서 준수)", GOLDEN_TSV).isNotNull();
            String content = new String(in.readAllBytes(), StandardCharsets.UTF_8);
            for (String line : content.split("\n")) {
                if (line.isBlank()) {
                    continue;
                }
                String[] c = line.split("\t", -1);
                assertThat(c).as("TSV 열 4개: %s", line).hasSize(4);
                rows.add(new Row(c[0].trim(), c[1].trim(), c[2].trim(), c[3].trim()));
            }
        }
        return rows;
    }

    @Test
    @DisplayName("단정1: 59행 전건 latitude/longitude non-null")
    void allRowsHaveCoordinates() throws IOException {
        List<Row> rows = loadGolden();
        assertThat(rows).hasSize(59);
        assertThat(rows).allSatisfy(r -> {
            assertThat(r.lat()).as("lat non-null: %s", r.breweryId()).isNotEmpty();
            assertThat(r.lng()).as("lng non-null: %s", r.breweryId()).isNotEmpty();
        });
    }

    @Test
    @DisplayName("단정2: coord_source 분포 == ADDRESS 53 / NORMALIZED 2 / SEED 4")
    void coordSourceDistributionMatches() throws IOException {
        Map<String, Integer> dist = new TreeMap<>();
        for (Row r : loadGolden()) {
            dist.merge(r.source(), 1, Integer::sum);
        }
        assertThat(dist).containsExactlyInAnyOrderEntriesOf(new LinkedHashMap<>(EXPECTED_SOURCE_DIST));
    }

    @Test
    @DisplayName("단정3: 범위 검증 전건 통과 (위도 33~39·경도 124~132)")
    void allCoordinatesWithinKoreaRange() throws IOException {
        for (Row r : loadGolden()) {
            double lat = Double.parseDouble(r.lat());
            double lng = Double.parseDouble(r.lng());
            assertThat(lat).as("위도 범위: %s", r.breweryId()).isBetween(LAT_MIN, LAT_MAX);
            assertThat(lng).as("경도 범위: %s", r.breweryId()).isBetween(LNG_MIN, LNG_MAX);
        }
    }

    @Test
    @DisplayName("단정4: 시드 4건 좌표가 실측 확정값과 소수 4자리 일치 + coord_source=SEED (축 전도 최종 검산)")
    void seedCoordinatesMatchReference() throws IOException {
        Map<String, Row> byId = new LinkedHashMap<>();
        for (Row r : loadGolden()) {
            byId.put(r.breweryId(), r);
        }
        SEED_REF.forEach((id, ref) -> {
            Row r = byId.get(id);
            assertThat(r).as("시드 %s 존재", id).isNotNull();
            assertThat(r.source()).as("%s coord_source", id).isEqualTo("KAKAO_ADDRESS_SEED");
            assertThat(round4(r.lat())).as("%s 위도", id).isEqualByComparingTo(round4Val(ref[0]));
            assertThat(round4(r.lng())).as("%s 경도", id).isEqualByComparingTo(round4Val(ref[1]));
        });
    }

    private static BigDecimal round4(String v) {
        return new BigDecimal(v).setScale(4, RoundingMode.HALF_UP);
    }

    private static BigDecimal round4Val(double v) {
        return BigDecimal.valueOf(v).setScale(4, RoundingMode.HALF_UP);
    }
}
