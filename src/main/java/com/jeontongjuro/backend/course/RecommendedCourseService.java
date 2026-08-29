package com.jeontongjuro.backend.course;

import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.brewery.BrewerySigunguParser;
import com.jeontongjuro.backend.brewery.query.BreweryNotFoundException;
import com.jeontongjuro.backend.feature.BreweryFeatureTagRepository;
import com.jeontongjuro.backend.liquortype.ProductLiquorTypeRepository;
import com.jeontongjuro.backend.product.query.ProductCardResponse;
import com.jeontongjuro.backend.product.query.ProductQueryService;
import com.jeontongjuro.backend.tour.BreweryNearby;
import com.jeontongjuro.backend.tour.BreweryNearbyRepository;
import com.jeontongjuro.backend.tour.TourContent;
import com.jeontongjuro.backend.tour.TourContentRepository;
import com.jeontongjuro.backend.tour.TourGeoValidator;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 기능명세에 따라 양조장 1곳과 카테고리별 주변 장소 최대 8곳을 동적으로 구성한다. */
@Service
public class RecommendedCourseService {

    static final int PER_CATEGORY_LIMIT = 2;
    static final List<Integer> SEARCH_RADII_METERS = List.of(5_000, 10_000, 20_000);

    private final BreweryRepository breweryRepository;
    private final BreweryNearbyRepository nearbyRepository;
    private final TourContentRepository tourContentRepository;
    private final ProductQueryService productQueryService;
    private final BreweryFeatureTagRepository featureTagRepository;
    private final ProductLiquorTypeRepository liquorTypeRepository;

    public RecommendedCourseService(BreweryRepository breweryRepository,
                                    BreweryNearbyRepository nearbyRepository,
                                    TourContentRepository tourContentRepository,
                                    ProductQueryService productQueryService,
                                    BreweryFeatureTagRepository featureTagRepository,
                                    ProductLiquorTypeRepository liquorTypeRepository) {
        this.breweryRepository = breweryRepository;
        this.nearbyRepository = nearbyRepository;
        this.tourContentRepository = tourContentRepository;
        this.productQueryService = productQueryService;
        this.featureTagRepository = featureTagRepository;
        this.liquorTypeRepository = liquorTypeRepository;
    }

    @Transactional(readOnly = true)
    public RecommendedCourseResponse findByBreweryId(String breweryId) {
        Brewery brewery = breweryRepository.findById(breweryId)
                .orElseThrow(() -> new BreweryNotFoundException("양조장을 찾을 수 없습니다: " + breweryId));
        List<ProductCardResponse> products = productQueryService.listProducts(breweryId, 0, 100).content();
        List<String> descriptions = products.stream().map(ProductCardResponse::description)
                .filter(value -> value != null && !value.isBlank()).toList();
        List<BreweryNearby> nearby = nearbyRepository.findCourseCandidates(breweryId);
        Map<String, TourContent> contentById = loadContent(nearby);
        List<Candidate> candidates = candidates(brewery, nearby, contentById, descriptions);

        List<CourseStopResponse> stops = new ArrayList<>();
        stops.add(centerStop(brewery, contentById.get(brewery.getContentId()), products));
        append(stops, select(candidates, c -> c.type() == CourseStopType.RESTAURANT, true));
        append(stops, select(candidates, RecommendedCourseService::isTourist, false));
        append(stops, select(candidates, c -> c.type() == CourseStopType.CAFE, false));
        append(stops, select(candidates, c -> c.type() == CourseStopType.ACCOMMODATION, false));

        return new RecommendedCourseResponse(brewery.getBreweryId(),
                brewery.getBusinessName() + " 코스", regionLabel(brewery),
                brewery.getBreweryId(), stops);
    }

    private Map<String, TourContent> loadContent(List<BreweryNearby> nearby) {
        List<String> ids = nearby.stream().map(BreweryNearby::getContentId).distinct().toList();
        Map<String, TourContent> result = new LinkedHashMap<>();
        tourContentRepository.findAllById(ids).forEach(content -> result.put(content.getContentId(), content));
        return result;
    }

