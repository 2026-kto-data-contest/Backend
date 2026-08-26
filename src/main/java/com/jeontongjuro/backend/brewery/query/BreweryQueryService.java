package com.jeontongjuro.backend.brewery.query;

import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.brewery.BrewerySigunguParser;
import com.jeontongjuro.backend.experience.BreweryExperience;
import com.jeontongjuro.backend.experience.BreweryExperienceRepository;
import com.jeontongjuro.backend.feature.BreweryFeatureTag;
import com.jeontongjuro.backend.feature.BreweryFeatureTagRepository;
import com.jeontongjuro.backend.feature.FeatureType;
import com.jeontongjuro.backend.global.web.PageResponse;
import com.jeontongjuro.backend.liquortype.LiquorType;
import com.jeontongjuro.backend.liquortype.ProductLiquorTypeRepository;
import com.jeontongjuro.backend.product.ProductBreweryLinkRepository;
import com.jeontongjuro.backend.product.query.ProductQueryService;
import com.jeontongjuro.backend.product.query.SensoryTag;
import com.jeontongjuro.backend.product.query.SensoryTagMatcher;
import com.jeontongjuro.backend.search.SearchKeyword;
import com.jeontongjuro.backend.tour.TourContent;
import com.jeontongjuro.backend.tour.TourContentRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 양조장 조회 서비스. 리스트(필터·페이징)와 상세(단건) 두 진입점을 제공한다.
 * <p>
 * 프로젝션 방식 = <b>Specification 필터 + 서비스 계층 명시 매핑</b>(JPQL 생성자 표현식 아님).
 * 이유: 후속 주종·도수 필터를 Specification 하나 추가로 얹는 확장성이 이 계열의 핵심인데,
 * 생성자 표현식 프로젝션은 Specification과 조합이 까다롭다. brewery는 단일 테이블·연관 없음이라
 * 엔티티를 읽어 DTO로 옮겨도 조인·N+1이 없고, {@code open-in-view=false}에서도 매핑이
 * 트랜잭션 안({@code @Transactional(readOnly)})에서 일어나 지연로딩 위험이 없다.
 * <p>
 * 도수·주종·대표 이미지·특징 태그는 brewery 엔티티에 없는 파생값이라, 자식 테이블을 {@code IN} 배치로
 * 한 번씩 읽어 brewery_id별로 그룹핑한다(N+1 회피 — 특징 태그 선례와 동일). 리스트 한 페이지당
 * 자식 조회는 특징·주종·도수·이미지 각 1쿼리(+페이지·count)라 상수로 유지된다.
 * <p>
 * 정렬은 고정: business_name ASC, brewery_id ASC(클라이언트 sort 미수용). 크기는 상한 클램프.
 */
@Service
@Transactional(readOnly = true)
public class BreweryQueryService {

    /** 페이지 기본 크기(size 미지정·비정상 시). */
    static final int DEFAULT_SIZE = 20;
    /** 페이지 최대 크기. 초과 요청은 400이 아니라 이 값으로 클램프한다(부분 결과라도 반환). */
    static final int MAX_SIZE = 100;

    /** 고정 정렬: 상호명 오름차순, 동명 시 brewery_id로 전순서 확정(경계 중복·누락 방지). */
    private static final Sort FIXED_SORT =
            Sort.by(Sort.Order.asc("businessName"), Sort.Order.asc("breweryId"));

    private final BreweryRepository breweryRepository;
    private final BreweryFeatureTagRepository featureTagRepository;
    private final ProductLiquorTypeRepository liquorTypeRepository;
    private final ProductBreweryLinkRepository linkRepository;
    private final TourContentRepository tourContentRepository;
    private final BreweryExperienceRepository experienceRepository;
    private final ProductQueryService productQueryService;

    public BreweryQueryService(BreweryRepository breweryRepository,
                               BreweryFeatureTagRepository featureTagRepository,
                               ProductLiquorTypeRepository liquorTypeRepository,
                               ProductBreweryLinkRepository linkRepository,
                               TourContentRepository tourContentRepository,
                               BreweryExperienceRepository experienceRepository,
                               ProductQueryService productQueryService) {
        this.breweryRepository = breweryRepository;
        this.featureTagRepository = featureTagRepository;
        this.liquorTypeRepository = liquorTypeRepository;
        this.linkRepository = linkRepository;
        this.tourContentRepository = tourContentRepository;
        this.experienceRepository = experienceRepository;
        this.productQueryService = productQueryService;
    }

