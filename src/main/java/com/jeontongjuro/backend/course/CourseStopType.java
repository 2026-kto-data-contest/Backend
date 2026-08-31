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
}
