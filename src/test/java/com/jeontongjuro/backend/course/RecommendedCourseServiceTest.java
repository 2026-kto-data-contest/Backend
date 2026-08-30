package com.jeontongjuro.backend.course;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.brewery.VisitState;
import com.jeontongjuro.backend.brewery.query.BreweryNotFoundException;
import com.jeontongjuro.backend.feature.BreweryFeatureTagRepository;
import com.jeontongjuro.backend.global.web.PageResponse;
import com.jeontongjuro.backend.liquortype.ProductLiquorTypeRepository;
import com.jeontongjuro.backend.liquortype.LiquorType;
import com.jeontongjuro.backend.product.query.ProductCardResponse;
import com.jeontongjuro.backend.product.query.ProductFlavorTag;
import com.jeontongjuro.backend.product.query.ProductQueryService;
import com.jeontongjuro.backend.tour.BreweryNearby;
import com.jeontongjuro.backend.tour.BreweryNearbyRepository;
import com.jeontongjuro.backend.tour.TourContent;
import com.jeontongjuro.backend.tour.TourContentRepository;
import com.jeontongjuro.backend.tour.TourContentRow;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class RecommendedCourseServiceTest {

    private BreweryRepository breweryRepository;
    private BreweryNearbyRepository nearbyRepository;
    private TourContentRepository tourContentRepository;
    private ProductQueryService productQueryService;
    private BreweryFeatureTagRepository featureTagRepository;
    private ProductLiquorTypeRepository liquorTypeRepository;
    private RecommendedCourseService service;

    @BeforeEach
    void setUp() {
        breweryRepository = Mockito.mock(BreweryRepository.class);
        nearbyRepository = Mockito.mock(BreweryNearbyRepository.class);
        tourContentRepository = Mockito.mock(TourContentRepository.class);
        productQueryService = Mockito.mock(ProductQueryService.class);
        featureTagRepository = Mockito.mock(BreweryFeatureTagRepository.class);
        liquorTypeRepository = Mockito.mock(ProductLiquorTypeRepository.class);
        service = new RecommendedCourseService(breweryRepository, nearbyRepository, tourContentRepository,
                productQueryService, featureTagRepository, liquorTypeRepository);
        given(productQueryService.listProducts(any(), any(Integer.class), any(Integer.class)))
                .willReturn(PageResponse.of(List.of(), 0, 100, 0));
        given(featureTagRepository.findByBreweryIdIn(any())).willReturn(List.of());
        given(liquorTypeRepository.findDistinctTypesByBreweryIdIn(any())).willReturn(List.of());
    }

    @Test
    void centerAndTwoStopsPerCategoryBuildNineStopCourse() {
        Brewery brewery = brewery();
        List<BreweryNearby> nearby = List.of(
                nearby("CENTER", 0),
                nearby("FOOD-1", 100),
                nearby("FOOD-2", 200),
                nearby("TOUR-1", 300),
                nearby("CULTURE-1", 400),
                nearby("CAFE-1", 5_500),
                nearby("CAFE-2", 9_000),
                nearby("STAY-1", 22_000),
                nearby("STAY-2", 31_000));
        List<TourContent> contents = List.of(
                content("CENTER", "12", null, "양조장 관광 콘텐츠"),
                content("FOOD-1", "39", null, "가까운 식당"),
                content("FOOD-2", "39", null, "두 번째 식당"),
                content("TOUR-1", "12", null, "관광지"),
                content("CULTURE-1", "14", null, "문화시설"),
                content("CAFE-1", "39", "A05020900", "첫 카페"),
                content("CAFE-2", "39", "A05020900", "둘째 카페"),
                content("STAY-1", "32", null, "첫 숙소"),
                content("STAY-2", "32", null, "둘째 숙소"));

        given(breweryRepository.findById("BRW-001")).willReturn(Optional.of(brewery));
        given(nearbyRepository.findCourseCandidates("BRW-001")).willReturn(nearby);
        given(tourContentRepository.findAllById(any())).willReturn(contents);

        RecommendedCourseResponse response = service.findByBreweryId("BRW-001");

        assertThat(response.courseId()).isEqualTo("BRW-001");
        assertThat(response.regionLabel()).isEqualTo("충북 영동");
        assertThat(response.stops()).hasSize(9);
        assertThat(response.stops()).extracting(CourseStopResponse::type)
                .containsExactly(CourseStopType.BREWERY, CourseStopType.RESTAURANT, CourseStopType.RESTAURANT,
                        CourseStopType.TOURIST_ATTRACTION, CourseStopType.CULTURAL_FACILITY,
                        CourseStopType.CAFE, CourseStopType.CAFE,
                        CourseStopType.ACCOMMODATION, CourseStopType.ACCOMMODATION);
        assertThat(response.stops()).extracting(CourseStopResponse::order)
                .containsExactly(1, 2, 3, 4, 5, 6, 7, 8, 9);
        assertThat(response.stops()).extracting(CourseStopResponse::contentId)
                .doesNotHaveDuplicates();
    }

    @Test
    void cafeDetailedCategoryIsSeparatedFromRestaurant() {
        Brewery brewery = brewery();
        given(breweryRepository.findById("BRW-001")).willReturn(Optional.of(brewery));
        given(nearbyRepository.findCourseCandidates("BRW-001"))
                .willReturn(List.of(nearby("CAFE-1", 100)));
        given(tourContentRepository.findAllById(any()))
                .willReturn(List.of(content("CAFE-1", "39", "A05020900", "카페")));

        assertThat(service.findByBreweryId("BRW-001").stops().get(1).type())
                .isEqualTo(CourseStopType.CAFE);
    }

    @Test
    void pairingRestaurantRanksBeforeCloserNonMatchingRestaurant() {
        Brewery brewery = brewery();
        given(breweryRepository.findById("BRW-001")).willReturn(Optional.of(brewery));
        given(productQueryService.listProducts("BRW-001", 0, 100)).willReturn(PageResponse.of(List.of(
                new ProductCardResponse(1, "탁주", null, null, null, List.of(LiquorType.탁주),
                        List.of(ProductFlavorTag.고소함, ProductFlavorTag.부드러움),
                        "파전과 잘 어울리는 술", null)), 0, 100, 1));
        given(nearbyRepository.findCourseCandidates("BRW-001")).willReturn(List.of(
                nearby("CLOSE-NON-MATCH", 100), nearby("PAIRING-MATCH", 1_000)));
        given(tourContentRepository.findAllById(any())).willReturn(List.of(
                content("CLOSE-NON-MATCH", "39", "A05020200", "가까운 양식당"),
                content("PAIRING-MATCH", "39", "A05020100", "한식당")));

        List<CourseStopResponse> stops = service.findByBreweryId("BRW-001").stops();

        assertThat(stops.get(1).contentId()).isEqualTo("PAIRING-MATCH");
        assertThat(stops.get(1).pairingComment()).contains("파전").contains("한식");
        assertThat(stops.get(2).contentId()).isEqualTo("CLOSE-NON-MATCH");
        assertThat(stops.get(2).pairingComment()).isNull();
    }

    @Test
    void missingCandidatesReturnsOnlyCenterInsteadOfNullArray() {
        Brewery brewery = brewery();
        given(breweryRepository.findById("BRW-001")).willReturn(Optional.of(brewery));
        given(nearbyRepository.findCourseCandidates("BRW-001")).willReturn(List.of());
        given(tourContentRepository.findAllById(any())).willReturn(List.of());

        assertThat(service.findByBreweryId("BRW-001").stops())
                .singleElement().extracting(CourseStopResponse::type).isEqualTo(CourseStopType.BREWERY);
    }

    @Test
    void unknownBreweryThrowsSharedNotFoundException() {
        given(breweryRepository.findById("BRW-999")).willReturn(Optional.empty());

        assertThatThrownBy(() -> service.findByBreweryId("BRW-999"))
                .isInstanceOf(BreweryNotFoundException.class)
                .hasMessage("양조장을 찾을 수 없습니다: BRW-999");
    }

    private Brewery brewery() {
        Brewery brewery = Brewery.seed("BRW-001", "갈기산", "갈기산",
                "충청북도 영동군", null, 1L, VisitState.Y, VisitState.N);
        brewery.applyRegion("충북", "충청");
        brewery.applyCoordinate(new BigDecimal("36.000000"), new BigDecimal("127.000000"), null, null);
        brewery.applyContentMatch("CENTER", null);
        return brewery;
    }

    private BreweryNearby nearby(String contentId, int distance) {
        return BreweryNearby.create("BRW-001", contentId, BigDecimal.valueOf(distance), 20_000);
    }

    private TourContent content(String id, String type, String cat3, String title) {
        TourContentRow row = new TourContentRow(id, type, title, "충북 영동군", null, null,
                null, null, null, null, cat3, null, null, null, null, null,
                "127.1", "36.1", null, null, null, null, null, null, null);
        return TourContent.create(row, new BigDecimal("36.100000"), new BigDecimal("127.100000"));
    }
}
