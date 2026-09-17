package com.jeontongjuro.backend.search.recommended;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import org.junit.jupiter.api.Test;

class RecommendedSearchKeywordServiceTest {

    private final RecommendedSearchKeywordService service = new RecommendedSearchKeywordService(new ObjectMapper());

    @Test
    void loadsServerManagedKeywordsInConfiguredOrder() {
        List<RecommendedSearchKeywordResponse> keywords = service.list();

        assertThat(keywords).hasSize(7);
        assertThat(keywords).extracting(RecommendedSearchKeywordResponse::keyword)
                .containsExactly("막걸리", "복순도가", "와인", "소주", "국순당", "양조", "와이너리");
    }
}
