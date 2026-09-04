package com.jeontongjuro.backend.map;

import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.course.CourseStopType;
import com.jeontongjuro.backend.tour.TourContent;
import com.jeontongjuro.backend.tour.TourContentRepository;
import java.math.BigDecimal;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 지도 장소 상세 조회. 요청 category가 조회 대상 테이블을 결정한다 —
 * {@code BREWERY}면 brewery(placeId = BRW-xxx), 그 외면 tour_content(placeId = content_id).
 * <p>
 * ★placeId 형식으로 소스를 추론하지 않는다. content_id는 전량 6~7자리 숫자라 BRW-xxx와 겹치지 않지만,
 * 형식 추론은 원천 데이터가 바뀌면 조용히 깨진다. category를 판별 수단으로 삼는 편이 계약이 명시적이다.
 * <p>
 * ★분류는 {@link CourseStopType#from}·{@link CourseStopType#subcategoryOf}만 사용한다.
 * cat3·lcls_systm·제목 키워드를 여기서 다시 판정하면 분류 규칙이 두 벌이 된다.
 */
@Service
@Transactional(readOnly = true)
public class MapPlaceDetailService {

    private final BreweryRepository breweryRepository;
    private final TourContentRepository tourContentRepository;

    public MapPlaceDetailService(BreweryRepository breweryRepository,
                                 TourContentRepository tourContentRepository) {
        this.breweryRepository = breweryRepository;
        this.tourContentRepository = tourContentRepository;
    }

    public MapPlaceDetailResponse findDetail(String placeId, String categoryValue) {
        MapPlaceCategory requested = MapPlaceCategory.parse(categoryValue);
        return requested == MapPlaceCategory.BREWERY
                ? breweryDetail(placeId)
                : tourContentDetail(placeId, requested);
    }

    /** 양조장 상세. 대표 이미지는 brewery에 컬럼이 없어 매칭된 content_id로 tour_content에서 읽는다(최대 2쿼리). */
    private MapPlaceDetailResponse breweryDetail(String placeId) {
        Brewery brewery = breweryRepository.findById(placeId).orElseThrow(() -> notFound(placeId));
        return new MapPlaceDetailResponse(
                brewery.getBreweryId(),
                brewery.getBusinessName(),
                MapPlaceCategory.BREWERY,
                categoryName(MapPlaceCategory.BREWERY),
                null,
                brewery.getLatitude(),
                brewery.getLongitude(),
                null,
                blankToNull(brewery.getAddress()),
                blankToNull(brewery.getPhone()),
                breweryImageUrl(brewery),
                kakaoMapUrl(brewery.getKakaoPlaceUrl(), brewery.getBusinessName(),
                        brewery.getLatitude(), brewery.getLongitude()));
    }

    /**
     * 관광 콘텐츠 상세(1쿼리). 실제 분류를 5종으로 매핑한 뒤 요청 category와 대조한다.
     * <p>
     * ★대조는 반드시 매핑 후 값으로 한다. 클라이언트는 5종만 알기 때문에, 내부 8종 enum으로 비교하면
     * 전통시장·문화시설을 TOURIST_ATTRACTION으로 요청한 정상 호출이 404가 된다.
     * ★분류가 ETC면 매핑 결과가 null이라 어떤 요청 category와도 일치하지 않아 404가 된다 —
     * 목록에 노출되지 않는 장소는 상세도 열리지 않는다.
     */
    private MapPlaceDetailResponse tourContentDetail(String placeId, MapPlaceCategory requested) {
        TourContent content = tourContentRepository.findById(placeId).orElseThrow(() -> notFound(placeId));
        MapPlaceCategory actual = categoryOf(content);
        if (actual != requested) {
            throw notFound(placeId);
        }
        return new MapPlaceDetailResponse(
                content.getContentId(),
                content.getTitle(),
                actual,
                categoryName(actual),
                CourseStopType.subcategoryOf(content),
                content.getLatitude(),
                content.getLongitude(),
                null,
                joinAddress(content.getAddr1(), content.getAddr2()),
                null,
                blankToNull(content.getFirstImage()),
                kakaoMapUrl(null, content.getTitle(), content.getLatitude(), content.getLongitude()));
    }

    /** 공통 분류 규칙의 8종 결과를 지도가 노출하는 5종으로 축약한다. 노출 대상이 아니면 null. */
    private MapPlaceCategory categoryOf(TourContent content) {
        return switch (CourseStopType.from(content)) {
            case RESTAURANT -> MapPlaceCategory.RESTAURANT;
            case CAFE -> MapPlaceCategory.CAFE;
            case ACCOMMODATION -> MapPlaceCategory.ACCOMMODATION;
            case TOURIST_ATTRACTION, CULTURAL_FACILITY, MARKET -> MapPlaceCategory.TOURIST_ATTRACTION;
            default -> null;
        };
    }

    /**
     * 카테고리 한글 라벨. ★추천 코스 API가 쓰는 라벨과 같은 값을 쓴다(기능명세 확정).
     * {@link MapPlaceCategory#displayName()}은 RESTAURANT를 "식당"으로 부르므로 여기서는 사용하지 않는다.
     */
    private String categoryName(MapPlaceCategory category) {
        return switch (category) {
            case BREWERY -> "양조장";
            case RESTAURANT -> "음식점";
            case CAFE -> "카페";
            case ACCOMMODATION -> "숙소";
            case TOURIST_ATTRACTION -> "관광지";
        };
    }

    private String breweryImageUrl(Brewery brewery) {
        String contentId = brewery.getContentId();
        if (contentId == null || contentId.isBlank()) {
            return null;
        }
        return tourContentRepository.findById(contentId)
                .map(content -> blankToNull(content.getFirstImage()))
                .orElse(null);
    }

    /** 추천 코스 API와 같은 형태의 카카오맵 링크를 만든다. 저장된 장소 URL이 있으면 그대로 쓴다. */
    private String kakaoMapUrl(String placeUrl, String name, BigDecimal latitude, BigDecimal longitude) {
        if (placeUrl != null && !placeUrl.isBlank()) {
            return placeUrl;
        }
        if (name == null || name.isBlank()) {
            return null;
        }
        String encodedName = URLEncoder.encode(name, StandardCharsets.UTF_8);
        if (latitude != null && longitude != null) {
            return "https://map.kakao.com/link/to/" + encodedName + ","
                    + latitude.toPlainString() + "," + longitude.toPlainString();
        }
        return "https://map.kakao.com/link/search/" + encodedName;
    }

    /** 추천 코스 API와 같은 주소 조합 규칙: addr1만 / addr2만 / "addr1 addr2" / 둘 다 없으면 null. */
    private String joinAddress(String addr1, String addr2) {
        if (addr1 == null || addr1.isBlank()) {
            return blankToNull(addr2);
        }
        if (addr2 == null || addr2.isBlank()) {
            return addr1;
        }
        return addr1 + " " + addr2;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private MapPlaceNotFoundException notFound(String placeId) {
        return new MapPlaceNotFoundException("장소를 찾을 수 없습니다: " + placeId);
    }
}
