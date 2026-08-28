package com.jeontongjuro.backend.search.unified;

import com.jeontongjuro.backend.brewery.query.BreweryListItemResponse;
import com.jeontongjuro.backend.global.error.ErrorResponse;
import com.jeontongjuro.backend.global.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 통합 검색 API. GET /api/v1/search.
 * <p>
 * 검색어로 양조장을 정확도순(양조장명 전방일치 &gt; 부분일치 &gt; 제품명 매칭)으로 정렬해 양조장 카드 목록으로
 * 반환한다. 결과 아이템은 양조장 목록 API(GET /api/v1/breweries)와 동일한 {@link BreweryListItemResponse}라
 * 프론트가 카드 컴포넌트를 공유한다. 인증이 필요 없는 공개 API다(비로그인도 검색을 쓴다).
 */
@RestController
@RequestMapping("/api/v1/search")
@Tag(name = "검색", description = "통합 검색·자동완성 API")
public class UnifiedSearchController {

    private final UnifiedSearchService unifiedSearchService;

    public UnifiedSearchController(UnifiedSearchService unifiedSearchService) {
        this.unifiedSearchService = unifiedSearchService;
    }

    @GetMapping
    @Operation(summary = "통합 검색", description = """
            검색어로 양조장을 정확도순으로 정렬해 조회합니다. 정렬 우선순위는
            1) 양조장명 전방일치 → 2) 양조장명 부분일치 → 3) 취급 제품명 부분일치이며,
            같은 순위 안에서는 양조장 이름 가나다순입니다. 한 양조장이 이름·제품 양쪽에 걸려도 한 번만 나옵니다
            (양조장 단위 중복 제거). totalElements가 화면의 검색 결과 개수({N})입니다.

            결과 카드는 양조장 목록 API와 동일한 형태입니다(썸네일·양조장명·지역·소개·태그 등).
            page는 0부터 시작하며, 20개 단위 무한 스크롤에 맞춰 기본 size는 20입니다.

            검색어는 앞뒤 공백을 트림한 뒤 최대 20자까지 허용합니다(트림 후 20자 초과는 400).
            한글·영문·숫자·공백 외 문자(이모지·특수문자)는 입력은 되지만 검색 실행 시 무시됩니다 — 다만
            매칭 대상 제품/양조장명에도 같은 정규화를 적용하므로, 자동완성이 내려준 이름(예: 이화주(술샘))을
            그대로 재검색해도 결과가 나옵니다. 트림 후 빈 값이면 결과 0건을 반환합니다(에러 아님).
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "검색 성공(매칭 없으면 content 빈 배열·totalElements 0)"),
            @ApiResponse(responseCode = "400",
                    description = "검색어가 트림 후 20자를 초과함(code=INVALID_QUERY_PARAMETER)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"INVALID_QUERY_PARAMETER\","
                                    + "\"message\":\"검색어는 최대 20자까지 입력할 수 있습니다.\"}"))),
            @ApiResponse(responseCode = "500",
                    description = "서버 내부 오류(code=INTERNAL_SERVER_ERROR).",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"INTERNAL_SERVER_ERROR\","
                                    + "\"message\":\"서버 내부 오류가 발생했습니다.\"}")))
    })
    public PageResponse<BreweryListItemResponse> search(
            @Parameter(description = "검색어(트림 후 최대 20자). 비어 있으면 결과 0건", example = "안동")
            @RequestParam(required = false) String keyword,
            @Parameter(description = "페이지 번호. 0부터 시작. 음수를 보내면 400이 아니라 0으로 보정됨", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "한 페이지에 받을 개수. 기본 20, 최대 100. "
                    + "1 미만은 기본값(20), 100 초과는 100으로 보정됨", example = "20")
            @RequestParam(defaultValue = "20") int size) {
        return unifiedSearchService.search(keyword, page, size);
    }
}