    private List<Candidate> candidates(Brewery brewery, List<BreweryNearby> nearby,
                                       Map<String, TourContent> contentById, List<String> descriptions) {
        Set<String> seen = new HashSet<>();
        List<Candidate> result = new ArrayList<>();
        for (BreweryNearby row : nearby) {
            String id = row.getContentId();
            if (id == null || id.equals(brewery.getContentId()) || !seen.add(id)) continue;
            TourContent content = contentById.get(id);
            if (content == null || row.getDistanceM() == null) continue;
            CourseStopType type = CourseStopType.from(content);
            String pairing = type == CourseStopType.RESTAURANT
                    ? FoodPairingMatcher.pairingComment(descriptions, content).orElse(null) : null;
            result.add(new Candidate(distance(row), content, type, pairing));
        }
        // brewery_nearby는 현재 운영 수집 반경이 20km라 그 밖의 후보가 없다. 20km까지 넓혀도
        // 카테고리 정원이 안 차면 tour_content 전체 좌표에서 Haversine 거리로 보충해 상한 없는 확장을 구현한다.
        if (needsGlobalFallback(result) && brewery.getLatitude() != null && brewery.getLongitude() != null) {
            for (TourContent content : tourContentRepository.findAll()) {
                String id = content.getContentId();
                if (id == null || id.equals(brewery.getContentId()) || !seen.add(id)
                        || content.getLatitude() == null || content.getLongitude() == null) continue;
                CourseStopType type = CourseStopType.from(content);
                if (!isCourseCategory(type)) continue;
                int meters = (int) Math.round(TourGeoValidator.haversineMeters(
                        brewery.getLatitude(), brewery.getLongitude(), content.getLatitude(), content.getLongitude()));
                String pairing = type == CourseStopType.RESTAURANT
                        ? FoodPairingMatcher.pairingComment(descriptions, content).orElse(null) : null;
                result.add(new Candidate(meters, content, type, pairing));
            }
        }
        return result;
    }

    private boolean needsGlobalFallback(List<Candidate> candidates) {
        return candidates.stream().filter(c -> c.type() == CourseStopType.RESTAURANT).count() < PER_CATEGORY_LIMIT
                || candidates.stream().filter(RecommendedCourseService::isTourist).count() < PER_CATEGORY_LIMIT
                || candidates.stream().filter(c -> c.type() == CourseStopType.CAFE).count() < PER_CATEGORY_LIMIT
                || candidates.stream().filter(c -> c.type() == CourseStopType.ACCOMMODATION).count() < PER_CATEGORY_LIMIT;
    }

    private static boolean isCourseCategory(CourseStopType type) {
        return type == CourseStopType.RESTAURANT || type == CourseStopType.CAFE
                || type == CourseStopType.ACCOMMODATION || type == CourseStopType.TOURIST_ATTRACTION
                || type == CourseStopType.CULTURAL_FACILITY || type == CourseStopType.MARKET;
    }

    /** 5→10→20km로 넓혀 두 곳을 확보하고 최종 반경 안에서 페어링·거리 우선순위를 적용한다. */
    private List<Candidate> select(List<Candidate> candidates, Predicate<Candidate> category,
                                   boolean pairingFirst) {
        List<Candidate> categoryCandidates = candidates.stream().filter(category).toList();
        if (categoryCandidates.isEmpty()) return List.of();
        int finalRadius = categoryCandidates.stream().mapToInt(RecommendedCourseService::distance).max().orElse(20_000);
        for (int radius : SEARCH_RADII_METERS) {
            if (categoryCandidates.stream().filter(c -> distance(c) <= radius).count() >= PER_CATEGORY_LIMIT) {
                finalRadius = radius;
                break;
            }
        }
        Comparator<Candidate> byDistance = Comparator.comparingInt((Candidate candidate) -> distance(candidate))
                .thenComparing(c -> c.content().getContentId());
        Comparator<Candidate> order = pairingFirst
                ? Comparator.comparing((Candidate c) -> c.pairingComment() != null).reversed().thenComparing(byDistance)
                : byDistance;
        int selectedRadius = finalRadius;
        return categoryCandidates.stream().filter(c -> distance(c) <= selectedRadius)
                .sorted(order).limit(PER_CATEGORY_LIMIT).toList();
    }

    private void append(List<CourseStopResponse> stops, List<Candidate> selected) {
        for (Candidate candidate : selected) stops.add(toStop(stops.size() + 1, candidate));
    }

