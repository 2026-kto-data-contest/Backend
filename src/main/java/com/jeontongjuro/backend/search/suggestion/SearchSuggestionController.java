package com.jeontongjuro.backend.search.suggestion;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import com.jeontongjuro.backend.global.error.ErrorResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 검색 자동완성 API. GET /api/v1/search/suggestions.
 * <p>
 * 입력 필드에 1자 이상 입력했을 때 호출한다. 인증이 필요 없는 공개 API다(비로그인 사용자도 자동완성을 쓴다).
 */
@RestController
@RequestMapping("/api/v1/search/suggestions")
@Tag(name = "검색", description = "검색 자동완성 API")
public class SearchSuggestionController {

    private final SearchSuggestionService searchSuggestionService;

    public SearchSuggestionController(SearchSuggestionService searchSuggestionService) {
        this.searchSuggestionService = searchSuggestionService;
    }

    @GetMapping
    @Operation(summary = "검색 자동완성", description = """
            양조장명·전통주명(판매중단·원본오류 제외, 중복 병합 적용 후 표시집합 기준) 부분일치 결과를
            전방일치 우선 → 그 외 부분일치 순으로 정렬하고, 동순위는 가나다순으로 정렬해 최대 10건 반환합니다.
            매칭 결과가 없으면 빈 배열([])을 반환합니다(별도 안내 문구 없음).

            검색어는 앞뒤 공백을 트림한 뒤 최대 20자까지 허용합니다(트림 후 20자 초과는 400).
            트림 후 빈 값이면 빈 배열을 반환합니다(검색 실행 차단, 에러 아님).
            한글·영문·숫자·공백 외 문자(이모지·특수문자)는 입력은 되지만 검색 실행 시 무시됩니다.

            항목의 keyword·displayName은 이 항목의 표시 텍스트이며, 항목을 탭했을 때 그대로
            POST /api/v1/search/recent 요청 바디(type·id·keyword·displayName)로 전달할 수 있습니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "조회 성공(매칭 없으면 빈 배열)"),
            @ApiResponse(responseCode = "400",
                    description = "검색어가 트림 후 20자를 초과함(code=INVALID_QUERY_PARAMETER)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"INVALID_QUERY_PARAMETER\","
                                    + "\"message\":\"검색어는 최대 20자까지 입력할 수 있습니다.\"}")))
    })
    public List<SearchSuggestionResponse> suggest(
            @Parameter(description = "검색어(1자 이상, 트림 후 최대 20자). 비어 있으면 빈 배열을 반환합니다.",
                    example = "산")
            @RequestParam(required = false) String keyword) {
        return searchSuggestionService.suggest(keyword);
    }
}