    public PageResponse<BreweryListItemResponse> search(BrewerySearchCondition condition, int page, int size) {
        Pageable pageable = PageRequest.of(clampPage(page), clampSize(size), FIXED_SORT);
        Specification<Brewery> spec = BreweryQuerySpecifications.build(condition);

        Page<Brewery> result = breweryRepository.findAll(spec, pageable);
        return PageResponse.of(toListItems(result.getContent()), result);
    }

    /**
     * 통합 검색(GET /api/v1/search) — 정확도순 정렬 + 양조장 단위 중복 제거. 순위:
     * <pre>1순위 상호명 전방일치 &gt; 2순위 상호명 부분일치 &gt; 3순위 표시집합 제품명 부분일치</pre>
     * 한 양조장은 자기가 해당하는 가장 높은 순위에만 속하고(이름·제품 동시 매칭 중복 제거), 순위 내부는 목록
     * API와 같은 {@code businessName ASC → breweryId ASC}로 정렬한다. {@code totalElements}는 중복 제거 후
     * 양조장 수(= 명세의 {N}).
     * <p>
     * 매칭은 입력·대상 모두 {@link SearchKeyword#normalizeTarget} 규칙(허용문자 제거·NFC·lower)으로 정규화해
     * 비교한다 — 특수문자를 포함한 이름(예: {@code 이화주(술샘)})도 자동완성이 내려준 그대로 재검색하면 매칭된다.
     * needle은 {@link SearchKeyword#normalizeForMatch}로 이미 정규화된 값이며, 빈 문자열이면 결과 0건이다.
     * <p>
     * 전략은 인메모리다: 모집단이 전 양조장(≤59)이라 전건을 읽어 순위 부여·정렬한 뒤 페이지를 슬라이스하고,
     * 그 페이지 양조장만 카드로 매핑한다({@link #toListItems}). 3순위 표시집합·특수문자 정규화가 SQL 술어로
     * 깔끔히 떨어지지 않고(제외 시드가 파일·병합이 Java 로직), 모집단이 작아 DB 페이징 이점이 없어서다.
     * 매핑 쿼리 수는 페이지 크기 기준 상수라 N+1이 없다.
     */
    public PageResponse<BreweryListItemResponse> searchByAccuracy(String needle, int page, int size) {
        int clampedPage = clampPage(page);
        int clampedSize = clampSize(size);
        if (needle == null || needle.isEmpty()) {
            return PageResponse.of(List.of(), clampedPage, clampedSize, 0L);
        }

        Map<String, List<String>> productNamesByBrewery =
                productQueryService.displayedProductNamesByBreweryId();

        List<RankedBrewery> matched = new ArrayList<>();
        for (Brewery brewery : breweryRepository.findAll()) {
            int tier = tierOf(brewery, needle,
                    productNamesByBrewery.getOrDefault(brewery.getBreweryId(), List.of()));
            if (tier > 0) {
                matched.add(new RankedBrewery(tier, brewery));
            }
        }
        matched.sort(Comparator
                .comparingInt(RankedBrewery::tier)
                .thenComparing(r -> r.brewery().getBusinessName())
                .thenComparing(r -> r.brewery().getBreweryId()));

        long totalElements = matched.size();
        int from = Math.min(clampedPage * clampedSize, matched.size());
        int to = Math.min(from + clampedSize, matched.size());
        List<Brewery> pageBreweries = matched.subList(from, to).stream()
                .map(RankedBrewery::brewery)
                .toList();

        return PageResponse.of(toListItems(pageBreweries), clampedPage, clampedSize, totalElements);
    }

    /**
     * 정확도 순위: 1=상호명 전방일치, 2=상호명 부분일치, 3=표시집합 제품명 부분일치, 0=미매칭. 매칭은 전부
     * 정규화된 문자열({@link SearchKeyword#normalizeTarget}) 기준이라 입력과 규칙이 같다. 한 양조장은 가장 높은
     * 순위 하나만 반환한다(상위 순위에 걸리면 하위는 보지 않는다 → 중복 제거).
     */
    private static int tierOf(Brewery brewery, String needle, List<String> productNames) {
        String name = SearchKeyword.normalizeTarget(brewery.getBusinessName());
        if (name.startsWith(needle)) {
            return 1;
        }
        if (name.contains(needle)) {
            return 2;
        }
        for (String productName : productNames) {
            if (SearchKeyword.normalizeTarget(productName).contains(needle)) {
                return 3;
            }
        }
        return 0;
    }

