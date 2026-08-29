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

    private static final String CAFE_CAT3 = "A05020900";

    public static CourseStopType from(TourContent content) {
        if ("39".equals(content.getContentTypeId())) {
            return CAFE_CAT3.equals(content.getCat3()) ? CAFE : RESTAURANT;
        }
        return switch (content.getContentTypeId()) {
            case "12", "15", "28" -> TOURIST_ATTRACTION;
            case "14" -> CULTURAL_FACILITY;
            case "32" -> ACCOMMODATION;
            case "38" -> MARKET;
            default -> ETC;
        };
    }
}
