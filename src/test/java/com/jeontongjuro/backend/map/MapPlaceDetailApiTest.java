package com.jeontongjuro.backend.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.brewery.CoordSource;
import com.jeontongjuro.backend.brewery.PhoneSource;
import com.jeontongjuro.backend.brewery.VisitState;
import com.jeontongjuro.backend.tour.TourContent;
import com.jeontongjuro.backend.tour.TourContentRepository;
import com.jeontongjuro.backend.tour.TourContentRow;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

/**
 * 지도 장소 상세 조회 API 인수 검증(GET /api/v1/map/places/{placeId}). 웹 스택 전체
 * (시큐리티 공개 경로·컨트롤러·서비스·직렬화·에러 어드바이스)를 통과시켜 HTTP 계약을 고정한다.
 * <p>
 * 파이프라인 데이터를 적재하지 않고 <b>전용 소형 픽스처</b>로 계약만 검증한다 — 라이브 골든 수치를
 * 억지로 대입하지 않는다. 필드 조합·분류 규칙 자체는 {@link MapPlaceDetailServiceTest}가 단위로 덮는다.
 * <p>
 * 검증 축:
 *   (1) 양조장·관광 콘텐츠 200 + 12필드 키 계약
 *   (2) 전통시장·문화시설이 TOURIST_ATTRACTION 요청으로 열린다(내부 8종을 5종으로 접어 대조)
 *   (3) 없는 장소·분류 불일치·목록 미노출(ETC) → 404 {code=MAP_PLACE_NOT_FOUND}
 *   (4) category 누락·허용 집합 밖 → 400 {code=INVALID_QUERY_PARAMETER}
 *   (5) 에러 바디는 code·message 딱 2필드(timestamp·path 없음)
 *   (6) 인증 없이 열린다(공개 경로 등록 확인)
 * DB 미기동 시 조용한 그린 방지를 위해 @EnabledIf로 명시 스킵한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/jeontongjuro_test"
})
@EnabledIf(value = "com.jeontongjuro.backend.testsupport.LocalPostgres#isUp",
        disabledReason = "로컬 PostgreSQL 미기동(docker compose up -d 필요) — 지도 상세 API verify 스킵")
class MapPlaceDetailApiTest {

    private static final String BREWERY_WITH_LINK = "BRW-MAPD-1";
    private static final String BREWERY_WITHOUT_LINK = "BRW-MAPD-2";
    private static final String CONTENT_BREWERY_IMAGE = "MAPD-IMG";
    private static final String CONTENT_RESTAURANT = "MAPD-REST";
    private static final String CONTENT_MARKET = "MAPD-MARKET";
    private static final String CONTENT_CULTURAL = "MAPD-CULT";
    private static final String CONTENT_EXCLUDED = "MAPD-ETC";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private BreweryRepository breweryRepository;
    @Autowired
    private TourContentRepository tourContentRepository;

    @BeforeEach
    void seedFixtures() {
        // tour_content를 먼저 저장한다(brewery.content_id 매칭보다 앞서야 안전).
        tourContentRepository.save(tour(CONTENT_BREWERY_IMAGE, "39", "양조장 매칭 콘텐츠",
                "주소", null, "A05020100", "http://img/brewery-main.jpg"));
        tourContentRepository.save(tour(CONTENT_RESTAURANT, "39", "지도상세 한식당",
                "경기도 테스트시 테스트로 1", "2층", "A05020100", "http://img/rest.jpg"));
        tourContentRepository.save(tour(CONTENT_MARKET, "38", "지도상세 전통시장",
                "주소", null, "A04010100", null));
        tourContentRepository.save(tour(CONTENT_CULTURAL, "14", "지도상세 박물관",
                "주소", null, "A02060100", null));
        tourContentRepository.save(tour(CONTENT_EXCLUDED, "14", "지도상세 과학관",
                "주소", null, "A02060300", null));

        Brewery linked = brewery(BREWERY_WITH_LINK, "지도상세 양조장");
        linked.applyPhone("033-340-4300", PhoneSource.TOUR);
        linked.applyKakaoPlaceUrl("http://place.map.kakao.com/775716025");
        linked.applyContentMatch(CONTENT_BREWERY_IMAGE, OffsetDateTime.now(ZoneOffset.UTC));
        breweryRepository.save(linked);
        breweryRepository.save(brewery(BREWERY_WITHOUT_LINK, "링크없는 지도상세 양조장"));
    }

    // ── 200 계약 ──────────────────────────────────────────────────────────────
    @Test
    @DisplayName("양조장 → 200 + 전화·카카오 URL·대표 이미지 노출, 세부분류는 null")
    void breweryDetailReturns200() throws Exception {
        mockMvc.perform(get("/api/v1/map/places/{id}", BREWERY_WITH_LINK).param("category", "BREWERY"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.placeId").value(BREWERY_WITH_LINK))
                .andExpect(jsonPath("$.placeName").value("지도상세 양조장"))
                .andExpect(jsonPath("$.category").value("BREWERY"))
                .andExpect(jsonPath("$.categoryName").value("양조장"))
                .andExpect(jsonPath("$.subcategoryName").doesNotExist())
                .andExpect(jsonPath("$.phone").value("033-340-4300"))
                .andExpect(jsonPath("$.kakaoMapUrl").value("http://place.map.kakao.com/775716025"))
                .andExpect(jsonPath("$.imageUrl").value("http://img/brewery-main.jpg"))
                .andExpect(jsonPath("$.distanceMeters").doesNotExist());
    }

    @Test
    @DisplayName("카카오 URL이 없는 양조장 → 이름·좌표 길찾기 링크, 전화·이미지 null")
    void breweryWithoutPlaceUrlBuildsCoordinateLink() throws Exception {
        JsonNode body = readBody(get("/api/v1/map/places/{id}", BREWERY_WITHOUT_LINK)
                .param("category", "BREWERY"));
        assertThat(body.get("kakaoMapUrl").asText())
                .startsWith("https://map.kakao.com/link/to/")
                .endsWith(",36.100000,127.100000");
        assertThat(body.get("phone").isNull()).isTrue();
        assertThat(body.get("imageUrl").isNull()).isTrue();
    }

    @Test
    @DisplayName("관광 콘텐츠 → 200 + 대분류는 categoryName, 세부분류는 subcategoryName, 전화는 항상 null")
    void tourContentDetailReturns200() throws Exception {
        mockMvc.perform(get("/api/v1/map/places/{id}", CONTENT_RESTAURANT).param("category", "RESTAURANT"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("RESTAURANT"))
                .andExpect(jsonPath("$.categoryName").value("음식점"))
                .andExpect(jsonPath("$.subcategoryName").value("한식"))
                .andExpect(jsonPath("$.address").value("경기도 테스트시 테스트로 1 2층"))
                .andExpect(jsonPath("$.phone").doesNotExist())
                .andExpect(jsonPath("$.imageUrl").value("http://img/rest.jpg"))
                .andExpect(jsonPath("$.kakaoMapUrl").exists());
    }

    @Test
    @DisplayName("응답은 명세가 정한 12필드 키를 모두 가진다")
    void responseExposesTwelveContractKeys() throws Exception {
        JsonNode body = readBody(get("/api/v1/map/places/{id}", CONTENT_RESTAURANT)
                .param("category", "RESTAURANT"));
        List<String> keys = new ArrayList<>();
        body.fieldNames().forEachRemaining(keys::add);
        assertThat(keys).containsExactlyInAnyOrder("placeId", "placeName", "category", "categoryName",
                "subcategoryName", "latitude", "longitude", "distanceMeters", "address", "phone",
                "imageUrl", "kakaoMapUrl");
    }

    // ── 8종 → 5종 매핑 ────────────────────────────────────────────────────────
    @Test
    @DisplayName("전통시장·문화시설은 TOURIST_ATTRACTION 요청으로 열린다(클라이언트는 5종만 안다)")
    void marketAndCulturalFacilityOpenAsTouristAttraction() throws Exception {
        mockMvc.perform(get("/api/v1/map/places/{id}", CONTENT_MARKET)
                        .param("category", "TOURIST_ATTRACTION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("TOURIST_ATTRACTION"))
                .andExpect(jsonPath("$.categoryName").value("관광지"))
                .andExpect(jsonPath("$.subcategoryName").value("전통시장"));

        mockMvc.perform(get("/api/v1/map/places/{id}", CONTENT_CULTURAL)
                        .param("category", "TOURIST_ATTRACTION"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.category").value("TOURIST_ATTRACTION"))
                .andExpect(jsonPath("$.subcategoryName").value("박물관"));
    }

    // ── 404 ───────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("없는 placeId → 404 {code=MAP_PLACE_NOT_FOUND}")
    void unknownPlaceReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/map/places/{id}", "9999999").param("category", "RESTAURANT"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MAP_PLACE_NOT_FOUND"))
                .andExpect(jsonPath("$.message").isNotEmpty());

        mockMvc.perform(get("/api/v1/map/places/{id}", "BRW-999").param("category", "BREWERY"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MAP_PLACE_NOT_FOUND"));
    }

    @Test
    @DisplayName("요청 category와 실제 분류가 다르면 404")
    void mismatchedCategoryReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/map/places/{id}", CONTENT_RESTAURANT).param("category", "CAFE"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MAP_PLACE_NOT_FOUND"));
    }

    @Test
    @DisplayName("목록에 노출되지 않는 장소(ETC) → 404")
    void excludedPlaceReturns404() throws Exception {
        mockMvc.perform(get("/api/v1/map/places/{id}", CONTENT_EXCLUDED)
                        .param("category", "TOURIST_ATTRACTION"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("MAP_PLACE_NOT_FOUND"));
    }

    // ── 400 ───────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("category 누락·허용 집합 밖 → 400 {code=INVALID_QUERY_PARAMETER}")
    void invalidCategoryReturns400() throws Exception {
        mockMvc.perform(get("/api/v1/map/places/{id}", CONTENT_RESTAURANT))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUERY_PARAMETER"));

        mockMvc.perform(get("/api/v1/map/places/{id}", CONTENT_RESTAURANT).param("category", "FOOD"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUERY_PARAMETER"));

        // 내부 8종 이름이라도 클라이언트 계약(5종) 밖이면 거부한다.
        mockMvc.perform(get("/api/v1/map/places/{id}", CONTENT_MARKET).param("category", "MARKET"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_QUERY_PARAMETER"));
    }

    @Test
    @DisplayName("에러 바디는 code·message 딱 2필드다(timestamp·path 없음)")
    void errorBodyHasExactlyTwoFields() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/v1/map/places/{id}", "9999999")
                        .param("category", "RESTAURANT"))
                .andExpect(status().isNotFound()).andReturn();
        JsonNode body = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
        List<String> keys = new ArrayList<>();
        body.fieldNames().forEachRemaining(keys::add);
        assertThat(keys).containsExactlyInAnyOrder("code", "message");
    }

    // ── helpers ───────────────────────────────────────────────────────────────
    private JsonNode readBody(MockHttpServletRequestBuilder request) throws Exception {
        MvcResult result = mockMvc.perform(request).andExpect(status().isOk()).andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private Brewery brewery(String id, String name) {
        Brewery brewery = Brewery.seed(id, name, name, "충북 테스트군 테스트로 1", null, 0L,
                VisitState.UNKNOWN, VisitState.UNKNOWN);
        brewery.applyCoordinate(new BigDecimal("36.100000"), new BigDecimal("127.100000"),
                CoordSource.KAKAO_ADDRESS, OffsetDateTime.now(ZoneOffset.UTC));
        return brewery;
    }

    private TourContent tour(String id, String contentTypeId, String title, String addr1, String addr2,
                             String cat3, String firstImage) {
        TourContentRow row = new TourContentRow(id, contentTypeId, title, addr1, addr2,
                null, null, null, null, null, cat3, null, null, null,
                null, null, "127.1", "36.1", null, firstImage, null, null, null, null, null);
        return TourContent.create(row, new BigDecimal("36.100000"), new BigDecimal("127.100000"));
    }
}