    /**
     * 양조장 목록 → 카드 응답 매핑(리스트·통합검색 공용). 특징·주종·도수·대표 이미지·소개·맛 태그를 각각 IN 배치로
     * 한 번씩 로딩해 brewery_id별로 붙인다(N+1 회피). 빈 목록이면 각 배치 헬퍼가 빈 Map을 반환해 빈 목록을 낸다.
     */
    private List<BreweryListItemResponse> toListItems(List<Brewery> breweries) {
        Map<String, List<FeatureType>> tagsByBrewery = featureTagsFor(breweries);
        Map<String, List<LiquorType>> liquorsByBrewery = liquorTypesFor(breweries);
        Map<String, AbvRange> abvByBrewery = abvFor(breweries);
        Map<String, TourContent> tourContentByBrewery = tourContentByBreweryId(breweries);
        Map<String, MainImageResponse> imageByBrewery = mainImagesFrom(tourContentByBrewery);
        Map<String, String> introByBrewery = introductionsFor(breweries, tourContentByBrewery);
        Map<String, String> characteristicsByBrewery =
                productQueryService.representativeCharacteristicsByBreweryId(breweryIds(breweries));

        return breweries.stream()
                .map(b -> {
                    AbvRange abv = abvByBrewery.get(b.getBreweryId());
                    List<SensoryTag> flavorTags =
                            SensoryTagMatcher.match(characteristicsByBrewery.get(b.getBreweryId()));
                    return BreweryListItemResponse.from(b,
                            tagsByBrewery.getOrDefault(b.getBreweryId(), List.of()),
                            abv == null ? null : abv.min(),
                            abv == null ? null : abv.max(),
                            liquorsByBrewery.getOrDefault(b.getBreweryId(), List.of()),
                            imageByBrewery.get(b.getBreweryId()),
                            BrewerySigunguParser.parse(b.getAddress()),
                            flavorTags,
                            introByBrewery.get(b.getBreweryId()));
                })
                .toList();
    }

    /**
     * 단건 상세 조회. 없는 breweryId면 {@link BreweryNotFoundException}(404).
     * 자식 배치 조회 헬퍼를 단일 원소 리스트로 재사용해 리스트와 동일한 매핑 규칙(정렬·null 규약)을 공유한다.
     */
    public BreweryDetailResponse findDetail(String breweryId) {
        Brewery brewery = breweryRepository.findById(breweryId)
                .orElseThrow(() -> new BreweryNotFoundException(
                        "양조장을 찾을 수 없습니다: " + breweryId));

        List<Brewery> one = List.of(brewery);
        List<FeatureType> tags = featureTagsFor(one).getOrDefault(breweryId, List.of());
        List<LiquorType> liquors = liquorTypesFor(one).getOrDefault(breweryId, List.of());
        AbvRange abv = abvFor(one).get(breweryId);
        MainImageResponse image = mainImagesFor(one).get(breweryId);
        String overview = overviewFor(brewery);
        List<ExperienceResponse> experiences = experiencesFor(breweryId);

        return BreweryDetailResponse.of(brewery, tags,
                abv == null ? null : abv.min(),
                abv == null ? null : abv.max(),
                liquors, image, overview, experiences);
    }

    /**
     * 체험 프로그램(#52) — 상세 전용. program_name 오름차순으로 읽어 응답 배열 순서를 결정론화한다(삽입 순서와
     * 무관하게 안정). 체험이 없으면 빈 배열. 리스트 API엔 노출하지 않는다(스캔용).
     */
    private List<ExperienceResponse> experiencesFor(String breweryId) {
        List<ExperienceResponse> out = new ArrayList<>();
        for (BreweryExperience e : experienceRepository.findByBreweryIdOrderByProgramNameAsc(breweryId)) {
            out.add(ExperienceResponse.from(e));
        }
        return out;
    }

