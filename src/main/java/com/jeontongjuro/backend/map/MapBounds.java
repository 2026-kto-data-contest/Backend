package com.jeontongjuro.backend.map;

import com.jeontongjuro.backend.global.error.InvalidQueryParameterException;
import java.math.BigDecimal;

record MapBounds(BigDecimal south, BigDecimal west, BigDecimal north, BigDecimal east) {
    static MapBounds of(BigDecimal south, BigDecimal west, BigDecimal north, BigDecimal east) {
        if (south.compareTo(new BigDecimal("-90")) < 0 || north.compareTo(new BigDecimal("90")) > 0
                || west.compareTo(new BigDecimal("-180")) < 0 || east.compareTo(new BigDecimal("180")) > 0
                || south.compareTo(north) >= 0 || west.compareTo(east) >= 0) {
            throw new InvalidQueryParameterException("지도 영역 좌표가 올바르지 않습니다. south < north, west < east 이어야 합니다.");
        }
        return new MapBounds(south, west, north, east);
    }
}
