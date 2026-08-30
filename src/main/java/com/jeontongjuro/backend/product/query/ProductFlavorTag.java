package com.jeontongjuro.backend.product.query;

import com.jeontongjuro.backend.liquortype.LiquorType;
import java.util.ArrayList;
import java.util.List;

/** 기능명세서의 제품 카드용 주종별 고정 맛 태그. */
public enum ProductFlavorTag {
    달콤함,
    상큼함,
    고소함,
    부드러움,
    깔끔함,
    담백함,
    묵직함,
    드라이함;

    public static List<ProductFlavorTag> from(List<LiquorType> liquorTypes) {
        List<ProductFlavorTag> tags = new ArrayList<>();
        for (LiquorType liquorType : liquorTypes) {
            for (ProductFlavorTag tag : tagsFor(liquorType)) {
                if (!tags.contains(tag)) {
                    tags.add(tag);
                }
            }
        }
        return List.copyOf(tags);
    }

    private static List<ProductFlavorTag> tagsFor(LiquorType liquorType) {
        return switch (liquorType) {
            case 과실주 -> List.of(달콤함, 상큼함);
            case 탁주 -> List.of(고소함, 부드러움);
            case 약주, 청주 -> List.of(깔끔함, 담백함);
            case 증류주 -> List.of(묵직함, 드라이함);
            case 기타 -> List.of();
        };
    }
}
