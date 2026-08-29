package com.jeontongjuro.backend.recommendation;

import com.jeontongjuro.backend.brewery.query.BreweryListItemResponse;
import com.jeontongjuro.backend.global.web.PageResponse;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 양조장 추천 순서를 코스 카드로 투영해 전체 목록과 홈 미리보기에 동일하게 제공한다. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class RecommendedCourseListService {

    static final int DEFAULT_SIZE = 20;
    static final int HOME_SIZE = 5;
    static final int MAX_SIZE = 100;

    private final RecommendedBreweryService recommendedBreweryService;

    public PageResponse<RecommendedCourseCardResponse> list(Long memberId, int page, int size) {
        int clampedPage = Math.max(0, page);
        int clampedSize = size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        PageResponse<BreweryListItemResponse> breweries =
                recommendedBreweryService.recommend(memberId, clampedPage, clampedSize);
        List<RecommendedCourseCardResponse> cards = breweries.content().stream()
                .map(this::toCard)
                .toList();
        return new PageResponse<>(cards, breweries.page(), breweries.size(),
                breweries.totalElements(), breweries.totalPages());
    }

    public List<RecommendedCourseCardResponse> homePreview(Long memberId) {
        return list(memberId, 0, HOME_SIZE).content();
    }

    private RecommendedCourseCardResponse toCard(BreweryListItemResponse brewery) {
        String imageUrl = brewery.mainImage() == null ? null : brewery.mainImage().url();
        return new RecommendedCourseCardResponse(
                brewery.breweryId(),
                imageUrl,
                regionLabel(brewery),
                brewery.businessName() + " 코스");
    }

    private String regionLabel(BreweryListItemResponse brewery) {
        if (hasText(brewery.sido()) && hasText(brewery.sigungu())) {
            return brewery.sido().strip() + " " + brewery.sigungu().strip();
        }
        if (hasText(brewery.sido())) {
            return brewery.sido().strip();
        }
        return hasText(brewery.region()) ? brewery.region().strip() : null;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }
}