    private CourseStopResponse centerStop(Brewery brewery, TourContent matchedContent,
                                          List<ProductCardResponse> products) {
        List<String> featureTags = featureTagRepository.findByBreweryIdIn(List.of(brewery.getBreweryId())).stream()
                .map(tag -> tag.getFeatureType().name()).distinct().toList();
        List<String> liquorTypes = liquorTypeRepository.findDistinctTypesByBreweryIdIn(List.of(brewery.getBreweryId()))
                .stream().map(row -> ((Enum<?>) row[1]).name()).distinct().toList();
        if (liquorTypes.isEmpty()) {
            liquorTypes = products.stream().flatMap(product -> product.liquorTypes().stream())
                    .map(Enum::name).distinct().toList();
        }
        String image = matchedContent == null ? null
                : firstNonBlank(matchedContent.getFirstImage(), matchedContent.getFirstImage2());
        return new CourseStopResponse(1, CourseStopType.BREWERY, brewery.getBreweryId(), brewery.getBusinessName(),
                brewery.getAddress(), brewery.getLatitude(), brewery.getLongitude(), 0, image, "여행의 시작",
                "양조장", null, brewery.getKakaoPlaceUrl(), null, featureTags, liquorTypes);
    }

    private CourseStopResponse toStop(int order, Candidate candidate) {
        TourContent content = candidate.content();
        return new CourseStopResponse(order, candidate.type(), content.getContentId(), content.getTitle(),
                joinAddress(content.getAddr1(), content.getAddr2()), content.getLatitude(), content.getLongitude(),
                distance(candidate), firstNonBlank(content.getFirstImage(), content.getFirstImage2()),
                reason(candidate.type()), categoryName(candidate.type()), subcategoryName(content, candidate.type()),
                kakaoSearchUrl(content.getTitle()), candidate.pairingComment(), List.of(), List.of());
    }

    private static boolean isTourist(Candidate candidate) {
        return candidate.type() == CourseStopType.TOURIST_ATTRACTION
                || candidate.type() == CourseStopType.CULTURAL_FACILITY
                || candidate.type() == CourseStopType.MARKET;
    }

    private static int distance(Candidate candidate) {
        return candidate.distanceMeters();
    }

    private static int distance(BreweryNearby nearby) {
        return nearby.getDistanceM().setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private String regionLabel(Brewery brewery) {
        String sigungu = BrewerySigunguParser.parse(brewery.getAddress());
        if (brewery.getSido() == null) return sigungu == null ? brewery.getRegion() : sigungu;
        return sigungu == null ? brewery.getSido() : brewery.getSido() + " " + sigungu;
    }

    private String categoryName(CourseStopType type) {
        return switch (type) {
            case BREWERY -> "양조장";
            case RESTAURANT -> "음식점";
            case CAFE -> "카페";
            case ACCOMMODATION -> "숙소";
            default -> "관광지";
        };
    }

    private String subcategoryName(TourContent content, CourseStopType type) {
        if (type == CourseStopType.CULTURAL_FACILITY) return "문화시설";
        if (type == CourseStopType.MARKET) return "쇼핑·시장";
        return firstNonBlank(content.getLclsSystm3(), content.getLclsSystm2(), content.getCat3());
    }

    private String reason(CourseStopType type) {
        return switch (type) {
            case RESTAURANT -> "양조장과 가까운 지역 음식점";
            case CAFE -> "여행 중 쉬어가기 좋은 카페";
            case ACCOMMODATION -> "코스와 가까운 숙소";
            default -> "함께 둘러보기 좋은 주변 명소";
        };
    }

    private String kakaoSearchUrl(String title) {
        if (title == null || title.isBlank()) return null;
        return "https://map.kakao.com/link/search/" + URLEncoder.encode(title, StandardCharsets.UTF_8);
    }

    private String joinAddress(String addr1, String addr2) {
        if (addr1 == null || addr1.isBlank()) return firstNonBlank(addr2);
        if (addr2 == null || addr2.isBlank()) return addr1;
        return addr1 + " " + addr2;
    }

    private String firstNonBlank(String... values) {
        for (String value : values) if (value != null && !value.isBlank()) return value;
        return null;
    }

    private record Candidate(int distanceMeters, TourContent content, CourseStopType type,
                             String pairingComment) {
    }
}
