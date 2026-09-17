package com.jeontongjuro.backend.search.recommended;

import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeontongjuro.backend.brewery.query.BreweryQueryService;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import java.util.Map;

class RecommendedSearchKeywordServiceTest {

    private final BreweryQueryService breweryQueryService = mock(BreweryQueryService.class);
    private final RecommendedSearchKeywordService service =
            new RecommendedSearchKeywordService(new ObjectMapper(), breweryQueryService);

    @Test
    void loadsServerManagedKeywordsInConfiguredOrder() {
        when(breweryQueryService.countByAccuracy(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(Map.ofEntries(
                        Map.entry("막걸리", 20L), Map.entry("와이너리", 5L), Map.entry("국순당", 1L),
                        Map.entry("양조", 9L), Map.entry("와인", 16L), Map.entry("로제", 10L),
                        Map.entry("탁주", 8L), Map.entry("소주", 7L), Map.entry("화이트", 7L),
                        Map.entry("드라이", 6L), Map.entry("레드", 6L), Map.entry("스위트", 6L),
                        Map.entry("생막걸리", 6L), Map.entry("머루", 5L), Map.entry("주조", 5L),
                        Map.entry("양조장", 4L), Map.entry("복순도가", 1L)));

        List<RecommendedSearchKeywordResponse> keywords = service.list();

        assertThat(keywords).isNotEmpty();
        assertThat(keywords.get(0)).isEqualTo(new RecommendedSearchKeywordResponse("막걸리", 20));
        assertThat(keywords).extracting(RecommendedSearchKeywordResponse::keyword)
                .contains("양조", "와이너리");
        assertThat(keywords).allSatisfy(keyword -> assertThat(keyword.resultCount()).isGreaterThanOrEqualTo(5));
        assertThat(keywords).extracting(RecommendedSearchKeywordResponse::keyword)
                .doesNotHaveDuplicates();
        assertThat(keywords).noneMatch(keyword -> keyword.keyword().equals("국순당"));
    }
}
