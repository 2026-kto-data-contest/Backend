package com.jeontongjuro.backend.brewery.query;

import com.jeontongjuro.backend.global.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 양조장 리스트 조회 API. GET /api/v1/breweries.
 * <p>
 * 계약 파라미터(전부 선택): region · reservationVisit · alwaysVisit · keyword · liquorType · minAbv · maxAbv · page · size.
 * 원문 파라미터는 {@link BrewerySearchCondition#of}에서 검증·정규화되고,
 * 허용 집합 밖 값(음수 도수·minAbv&gt;maxAbv 등 포함)은 400({@code INVALID_QUERY_PARAMETER})으로 매핑된다.
 */
@RestController
@RequestMapping("/api/v1/breweries")
@Tag(name = "양조장", description = "양조장 조회 API")
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
            @ApiResponse(responseCode = "400", description = "잘못된 필터 또는 페이징 값")
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
            @Parameter(
                    description = "주종 필터. 여러 번 보내면(liquorType=탁주&liquorType=증류주) 그중 하나라도 "
                            + "빚는 양조장이 모두 나옵니다. 허용값: 탁주, 약주, 청주, 증류주, 과실주. 보내지 않으면 주종 제한 없음",
                    example = "[\"탁주\"]")
            @RequestParam(required = false) List<String> liquorType,
            @Parameter(description = "최소 도수(%). 이 도수 이상을 파는 곳. maxAbv와 함께 주면 그 범위와 겹치는 제품이 있는 곳. "
                    + "0~100, 단독 사용 가능. 보내지 않으면 하한 제한 없음", example = "15")
            @RequestParam(required = false) BigDecimal minAbv,
            @Parameter(description = "최대 도수(%). 이 도수 이하를 파는 곳. 0~100, minAbv보다 작으면 400. "
                    + "보내지 않으면 상한 제한 없음", example = "29")
            @RequestParam(required = false) BigDecimal maxAbv,
            @Parameter(description = "페이지 번호. 0부터 시작", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "한 페이지에 받을 개수. 최대 100", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        BrewerySearchCondition condition = BrewerySearchCondition.of(
                region, reservationVisit, alwaysVisit, keyword, liquorType, minAbv, maxAbv);
        return breweryQueryService.search(condition, page, size);
    }

    @GetMapping("/{breweryId}")
    @Operation(summary = "양조장 상세 조회", description = """
            양조장 상세 화면에서 사용하는 단건 조회 API입니다.
            경로의 breweryId(BRW-xxx)로 한 곳을 조회하며, 리스트에 없는 주소·좌표·홈페이지까지 함께 내려줍니다.

            도수(alcoholMin/alcoholMax)는 그 양조장 제품의 최소·최대 도수이고, 도수 정보가 있는 제품이
            하나도 없으면 둘 다 null입니다. 취급 주종(liquorTypes)과 특징 태그(featureTags)는 없으면 빈 배열,
            대표 이미지(mainImage)는 없으면 null입니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "양조장 상세 조회 성공"),
            @ApiResponse(responseCode = "404", description = "해당 breweryId의 양조장이 없음(BREWERY_NOT_FOUND)")
    })
    public BreweryDetailResponse detail(
            @Parameter(description = "양조장 고유 ID(BRW-xxx)", example = "BRW-001")
            @PathVariable String breweryId) {
        return breweryQueryService.findDetail(breweryId);
    }
}
