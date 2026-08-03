package com.jeontongjuro.backend.brewery.query;

import com.jeontongjuro.backend.global.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 양조장 리스트 조회 API. GET /api/v1/breweries.
 * <p>
 * 이번 PR 계약 파라미터(전부 선택): region · reservationVisit · alwaysVisit · keyword · page · size.
 * 주종·도수 등은 후속 PR. 원문 파라미터는 {@link BrewerySearchCondition#of}에서 검증·정규화되고,
 * 허용 집합 밖 값은 400({@code INVALID_QUERY_PARAMETER})으로 매핑된다.
 */
@RestController
@RequestMapping("/api/v1/breweries")
@Tag(name = "양조장", description = "양조장 조회 API")
@SecurityRequirement(name = "sessionCookie")
public class BreweryQueryController {

    private final BreweryQueryService breweryQueryService;

    public BreweryQueryController(BreweryQueryService breweryQueryService) {
        this.breweryQueryService = breweryQueryService;
    }

    @GetMapping
    @Operation(summary = "양조장 목록 조회", description = """
            양조장 목록 화면에서 사용하는 조회 API입니다.
            원하는 필터만 보내면 되고, 아무 필터도 보내지 않으면 전체 양조장을 조회합니다.

            결과는 양조장 이름 가나다순으로 정렬됩니다.
            page는 0부터 시작하며, 다음 페이지는 응답의 page에 1을 더해 요청하세요.
            totalPages가 0이거나 현재 page에 1을 더한 값이 totalPages와 같으면 마지막 페이지입니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "양조장 목록 조회 성공"),
            @ApiResponse(responseCode = "400", description = "잘못된 필터 또는 페이징 값"),
            @ApiResponse(responseCode = "401", description = "로그인 필요")
    })
    public PageResponse<BreweryListItemResponse> list(
            @Parameter(description = "지역 필터. 보내지 않으면 전체 지역", example = "전라")
            @RequestParam(required = false) String region,
            @Parameter(description = "예약 방문 가능 여부: Y(가능), N(불가), UNKNOWN(정보 없음)", example = "Y")
            @RequestParam(required = false) String reservationVisit,
            @Parameter(description = "상시 방문 가능 여부: Y(가능), N(불가), UNKNOWN(정보 없음)", example = "Y")
            @RequestParam(required = false) String alwaysVisit,
            @Parameter(description = "양조장 이름 검색어. 보내지 않으면 이름 검색 안 함", example = "해남")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "페이지 번호. 0부터 시작", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "한 페이지에 받을 개수. 최대 100", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        BrewerySearchCondition condition =
                BrewerySearchCondition.of(region, reservationVisit, alwaysVisit, keyword);
        return breweryQueryService.search(condition, page, size);
    }
}
