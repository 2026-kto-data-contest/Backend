package com.jeontongjuro.backend.product.query;

import com.jeontongjuro.backend.global.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 양조장 제품 목록 조회 API. GET /api/v1/breweries/{breweryId}/products.
 * <p>
 * 양조장 상세 화면에서 그 양조장의 제품 카드를 페이지 단위로 내려준다. 판매중단·원본오류 제외와 중복 병합을
 * 거친 노출 제품만 담기며, page·size는 리스트 API와 같은 방식으로 클램프한다(범위 밖 값도 400이 아니라 보정).
 */
@RestController
@RequestMapping("/api/v1/breweries/{breweryId}/products")
@Tag(name = "양조장", description = "양조장 조회 API")
public class ProductQueryController {

    private final ProductQueryService productQueryService;

    public ProductQueryController(ProductQueryService productQueryService) {
        this.productQueryService = productQueryService;
    }

    @GetMapping
    @Operation(summary = "양조장 제품 목록 조회", description = """
            양조장 상세 화면에서 그 양조장의 제품 목록을 조회하는 API입니다.
            page는 0부터 시작하며, 다음 페이지는 응답의 page에 1을 더해 요청하세요.

            도수(alcoholMin/alcoholMax)는 도수 정보가 없으면 둘 다 null, 단일 도수면 서로 같습니다.
            용량(volume)은 원문 그대로이며 복수 표기일 수 있습니다. 주종(liquorTypes)은 없으면 빈 배열,
            소개(description)와 수상 뱃지(awardBadge)는 없으면 null입니다.

            totalElements는 판매중단·원본오류 제외 및 중복 병합을 모두 반영한 노출 제품 수입니다.
            """)
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "제품 목록 조회 성공"),
            @ApiResponse(responseCode = "400", description = "page·size 형식 오류(INVALID_QUERY_PARAMETER)"),
            @ApiResponse(responseCode = "404", description = "해당 breweryId의 양조장이 없음(BREWERY_NOT_FOUND)")
    })
    public PageResponse<ProductCardResponse> list(
            @Parameter(description = "양조장 고유 ID(BRW-xxx)", example = "BRW-001")
            @PathVariable String breweryId,
            @Parameter(description = "페이지 번호. 0부터 시작", example = "0")
            @RequestParam(defaultValue = "0") int page,
            @Parameter(description = "한 페이지에 받을 개수. 기본 5, 최대 100", example = "5")
            @RequestParam(defaultValue = "5") int size) {
        return productQueryService.listProducts(breweryId, page, size);
    }
}
