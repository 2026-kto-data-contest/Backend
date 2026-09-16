package com.jeontongjuro.backend.recommendation;

import com.jeontongjuro.backend.brewery.query.BreweryListItemResponse;
import com.jeontongjuro.backend.global.web.PageResponse;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import java.util.Map;
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
    private static final Map<String, CourseTitle> COURSE_TITLES = loadCourseTitles();

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

    /** 홈에서 이미 조회한 추천 순서를 재사용해 전체 양조장 조회를 반복하지 않는다. */
    public List<RecommendedCourseCardResponse> homePreviewFrom(List<BreweryListItemResponse> breweries) {
        return breweries.stream().limit(HOME_SIZE).map(this::toCard).toList();
    }

    private RecommendedCourseCardResponse toCard(BreweryListItemResponse brewery) {
        String imageUrl = localAssetUrl(brewery.breweryId());
        if (imageUrl == null && brewery.mainImage() != null) {
            imageUrl = brewery.mainImage().url();
        }
        CourseTitle courseTitle = COURSE_TITLES.get(brewery.breweryId());
        return new RecommendedCourseCardResponse(
                brewery.breweryId(),
                imageUrl,
                regionLabel(brewery),
                courseTitle == null ? brewery.businessName() + " 코스" : courseTitle.title());
    }

    private String localAssetUrl(String breweryId) {
        String assetPath = "/recommended-courses/" + breweryId + ".png";
        return getClass().getResource("/static" + assetPath) == null ? null : assetPath;
    }

    private static Map<String, CourseTitle> loadCourseTitles() {
        try (var stream = RecommendedCourseListService.class.getResourceAsStream("/recommended_course_titles.json")) {
            if (stream == null) {
                throw new IllegalStateException("추천 코스 타이틀 리소스를 찾을 수 없습니다.");
            }
            return new ObjectMapper().readValue(stream, new TypeReference<>() {});
        } catch (IOException e) {
            throw new IllegalStateException("추천 코스 타이틀 리소스를 읽을 수 없습니다.", e);
        }
    }

    private record CourseTitle(String brewery, String title) {
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
