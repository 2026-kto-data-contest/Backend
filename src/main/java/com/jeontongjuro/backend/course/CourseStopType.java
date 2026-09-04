package com.jeontongjuro.backend.course;

import com.jeontongjuro.backend.tour.TourContent;

/** 추천 코스가 외부에 노출하는 장소 유형. */
public enum CourseStopType {
    BREWERY,
    RESTAURANT,
    TOURIST_ATTRACTION,
    CULTURAL_FACILITY,
    MARKET,
    CAFE,
    ACCOMMODATION,
    ETC;

    public static CourseStopType from(TourContent content) {
        return CoursePlaceClassifier.typeOf(content);
    }

    /** 콘텐츠의 장소 유형과 일치하는 사용자용 세부분류를 공통 분류 규칙으로 반환한다. */
    public static String subcategoryOf(TourContent content) {
        return CoursePlaceClassifier.subcategoryOf(content, from(content));
    }
}
