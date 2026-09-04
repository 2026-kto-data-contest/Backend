package com.jeontongjuro.backend.map;

import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.course.CourseStopType;
import com.jeontongjuro.backend.global.error.InvalidQueryParameterException;
import com.jeontongjuro.backend.global.web.PageResponse;
import com.jeontongjuro.backend.tour.TourContent;
import com.jeontongjuro.backend.tour.TourContentRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MapPlaceService {
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 300;
    private final BreweryRepository breweryRepository;
    private final TourContentRepository tourContentRepository;

    public MapPlaceService(BreweryRepository breweryRepository, TourContentRepository tourContentRepository) {
        this.breweryRepository = breweryRepository;
        this.tourContentRepository = tourContentRepository;
    }

    public PageResponse<MapPlaceResponse> find(BigDecimal south, BigDecimal west, BigDecimal north, BigDecimal east,
                                                String categoryValue, BigDecimal userLatitude,
                                                BigDecimal userLongitude, int requestedPage, int requestedSize) {
        MapBounds bounds = MapBounds.of(south, west, north, east);
        MapPlaceCategory category = MapPlaceCategory.parse(categoryValue);
        validateUserCoordinatePair(userLatitude, userLongitude);
        int page = Math.max(0, requestedPage);
        int size = requestedSize < 1 ? DEFAULT_SIZE : Math.min(requestedSize, MAX_SIZE);

        List<MapPlaceResponse> places = new ArrayList<>();
        if (category == MapPlaceCategory.BREWERY) {
            breweryRepository.findWithinBounds(bounds.south(), bounds.north(), bounds.west(), bounds.east())
                    .stream().map(b -> fromBrewery(b, userLatitude, userLongitude)).forEach(places::add);
        } else {
            Set<String> breweryContentIds = breweryRepository.findWithinBounds(
                            bounds.south(), bounds.north(), bounds.west(), bounds.east()).stream()
                    .map(Brewery::getContentId).filter(Objects::nonNull).collect(Collectors.toSet());
            tourContentRepository.findWithinBounds(bounds.south(), bounds.north(), bounds.west(), bounds.east())
                    .stream().filter(t -> !breweryContentIds.contains(t.getContentId()))
                    .filter(t -> categoryOf(t) == category)
                    .map(t -> fromTour(t, category, userLatitude, userLongitude)).forEach(places::add);
        }
        Comparator<MapPlaceResponse> comparator = userLatitude == null
                ? Comparator.comparing(MapPlaceResponse::placeName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                : Comparator.comparing(MapPlaceResponse::distance).thenComparing(MapPlaceResponse::placeName,
                        Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER));
        places.sort(comparator);
        int from = (int) Math.min((long) page * size, places.size());
        int to = (int) Math.min((long) from + size, places.size());
        return PageResponse.of(places.subList(from, to), page, size, places.size());
    }

    private void validateUserCoordinatePair(BigDecimal latitude, BigDecimal longitude) {
        if ((latitude == null) != (longitude == null)) {
            throw new InvalidQueryParameterException("userLatitude와 userLongitude는 함께 전달해야 합니다.");
        }
        if (latitude != null && (latitude.compareTo(new BigDecimal("-90")) < 0
                || latitude.compareTo(new BigDecimal("90")) > 0
                || longitude.compareTo(new BigDecimal("-180")) < 0
                || longitude.compareTo(new BigDecimal("180")) > 0)) {
            throw new InvalidQueryParameterException("사용자 좌표가 올바르지 않습니다.");
        }
    }

    private MapPlaceCategory categoryOf(TourContent content) {
        return switch (CourseStopType.from(content)) {
            case RESTAURANT -> MapPlaceCategory.RESTAURANT;
            case CAFE -> MapPlaceCategory.CAFE;
            case ACCOMMODATION -> MapPlaceCategory.ACCOMMODATION;
            case TOURIST_ATTRACTION, CULTURAL_FACILITY, MARKET -> MapPlaceCategory.TOURIST_ATTRACTION;
            default -> null;
        };
    }

    private MapPlaceResponse fromBrewery(Brewery b, BigDecimal userLat, BigDecimal userLng) {
        return new MapPlaceResponse(b.getBreweryId(), b.getBusinessName(), MapPlaceCategory.BREWERY,
                MapPlaceCategory.BREWERY.displayName(), distance(userLat, userLng, b.getLatitude(), b.getLongitude()),
                b.getAddress(), b.getPhone(), b.getLatitude(), b.getLongitude(), null);
    }

    private MapPlaceResponse fromTour(TourContent t, MapPlaceCategory category,
                                      BigDecimal userLat, BigDecimal userLng) {
        String address = String.join(" ", java.util.stream.Stream.of(t.getAddr1(), t.getAddr2())
                .filter(v -> v != null && !v.isBlank()).toList());
        String categoryName = CourseStopType.subcategoryOf(t);
        if (categoryName == null || categoryName.isBlank()) categoryName = category.displayName();
        return new MapPlaceResponse(t.getContentId(), t.getTitle(), category, categoryName,
                distance(userLat, userLng, t.getLatitude(), t.getLongitude()), address, null,
                t.getLatitude(), t.getLongitude(), t.getFirstImage());
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
