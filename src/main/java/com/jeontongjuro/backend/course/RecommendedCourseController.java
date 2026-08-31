package com.jeontongjuro.backend.course;

import com.jeontongjuro.backend.global.error.ErrorResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/breweries")
@Tag(name = "추천 코스", description = "양조장 중심 추천 코스 API")
public class RecommendedCourseController {

    private final RecommendedCourseService recommendedCourseService;

    public RecommendedCourseController(RecommendedCourseService recommendedCourseService) {
        this.recommendedCourseService = recommendedCourseService;
    }

    @GetMapping("/{breweryId}/recommended-course")
    @Operation(summary = "양조장 중심 추천 코스 조회", description = """
            선택한 양조장을 첫 장소로 고정하고 brewery_nearby·tour_content 캐시에서 음식점·관광지·카페·숙소를
            카테고리별 최대 2곳씩 구성합니다. 후보 반경은 5km에서 시작해 부족하면 10km, 20km를 거쳐
            필요한 후보가 생길 때까지 상한 없이 확장합니다. 데이터가 부족한 카테고리는 있는 장소만 반환하므로
            전체 코스가 9곳보다 적을 수 있습니다.
            음식점은 술 설명의 안주 키워드와 음식 분류가 맞는 후보를 먼저 노출하고 부족분은 거리순으로 채웁니다.
            관광공사 구·신 분류는 음식점·관광지·카페·숙소의 사용자용 한글 세부 분류로 정규화하며,
            원본 분류 코드는 응답에 노출하지 않습니다. 스칼라 결측값은 null, 배열 결측값은 빈 배열로 반환합니다.
            로그인 없이 조회할 수 있으며 실제 도로 경로 최적화는 하지 않습니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "양조장 중심 추천 코스 조회 성공"),
            @ApiResponse(responseCode = "404", description = "존재하지 않는 양조장(code=BREWERY_NOT_FOUND)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class),
                            examples = @ExampleObject(value = "{\"code\":\"BREWERY_NOT_FOUND\","
                                    + "\"message\":\"양조장을 찾을 수 없습니다: BRW-999\"}"))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류(code=INTERNAL_SERVER_ERROR)",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public RecommendedCourseResponse get(
            @Parameter(description = "코스 중심 양조장 ID", example = "BRW-001")
            @PathVariable String breweryId) {
        return recommendedCourseService.findByBreweryId(breweryId);
    }
}
