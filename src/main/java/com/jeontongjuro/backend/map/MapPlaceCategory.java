package com.jeontongjuro.backend.map;

import com.jeontongjuro.backend.global.error.InvalidQueryParameterException;
import java.util.Arrays;

public enum MapPlaceCategory {
    BREWERY("양조장"), RESTAURANT("식당"), TOURIST_ATTRACTION("관광지"),
    CAFE("카페"), ACCOMMODATION("숙소");

    private final String displayName;

    MapPlaceCategory(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    public static MapPlaceCategory parse(String raw) {
        try {
            return valueOf(raw == null ? "" : raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidQueryParameterException("허용되지 않은 category 값입니다: '" + raw
                    + "' (허용: " + String.join(", ", Arrays.stream(values()).map(Enum::name).toList()) + ")");
        }
    }
}
