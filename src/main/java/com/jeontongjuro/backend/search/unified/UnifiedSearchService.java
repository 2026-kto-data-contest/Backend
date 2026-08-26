package com.jeontongjuro.backend.search.unified;

import com.jeontongjuro.backend.brewery.query.BreweryListItemResponse;
import com.jeontongjuro.backend.brewery.query.BreweryQueryService;
import com.jeontongjuro.backend.global.web.PageResponse;
import com.jeontongjuro.backend.search.SearchKeyword;
import org.springframework.stereotype.Service;

/**
 * 통합 검색(GET /api/v1/search) 진입 서비스. 검색어 정규화·검증({@link SearchKeyword})만 책임지고, 정확도순
 * 정렬·중복 제거·페이징·카드 매핑은 {@link BreweryQueryService#searchByAccuracy}에 위임한다(양조장 카드와
 * 같은 DTO·배치 매핑을 공유하기 위함).
 * <p>
 * 검색어는 자동완성과 동일한 규칙으로 정규화한다(트림 후 20자 초과 400, 허용문자 밖 제거·NFC·lower). 트림 후
 * 빈 값이거나 허용문자 제거 후 빈 값이면 needle이 ""가 되어 결과 0건({@code totalElements:0})을 반환한다
 * (명세: 빈 검색은 에러가 아니라 동작 없음).
 */
@Service
public class UnifiedSearchService {

    private final BreweryQueryService breweryQueryService;

    public UnifiedSearchService(BreweryQueryService breweryQueryService) {
        this.breweryQueryService = breweryQueryService;
    }

    public PageResponse<BreweryListItemResponse> search(String keyword, int page, int size) {
        String needle = SearchKeyword.normalizeForMatch(keyword);
        return breweryQueryService.searchByAccuracy(needle, page, size);
    }
}
