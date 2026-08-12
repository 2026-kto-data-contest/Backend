package com.jeontongjuro.backend.brewery.query;

import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.feature.BreweryFeatureTag;
import com.jeontongjuro.backend.feature.BreweryFeatureTagRepository;
import com.jeontongjuro.backend.feature.FeatureType;
import com.jeontongjuro.backend.global.web.PageResponse;
import com.jeontongjuro.backend.liquortype.LiquorType;
import com.jeontongjuro.backend.liquortype.ProductLiquorTypeRepository;
import com.jeontongjuro.backend.product.ProductBreweryLinkRepository;
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

    public BreweryQueryService(BreweryRepository breweryRepository,
                               BreweryFeatureTagRepository featureTagRepository,
                               ProductLiquorTypeRepository liquorTypeRepository,
                               ProductBreweryLinkRepository linkRepository,
                               TourContentRepository tourContentRepository) {
        this.breweryRepository = breweryRepository;
        this.featureTagRepository = featureTagRepository;
        this.liquorTypeRepository = liquorTypeRepository;
        this.linkRepository = linkRepository;
        this.tourContentRepository = tourContentRepository;
    }

    public PageResponse<BreweryListItemResponse> search(BrewerySearchCondition condition, int page, int size) {
        Pageable pageable = PageRequest.of(clampPage(page), clampSize(size), FIXED_SORT);
        Specification<Brewery> spec = BreweryQuerySpecifications.build(condition);

        Page<Brewery> result = breweryRepository.findAll(spec, pageable);
        List<Brewery> breweries = result.getContent();

        Map<String, List<FeatureType>> tagsByBrewery = featureTagsFor(breweries);
        Map<String, List<LiquorType>> liquorsByBrewery = liquorTypesFor(breweries);
        Map<String, AbvRange> abvByBrewery = abvFor(breweries);
        Map<String, MainImageResponse> imageByBrewery = mainImagesFor(breweries);

        List<BreweryListItemResponse> content = breweries.stream()
                .map(b -> {
                    AbvRange abv = abvByBrewery.get(b.getBreweryId());
                    return BreweryListItemResponse.from(b,
                            tagsByBrewery.getOrDefault(b.getBreweryId(), List.of()),
                            abv == null ? null : abv.min(),
                            abv == null ? null : abv.max(),
                            liquorsByBrewery.getOrDefault(b.getBreweryId(), List.of()),
                            imageByBrewery.get(b.getBreweryId()));
                })
                .toList();
        return PageResponse.of(content, result);
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

        return BreweryDetailResponse.of(brewery, tags,
                abv == null ? null : abv.min(),
                abv == null ? null : abv.max(),
                liquors, image, overview);
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
}
