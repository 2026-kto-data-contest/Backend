package com.jeontongjuro.backend.map;

import com.jeontongjuro.backend.brewery.query.MainImageResponse;
import com.jeontongjuro.backend.liquortype.LiquorType;
import com.jeontongjuro.backend.product.query.ProductFlavorTag;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;

public record MapLiquorMenuItemResponse(
        @Schema(example = "441") Integer productId,
        @Schema(example = "우곡생주") String productName,
        @Schema(example = "BRW-001") String breweryId,
        @Schema(example = "해창주조장") String breweryName,
        List<LiquorType> liquorTypes,
        List<ProductFlavorTag> flavorTags,
        BigDecimal alcoholMin,
        BigDecimal alcoholMax,
        String volume,
        String address,
        BigDecimal latitude,
        BigDecimal longitude,
        MainImageResponse image) {
}
