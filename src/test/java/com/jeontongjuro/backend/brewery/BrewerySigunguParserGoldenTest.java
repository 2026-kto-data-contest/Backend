package com.jeontongjuro.backend.brewery;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 시군구 라벨 파서 골든 대조(순수 함수, DB·Spring 불필요). 골든 brewery raw 픽스처(59행) 주소로
 * 파싱 성공률 100%(sido null 0)인지, 스펙이 명시한 7개 실명 예시가 정확히 재현되는지 검증한다.
 */
class BrewerySigunguParserGoldenTest {

    private static final String BREWERY_FILE = "/golden/20260728_brewery_raw.json";
    private static final int GOLDEN_TOTAL = 59;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    @DisplayName("골든 59행 전체 시군구 non-null(59/59) — 하나라도 null이면 규칙 구현이 틀린 것")
    void allGoldenAddressesYieldNonNullSigungu() throws IOException {
        int rows = 0;
        for (JsonNode brewery : readData()) {
            String address = brewery.get("주소").asText();
            String sigungu = BrewerySigunguParser.parse(address);
            assertThat(sigungu)
                    .as("시군구 non-null: %s (%s)", brewery.get("상호명").asText(), address)
                    .isNotNull();
            rows++;
        }
        assertThat(rows).isEqualTo(GOLDEN_TOTAL);
    }

    @Test
    @DisplayName("스펙 명시 7개 실명 예시 재현: 2단계 자치구·접미어 없음·제주 sido 충돌·세종형 포함")
    void namedExamplesMatchSpec() {
        // BRW-005 경기도 안산시 단원구… → 안산 (2단계 자치구는 상위 시만)
        assertThat(BrewerySigunguParser.parse("경기도 안산시 단원구 뻐꾹산길 107")).isEqualTo("안산");
        // BRW-006 부산 금정구… → 금정
        assertThat(BrewerySigunguParser.parse("부산 금정구 산성로 453")).isEqualTo("금정");
        // BRW-029 경기 여주 점봉길… → 여주 (원본에 접미어 없음)
        assertThat(BrewerySigunguParser.parse("경기 여주 점봉길 93-12")).isEqualTo("여주");
        // BRW-044 충북 청주 청원구… → 청주 (접미어 없음 + 자치구 동반)
        assertThat(BrewerySigunguParser.parse("충북 청주 청원구 내수읍 미원초정로 1275")).isEqualTo("청주");
        // BRW-046 제주 제주시 애월읍… → 제주 (sido와 같은 문자열이 된다, 정상)
        assertThat(BrewerySigunguParser.parse("제주 제주시 애월읍 애원로 283")).isEqualTo("제주");
        // BRW-045 제주도 서귀포시… → 서귀포
        assertThat(BrewerySigunguParser.parse("제주도 서귀포시 표선면 중산간동로 4726")).isEqualTo("서귀포");
        // BRW-022 울산광역시 울주군… → 울주
        assertThat(BrewerySigunguParser.parse("울산광역시 울주군 상북면 향산리 439")).isEqualTo("울주");
    }

    @Test
    @DisplayName("빈 값·null 주소는 null(예외로 죽지 않는다 — 이 파서는 응답 파생값용)")
    void blankOrNullAddressYieldsNull() {
        assertThat(BrewerySigunguParser.parse(null)).isNull();
        assertThat(BrewerySigunguParser.parse("  ")).isNull();
        assertThat(BrewerySigunguParser.parse("미분류시도 어딘가 123")).isNull();
    }

    private Iterable<JsonNode> readData() throws IOException {
        try (InputStream in = getClass().getResourceAsStream(BREWERY_FILE)) {
            assertThat(in).as("클래스패스 리소스 존재: %s", BREWERY_FILE).isNotNull();
            return objectMapper.readTree(in).get("data");
        }
    }
}
