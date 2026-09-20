package com.jeontongjuro.backend.map;

import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.brewery.BreweryStaticImageUrls;
import com.jeontongjuro.backend.brewery.BreweryVisibilityPolicy;
import com.jeontongjuro.backend.brewery.ContactSupplementPolicy;
import com.jeontongjuro.backend.course.CourseStopType;
import com.jeontongjuro.backend.global.error.InvalidQueryParameterException;
import com.jeontongjuro.backend.global.web.PageResponse;
import com.jeontongjuro.backend.tour.TourContent;
import com.jeontongjuro.backend.tour.TourContentRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MapPlaceService {
    private static final int DEFAULT_SIZE = 20;
    private static final int MAX_SIZE = 300;
    private static final int SEARCH_MAX_SIZE = 100;
    private static final Map<MapPlaceCategory, List<String>> CONTENT_TYPES_BY_CATEGORY = Map.of(
            MapPlaceCategory.RESTAURANT, List.of("39"),
            MapPlaceCategory.CAFE, List.of("39"),
            MapPlaceCategory.ACCOMMODATION, List.of("32"),
            MapPlaceCategory.TOURIST_ATTRACTION, List.of("12", "14", "15", "28", "38"));
    private final BreweryRepository breweryRepository;
    private final TourContentRepository tourContentRepository;

    public MapPlaceService(BreweryRepository breweryRepository, TourContentRepository tourContentRepository) {
        this.breweryRepository = breweryRepository;
        this.tourContentRepository = tourContentRepository;
    }

    public PageResponse<MapPlaceResponse> find(BigDecimal south, BigDecimal west, BigDecimal north, BigDecimal east,
                                                String categoryValue, int requestedPage, int requestedSize) {
        MapBounds bounds = MapBounds.of(south, west, north, east);
        MapPlaceCategory category = MapPlaceCategory.parse(categoryValue);
        int page = Math.max(0, requestedPage);
        int size = requestedSize < 1 ? DEFAULT_SIZE : Math.min(requestedSize, MAX_SIZE);

        List<MapPlaceResponse> places = new ArrayList<>();
        if (category == MapPlaceCategory.BREWERY) {
            List<Brewery> breweries = breweryRepository.findWithinBounds(
                            bounds.south(), bounds.north(), bounds.west(), bounds.east()).stream()
                    .filter(b -> BreweryVisibilityPolicy.isVisible(b.getBreweryId())).toList();
            Map<String, String> imageByBrewery = breweryImageUrls(breweries);
            breweries.stream().map(b -> fromBrewery(b, imageByBrewery.get(b.getBreweryId())))
                    .forEach(places::add);
        } else {
            Set<String> breweryContentIds = breweryRepository.findWithinBounds(
                            bounds.south(), bounds.north(), bounds.west(), bounds.east()).stream()
                    .map(Brewery::getContentId).filter(Objects::nonNull).collect(Collectors.toSet());
            tourContentRepository.findWithinBoundsAndContentTypeIn(bounds.south(), bounds.north(), bounds.west(), bounds.east(),
                            CONTENT_TYPES_BY_CATEGORY.getOrDefault(category,
                                    List.of("12", "14", "15", "28", "32", "38", "39")))
                    .stream().filter(t -> !breweryContentIds.contains(t.getContentId()))
                    .filter(t -> categoryOf(t) == category)
                    .map(t -> fromTour(t, category)).forEach(places::add);
        }
        places.sort(Comparator.comparing(MapPlaceResponse::placeName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(MapPlaceResponse::placeId, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        int from = (int) Math.min((long) page * size, places.size());
        int to = (int) Math.min((long) from + size, places.size());
        return PageResponse.of(places.subList(from, to), page, size, places.size());
    }

    public PageResponse<MapPlaceResponse> search(String keywordValue, String categoryValue,
                                                  int requestedPage, int requestedSize) {
        String keyword = validateKeyword(keywordValue);
        MapPlaceCategory category = categoryValue == null ? null : MapPlaceCategory.parse(categoryValue);
        int page = Math.max(0, requestedPage);
        int size = requestedSize < 1 ? DEFAULT_SIZE : Math.min(requestedSize, SEARCH_MAX_SIZE);

        List<MapPlaceResponse> places = new ArrayList<>();
        if (category == null || category == MapPlaceCategory.BREWERY) {
            List<Brewery> breweries = breweryRepository.searchMapPlaces(keyword).stream()
                    .filter(b -> BreweryVisibilityPolicy.isVisible(b.getBreweryId())).toList();
            Map<String, String> imageByBrewery = breweryImageUrls(breweries);
            breweries.stream().map(b -> fromBrewery(b, imageByBrewery.get(b.getBreweryId())))
                    .forEach(places::add);
        }
        if (category != MapPlaceCategory.BREWERY) {
            Set<String> breweryContentIds = breweryRepository.findAll().stream()
                    .map(Brewery::getContentId).filter(Objects::nonNull).collect(Collectors.toSet());
            tourContentRepository.searchMapPlaces(keyword).stream()
                    .filter(t -> !breweryContentIds.contains(t.getContentId()))
                    .filter(t -> categoryOf(t) != null)
                    .filter(t -> category == null || categoryOf(t) == category)
                    .map(t -> fromTour(t, categoryOf(t)))
                    .forEach(places::add);
        }

        places.sort(Comparator.comparingInt((MapPlaceResponse place) -> relevance(place, keyword))
                .thenComparing(MapPlaceResponse::placeName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER))
                .thenComparing(MapPlaceResponse::placeId, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)));
        int from = (int) Math.min((long) page * size, places.size());
        int to = (int) Math.min((long) from + size, places.size());
        return PageResponse.of(places.subList(from, to), page, size, places.size());
    }

    private String validateKeyword(String raw) {
        String keyword = raw == null ? "" : raw.trim();
        if (keyword.isEmpty() || keyword.length() > 50) {
            throw new InvalidQueryParameterException("keyword는 공백 제거 후 1~50자여야 합니다.");
        }
        return keyword.toLowerCase(Locale.ROOT);
    }

    private int relevance(MapPlaceResponse place, String keyword) {
        String name = place.placeName() == null ? "" : place.placeName().toLowerCase(Locale.ROOT);
        String address = place.roadAddressName() == null ? "" : place.roadAddressName().toLowerCase(Locale.ROOT);
        if (name.equals(keyword)) return 0;
        if (name.startsWith(keyword)) return 1;
        if (name.contains(keyword)) return 2;
        return address.contains(keyword) ? 3 : 4;
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

    private MapPlaceResponse fromBrewery(Brewery b, String imageUrl) {
        return new MapPlaceResponse(b.getBreweryId(), b.getBusinessName(), MapPlaceCategory.BREWERY,
                MapPlaceCategory.BREWERY.displayName(), null,
                b.getAddress(), ContactSupplementPolicy.phone(b.getBreweryId(), b.getPhone()),
                b.getLatitude(), b.getLongitude(), imageUrl);
    }

    /** 지도 양조장 목록의 이미지도 상세 API와 같은 우선순위(관광공사 원본 → 정적 사진)를 사용한다. */
    private Map<String, String> breweryImageUrls(List<Brewery> breweries) {
        if (breweries.isEmpty()) {
            return Map.of();
        }
        List<String> contentIds = breweries.stream()
                .map(Brewery::getContentId).filter(Objects::nonNull).toList();
        Map<String, TourContent> tourByContentId = new HashMap<>();
        if (!contentIds.isEmpty()) {
            for (TourContent content : tourContentRepository.findByContentIdIn(contentIds)) {
                tourByContentId.put(content.getContentId(), content);
            }
        }
        Map<String, String> imageByBrewery = new HashMap<>();
        for (Brewery brewery : breweries) {
            TourContent content = tourByContentId.get(brewery.getContentId());
            String tourImage = content == null ? null : blankToNull(content.getFirstImage());
            imageByBrewery.put(brewery.getBreweryId(), tourImage != null
                    ? tourImage : BreweryStaticImageUrls.url(brewery.getBreweryId()));
        }
        return imageByBrewery;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value;
    }

    private MapPlaceResponse fromTour(TourContent t, MapPlaceCategory category) {
        String address = String.join(" ", java.util.stream.Stream.of(t.getAddr1(), t.getAddr2())
                .filter(v -> v != null && !v.isBlank()).toList());
        String categoryName = CourseStopType.subcategoryOf(t);
        if (categoryName == null || categoryName.isBlank()) categoryName = category.displayName();
        return new MapPlaceResponse(t.getContentId(), t.getTitle(), category, categoryName,
                null, address, null,
                t.getLatitude(), t.getLongitude(), t.getFirstImage());
    }

}
