package com.jeontongjuro.backend.product.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 제품 카드 응답 DTO의 설명 엔티티 디코딩 단위 검증(DB 없음).
 * 입력은 aT 원본 실데이터(sr 번호 표기)에서 그대로 따왔다 — 절단 되돌림을 이미 거친 문자열을 넣는다.
 */
class ProductCardResponseTest {

    @Test
    @DisplayName("1중 인코딩(&quot;) → 디코딩")
    void decodesSingleEncoded() {
        // sr=248 BRW-029 — sr=246과 같은 문장의 1중 인코딩본
        assertThat(card("&quot;술아&quot;는 두가지 술이 만나 새로운 술이 만들어지는 한국의 전통주인 과하주를 복원한 제품이다.")
                .description())
                .isEqualTo("\"술아\"는 두가지 술이 만나 새로운 술이 만들어지는 한국의 전통주인 과하주를 복원한 제품이다.");
    }

    @Test
    @DisplayName("2중 인코딩(&amp;quot;) → 디코딩")
    void decodesDoubleEncoded() {
        // sr=246 BRW-029 — 디자이너 QA가 보고한 바로 그 문자열
        assertThat(card("&amp;quot;술아&amp;quot;는 두가지 술이 만나 새로운 술이 만들어지는 한국의 전통주인 과하주를 복원한 제품이다.")
                .description())
                .isEqualTo("\"술아\"는 두가지 술이 만나 새로운 술이 만들어지는 한국의 전통주인 과하주를 복원한 제품이다.");
    }

    @Test
    @DisplayName("2중 인코딩 숫자 참조(&amp;#039;) → 디코딩(&quot;만 치환하는 설계였다면 여기서 샌다)")
    void decodesDoubleEncodedNumericReference() {
        // sr=642 BRW-021 — 표시 범위에서 유일한 &#039; 계열. 이번 작업의 핵심 위험이었다.
        assertThat(card("매화마름 맛술은 멸종위기 식물 매화마름을 보존하는 한국 내셔널 트러스트의 "
                + "&amp;#039;매화마름쌀&amp;#039;로 빚는 술이다.").description())
                .isEqualTo("매화마름 맛술은 멸종위기 식물 매화마름을 보존하는 한국 내셔널 트러스트의 "
                        + "'매화마름쌀'로 빚는 술이다.");
    }

    @Test
    @DisplayName("description이 null(미노출) → null 유지")
    void keepsNull() {
        assertThat(card(null).description()).isNull();
    }

    @Test
    @DisplayName("엔티티 없는 평문 → 원문 그대로(변형 0)")
    void keepsPlainTextUnchanged() {
        String plain = "옅은 핑크빛이 도는 스위트 와인으로, 장미향이 은은하게 나는 달콤한 디저트 와인이다.";
        assertThat(card(plain).description()).isEqualTo(plain);
    }

    @Test
    @DisplayName("리터럴 & 가 든 문자열 → 훼손되지 않음")
    void keepsLiteralAmpersand() {
        String literal = "와인&재즈 페스티벌에서 선보인 R&D 제품이다.";
        assertThat(card(literal).description()).isEqualTo(literal);
    }

    @Test
    @DisplayName("멱등성 — 이미 디코딩된 값을 다시 넣어도 동일")
    void isIdempotent() {
        String once = card("&amp;quot;술아&amp;quot;는 과하주를 복원한 제품이다.").description();
        assertThat(card(once).description()).isEqualTo(once);
    }

    @Test
    @DisplayName("description 외 필드는 건드리지 않는다")
    void touchesOnlyDescription() {
        ProductCardResponse c = new ProductCardResponse(
                441, "진맥 &quot;소주&quot;", new BigDecimal("40.0"), new BigDecimal("40.0"),
                "200ml", List.of(), "설명이다.", "대상 &amp;quot;수상&amp;quot;");
        assertThat(c.productName()).isEqualTo("진맥 &quot;소주&quot;");
        assertThat(c.awardBadge()).isEqualTo("대상 &amp;quot;수상&amp;quot;");
        assertThat(c.volume()).isEqualTo("200ml");
    }

    private static ProductCardResponse card(String description) {
        return new ProductCardResponse(441, "진맥 소주", null, null, null, List.of(), description, null);
    }
}
