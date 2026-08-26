package com.jeontongjuro.backend.search;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.jeontongjuro.backend.global.error.InvalidQueryParameterException;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 검색 키워드 정규화 단위 검증(DB 불필요). 핵심 불변식: 어떤 이름 x든(길이 통과 시)
 * {@code normalizeForMatch(x) == normalizeTarget(x)} — 자동완성이 내려준 이름을 그대로 재검색하면
 * 자기 자신에 매칭된다(특수문자 포함 이름의 "재검색 0건" 방지).
 */
class SearchKeywordTest {

    @Test
    @DisplayName("입력·대상 정규화 규칙이 같다: 자동완성 노출 텍스트를 그대로 재검색하면 자기 자신에 매칭된다")
    void inputAndTargetNormalizationAgreeSoSelfMatchHolds() {
        List<String> names = List.of(
                "이화주(술샘)", "P.S 로제 스파클링", "티나(TINA)", "여포의 꿈 레드(Dry)",
                "진도홍주, 만홍", "국가유산·명인 조옥화 안동소주", "산막와이너리, 미", "아임프리6.0");
        for (String name : names) {
            String asInput = SearchKeyword.normalizeForMatch(name);
            String asTarget = SearchKeyword.normalizeTarget(name);
            assertThat(asInput).isEqualTo(asTarget);
            assertThat(asInput).isNotEmpty();
            // 재검색 시뮬레이션: 대상 이름을 정규화한 값이 입력 needle을 포함해야 한다(자기 매칭)
            assertThat(asTarget).contains(asInput);
        }
    }

    @Test
    @DisplayName("특수문자 제거로 괄호 안팎이 이어붙는다: 이화주(술샘) → 이화주술샘")
    void specialCharsAreRemovedJoiningNeighbors() {
        assertThat(SearchKeyword.normalizeForMatch("이화주(술샘)")).isEqualTo("이화주술샘");
        assertThat(SearchKeyword.normalizeTarget("티나(TINA)")).isEqualTo("티나tina");
    }

    @Test
    @DisplayName("빈 값·공백만·허용문자 밖만 → 빈 문자열(검색 실행 없음)")
    void blankOrSymbolOnlyNormalizesToEmpty() {
        assertThat(SearchKeyword.normalizeForMatch(null)).isEmpty();
        assertThat(SearchKeyword.normalizeForMatch("")).isEmpty();
        assertThat(SearchKeyword.normalizeForMatch("   ")).isEmpty();
        assertThat(SearchKeyword.normalizeForMatch("()%,·")).isEmpty();
    }

    @Test
    @DisplayName("트림 후 20자 초과 → 400, 앞뒤 공백 포함 20자 → 통과")
    void lengthLimitAppliesAfterTrim() {
        assertThatThrownBy(() -> SearchKeyword.normalizeForMatch("가".repeat(21)))
                .isInstanceOf(InvalidQueryParameterException.class);
        assertThat(SearchKeyword.normalizeForMatch(" " + "가".repeat(20) + " "))
                .isEqualTo("가".repeat(20));
    }
}
