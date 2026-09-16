package com.jeontongjuro.backend.map;

import com.jeontongjuro.backend.global.error.ErrorResponse;
import com.jeontongjuro.backend.global.web.PageResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/map/liquor-menus")
@Tag(name = "지도", description = "지도 추천 콘텐츠 API")
public class MapLiquorMenuController {
    private final MapLiquorMenuService service;
    public MapLiquorMenuController(MapLiquorMenuService service) { this.service = service; }

    @GetMapping
    @Operation(summary = "지도 전통주 메뉴 필터 목록 조회", description = "맛·주종별 전통주 필터 목록을 조회합니다.")
    @ApiResponse(responseCode = "200", description = "메뉴 필터 조회 성공")
    public List<MapLiquorMenuResponse> menus() { return service.menus(); }

    @GetMapping("/{menu}/products")
    @Operation(summary = "메뉴별 전통주 조회", description = "선택한 맛 또는 주종에 해당하는 전통주 카드 목록을 조회합니다.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "전통주 조회 성공"),
            @ApiResponse(responseCode = "400", description = "존재하지 않는 메뉴",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "500", description = "서버 내부 오류",
                    content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    public PageResponse<MapLiquorMenuItemResponse> products(
            @Parameter(description = "GET /api/v1/map/liquor-menus 응답의 menu 값", example = "FRESH")
            @PathVariable String menu,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "4") int size) {
        return service.products(menu, page, size);
    }
}
