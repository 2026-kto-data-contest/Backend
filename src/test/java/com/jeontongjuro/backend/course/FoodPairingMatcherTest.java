package com.jeontongjuro.backend.course;

import static org.assertj.core.api.Assertions.assertThat;

import com.jeontongjuro.backend.tour.TourContent;
import com.jeontongjuro.backend.tour.TourContentRow;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.Test;

class FoodPairingMatcherTest {

    @Test
    void jeonFoodContextMatchesAndIncludesBreweryAndReason() {
        assertThat(FoodPairingMatcher.pairingComment(
                List.of("해물전 안주와 잘 어울립니다"), koreanRestaurant(), "갈기산양조장"))
                .get().asString().contains("갈기산양조장의 전 페어링", "한식");
    }

    @ParameterizedTest
    @ValueSource(strings = {"전통 방식으로 빚었습니다", "전통주입니다", "전남의 쌀", "완전 부드러운 맛",
            "이전 제품보다 산뜻합니다", "오래전부터 이어온 술"})
    void jeonFalsePositiveContextsDoNotMatch(String description) {
        assertThat(FoodPairingMatcher.pairingComment(
                List.of(description), koreanRestaurant(), "갈기산양조장")).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"대한민국 우리술품평회 대상", "배상면 회장의 역작", "농업회사법인",
            "국제소믈리에협회 인증"})
    void sashimiFalsePositiveContextsDoNotMatch(String description) {
        assertThat(FoodPairingMatcher.pairingComment(
                List.of(description), japaneseRestaurant(), "갈기산양조장")).isEmpty();
    }

    @Test
    void sashimiFoodContextStillMatches() {
        assertThat(FoodPairingMatcher.pairingComment(
                List.of("족발, 삼겹살, 회 등 다양한 안주와 어울린다"), japaneseRestaurant(), "갈기산양조장"))
                .get().asString().contains("회 페어링");
    }

    private TourContent koreanRestaurant() {
        TourContentRow row = new TourContentRow("FOOD", "39", "영동 한식당", "주소", null, null,
                null, null, null, null, "A05020100", null, null, null, null, null,
                "127.1", "36.1", null, null, null, null, null, null, null);
        return TourContent.create(row, new BigDecimal("36.100000"), new BigDecimal("127.100000"));
    }


    private TourContent japaneseRestaurant() {
        TourContentRow row = new TourContentRow("JAPANESE", "39", "바다 횟집", "주소", null, null,
                null, null, null, null, "A05020300", null, null, null, null, null,
                "127.1", "36.1", null, null, null, null, null, null, null);
        return TourContent.create(row, new BigDecimal("36.100000"), new BigDecimal("127.100000"));
    }
}
