package com.jeontongjuro.backend.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.brewery.CoordSource;
import com.jeontongjuro.backend.brewery.PhoneSource;
import com.jeontongjuro.backend.brewery.VisitState;
import com.jeontongjuro.backend.global.error.InvalidQueryParameterException;
import com.jeontongjuro.backend.tour.TourContent;
import com.jeontongjuro.backend.tour.TourContentRepository;
import com.jeontongjuro.backend.tour.TourContentRow;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 지도 장소 상세 조회 서비스 계약 검증(단위). 분류는 실제 공통 규칙({@code CourseStopType})이 돌고
 * 저장소만 대역으로 바꿔 조합을 촘촘히 검증한다 — DB를 붙이지 않으므로 데이터 오염 위험이 없다.
 * <p>
 * HTTP 상태코드·에러 바디 2필드 계약은 {@link MapPlaceDetailApiTest}가 웹 스택으로 검증한다.
 */
class MapPlaceDetailServiceTest {

    private BreweryRepository breweryRepository;
    private TourContentRepository tourContentRepository;
    private MapPlaceDetailService service;

    @BeforeEach
    void setUp() {
        breweryRepository = mock(BreweryRepository.class);
        tourContentRepository = mock(TourContentRepository.class);
        service = new MapPlaceDetailService(breweryRepository, tourContentRepository);
    }

    // ── 양조장 ────────────────────────────────────────────────────────────────
    @Test
    @DisplayName("양조장: 전화·카카오 place URL이 있으면 원문 그대로 내리고 세부분류는 null")
    void breweryWithPhoneAndPlaceUrl() {
        Brewery brewery = brewery("BRW-101", "테스트 양조장", "36.5", "127.5");
        brewery.applyPhone("033-340-4300", PhoneSource.TOUR);
        brewery.applyKakaoPlaceUrl("http://place.map.kakao.com/775716025");
        when(breweryRepository.findById("BRW-101")).thenReturn(Optional.of(brewery));

        MapPlaceDetailResponse response = service.findDetail("BRW-101", "BREWERY");

        assertThat(response.placeId()).isEqualTo("BRW-101");
        assertThat(response.placeName()).isEqualTo("테스트 양조장");
        assertThat(response.category()).isEqualTo(MapPlaceCategory.BREWERY);
        assertThat(response.categoryName()).isEqualTo("양조장");
        assertThat(response.subcategoryName()).isNull();
        assertThat(response.phone()).isEqualTo("033-340-4300");
        assertThat(response.kakaoMapUrl()).isEqualTo("http://place.map.kakao.com/775716025");
        assertThat(response.address()).isEqualTo("주소");
        assertThat(response.distanceMeters()).isNull();
        assertThat(response.imageUrl()).isNull();
    }

    @Test
    @DisplayName("양조장: 카카오 place URL이 없으면 이름·좌표로 길찾기 링크를 만들고 전화는 null")
    void breweryWithoutPhoneAndPlaceUrlFallsBackToCoordinateLink() {
        Brewery brewery = brewery("BRW-102", "링크없는 양조장", "36.549763", "128.708700");
        when(breweryRepository.findById("BRW-102")).thenReturn(Optional.of(brewery));

        MapPlaceDetailResponse response = service.findDetail("BRW-102", "BREWERY");

        assertThat(response.phone()).isNull();
        assertThat(response.kakaoMapUrl())
                .startsWith("https://map.kakao.com/link/to/")
                .endsWith(",36.549763,128.708700");
    }

    @Test
    @DisplayName("양조장: content_id로 매칭된 tour_content의 first_image를 대표 이미지로 쓴다")
    void breweryImageComesFromMatchedTourContent() {
        Brewery brewery = brewery("BRW-103", "이미지 양조장", "36.5", "127.5");
        brewery.applyContentMatch("745328", OffsetDateTime.now());
        when(breweryRepository.findById("BRW-103")).thenReturn(Optional.of(brewery));
        when(tourContentRepository.findById("745328")).thenReturn(Optional.of(
                tour("745328", "39", "매칭 콘텐츠", "주소", null, "A05020100", "http://img/main.jpg")));

        assertThat(service.findDetail("BRW-103", "BREWERY").imageUrl()).isEqualTo("http://img/main.jpg");
    }

