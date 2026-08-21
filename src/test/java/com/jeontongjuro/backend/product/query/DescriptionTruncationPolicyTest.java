package com.jeontongjuro.backend.product.query;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 설명 절단 되돌림 정책 단위 검증(DB 없음). 스펙 §⑤의 케이스를 크래프트 입력으로 고정한다.
 * 실데이터 스냅샷 회귀는 {@link ProductQueryGoldenTest}가 별도로 잡는다.
 */
class DescriptionTruncationPolicyTest {

    @Test
    @DisplayName("78자 절단 + '. ' 존재 → 마지막 문장 끝까지 되돌림")
    void revertsAt78WithPeriodSpace() {
        String s = "첫 문장이다. 두번째 문장은 여기서 잘려버린 미완성 꼬리표현들이 이어지는 중이며 계속됨";
        // 길이를 78로 맞춘다
        s = padTo(s, 78);
        assertThat(s).hasSize(78);
        assertThat(DescriptionTruncationPolicy.apply(s)).isEqualTo("첫 문장이다.");
    }

    @Test
    @DisplayName("79자 절단 + '. ' 존재 → 되돌림")
    void revertsAt79WithPeriodSpace() {
        String s = padTo("완결된 첫 문장. 그리고 뒤에 붙은 미완성 표현이 계속 이어지다가 여기쯤에서 그냥 잘린다네", 79);
        assertThat(s).hasSize(79);
        assertThat(DescriptionTruncationPolicy.apply(s)).isEqualTo("완결된 첫 문장.");
    }

    @Test
    @DisplayName("78/79자인데 마침표가 맨 끝(완결 문장) → 원문 그대로")
    void keepsWholeWhenPeriodAtEnd() {
        // 내부에 '. '가 없고 끝이 '.'인 78자 문장(완결) — 되돌림 결과가 원문과 같아야 한다
        String s = padTo("이문장은쉼표없이온전히한문장으로이어지다가맨끝에서마침표로끝난다", 77) + ".";
        assertThat(s).hasSize(78);
        assertThat(s).doesNotContain(". ");
        assertThat(s.charAt(77)).isEqualTo('.');
        assertThat(DescriptionTruncationPolicy.apply(s)).isEqualTo(s);
    }

    @Test
    @DisplayName("78/79자인데 문장 끝(마침표+공백/끝)이 하나도 없음 → null(미노출)")
    void nullWhenNoSentenceEnd() {
        String s = padTo("마침표가 전혀 없이 쉼표만, 이어지다, 문장이, 끝나지, 않고, 계속, 흘러가는, 소개, 문구", 78);
        assertThat(s).hasSize(78);
        assertThat(s).doesNotContain(". ");
        assertThat(s.charAt(s.length() - 1)).isNotEqualTo('.');
        assertThat(DescriptionTruncationPolicy.apply(s)).isNull();
    }

    @Test
    @DisplayName("77자 이하 → 절단 판정 안 받고 원문 그대로")
    void keepsWhenNotTruncatedLength() {
        String s = padTo("짧은 소개, 마침표 없이 끝나는 문구", 77);
        assertThat(s).hasSize(77);
        assertThat(DescriptionTruncationPolicy.apply(s)).isEqualTo(s);
    }

    @Test
    @DisplayName("80자 이상 → 절단 판정 안 받고 원문 그대로")
    void keepsWhenLongerThanTruncation() {
        String s = padTo("길게 이어지는 소개, 마침표 없이", 120);
        assertThat(s).hasSize(120);
        assertThat(DescriptionTruncationPolicy.apply(s)).isEqualTo(s);
    }

    @Test
    @DisplayName("소수점은 되돌림 지점이 아니다 — '. '에서만 자른다")
    void doesNotCutAtDecimalPoint() {
        // "…막걸리. 순수령 5.8도와…" 패턴을 79자로: 5.8의 마침표는 뒤가 숫자라 무시, 막걸리.에서 컷
        String s = padTo("무첨가 프리미엄 막걸리. 순수령 5.8도와 비교하여 더 무게감 있는 바디감이 담겨있는 술", 79);
        assertThat(s).hasSize(79);
        assertThat(DescriptionTruncationPolicy.apply(s)).isEqualTo("무첨가 프리미엄 막걸리.");
    }

    @Test
    @DisplayName("마침표 뒤 비ASCII 공백(개행·전각공백·NBSP)은 문장 끝이 아니다 → 경계 없으면 null "
            + "(실데이터 product_raw.description엔 0건 — 방어적 봉인)")
    void nonAsciiWhitespaceAfterPeriodIsNotSentenceEnd() {
        // 정책은 '마침표+ASCII 0x20' 또는 '마침표+문자열끝'만 문장 끝으로 본다. 마침표 뒤가 개행/전각공백/NBSP면
        // 경계로 인정하지 않는다 — 이 세 문자가 유일한 마침표 뒤에 오면 문장 끝이 없어 null을 반환한다.
        // ★실데이터 조사: product_raw.description 전수에서 이 세 문자 0건(관광공사 overview는 이 정책의 입력이 아니다).
        //   현재 동작을 봉인만 한다(정책 변경은 스코프 밖).
        for (char ws : new char[] {'\n', '\u3000', '\u00A0'}) {
            String s = padTo("여기서 문장이 끝난다." + ws + "미완성 꼬리표현이 계속 이어지는 중", 78);
            assertThat(s).hasSize(78);
            assertThat(s).as("마침표 뒤 ASCII 공백 경계는 없다(U+%04X)", (int) ws).doesNotContain(". ");
            assertThat(DescriptionTruncationPolicy.apply(s))
                    .as("마침표 뒤 비ASCII 공백 U+%04X은 경계가 아님 → null", (int) ws)
                    .isNull();
        }
    }

    @Test
    @DisplayName("번호 목록의 'N. '은 문장 끝으로 오인된다 → 마지막 'N.'에서 잘림(현재 동작 봉인, 78/79 실데이터 0건)")
    void numberListDotSpaceIsFalsePositive() {
        // '3. ' 같은 번호 목록 구분자도 '마침표+공백'이라 문장 끝으로 오탐되어 목록 중간에서 잘린다.
        // ★78/79 길이의 실데이터엔 이 패턴 0건이지만, 정책의 알려진 오탐을 합성 입력으로 봉인한다(변경 아님).
        String s = padTo("재료는 1. 쌀 2. 누룩 3. 정제수를 정성껏 배합하여 오랜 시간 발효 숙성시킨 전통 방식의 술", 78);
        assertThat(s).hasSize(78);
        assertThat(DescriptionTruncationPolicy.apply(s)).isEqualTo("재료는 1. 쌀 2. 누룩 3.");
    }

    @Test
    @DisplayName("null → null, 빈 문자열 → null")
    void nullAndEmpty() {
        assertThat(DescriptionTruncationPolicy.apply(null)).isNull();
        assertThat(DescriptionTruncationPolicy.apply("")).isNull();
    }

    /** 문자열 뒤에 '가'를 붙여 정확히 target 길이로 만든다(문장 끝 구조는 유지). */
    private static String padTo(String base, int target) {
        if (base.length() == target) {
            return base;
        }
        if (base.length() > target) {
            return base.substring(0, target);
        }
        StringBuilder sb = new StringBuilder(base);
        while (sb.length() < target) {
            sb.append('가');
        }
        return sb.toString();
    }
}
