package com.jeontongjuro.backend.brewery.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.jeontongjuro.backend.liquortype.LiquorType;
import com.jeontongjuro.backend.product.query.ProductCardResponse;
import java.util.List;
import org.junit.jupiter.api.Test;

class RepresentativeLiquorTypeSelectorTest {

    @Test
    void prioritizesAwardThenProductCountThenDeclarationOrder() {
        List<ProductCardResponse> products = List.of(
                product(1, null, LiquorType.탁주),
                product(2, null, LiquorType.탁주),
                product(3, "수상", LiquorType.청주),
                product(4, null, LiquorType.증류주));

        RepresentativeLiquorTypesResponse selected = RepresentativeLiquorTypeSelector.select(
                List.of(LiquorType.탁주, LiquorType.약주, LiquorType.청주, LiquorType.증류주), products);

        assertThat(selected.items()).containsExactly(LiquorType.청주, LiquorType.탁주);
        assertThat(selected.remainingCount()).isEqualTo(2);
    }

    @Test
    void exposesAllWhenThereAreAtMostTwoTypes() {
        RepresentativeLiquorTypesResponse selected = RepresentativeLiquorTypeSelector.select(
                List.of(LiquorType.약주, LiquorType.과실주), List.of());

        assertThat(selected.items()).containsExactly(LiquorType.약주, LiquorType.과실주);
        assertThat(selected.remainingCount()).isZero();
    }

    private ProductCardResponse product(int id, String awardBadge, LiquorType type) {
        return new ProductCardResponse(id, "제품" + id, null, null, null,
                List.of(type), null, awardBadge);
    }
}
