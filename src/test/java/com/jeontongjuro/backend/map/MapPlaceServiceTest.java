package com.jeontongjuro.backend.map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.brewery.CoordSource;
import com.jeontongjuro.backend.brewery.VisitState;
import com.jeontongjuro.backend.global.error.InvalidQueryParameterException;
import com.jeontongjuro.backend.global.web.PageResponse;
import com.jeontongjuro.backend.tour.TourContentRepository;
import com.jeontongjuro.backend.tour.TourContent;
import com.jeontongjuro.backend.tour.TourContentRow;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class MapPlaceServiceTest {
    private BreweryRepository breweryRepository;
    private TourContentRepository tourContentRepository;
    private MapPlaceService service;

    @BeforeEach
    void setUp() {
        breweryRepository = mock(BreweryRepository.class);
        tourContentRepository = mock(TourContentRepository.class);
        service = new MapPlaceService(breweryRepository, tourContentRepository);
    }

    @Test
    void 사용자좌표가있으면거리순으로반환한다() {
        Brewery far = brewery("BRW-002", "가 양조장", "37.20", "127.00");
        Brewery near = brewery("BRW-001", "나 양조장", "37.01", "127.00");
        when(breweryRepository.findWithinBounds(any(), any(), any(), any())).thenReturn(List.of(far, near));
        PageResponse<MapPlaceResponse> result = service.find(
                bd("36"), bd("126"), bd("38"), bd("128"), "BREWERY", bd("37"), bd("127"), 0, 20);
        assertThat(result.content()).extracting(MapPlaceResponse::placeId).containsExactly("BRW-001", "BRW-002");
        assertThat(result.content().get(0).distance()).isEqualTo(1.1);
    }

    @Test
    void 사용자좌표가없으면이름순이고크기는300으로제한한다() {
        Brewery second = brewery("BRW-002", "나 양조장", "37.20", "127.00");
        Brewery first = brewery("BRW-001", "가 양조장", "37.01", "127.00");
        when(breweryRepository.findWithinBounds(any(), any(), any(), any())).thenReturn(List.of(second, first));
        PageResponse<MapPlaceResponse> result = service.find(
                bd("36"), bd("126"), bd("38"), bd("128"), "BREWERY", null, null, 0, 999);
        assertThat(result.size()).isEqualTo(300);
        assertThat(result.content()).extracting(MapPlaceResponse::placeName).containsExactly("가 양조장", "나 양조장");
        assertThat(result.content()).allMatch(place -> place.distance() == null);
    }

    @Test
    void 잘못된영역과좌표쌍과카테고리를거부한다() {
        assertThatThrownBy(() -> service.find(bd("38"), bd("126"), bd("37"), bd("128"),
                "BREWERY", null, null, 0, 20)).isInstanceOf(InvalidQueryParameterException.class);
        assertThatThrownBy(() -> service.find(bd("36"), bd("126"), bd("38"), bd("128"),
                "BREWERY", bd("37"), null, 0, 20)).isInstanceOf(InvalidQueryParameterException.class);
        assertThatThrownBy(() -> service.find(bd("36"), bd("126"), bd("38"), bd("128"),
                "MARKET", null, null, 0, 20)).isInstanceOf(InvalidQueryParameterException.class);
    }

    @Test
    void 관광공사장소는주소일부가없어도조회되고세부분류를내린다() {
        TourContent restaurant = tour("1", "39", "한식집", null, "상세주소", "A05020100");
        when(breweryRepository.findWithinBounds(any(), any(), any(), any())).thenReturn(List.of());
        when(tourContentRepository.findWithinBounds(any(), any(), any(), any())).thenReturn(List.of(restaurant));

        PageResponse<MapPlaceResponse> result = service.find(
                bd("36"), bd("126"), bd("38"), bd("128"), "RESTAURANT", null, null, 0, 20);

        assertThat(result.totalElements()).isOne();
        assertThat(result.content().get(0).roadAddressName()).isEqualTo("상세주소");
        assertThat(result.content().get(0).categoryName()).isEqualTo("한식");
    }

    @Test
    void 다섯카테고리분류와전통시장관광지포함을보장한다() {
        List<TourContent> contents = List.of(
                tour("restaurant", "39", "식당", "주소", null, "A05020100"),
                tour("cafe", "39", "카페", "주소", null, "A05020900"),
                tour("tour", "12", "공원", "주소", null, "A01010100"),
                tour("market", "38", "전통시장", "주소", null, "A04010100"),
                tour("accommodation", "32", "한옥 숙소", "주소", null, "B02011600"));
        when(breweryRepository.findWithinBounds(any(), any(), any(), any())).thenReturn(List.of());
        when(tourContentRepository.findWithinBounds(any(), any(), any(), any())).thenReturn(contents);

        assertThat(findIds("RESTAURANT")).containsExactly("restaurant");
        assertThat(findIds("CAFE")).containsExactly("cafe");
        assertThat(findIds("TOURIST_ATTRACTION")).containsExactlyInAnyOrder("tour", "market");
        assertThat(findIds("ACCOMMODATION")).containsExactly("accommodation");
    }

    private Brewery brewery(String id, String name, String latitude, String longitude) {
        Brewery brewery = Brewery.seed(id, name, name, "주소", null, 0L, VisitState.UNKNOWN, VisitState.UNKNOWN);
        brewery.applyCoordinate(bd(latitude), bd(longitude), CoordSource.KAKAO_ADDRESS, OffsetDateTime.now());
        return brewery;
    }

    private BigDecimal bd(String value) {
        return new BigDecimal(value);
    }

    private List<String> findIds(String category) {
        return service.find(bd("36"), bd("126"), bd("38"), bd("128"), category,
                        null, null, 0, 20).content().stream().map(MapPlaceResponse::placeId).toList();
    }

    private TourContent tour(String id, String contentTypeId, String title,
                             String addr1, String addr2, String cat3) {
        TourContentRow row = new TourContentRow(id, contentTypeId, title, addr1, addr2,
                null, null, null, null, null, cat3, null, null, null,
                null, null, "127", "37", null, null, null, null, null, null, null);
        return TourContent.create(row, bd("37"), bd("127"));
    }
}
