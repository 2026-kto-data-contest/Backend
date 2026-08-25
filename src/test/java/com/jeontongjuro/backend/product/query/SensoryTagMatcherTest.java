package com.jeontongjuro.backend.product.query;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 맛 태그 매칭 단위 검증(DB 없음). 키워드셋 8종 봉인(정찰 세션 산출값 — 임의 증감 금지) + 활용형 회귀를 고정한다.
 */
class SensoryTagMatcherTest {

    @Test
    @DisplayName("키워드셋 봉인: 8종 각 1건 이상 매칭")
    void allEightTagsMatchAtLeastOnce() {
        assertThat(SensoryTagMatcher.match("상큼한 맛이 특징")).containsExactly(SensoryTag.상큼함);
        assertThat(SensoryTagMatcher.match("달콤한 맛")).containsExactly(SensoryTag.달콤함);
        assertThat(SensoryTagMatcher.match("드라이한 스타일")).containsExactly(SensoryTag.드라이);
        assertThat(SensoryTagMatcher.match("은은한 산미")).containsExactly(SensoryTag.산미);
        assertThat(SensoryTagMatcher.match("부드럽고 목넘김이 좋음")).containsExactly(SensoryTag.부드러움);
        assertThat(SensoryTagMatcher.match("묵직한 바디감")).containsExactly(SensoryTag.묵직함);
        assertThat(SensoryTagMatcher.match("깔끔한 뒷맛")).containsExactly(SensoryTag.깔끔함);
        assertThat(SensoryTagMatcher.match("향긋한 꽃향기")).containsExactly(SensoryTag.향긋함);
    }

    @Test
    @DisplayName("달콤함 동의어 3종(달콤·단맛·달달) 전부 매칭")
    void sweetnessSynonyms() {
        assertThat(SensoryTagMatcher.match("달콤한 맛")).containsExactly(SensoryTag.달콤함);
        assertThat(SensoryTagMatcher.match("은은한 단맛이 남는다")).containsExactly(SensoryTag.달콤함);
        assertThat(SensoryTagMatcher.match("달달한 향")).containsExactly(SensoryTag.달콤함);
    }

    @Test
    @DisplayName("산미 동의어(새콤) 매칭")
    void sourSynonym() {
        assertThat(SensoryTagMatcher.match("새콤한 맛이 감돈다")).containsExactly(SensoryTag.산미);
    }

    @Test
    @DisplayName("★ㅂ-irregular 활용형 회귀: '부드러운'·'부드러움'은 '부드럽' 부분문자열을 포함하지 않아도 매칭돼야 한다")
    void softnessConjugationForms() {
        assertThat(SensoryTagMatcher.match("부드러움이 입 안 전체를 감싼다")).containsExactly(SensoryTag.부드러움);
        assertThat(SensoryTagMatcher.match("부드러운 목넘김")).containsExactly(SensoryTag.부드러움);
        assertThat(SensoryTagMatcher.match("부드러워서 좋다")).containsExactly(SensoryTag.부드러움);
        assertThat(SensoryTagMatcher.match("부드러웠던 첫 느낌")).containsExactly(SensoryTag.부드러움);
    }

    @Test
    @DisplayName("복수 태그 동시 매칭: 선언 순서(상큼함→…→향긋함) 고정")
    void multipleTagsPreserveDeclarationOrder() {
        assertThat(SensoryTagMatcher.match("향긋하면서도 상큼하고 깔끔한 뒷맛"))
                .containsExactly(SensoryTag.상큼함, SensoryTag.깔끔함, SensoryTag.향긋함);
    }

    @Test
    @DisplayName("부정문 배제 없음(스펙 규칙): '산미는 적지만'도 산미로 매칭된다")
    void noNegationHandling() {
        assertThat(SensoryTagMatcher.match("산미는 적지만 끝맛이 깔끔하다"))
                .containsExactlyInAnyOrder(SensoryTag.산미, SensoryTag.깔끔함);
    }

    @Test
    @DisplayName("null·blank·무매칭은 빈 배열")
    void emptyForNullBlankOrNoMatch() {
        assertThat(SensoryTagMatcher.match(null)).isEmpty();
        assertThat(SensoryTagMatcher.match("")).isEmpty();
        assertThat(SensoryTagMatcher.match("   ")).isEmpty();
        assertThat(SensoryTagMatcher.match("전통 방식으로 빚은 술입니다")).isEmpty();
    }

    @Test
    @DisplayName("같은 태그의 키워드가 여러 번 등장해도 중복 없이 1개만")
    void sameTagNotDuplicated() {
        assertThat(SensoryTagMatcher.match("달콤달콤하고 단맛이 풍부"))
                .containsExactly(SensoryTag.달콤함);
    }

    @Test
    @DisplayName("List.of() 불변 반환값이라도 API 계약상 빈 배열([])로 직렬화될 형태")
    void noMatchReturnsEmptyListNotNull() {
        List<SensoryTag> result = SensoryTagMatcher.match("아무 관능 표현도 없는 문구");
        assertThat(result).isNotNull().isEmpty();
    }
}
