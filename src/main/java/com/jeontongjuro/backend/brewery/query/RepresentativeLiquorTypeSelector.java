package com.jeontongjuro.backend.brewery.query;

import com.jeontongjuro.backend.liquortype.LiquorType;
import com.jeontongjuro.backend.product.query.ProductCardResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/** 수상 제품 보유 → 제품 수 → 주종 선언 순서로 대표주종을 선정한다. */
final class RepresentativeLiquorTypeSelector {

    private static final int DISPLAY_LIMIT = 2;

    private RepresentativeLiquorTypeSelector() {
    }

    static RepresentativeLiquorTypesResponse select(
            List<LiquorType> allTypes,
            List<ProductCardResponse> products
    ) {
        Map<LiquorType, Stat> stats = new EnumMap<>(LiquorType.class);
        allTypes.forEach(type -> stats.put(type, new Stat()));

        for (ProductCardResponse product : products) {
            for (LiquorType type : product.liquorTypes()) {
                Stat stat = stats.get(type);
                if (stat != null) {
                    stat.productCount++;
                    stat.hasAward |= product.awardBadge() != null;
                }
            }
        }

        List<LiquorType> ordered = new ArrayList<>(allTypes);
        ordered.sort(Comparator
                .comparing((LiquorType type) -> stats.get(type).hasAward).reversed()
                .thenComparing((LiquorType type) -> stats.get(type).productCount, Comparator.reverseOrder())
                .thenComparingInt(Enum::ordinal));

        int visibleCount = Math.min(DISPLAY_LIMIT, ordered.size());
        return new RepresentativeLiquorTypesResponse(
                ordered.subList(0, visibleCount),
                Math.max(0, ordered.size() - visibleCount));
    }

    private static final class Stat {
        private boolean hasAward;
        private int productCount;
    }
}
