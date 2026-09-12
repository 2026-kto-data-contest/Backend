package com.jeontongjuro.backend.brewery.query;

import com.jeontongjuro.backend.liquortype.LiquorType;
import com.jeontongjuro.backend.product.query.ProductCardResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
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

        // 동점 순서는 enum 선언 순서가 아니라 제품 데이터에 나타난 주종 출력 순서를 따른다.
        // 제품에 나타나지 않은 주종은 allTypes의 순서를 fallback으로 사용한다.
        Map<LiquorType, Integer> declarationOrder = new HashMap<>();
        int nextOrder = 0;
        for (ProductCardResponse product : products) {
            for (LiquorType type : product.liquorTypes()) {
                if (!declarationOrder.containsKey(type)) {
                    declarationOrder.put(type, nextOrder++);
                }
            }
        }
        for (LiquorType type : allTypes) {
            if (!declarationOrder.containsKey(type)) {
                declarationOrder.put(type, nextOrder++);
            }
        }

        List<LiquorType> ordered = new ArrayList<>(allTypes);
        ordered.sort(Comparator
                .comparing((LiquorType type) -> stats.get(type).hasAward).reversed()
                .thenComparing((LiquorType type) -> stats.get(type).productCount, Comparator.reverseOrder())
                .thenComparingInt(declarationOrder::get));

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
