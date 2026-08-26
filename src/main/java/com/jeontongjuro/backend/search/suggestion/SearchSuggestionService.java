package com.jeontongjuro.backend.search.suggestion;

import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.product.query.ProductNameSuggestion;
import com.jeontongjuro.backend.product.query.ProductQueryService;
import com.jeontongjuro.backend.search.SearchKeyword;
import com.jeontongjuro.backend.search.recent.RecentSearchType;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 검색 자동완성(GET /api/v1/search/suggestions) 조회 서비스. 양조장명 + 전통주명(표시집합, 병합 적용 후)
 * 부분일치를 전방일치 우선 → 그 외 부분일치, 동순위는 가나다순으로 정렬해 최대 {@link #MAX_RESULTS}건만 반환한다.
 * <p>
 * 매칭 대상(상호명·제품명) 정규화는 통합검색과 동일하게 {@link SearchKeyword#normalizeTarget}을 그대로 쓴다
 * (허용문자 밖 제거 → 공백 트림 → NFC → lower). 자동완성이 내려준 이름을 그대로 재검색해도 자기 자신이
 * 잡히도록, 입력 정규화({@link SearchKeyword#normalizeForMatch})와 반드시 같은 규칙이어야 한다 — 예전에는
 * 이 서비스가 이스케이프까지 갖춘 SQL LIKE로 별도 재현해 특수문자를 제거하지 않았고, 그 결과 "이화주(술샘)"처럼
 * 괄호가 든 이름은 자기 자신 재검색조차 실패했다. 모집단이 작아(양조장 ≤100·표시 제품 ≤1300) 전량을 읽어
 * 인메모리로 정규화·매칭한다.
 * <p>
 * 쿼리 수는 항상 고정 3건이다: 양조장 전체 조회 1 + 제품 링크 전체 조회 1 + 제품 raw 배치 조회 1
 * ({@link ProductQueryService#allDisplayedProductNames}). 결과 건수·매칭 건수와 무관하게 늘지 않는다(N+1 없음).
 */
@Service
@Transactional(readOnly = true)
public class SearchSuggestionService {

    /** 노출 최대 건수(기획 고정값 — size 파라미터 없음). */
    private static final int MAX_RESULTS = 10;

    private final BreweryRepository breweryRepository;
    private final ProductQueryService productQueryService;

    public SearchSuggestionService(BreweryRepository breweryRepository, ProductQueryService productQueryService) {
        this.breweryRepository = breweryRepository;
        this.productQueryService = productQueryService;
    }

    public List<SearchSuggestionResponse> suggest(String keyword) {
        // 입력 정규화(트림·길이검증·허용문자 제거·NFC·lower)는 통합검색과 공유한다({@link SearchKeyword}).
        // 트림 후 빈 값이거나 허용문자 제거 후 빈 값이면 needle이 ""가 되어 검색을 실행하지 않는다(빈 배열).
        String needle = SearchKeyword.normalizeForMatch(keyword);
        if (needle.isEmpty()) {
            return List.of();
        }

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

    /** 상호명 부분일치. 매칭은 {@link SearchKeyword#normalizeTarget} 기준, 정렬·응답은 원문 기준(sortKey()). */
    private void collectBreweryCandidates(String needle, List<Candidate> candidates) {
        for (Brewery brewery : breweryRepository.findAll()) {
            String displayName = brewery.getBusinessName();
            String normalizedName = SearchKeyword.normalizeTarget(displayName);
            if (!normalizedName.contains(needle)) {
                continue;
            }
            candidates.add(new Candidate(
                    RecentSearchType.BREWERY,
                    brewery.getBreweryId(),
                    displayName,
                    normalizedName.startsWith(needle)));
        }
    }

    /** 전통주명 부분일치(표시집합 — 판매중단·원본오류 제외, 병합 적용 후). */
    private void collectProductCandidates(String needle, List<Candidate> candidates) {
        for (ProductNameSuggestion product : productQueryService.allDisplayedProductNames()) {
            String displayName = product.productName();
            String normalizedName = SearchKeyword.normalizeTarget(displayName);
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
