package com.jeontongjuro.backend.liquortype;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 키워드 사전(JSON) 계약 단위 테스트(DB 불필요). ★추론 규칙이 JSON에 있고 코드는 해석만 한다는 전제를 지킨다.
 * 분포를 단정하지 않고, 사전이 지시된 배제/설정 규칙을 따르는지만 검증한다.
 */
class LiquorKeywordDictionaryTest {

    private final LiquorKeywordDictionary dictionary = new LiquorKeywordDictionary(new ObjectMapper());

    @Test
    @DisplayName("접미어 배제: 양조/주조/도가는 키워드가 아니다(양조장 이름 접미어일 뿐)")
    void breweryNameSuffixesAreNotKeywords() {
        List<String> words = dictionary.keywords().stream().map(LiquorKeyword::keyword).toList();
        assertThat(words).doesNotContain("양조", "주조", "도가", "양조장");
    }

    @Test
    @DisplayName("향미어 분리: 유자·복분자 등은 주종 키워드가 아니라 향미어 목록에만 있다")
    void flavorWordsAreNotLiquorKeywords() {
        List<String> keywordWords = dictionary.keywords().stream().map(LiquorKeyword::keyword).toList();
        assertThat(dictionary.flavorWords()).contains("유자", "복분자", "오미자", "매실", "사과", "머루");
        assertThat(keywordWords).doesNotContainAnyElementsOf(dictionary.flavorWords());
    }

    @Test
    @DisplayName("'진'(gin)은 wordBoundary=true·scope=product_name 단독(오탐 억제)")
    void ginKeywordIsWordBoundaryAndNameScoped() {
        LiquorKeyword gin = dictionary.keywords().stream()
                .filter(k -> k.keyword().equals("진")).findFirst().orElseThrow();
        assertThat(gin.wordBoundary()).isTrue();
        assertThat(gin.liquorType()).isEqualTo(LiquorType.증류주);
        assertThat(gin.scope()).containsExactly(LiquorKeyword.SCOPE_PRODUCT_NAME);
    }

    @Test
    @DisplayName("모든 키워드의 scope는 description을 쓰지 않는다(음식 페어링 오탐 방지)")
    void noKeywordUsesDescriptionScope() {
        assertThat(dictionary.keywords()).allSatisfy(k ->
                assertThat(k.scope()).doesNotContain("description"));
    }

    @Test
    @DisplayName("모호어 '곡주'는 사전에서 제외(소곡주=약주만 포함)")
    void ambiguousGrainWordExcluded() {
        List<String> words = dictionary.keywords().stream().map(LiquorKeyword::keyword).toList();
        assertThat(words).doesNotContain("곡주");
        assertThat(words).contains("소곡주");
    }
}
