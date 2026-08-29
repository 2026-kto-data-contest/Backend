package com.jeontongjuro.backend.course;

import java.util.List;

/** 특정 양조장을 기준으로 요청 시점에 동적으로 구성한 추천 코스. */
public record RecommendedCourseResponse(
        String courseId,
        String title,
        String regionLabel,
        String centerBreweryId,
        List<CourseStopResponse> stops
) {
    public RecommendedCourseResponse {
        stops = stops == null ? List.of() : List.copyOf(stops);
    }
}
