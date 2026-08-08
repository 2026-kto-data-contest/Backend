package com.jeontongjuro.backend.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jeontongjuro.backend.product.AlcoholContentParser.AlcoholRange;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 도수 파서 단위 테스트(#35). Spring/DB 무의존 — 원본 alcohol_content 실측 원문을 그대로 케이스로 쓴다(지어내지 않음).
 * min/max가 정렬에 의존하지 않음("41, 25, 17"), 소수부 보존("12.5"/"36.5"), 접미·범위·슬래시 잡음 제거,
 * 매치 0개 → (null,null), 범위 초과 → fail-fast를 함께 검증한다.
 */
class AlcoholContentParserTest {

    private static void assertRange(String raw, String expectedMin, String expectedMax) {
        AlcoholRange r = AlcoholContentParser.parse(raw);
        assertThat(r.min()).as("min of '%s'", raw).isEqualByComparingTo(expectedMin);
        assertThat(r.max()).as("max of '%s'", raw).isEqualByComparingTo(expectedMax);
    }

    @Test
    @DisplayName("단일 정수 '12' → (12, 12)")
    void singleInteger() {
        assertRange("12", "12", "12");
    }

    @Test
    @DisplayName("단일 소수 '12.5' → (12.5, 12.5) — 소수부 보존")
    void singleDecimal() {
        assertRange("12.5", "12.5", "12.5");
    }

    @Test
    @DisplayName("퍼센트 접미 '10%' → (10, 10)")
    void percentSuffix() {
        assertRange("10%", "10", "10");
    }

    @Test
    @DisplayName("물결 범위 '14~16' → (14, 16)")
    void tildeRange() {
        assertRange("14~16", "14", "16");
    }

    @Test
    @DisplayName("쉼표 2값 '13, 15' → (13, 15)")
    void commaTwoValues() {
        assertRange("13, 15", "13", "15");
    }

    @Test
    @DisplayName("쉼표 3값 '25, 40, 54' → (25, 54)")
    void commaThreeValues() {
        assertRange("25, 40, 54", "25", "54");
    }

    @Test
    @DisplayName("순서 뒤바뀐 3값 '41, 25, 17' → (17, 41) — 정렬 비의존")
    void unsortedValues() {
        assertRange("41, 25, 17", "17", "41");
    }

    @Test
    @DisplayName("소수 혼합 '36.5, 56' → (36.5, 56)")
    void decimalMixed() {
        assertRange("36.5, 56", "36.5", "56");
    }

    @Test
    @DisplayName("슬래시 '24%/40' → (24, 40)")
    void slashOnePercent() {
        assertRange("24%/40", "24", "40");
    }

    @Test
    @DisplayName("슬래시 양쪽 퍼센트 '25%/ 40%' → (25, 40)")
    void slashBothPercent() {
        assertRange("25%/ 40%", "25", "40");
    }

    @Test
    @DisplayName("빈 문자열 '' → (null, null)")
    void emptyString() {
        AlcoholRange r = AlcoholContentParser.parse("");
        assertThat(r.min()).isNull();
        assertThat(r.max()).isNull();
    }

    @Test
    @DisplayName("비숫자 '도수 미상' → (null, null)")
    void nonNumeric() {
        AlcoholRange r = AlcoholContentParser.parse("도수 미상");
        assertThat(r.min()).isNull();
        assertThat(r.max()).isNull();
    }

    @Test
    @DisplayName("100 초과 '150' → fail-fast(IllegalStateException)")
    void overHundredFailFast() {
        assertThatThrownBy(() -> AlcoholContentParser.parse("150"))
                .isInstanceOf(IllegalStateException.class);
    }
}
