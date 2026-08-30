package com.jeontongjuro.backend.product.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.jeontongjuro.backend.liquortype.LiquorType;
import java.util.List;
import org.junit.jupiter.api.Test;

class ProductFlavorTagTest {

    @Test
    void mapsEverySpecifiedLiquorTypeToFixedTags() {
        assertThat(ProductFlavorTag.from(List.of(LiquorType.과실주)))
                .containsExactly(ProductFlavorTag.달콤함, ProductFlavorTag.상큼함);
        assertThat(ProductFlavorTag.from(List.of(LiquorType.탁주)))
                .containsExactly(ProductFlavorTag.고소함, ProductFlavorTag.부드러움);
        assertThat(ProductFlavorTag.from(List.of(LiquorType.약주, LiquorType.청주)))
                .containsExactly(ProductFlavorTag.깔끔함, ProductFlavorTag.담백함);
        assertThat(ProductFlavorTag.from(List.of(LiquorType.증류주)))
                .containsExactly(ProductFlavorTag.묵직함, ProductFlavorTag.드라이함);
    }

    @Test
    void otherAndMissingTypesHaveNoTagsAndMultipleTypesAreDeduplicated() {
        assertThat(ProductFlavorTag.from(List.of())).isEmpty();
        assertThat(ProductFlavorTag.from(List.of(LiquorType.기타))).isEmpty();
        assertThat(ProductFlavorTag.from(List.of(LiquorType.약주, LiquorType.청주, LiquorType.과실주)))
                .containsExactly(ProductFlavorTag.깔끔함, ProductFlavorTag.담백함,
                        ProductFlavorTag.달콤함, ProductFlavorTag.상큼함);
    }
}