    @Test
    @DisplayName("양조장: 매칭 콘텐츠의 first_image가 공백이면 imageUrl은 null(빈 문자열을 흘리지 않는다)")
    void breweryImageBlankBecomesNull() {
        Brewery brewery = brewery("BRW-104", "공백이미지 양조장", "36.5", "127.5");
        brewery.applyContentMatch("745329", OffsetDateTime.now());
        when(breweryRepository.findById("BRW-104")).thenReturn(Optional.of(brewery));
        when(tourContentRepository.findById("745329")).thenReturn(Optional.of(
                tour("745329", "39", "매칭 콘텐츠", "주소", null, "A05020100", "   ")));

        assertThat(service.findDetail("BRW-104", "BREWERY").imageUrl()).isNull();
    }

    @Test
    @DisplayName("없는 양조장 ID → MapPlaceNotFoundException")
    void unknownBreweryThrows() {
        when(breweryRepository.findById("BRW-999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findDetail("BRW-999", "BREWERY"))
                .isInstanceOf(MapPlaceNotFoundException.class)
                .hasMessageContaining("BRW-999");
    }

    // ── 관광 콘텐츠 5종 ───────────────────────────────────────────────────────
    @Test
    @DisplayName("음식점: 대분류 라벨은 categoryName, 세부분류는 subcategoryName에 나눠 담는다")
    void restaurantSplitsCategoryAndSubcategory() {
        stubTour(tour("2788304", "39", "테스트 한식당", "경기도 테스트시 테스트로 1", "2층",
                "A05020100", "http://img/rest.jpg"));

        MapPlaceDetailResponse response = service.findDetail("2788304", "RESTAURANT");

        assertThat(response.category()).isEqualTo(MapPlaceCategory.RESTAURANT);
        assertThat(response.categoryName()).isEqualTo("음식점");
        assertThat(response.subcategoryName()).isEqualTo("한식");
        assertThat(response.address()).isEqualTo("경기도 테스트시 테스트로 1 2층");
        assertThat(response.phone()).isNull();
        assertThat(response.distanceMeters()).isNull();
        assertThat(response.imageUrl()).isEqualTo("http://img/rest.jpg");
        assertThat(response.kakaoMapUrl())
                .startsWith("https://map.kakao.com/link/to/")
                .endsWith(",36.100000,127.100000");
    }

    @Test
    @DisplayName("카페 → categoryName=카페")
    void cafeCategory() {
        stubTour(tour("300001", "39", "테스트 카페", "주소", null, "A05020900", null));

        MapPlaceDetailResponse response = service.findDetail("300001", "CAFE");

        assertThat(response.category()).isEqualTo(MapPlaceCategory.CAFE);
        assertThat(response.categoryName()).isEqualTo("카페");
        assertThat(response.subcategoryName()).isEqualTo("카페");
    }

    @Test
    @DisplayName("숙소 → categoryName=숙소, 세부분류는 펜션")
    void accommodationCategory() {
        stubTour(tour("300002", "32", "테스트 펜션", "주소", null, "B02010700", null));

        MapPlaceDetailResponse response = service.findDetail("300002", "ACCOMMODATION");

        assertThat(response.category()).isEqualTo(MapPlaceCategory.ACCOMMODATION);
        assertThat(response.categoryName()).isEqualTo("숙소");
        assertThat(response.subcategoryName()).isEqualTo("펜션");
    }

    @Test
    @DisplayName("관광지 → categoryName=관광지")
    void touristAttractionCategory() {
        stubTour(tour("300003", "12", "테스트 명소", "주소", null, "A01010100", null));

        MapPlaceDetailResponse response = service.findDetail("300003", "TOURIST_ATTRACTION");

        assertThat(response.category()).isEqualTo(MapPlaceCategory.TOURIST_ATTRACTION);
        assertThat(response.categoryName()).isEqualTo("관광지");
        assertThat(response.subcategoryName()).isEqualTo("자연관광");
    }

    // ── 5종 매핑(§핵심): 내부 8종을 클라이언트가 아는 5종으로 접어서 대조한다 ──────
    @Test
    @DisplayName("전통시장은 TOURIST_ATTRACTION 요청으로 조회된다(내부 MARKET → 관광지)")
    void marketIsQueriedAsTouristAttraction() {
        stubTour(tour("1012430", "38", "덕계 종합상설시장", "주소", null, "A04010100", null));

        MapPlaceDetailResponse response = service.findDetail("1012430", "TOURIST_ATTRACTION");

        assertThat(response.category()).isEqualTo(MapPlaceCategory.TOURIST_ATTRACTION);
        assertThat(response.categoryName()).isEqualTo("관광지");
        assertThat(response.subcategoryName()).isEqualTo("전통시장");
    }

    @Test
    @DisplayName("문화시설은 TOURIST_ATTRACTION 요청으로 조회된다(내부 CULTURAL_FACILITY → 관광지)")
    void culturalFacilityIsQueriedAsTouristAttraction() {
        stubTour(tour("1123600", "14", "테스트 박물관", "주소", null, "A02060100", null));

        MapPlaceDetailResponse response = service.findDetail("1123600", "TOURIST_ATTRACTION");

        assertThat(response.category()).isEqualTo(MapPlaceCategory.TOURIST_ATTRACTION);
        assertThat(response.subcategoryName()).isEqualTo("박물관");
    }

    // ── 404 조건 ──────────────────────────────────────────────────────────────
    @Test
    @DisplayName("요청 category와 실제 분류가 다르면 404(카페로 요청했는데 실제는 음식점)")
    void mismatchedCategoryThrows() {
        stubTour(tour("400001", "39", "테스트 한식당", "주소", null, "A05020100", null));

        assertThatThrownBy(() -> service.findDetail("400001", "CAFE"))
                .isInstanceOf(MapPlaceNotFoundException.class);
    }

    @Test
    @DisplayName("목록에 노출되지 않는 분류(ETC)는 어떤 category로도 조회되지 않는다")
    void excludedContentThrowsForEveryCategory() {
        stubTour(tour("1054895", "14", "테스트 과학관", "주소", null, "A02060300", null));

        for (String category : new String[]{"TOURIST_ATTRACTION", "RESTAURANT", "CAFE", "ACCOMMODATION"}) {
            assertThatThrownBy(() -> service.findDetail("1054895", category))
                    .isInstanceOf(MapPlaceNotFoundException.class);
        }
    }

    @Test
    @DisplayName("없는 content_id → MapPlaceNotFoundException")
    void unknownContentThrows() {
        when(tourContentRepository.findById("9999999")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.findDetail("9999999", "RESTAURANT"))
                .isInstanceOf(MapPlaceNotFoundException.class)
                .hasMessageContaining("9999999");
    }

    // ── 400 조건 ──────────────────────────────────────────────────────────────
    @Test
    @DisplayName("category 누락·허용 집합 밖 값은 조회 전에 거부한다")
    void invalidCategoryIsRejected() {
        assertThatThrownBy(() -> service.findDetail("2788304", null))
                .isInstanceOf(InvalidQueryParameterException.class);
        assertThatThrownBy(() -> service.findDetail("2788304", ""))
                .isInstanceOf(InvalidQueryParameterException.class);
        assertThatThrownBy(() -> service.findDetail("2788304", "MARKET"))
                .isInstanceOf(InvalidQueryParameterException.class);
        assertThatThrownBy(() -> service.findDetail("2788304", "CULTURAL_FACILITY"))
                .isInstanceOf(InvalidQueryParameterException.class);
    }

    // ── 부분 결측 ─────────────────────────────────────────────────────────────
    @Test
    @DisplayName("주소 조합: addr1만 / addr2만 / 둘 다 없으면 null")
    void addressComposition() {
        stubTour(tour("500001", "39", "테스트 한식당", "주소1", null, "A05020100", null));
        assertThat(service.findDetail("500001", "RESTAURANT").address()).isEqualTo("주소1");

        stubTour(tour("500002", "39", "테스트 한식당", null, "주소2", "A05020100", null));
        assertThat(service.findDetail("500002", "RESTAURANT").address()).isEqualTo("주소2");

        stubTour(tour("500003", "39", "테스트 한식당", null, null, "A05020100", null));
        assertThat(service.findDetail("500003", "RESTAURANT").address()).isNull();
    }

    @Test
    @DisplayName("이미지 결측(빈 문자열 포함) → imageUrl null")
    void missingImageBecomesNull() {
        stubTour(tour("600001", "39", "테스트 한식당", "주소", null, "A05020100", ""));
        assertThat(service.findDetail("600001", "RESTAURANT").imageUrl()).isNull();

        stubTour(tour("600002", "39", "테스트 한식당", "주소", null, "A05020100", null));
        assertThat(service.findDetail("600002", "RESTAURANT").imageUrl()).isNull();
    }

    // ── helpers ───────────────────────────────────────────────────────────────
    private void stubTour(TourContent content) {
        when(tourContentRepository.findById(content.getContentId())).thenReturn(Optional.of(content));
    }

    private Brewery brewery(String id, String name, String latitude, String longitude) {
        Brewery brewery = Brewery.seed(id, name, name, "주소", null, 0L, VisitState.UNKNOWN, VisitState.UNKNOWN);
        brewery.applyCoordinate(new BigDecimal(latitude), new BigDecimal(longitude),
                CoordSource.KAKAO_ADDRESS, OffsetDateTime.now());
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
