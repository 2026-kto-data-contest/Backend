package com.jeontongjuro.backend.recommendation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import com.jeontongjuro.backend.brewery.VisitState;
import com.jeontongjuro.backend.brewery.query.BreweryListItemResponse;
import com.jeontongjuro.backend.brewery.query.MainImageResponse;
import com.jeontongjuro.backend.global.web.PageResponse;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RecommendedCourseListServiceTest {

    @Mock
    private RecommendedBreweryService recommendedBreweryService;

    private RecommendedCourseListService service;

    @BeforeEach
    void setUp() {
        service = new RecommendedCourseListService(recommendedBreweryService);
    }

    @Test
    void mapsBreweryToCourseCardAndKeepsPageMetadata() {
        BreweryListItemResponse brewery = brewery("BRW-001", "갈기산 양조장", "충북", "영동",
                new MainImageResponse("https://example.com/image.jpg", "Type1", true));
        given(recommendedBreweryService.recommend(null, 0, 20))
                .willReturn(PageResponse.of(List.of(brewery), 0, 20, 1));

        PageResponse<RecommendedCourseCardResponse> response = service.list(null, 0, 20);

        assertThat(response.totalElements()).isEqualTo(1);
        assertThat(response.content()).containsExactly(new RecommendedCourseCardResponse(
                "BRW-001", "https://example.com/image.jpg", "충북 영동", "갈기산 양조장 코스"));
    }

    @Test
    void invalidPagingIsClampedToCourseDefaults() {
        given(recommendedBreweryService.recommend(1L, 0, 20))
                .willReturn(PageResponse.of(List.of(), 0, 20, 0));

        PageResponse<RecommendedCourseCardResponse> response = service.list(1L, -1, 0);

        assertThat(response.page()).isZero();
        assertThat(response.size()).isEqualTo(20);
    }

    @Test
    void homePreviewUsesFiveItems() {
        given(recommendedBreweryService.recommend(1L, 0, 5))
                .willReturn(PageResponse.of(List.of(), 0, 5, 0));

        assertThat(service.homePreview(1L)).isEmpty();
    }

    @Test
    void missingImageAndDetailedRegionRemainNullable() {
        BreweryListItemResponse brewery = brewery("BRW-002", "무명 양조장", null, null, null);
        given(recommendedBreweryService.recommend(null, 0, 20))
                .willReturn(PageResponse.of(List.of(brewery), 0, 20, 1));

        RecommendedCourseCardResponse card = service.list(null, 0, 20).content().get(0);

        assertThat(card.imageUrl()).isNull();
        assertThat(card.regionLabel()).isEqualTo("충청");
    }

    private BreweryListItemResponse brewery(String id, String name, String sido, String sigungu,
                                             MainImageResponse image) {
        return new BreweryListItemResponse(id, name, sido, "충청", VisitState.Y, VisitState.N,
                List.of(), null, null, List.of(), image, sigungu, List.of(), null);
    }
}
