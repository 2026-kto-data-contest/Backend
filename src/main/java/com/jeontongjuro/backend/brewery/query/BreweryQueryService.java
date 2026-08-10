package com.jeontongjuro.backend.brewery.query;

import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.feature.BreweryFeatureTag;
import com.jeontongjuro.backend.feature.BreweryFeatureTagRepository;
import com.jeontongjuro.backend.feature.FeatureType;
import com.jeontongjuro.backend.global.web.PageResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 양조장 리스트 조회 서비스. 검증된 조건 → Specification 조합 → 페이징 조회 → 응답 DTO 매핑.
 * <p>
 * 프로젝션 방식 = <b>Specification 필터 + 서비스 계층 명시 매핑</b>(JPQL 생성자 표현식 아님).
 * 이유: 후속 주종·도수 필터를 Specification 하나 추가로 얹는 확장성이 이 PR의 핵심인데,
 * 생성자 표현식 프로젝션은 Specification과 조합이 까다롭다. brewery는 단일 테이블·연관 없음이라
 * 엔티티를 읽어 DTO로 옮겨도 조인·N+1이 없고, {@code open-in-view=false}에서도 매핑이
 * 트랜잭션 안({@code @Transactional(readOnly)})에서 일어나 지연로딩 위험이 없다.
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

    public BreweryQueryService(BreweryRepository breweryRepository,
                               BreweryFeatureTagRepository featureTagRepository) {
        this.breweryRepository = breweryRepository;
        this.featureTagRepository = featureTagRepository;
    }

    public PageResponse<BreweryListItemResponse> search(BrewerySearchCondition condition, int page, int size) {
        Pageable pageable = PageRequest.of(clampPage(page), clampSize(size), FIXED_SORT);
        Specification<Brewery> spec = BreweryQuerySpecifications.build(condition);

        Page<Brewery> result = breweryRepository.findAll(spec, pageable);
        Map<String, List<FeatureType>> tagsByBrewery = featureTagsFor(result.getContent());
        List<BreweryListItemResponse> content = result.getContent().stream()
                .map(b -> BreweryListItemResponse.from(b,
                        tagsByBrewery.getOrDefault(b.getBreweryId(), List.of())))
                .toList();
        return PageResponse.of(content, result);
    }

    /**
     * 이 페이지 양조장들의 특징 태그를 한 번의 IN 조회로 배치 로딩(N+1 회피) → brewery_id별 그룹.
     * 각 리스트는 FeatureType 선언 순서로 정렬해 응답 배열 순서를 결정론화한다(수상이력→…→대통령상).
     */
    private Map<String, List<FeatureType>> featureTagsFor(List<Brewery> breweries) {
        if (breweries.isEmpty()) {
            return Map.of();
        }
        List<String> ids = breweries.stream().map(Brewery::getBreweryId).toList();
        Map<String, List<FeatureType>> byBrewery = new HashMap<>();
        for (BreweryFeatureTag t : featureTagRepository.findByBreweryIdIn(ids)) {
            byBrewery.computeIfAbsent(t.getBreweryId(), k -> new ArrayList<>()).add(t.getFeatureType());
        }
        byBrewery.values().forEach(list -> list.sort(Comparator.comparingInt(Enum::ordinal)));
        return byBrewery;
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
}