    /**
     * 상세 소개글(#50) — content_id가 있으면 tour_content.overview를 읽어 반환. 미매칭·미백필이면 null.
     * 빈 문자열은 null로 정규화(백필이 비어 있으면 저장하지 않으나 방어). 상세 전용(리스트엔 노출하지 않는다).
     */
    private String overviewFor(Brewery brewery) {
        String contentId = brewery.getContentId();
        if (contentId == null) {
            return null;
        }
        return tourContentRepository.findById(contentId)
                .map(TourContent::getOverview)
                .filter(s -> !s.isBlank())
                .orElse(null);
    }

    /**
     * 이 페이지 양조장들의 특징 태그를 한 번의 IN 조회로 배치 로딩(N+1 회피) → brewery_id별 그룹.
     * 각 리스트는 FeatureType 선언 순서로 정렬해 응답 배열 순서를 결정론화한다(수상이력→…→대통령상).
     */
    private Map<String, List<FeatureType>> featureTagsFor(List<Brewery> breweries) {
        if (breweries.isEmpty()) {
            return Map.of();
        }
        List<String> ids = breweryIds(breweries);
        Map<String, List<FeatureType>> byBrewery = new HashMap<>();
        for (BreweryFeatureTag t : featureTagRepository.findByBreweryIdIn(ids)) {
            byBrewery.computeIfAbsent(t.getBreweryId(), k -> new ArrayList<>()).add(t.getFeatureType());
        }
        byBrewery.values().forEach(list -> list.sort(Comparator.comparingInt(Enum::ordinal)));
        return byBrewery;
    }

    /**
     * 이 페이지 양조장들의 취급 주종(distinct)을 IN 배치로 로딩 → brewery_id별 그룹.
     * 각 리스트는 LiquorType 선언 순서로 정렬(탁주→…→기타). recheck 무관 전체 태깅 대상(필터 의미와 일치).
     */
    private Map<String, List<LiquorType>> liquorTypesFor(List<Brewery> breweries) {
        if (breweries.isEmpty()) {
            return Map.of();
        }
        List<String> ids = breweryIds(breweries);
        Map<String, List<LiquorType>> byBrewery = new HashMap<>();
        for (Object[] row : liquorTypeRepository.findDistinctTypesByBreweryIdIn(ids)) {
            String breweryId = (String) row[0];
            LiquorType type = (LiquorType) row[1];
            byBrewery.computeIfAbsent(breweryId, k -> new ArrayList<>()).add(type);
        }
        byBrewery.values().forEach(list -> list.sort(Comparator.comparingInt(Enum::ordinal)));
        return byBrewery;
    }

    /**
     * 이 페이지 양조장들의 도수 범위(min의 최솟값·max의 최댓값)를 IN 배치 GROUP BY로 로딩 → brewery_id별.
     * 결과에 없는 양조장(도수 미상 링크뿐이거나 링크 없음)은 Map에 없어 호출자가 null로 응답한다.
     */
    private Map<String, AbvRange> abvFor(List<Brewery> breweries) {
        if (breweries.isEmpty()) {
            return Map.of();
        }
        List<String> ids = breweryIds(breweries);
        Map<String, AbvRange> byBrewery = new HashMap<>();
        for (Object[] row : linkRepository.aggregateAbvByBreweryIdIn(ids)) {
            String breweryId = (String) row[0];
            BigDecimal min = (BigDecimal) row[1];
            BigDecimal max = (BigDecimal) row[2];
            byBrewery.put(breweryId, new AbvRange(min, max));
        }
        return byBrewery;
    }

    /**
     * 이 페이지 양조장들의 대표 이미지를 로딩 → brewery_id별. content_id가 있는 양조장의 content_id만 모아
     * tour_content를 IN 배치로 읽는다(INNER JOIN이면 미매칭 양조장이 응답에서 사라지므로 배치+메모리 매핑).
     * content_id가 null이거나 first_image가 공백이면 그 양조장은 Map에 넣지 않아 호출자가 null로 응답한다.
     */
    private Map<String, MainImageResponse> mainImagesFor(List<Brewery> breweries) {
        if (breweries.isEmpty()) {
            return Map.of();
        }
        List<String> contentIds = breweries.stream()
                .map(Brewery::getContentId)
                .filter(Objects::nonNull)
                .toList();
        if (contentIds.isEmpty()) {
            return Map.of();
        }
        Map<String, TourContent> byContentId = new HashMap<>();
        for (TourContent tc : tourContentRepository.findByContentIdIn(contentIds)) {
            byContentId.put(tc.getContentId(), tc);
        }
        Map<String, MainImageResponse> byBrewery = new HashMap<>();
        for (Brewery b : breweries) {
            String contentId = b.getContentId();
            if (contentId == null) {
                continue;
            }
            TourContent tc = byContentId.get(contentId);
            if (tc == null) {
                continue;
            }
            MainImageResponse image = MainImageResponse.from(tc.getFirstImage(), tc.getCpyrhtDivCd());
            if (image != null) {
                byBrewery.put(b.getBreweryId(), image);
            }
        }
        return byBrewery;
    }

