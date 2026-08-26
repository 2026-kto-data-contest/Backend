package com.jeontongjuro.backend.search.suggestion;

import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.global.error.InvalidQueryParameterException;
import com.jeontongjuro.backend.product.query.ProductNameSuggestion;
import com.jeontongjuro.backend.product.query.ProductQueryService;
import com.jeontongjuro.backend.search.recent.RecentSearchType;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Pattern;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 검색 자동완성(GET /api/v1/search/suggestions) 조회 서비스. 양조장명 + 전통주명(표시집합, 병합 적용 후)
 * 부분일치를 전방일치 우선 → 그 외 부분일치, 동순위는 가나다순으로 정렬해 최대 {@link #MAX_RESULTS}건만 반환한다.
 * <p>
 * 정규화·이스케이프 규칙은 {@code BreweryQuerySpecifications.keywordContains}/{@code BrewerySearchCondition}이
 * 쓰는 규칙(NFC 정규화 + lower + LIKE 이스케이프)과 동일한 알고리즘을 재사용한다(그 클래스들의 헬퍼는
 * package-private이라 직접 호출할 수 없어, 동일 로직을 이 서비스 안에 그대로 재현했다).
 * <p>
 * 쿼리 수는 항상 고정 3건이다: 양조장 LIKE 조회 1 + 제품 링크 전체 조회 1 + 제품 raw 배치 조회 1
 * ({@link ProductQueryService#allDisplayedProductNames}). 결과 건수·매칭 건수와 무관하게 늘지 않는다(N+1 없음).
 */
@Service
@Transactional(readOnly = true)
public class SearchSuggestionService {

    /** 노출 최대 건수(기획 고정값 — size 파라미터 없음). */
    private static final int MAX_RESULTS = 10;
    /** 검색어 최대 길이(앞뒤 공백 트림 후 기준). 초과 시 400. */
    private static final int MAX_KEYWORD_LENGTH = 20;
    /** 허용 문자(한글·영문·숫자·공백) 밖은 검색 실행 시 제거한다(이모지·특수문자는 입력은 되지만 무시). */
    private static final Pattern DISALLOWED_CHARS = Pattern.compile("[^\\p{IsHangul}a-zA-Z0-9\\s]");

    private final BreweryRepository breweryRepository;
    private final ProductQueryService productQueryService;

    public SearchSuggestionService(BreweryRepository breweryRepository, ProductQueryService productQueryService) {
        this.breweryRepository = breweryRepository;
        this.productQueryService = productQueryService;
    }

    public List<SearchSuggestionResponse> suggest(String keyword) {
        String stripped = keyword == null ? "" : keyword.strip();
        if (stripped.length() > MAX_KEYWORD_LENGTH) {
            throw new InvalidQueryParameterException(
                    "검색어는 최대 " + MAX_KEYWORD_LENGTH + "자까지 입력할 수 있습니다.");
        }
        if (stripped.isEmpty()) {
            return List.of();
        }

        // 허용 문자 밖(이모지·특수문자)은 검색 실행 시 무시 — 필터링 후에도 빈 값이면 검색을 실행하지 않는다.
        String filtered = DISALLOWED_CHARS.matcher(stripped).replaceAll("").strip();
        if (filtered.isEmpty()) {
            return List.of();
        }
        String needle = Normalizer.normalize(filtered, Normalizer.Form.NFC).toLowerCase();

        List<Candidate> candidates = new ArrayList<>();
        collectBreweryCandidates(needle, candidates);
        collectProductCandidates(needle, candidates);

        candidates.sort(Comparator
                .comparing((Candidate c) -> !c.frontMatch())
                .thenComparing(Candidate::sortKey));

        return candidates.stream()
                .limit(MAX_RESULTS)
                .map(Candidate::toResponse)
                .toList();
    }

    /** 상호명 부분일치(대소문자 무시). {@code BreweryQuerySpecifications.keywordContains}와 동일 알고리즘. */
    private void collectBreweryCandidates(String needle, List<Candidate> candidates) {
        String pattern = "%" + escapeLike(needle) + "%";
        Specification<Brewery> spec = (root, query, cb) ->
                cb.like(cb.lower(root.get("businessName")), pattern, '\\');
        for (Brewery brewery : breweryRepository.findAll(spec)) {
            String displayName = brewery.getBusinessName();
            candidates.add(new Candidate(
                    RecentSearchType.BREWERY,
                    brewery.getBreweryId(),
                    displayName,
                    isFrontMatch(displayName, needle)));
        }
    }

    /** 전통주명 부분일치(표시집합 — 판매중단·원본오류 제외, 병합 적용 후). */
    private void collectProductCandidates(String needle, List<Candidate> candidates) {
        for (ProductNameSuggestion product : productQueryService.allDisplayedProductNames()) {
            String displayName = product.productName();
            String normalizedName = Normalizer.normalize(displayName, Normalizer.Form.NFC).toLowerCase();
            if (!normalizedName.contains(needle)) {
                continue;
            }
            candidates.add(new Candidate(
                    RecentSearchType.PRODUCT,
                    "PRD-%04d".formatted(product.productId()),
                    displayName,
                    normalizedName.startsWith(needle)));
        }
    }

    private static boolean isFrontMatch(String displayName, String needle) {
        return Normalizer.normalize(displayName, Normalizer.Form.NFC).toLowerCase().startsWith(needle);
    }

    private static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }

    /** 정렬·응답 변환 전 내부 운반용(타입 무관 공통 표현). */
    private record Candidate(RecentSearchType type, String id, String displayName, boolean frontMatch) {

        /** 동순위 가나다 비교 키 — NFC 정규화만 하고 대소문자는 건드리지 않는다(기본 순서 유지, #3). */
        String sortKey() {
            return Normalizer.normalize(displayName, Normalizer.Form.NFC);
        }

        SearchSuggestionResponse toResponse() {
            return new SearchSuggestionResponse(type, id, displayName, displayName);
        }
    }
}
