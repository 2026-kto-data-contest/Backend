package com.jeontongjuro.backend.map;

import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.course.CourseStopType;
import com.jeontongjuro.backend.global.web.PageResponse;
import com.jeontongjuro.backend.tour.TourContent;
import com.jeontongjuro.backend.tour.TourContentRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 지도 「지금 찾아보면 좋은 메뉴」 조회 서비스. */
@Service
@Transactional(readOnly = true)
public class MapMenuService {
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 100;

    private final BreweryRepository breweryRepository;
    private final TourContentRepository tourContentRepository;

    public MapMenuService(BreweryRepository breweryRepository, TourContentRepository tourContentRepository) {
        this.breweryRepository = breweryRepository;
        this.tourContentRepository = tourContentRepository;
    }

    public List<MapMenuResponse> menus() {
        return List.of(MapMenu.values()).stream()
                .map(menu -> new MapMenuResponse(menu.name(), menu.displayName()))
                .toList();
    }

    public PageResponse<MapPlaceResponse> places(String menuValue, BigDecimal userLatitude,
                                                  BigDecimal userLongitude, int requestedPage, int requestedSize) {
        MapMenu menu = MapMenu.parse(menuValue);
        int page = Math.max(0, requestedPage);
        int size = requestedSize < 1 ? DEFAULT_SIZE : Math.min(requestedSize, MAX_SIZE);
        validateCoordinatePair(userLatitude, userLongitude);

        Set<String> breweryContentIds = breweryRepository.findAll().stream()
                .map(Brewery::getContentId).filter(Objects::nonNull).collect(HashSet::new, Set::add, Set::addAll);
        List<MapPlaceResponse> places = new ArrayList<>();
        for (TourContent content : tourContentRepository.findByContentTypeIdIn(List.of("39"))) {
            if (content.getLatitude() == null || content.getLongitude() == null
                    || breweryContentIds.contains(content.getContentId())
                    || CourseStopType.from(content) != CourseStopType.RESTAURANT
                    || !containsMenu(content, menu)) {
                continue;
            }
            places.add(toResponse(content, userLatitude, userLongitude));
        }
        Comparator<MapPlaceResponse> order = userLatitude == null
                ? Comparator.comparing(MapPlaceResponse::placeName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                : Comparator.comparing(MapPlaceResponse::distance)
                        .thenComparing(MapPlaceResponse::placeName,
                                Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        places.sort(order.thenComparing(MapPlaceResponse::placeId));
        int from = (int) Math.min((long) page * size, places.size());
        int to = Math.min(from + size, places.size());
        return PageResponse.of(places.subList(from, to), page, size, places.size());
    }

    private boolean containsMenu(TourContent content, MapMenu menu) {
        String text = String.join(" ", java.util.stream.Stream.of(content.getTitle(), content.getCat1(),
                        content.getCat2(), content.getCat3(), content.getLclsSystm1(), content.getLclsSystm2(),
                        content.getLclsSystm3()).filter(v -> v != null && !v.isBlank()).toList())
                .toLowerCase(Locale.ROOT);
        return switch (menu) {
            case PAJEON -> text.contains("파전") || text.contains("전집") || text.contains("빈대떡")
                    || text.contains("부침개");
            case SAMHAP -> text.contains("삼합");
            case BOSSAM -> text.contains("보쌈");
            case HANWOO -> text.contains("한우");
            case MEAT -> text.contains("고기") || text.contains("갈비") || text.contains("삼겹살");
            case SASHIMI -> text.contains("회") || text.contains("횟집") || text.contains("생선");
            case CHEESE -> text.contains("치즈") || text.contains("파스타");
        };
    }

    private MapPlaceResponse toResponse(TourContent content, BigDecimal userLatitude, BigDecimal userLongitude) {
        String address = String.join(" ", java.util.stream.Stream.of(content.getAddr1(), content.getAddr2())
                .filter(v -> v != null && !v.isBlank()).toList());
        return new MapPlaceResponse(content.getContentId(), content.getTitle(), MapPlaceCategory.RESTAURANT,
                "식당", distance(userLatitude, userLongitude, content.getLatitude(), content.getLongitude()),
                address, null, content.getLatitude(), content.getLongitude(), content.getFirstImage());
    }

    private void validateCoordinatePair(BigDecimal latitude, BigDecimal longitude) {
        if ((latitude == null) != (longitude == null)) {
            throw new com.jeontongjuro.backend.global.error.InvalidQueryParameterException(
                    "userLatitude와 userLongitude는 함께 전달해야 합니다.");
        }
    }

    private Double distance(BigDecimal fromLat, BigDecimal fromLng, BigDecimal toLat, BigDecimal toLng) {
        if (fromLat == null) return null;
        double lat1 = Math.toRadians(fromLat.doubleValue());
        double lat2 = Math.toRadians(toLat.doubleValue());
        double dLat = lat2 - lat1;
        double dLng = Math.toRadians(toLng.doubleValue() - fromLng.doubleValue());
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
        return Math.round(6371.0088 * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a)) * 10.0) / 10.0;
    }
}
