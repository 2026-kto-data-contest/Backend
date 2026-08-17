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