    /**
     * 리스트 전용 배치 — 이 페이지 양조장들의 tour_content를 breweryId별로 한 번에 로딩(N+1 회피).
     * mainImage(파생)·introduction(overview 소스)이 이 결과를 공유해 tour_content 쿼리를 1회로 묶는다.
     * ★{@link #mainImagesFor}(상세 전용, 단건 List)는 건드리지 않는다 — 기존 상세 경로 회귀 방지.
     */
    private Map<String, TourContent> tourContentByBreweryId(List<Brewery> breweries) {
        if (breweries.isEmpty()) {
            return Map.of();
        }
        List<String> contentIds = breweries.stream()
                .map(Brewery::getContentId)
                .filter(Objects::nonNull)
                .toList();
        if (contentIds.isEmpty()) {
            return Map.of();
        }
        Map<String, TourContent> byContentId = new HashMap<>();
        for (TourContent tc : tourContentRepository.findByContentIdIn(contentIds)) {
            byContentId.put(tc.getContentId(), tc);
        }
        Map<String, TourContent> byBrewery = new HashMap<>();
        for (Brewery b : breweries) {
            String contentId = b.getContentId();
            if (contentId == null) {
                continue;
            }
            TourContent tc = byContentId.get(contentId);
            if (tc != null) {
                byBrewery.put(b.getBreweryId(), tc);
            }
        }
        return byBrewery;
    }

    /** {@link #tourContentByBreweryId} 결과에서 대표 이미지만 파생(추가 쿼리 없음). */
    private Map<String, MainImageResponse> mainImagesFrom(Map<String, TourContent> tourContentByBrewery) {
        Map<String, MainImageResponse> byBrewery = new HashMap<>();
        tourContentByBrewery.forEach((breweryId, tc) -> {
            MainImageResponse image = MainImageResponse.from(tc.getFirstImage(), tc.getCpyrhtDivCd());
            if (image != null) {
                byBrewery.put(breweryId, image);
            }
        });
        return byBrewery;
    }

    /**
     * 목록 additive(소개, 신규) — {@link BreweryIntroductionResolver} 우선순위(overview → designationNote)를
     * 배치로 적용(추가 쿼리 없음, tour_content는 {@link #tourContentByBreweryId}가 이미 배치 로딩).
     */
    private Map<String, String> introductionsFor(List<Brewery> breweries,
                                                  Map<String, TourContent> tourContentByBrewery) {
        Map<String, String> byBrewery = new HashMap<>();
        for (Brewery b : breweries) {
            TourContent tc = tourContentByBrewery.get(b.getBreweryId());
            String overview = tc == null ? null : tc.getOverview();
            String intro = BreweryIntroductionResolver.resolve(overview, b.getDesignationNote());
            if (intro != null) {
                byBrewery.put(b.getBreweryId(), intro);
            }
        }
        return byBrewery;
    }

    private static List<String> breweryIds(List<Brewery> breweries) {
        return breweries.stream().map(Brewery::getBreweryId).toList();
    }

    /** 음수 페이지는 0으로 클램프(PageRequest 계약 위반 방지). */
    private static int clampPage(int page) {
        return Math.max(0, page);
    }

    /** size 상한 클램프: 1 미만은 기본값, MAX_SIZE 초과는 MAX_SIZE로. */
    private static int clampSize(int size) {
        if (size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    /** 양조장 도수 범위(집계 결과 운반용 내부 타입). min/max 중 하나만 null일 수는 없다(SQL이 함께 산출). */
    private record AbvRange(BigDecimal min, BigDecimal max) {
    }

    /** 통합 검색의 순위 부여 결과(정렬·슬라이스 전 운반용 내부 타입). tier=1/2/3. */
    private record RankedBrewery(int tier, Brewery brewery) {
    }
}
