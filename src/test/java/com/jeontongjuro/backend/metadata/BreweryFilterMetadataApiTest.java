package com.jeontongjuro.backend.metadata;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.brewery.VisitState;
import com.jeontongjuro.backend.liquortype.LiquorType;
import com.jeontongjuro.backend.liquortype.ProductLiquorType;
import com.jeontongjuro.backend.liquortype.ProductLiquorTypeRepository;
import com.jeontongjuro.backend.override.ManualOverrideRepository;
import com.jeontongjuro.backend.product.JoinSource;
import com.jeontongjuro.backend.product.ProductBreweryLink;
import com.jeontongjuro.backend.product.ProductBreweryLinkRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIf;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * GET /api/v1/metadata/brewery-filters 응답 계약 검증(이슈 담당 박태근). ★골든 59/108은 이 테스트가 재현하지 않는다
 * — {@code @BeforeEach}는 파이프라인 데이터를 적재하지 않으므로, 골든 수치를 픽스처에 억지로 맞추지 않고
 * 전용 소형 픽스처(양조장 2곳)로 계약(순서·6+8종 전부 포함·0건 포함·enum 직렬화)만 검증한다. 골든 수치 대조는
 * 라이브 실측으로 별도 확인한다.
 * <p>
 * 픽스처: A(수도권, 탁주 1건) · B(강원, 증류주 1건). 나머지 4지역·4주종(기타 포함)은 이 픽스처 기준 0건이어야
 * 한다 — "0건도 그대로 내려간다"는 계약을 정확히 이 0건들로 검증한다.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
        "spring.datasource.url=jdbc:postgresql://localhost:5432/jeontongjuro_test"
})
@EnabledIf(value = "com.jeontongjuro.backend.testsupport.LocalPostgres#isUp",
        disabledReason = "로컬 PostgreSQL 미기동(docker compose up -d 필요) — 필터 메타데이터 API verify 스킵")
class BreweryFilterMetadataApiTest {

    private static final String BREWERY_A = "BRW-META-A";
    private static final String BREWERY_B = "BRW-META-B";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private BreweryRepository breweryRepository;
    @Autowired
    private ProductBreweryLinkRepository linkRepository;
    @Autowired
    private ProductLiquorTypeRepository productLiquorTypeRepository;
    @Autowired
    private ManualOverrideRepository overrideRepository;
    @Autowired
    private com.jeontongjuro.backend.feature.BreweryFeatureTagRepository featureTagRepository;
    @Autowired
    private com.jeontongjuro.backend.tour.BreweryNearbyRepository breweryNearbyRepository;
    @Autowired
    private com.jeontongjuro.backend.tour.TourContentRepository tourContentRepository;
    @Autowired
    private com.jeontongjuro.backend.experience.BreweryExperienceRepository experienceRepository;

    @BeforeEach
    void seedMinimalFixture() {
        // FK 자식 먼저 비운 뒤 brewery 재적재(BreweryQueryApiTest와 동일 순서)
        experienceRepository.deleteAll();
        featureTagRepository.deleteAll();
        productLiquorTypeRepository.deleteAll();
        linkRepository.deleteAll();
        overrideRepository.deleteAll();
        breweryNearbyRepository.deleteAll();
        breweryRepository.deleteAll();
        tourContentRepository.deleteAll();

        Brewery a = Brewery.seed(BREWERY_A, "메타A", "메타A", "서울", null, 0L, VisitState.UNKNOWN, VisitState.UNKNOWN);
        a.applyRegion("서울", "수도권");
        breweryRepository.save(a);

        Brewery b = Brewery.seed(BREWERY_B, "메타B", "메타B", "강원", null, 0L, VisitState.UNKNOWN, VisitState.UNKNOWN);
        b.applyRegion("강원", "강원");
        breweryRepository.save(b);

        // product_liquor_type.source_row_ref → product_brewery_link.source_row_ref FK 충족용 링크
        linkRepository.save(ProductBreweryLink.of(70001, "메타A-제품", "메타A", "메타A", BREWERY_A,
                JoinSource.AUTO, null, null));
        linkRepository.save(ProductBreweryLink.of(70002, "메타B-제품", "메타B", "메타B", BREWERY_B,
                JoinSource.AUTO, null, null));
        productLiquorTypeRepository.save(ProductLiquorType.manual(70001, BREWERY_A, LiquorType.탁주));
        productLiquorTypeRepository.save(ProductLiquorType.manual(70002, BREWERY_B, LiquorType.증류주));
    }

