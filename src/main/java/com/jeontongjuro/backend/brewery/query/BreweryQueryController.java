package com.jeontongjuro.backend.brewery.query;

import com.jeontongjuro.backend.global.web.PageResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 양조장 리스트 조회 API. GET /api/breweries.
 * <p>
 * 이번 PR 계약 파라미터(전부 선택): region · reservationVisit · alwaysVisit · keyword · page · size.
 * 주종·도수 등은 후속 PR. 원문 파라미터는 {@link BrewerySearchCondition#of}에서 검증·정규화되고,
 * 허용 집합 밖 값은 400({@code INVALID_QUERY_PARAMETER})으로 매핑된다.
 */
@RestController
@RequestMapping("/api/breweries")
public class BreweryQueryController {

    private final BreweryQueryService breweryQueryService;

    public BreweryQueryController(BreweryQueryService breweryQueryService) {
        this.breweryQueryService = breweryQueryService;
    }

    @GetMapping
    public PageResponse<BreweryListItemResponse> list(
            @RequestParam(required = false) String region,
            @RequestParam(required = false) String reservationVisit,
            @RequestParam(required = false) String alwaysVisit,
            @RequestParam(required = false) String keyword,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        BrewerySearchCondition condition =
                BrewerySearchCondition.of(region, reservationVisit, alwaysVisit, keyword);
        return breweryQueryService.search(condition, page, size);
    }
}
