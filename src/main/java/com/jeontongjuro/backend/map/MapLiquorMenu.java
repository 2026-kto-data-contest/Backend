package com.jeontongjuro.backend.map;

import com.jeontongjuro.backend.global.error.InvalidQueryParameterException;
import com.jeontongjuro.backend.product.query.ProductCardResponse;
import java.util.Arrays;
import java.util.List;

/** 지도 하단의 전통주 맛·주종 필터 사전. */
public enum MapLiquorMenu {
    SWEET("달콤함", Kind.FLAVOR), FRESH("상큼함", Kind.FLAVOR), NUTTY("고소함", Kind.FLAVOR),
    SMOOTH("부드러움", Kind.FLAVOR), CLEAN("깔끔함", Kind.FLAVOR), LIGHT("담백함", Kind.FLAVOR),
    HEAVY("묵직함", Kind.FLAVOR), DRY("드라이함", Kind.FLAVOR),
    TAKJU("탁주", Kind.LIQUOR_TYPE), YAKJU("약주", Kind.LIQUOR_TYPE), CHEONGJU("청주", Kind.LIQUOR_TYPE),
    DISTILLED("증류주", Kind.LIQUOR_TYPE), FRUIT_WINE("과실주", Kind.LIQUOR_TYPE), ETC("기타", Kind.LIQUOR_TYPE);

    enum Kind { FLAVOR, LIQUOR_TYPE }

    private final String displayName;
    private final Kind kind;

    MapLiquorMenu(String displayName, Kind kind) { this.displayName = displayName; this.kind = kind; }
    public String displayName() { return displayName; }
    public String kind() { return kind.name(); }

    public boolean matches(ProductCardResponse product) {
        if (kind == Kind.FLAVOR) return product.flavorTags().stream().anyMatch(tag -> tag.name().equals(displayName));
        return product.liquorTypes().stream().anyMatch(type -> type.name().equals(displayName));
    }

    public static MapLiquorMenu parse(String raw) {
        try { return valueOf(raw == null ? "" : raw.trim().toUpperCase()); }
        catch (IllegalArgumentException e) {
            throw new InvalidQueryParameterException("허용되지 않은 liquor menu 값입니다: '" + raw
                    + "' (허용: " + String.join(", ", Arrays.stream(values()).map(Enum::name).toList()) + ")");
        }
    }
}