    @Test
    @DisplayName("응답 계약: 주종 6개·지역 8개가 명세 순서대로 전부 나온다")
    void allChipsPresentInSpecOrder() throws Exception {
        mockMvc.perform(get("/api/v1/metadata/brewery-filters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liquorTypes.length()").value(6))
                .andExpect(jsonPath("$.liquorTypes[0].value").value("탁주"))
                .andExpect(jsonPath("$.liquorTypes[1].value").value("약주"))
                .andExpect(jsonPath("$.liquorTypes[2].value").value("청주"))
                .andExpect(jsonPath("$.liquorTypes[3].value").value("증류주"))
                .andExpect(jsonPath("$.liquorTypes[4].value").value("과실주"))
                .andExpect(jsonPath("$.liquorTypes[5].value").value("기타"))
                .andExpect(jsonPath("$.regions.length()").value(8))
                .andExpect(jsonPath("$.regions[0].value").value("수도권"))
                .andExpect(jsonPath("$.regions[1].value").value("강원"))
                .andExpect(jsonPath("$.regions[2].value").value("충청"))
                .andExpect(jsonPath("$.regions[3].value").value("전라"))
                .andExpect(jsonPath("$.regions[4].value").value("경상"))
                .andExpect(jsonPath("$.regions[5].value").value("부산"))
                .andExpect(jsonPath("$.regions[6].value").value("울산"))
                .andExpect(jsonPath("$.regions[7].value").value("제주"));
    }

    @Test
    @DisplayName("기타가 count=0으로 포함된다(빠지면 실패) — 0건 지역칩도 동일하게 포함")
    void zeroCountChipsAreIncludedNotDropped() throws Exception {
        mockMvc.perform(get("/api/v1/metadata/brewery-filters"))
                .andExpect(status().isOk())
                // 픽스처 기준 탁주 1·증류주 1만 있고 나머지(기타 포함)는 0
                .andExpect(jsonPath("$.liquorTypes[0].breweryCount").value(1))  // 탁주
                .andExpect(jsonPath("$.liquorTypes[1].breweryCount").value(0))  // 약주
                .andExpect(jsonPath("$.liquorTypes[2].breweryCount").value(0))  // 청주
                .andExpect(jsonPath("$.liquorTypes[3].breweryCount").value(1))  // 증류주
                .andExpect(jsonPath("$.liquorTypes[4].breweryCount").value(0))  // 과실주
                .andExpect(jsonPath("$.liquorTypes[5].breweryCount").value(0))  // 기타 — 0이지만 배열엔 존재
                // 픽스처 기준 수도권 1·강원 1만 있고 나머지 6지역은 0
                .andExpect(jsonPath("$.regions[0].breweryCount").value(1))  // 수도권
                .andExpect(jsonPath("$.regions[1].breweryCount").value(1))  // 강원
                .andExpect(jsonPath("$.regions[2].breweryCount").value(0))  // 충청
                .andExpect(jsonPath("$.regions[3].breweryCount").value(0))  // 전라
                .andExpect(jsonPath("$.regions[4].breweryCount").value(0))  // 경상
                .andExpect(jsonPath("$.regions[5].breweryCount").value(0))  // 부산
                .andExpect(jsonPath("$.regions[6].breweryCount").value(0))  // 울산
                .andExpect(jsonPath("$.regions[7].breweryCount").value(0)); // 제주
    }

    @Test
    @DisplayName("enum 직렬화: 주종·지역 value가 한글 값으로 나간다(코드·ordinal 아님)")
    void enumValuesSerializeAsKoreanStrings() throws Exception {
        mockMvc.perform(get("/api/v1/metadata/brewery-filters"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.liquorTypes[0].value").value("탁주"))
                .andExpect(jsonPath("$.regions[0].value").value("수도권"));
    }
}
