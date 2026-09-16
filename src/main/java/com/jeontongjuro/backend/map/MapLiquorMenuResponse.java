package com.jeontongjuro.backend.map;

import io.swagger.v3.oas.annotations.media.Schema;

public record MapLiquorMenuResponse(
        @Schema(example = "FRESH") String menu,
        @Schema(example = "상큼함") String displayName,
        @Schema(example = "FLAVOR") String kind) {
}
