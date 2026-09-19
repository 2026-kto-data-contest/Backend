package com.jeontongjuro.backend.map;

import com.jeontongjuro.backend.global.error.InvalidQueryParameterException;
import java.util.Arrays;

/** 지도에서 전통주와 함께 찾아보기 좋은 메뉴의 고정 사전. */
public enum MapMenu {
    PAJEON("파전", "파전·전"),
    SAMHAP("삼합", "삼합"),
    BOSSAM("보쌈", "보쌈"),
    HANWOO("한우", "한우"),
    MEAT("고기", "고기·갈비"),
    SASHIMI("회", "회·횟집"),
    CHEESE("치즈", "치즈·파스타");

    private final String displayName;
    private final String searchText;

    MapMenu(String displayName, String searchText) {
        this.displayName = displayName;
        this.searchText = searchText;
    }

    public String displayName() {
        return displayName;
    }

    String searchText() {
        return searchText;
    }

    public static MapMenu parse(String raw) {
        try {
            return valueOf(raw == null ? "" : raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new InvalidQueryParameterException("허용되지 않은 menu 값입니다: '" + raw
                    + "' (허용: " + String.join(", ", Arrays.stream(values()).map(Enum::name).toList()) + ")");
        }
    }
}
