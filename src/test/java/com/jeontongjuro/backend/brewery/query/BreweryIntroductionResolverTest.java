package com.jeontongjuro.backend.brewery.query;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 양조장 소개 우선순위 병합 단위 검증(DB 없음). overview 있음 / 없고 note 있음 / 둘 다 없음 3분기. */
class BreweryIntroductionResolverTest {

    @Test
    @DisplayName("overview 있음 → overview 그대로(designationNote 있어도 무시)")
    void overviewPresentWins() {
        assertThat(BreweryIntroductionResolver.resolve("1918년 창업한 …", "80년 전통 3대째 …"))
                .isEqualTo("1918년 창업한 …");
    }

    @Test
    @DisplayName("overview 없고 designationNote 있음 → designationNote")
    void fallsBackToDesignationNote() {
        assertThat(BreweryIntroductionResolver.resolve(null, "80년 전통 3대째 …"))
                .isEqualTo("80년 전통 3대째 …");
        assertThat(BreweryIntroductionResolver.resolve("", "80년 전통 3대째 …"))
                .isEqualTo("80년 전통 3대째 …");
        assertThat(BreweryIntroductionResolver.resolve("   ", "80년 전통 3대째 …"))
                .isEqualTo("80년 전통 3대째 …");
    }

    @Test
    @DisplayName("둘 다 없음 → null")
    void bothAbsentYieldsNull() {
        assertThat(BreweryIntroductionResolver.resolve(null, null)).isNull();
        assertThat(BreweryIntroductionResolver.resolve("", "   ")).isNull();
    }
}
