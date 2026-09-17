package com.jeontongjuro.backend.search.recommended;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** 검색 화면 추천 검색어 API. */
@RestController
@RequestMapping("/api/v1/search/recommended-keywords")
@RequiredArgsConstructor
@Tag(name = "검색", description = "통합 검색·자동완성 API")
public class RecommendedSearchKeywordController {

    private final RecommendedSearchKeywordService service;

    @GetMapping
    @Operation(summary = "추천 검색어 조회", description = "검색 화면에 노출할 서버 관리 추천 검색어를 반환합니다. 인증 없이 호출할 수 있습니다.")
    @ApiResponse(responseCode = "200", description = "추천 검색어 조회 성공")
    public List<RecommendedSearchKeywordResponse> list() {
        return service.list();
    }
}
