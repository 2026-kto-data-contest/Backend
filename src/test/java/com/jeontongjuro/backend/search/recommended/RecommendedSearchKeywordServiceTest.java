package com.jeontongjuro.backend.search.recommended;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;

class RecommendedSearchKeywordServiceTest {

    private final RecommendedSearchKeywordService service =
            new RecommendedSearchKeywordService(new ObjectMapper());

    @Test
    void loadsServerManagedKeywordsInConfiguredOrder() {
        List<RecommendedSearchKeywordResponse> keywords = service.list();

        assertThat(keywords).isNotEmpty();
        assertThat(keywords.get(0)).isEqualTo(new RecommendedSearchKeywordResponse("막걸리", 20));
        assertThat(keywords).extracting(RecommendedSearchKeywordResponse::keyword)
                .contains("양조", "와이너리");
        assertThat(keywords).allSatisfy(keyword -> assertThat(keyword.resultCount()).isGreaterThanOrEqualTo(5));
        assertThat(keywords).extracting(RecommendedSearchKeywordResponse::keyword)
                .doesNotHaveDuplicates();
    }
}
