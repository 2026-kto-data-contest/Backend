package com.jeontongjuro.backend.course;

import com.jeontongjuro.backend.tour.TourContent;
import java.util.Locale;
import java.util.Map;

/** 관광공사 구·신 분류를 추천 코스의 사용자용 분류로 정규화한다. */
final class CoursePlaceClassifier {

    private static final String CAFE_OLD_CODE = "A05020900";
    private static final Map<String, String> RESTAURANT_CATEGORIES = Map.of(
            "A05020100", "한식",
            "A05020200", "양식",
            "A05020300", "일식",
            "A05020400", "중식",
            "A05020700", "해물");
    private static final Map<String, String> ACCOMMODATION_CATEGORIES = Map.of(
            "B02010100", "관광호텔",
            "B02010500", "콘도·리조트",
            "B02010600", "유스호스텔",
            "B02010700", "펜션",
            "B02011000", "게스트하우스",
            "B02011600", "한옥");

    private CoursePlaceClassifier() {
    }

    static CourseStopType typeOf(TourContent content) {
        if ("39".equals(content.getContentTypeId())) {
            return isCafe(content) ? CourseStopType.CAFE : CourseStopType.RESTAURANT;
        }
        return switch (value(content.getContentTypeId())) {
            case "12", "15", "28" -> CourseStopType.TOURIST_ATTRACTION;
            case "14" -> CourseStopType.CULTURAL_FACILITY;
            case "32" -> CourseStopType.ACCOMMODATION;
            case "38" -> CourseStopType.MARKET;
            default -> CourseStopType.ETC;
        };
    }

    static String subcategoryOf(TourContent content, CourseStopType type) {
        return switch (type) {
            case RESTAURANT -> restaurantCategory(content);
            case CAFE -> cafeCategory(content);
            case ACCOMMODATION -> accommodationCategory(content);
            case CULTURAL_FACILITY -> culturalCategory(content);
            case MARKET -> "시장";
            case TOURIST_ATTRACTION -> touristCategory(content);
            default -> null;
        };
    }

    private static boolean isCafe(TourContent content) {
        if (CAFE_OLD_CODE.equalsIgnoreCase(value(content.getCat3()))) return true;
        String newClassification = joined(content.getLclsSystm1(), content.getLclsSystm2(), content.getLclsSystm3());
        if (newClassification.contains("카페") || newClassification.contains("커피")
                || newClassification.contains("베이커리") || newClassification.contains("디저트")
                || newClassification.contains("찻집") || value(content.getLclsSystm3()).startsWith("FD03")) {
            return true;
        }
        String title = normalized(content.getTitle());
        return title.contains("카페") || title.contains("cafe") || title.contains("커피")
                || title.contains("coffee") || title.contains("베이커리") || title.contains("bakery")
                || title.contains("디저트") || title.contains("찻집") || title.contains("티룸");
    }

    private static String restaurantCategory(TourContent content) {
        String labels = joined(content.getLclsSystm3(), content.getLclsSystm2());
        if (labels.contains("한식")) return "한식";
        if (labels.contains("일식") || labels.contains("횟집")) return "일식";
        if (labels.contains("중식")) return "중식";
        if (labels.contains("양식") || labels.contains("서양식")) return "양식";
        if (labels.contains("해물")) return "해물";
        return RESTAURANT_CATEGORIES.get(value(content.getCat3()).toUpperCase(Locale.ROOT));
    }

    private static String cafeCategory(TourContent content) {
        String text = joined(content.getTitle(), content.getLclsSystm2(), content.getLclsSystm3());
        if (text.contains("베이커리") || text.contains("bakery")) return "베이커리";
        if (text.contains("디저트")) return "디저트";
        if (text.contains("찻집") || text.contains("전통차")) return "전통찻집";
        return "카페";
    }

    private static String accommodationCategory(TourContent content) {
        String text = joined(content.getTitle(), content.getLclsSystm2(), content.getLclsSystm3());
        if (text.contains("한옥")) return "한옥";
        if (text.contains("게스트하우스")) return "게스트하우스";
        if (text.contains("펜션")) return "펜션";
        if (text.contains("리조트") || text.contains("콘도")) return "리조트";
        if (text.contains("호텔")) return "호텔";
        return ACCOMMODATION_CATEGORIES.get(value(content.getCat3()).toUpperCase(Locale.ROOT));
    }

    private static String culturalCategory(TourContent content) {
        String text = joined(content.getTitle(), content.getLclsSystm2(), content.getLclsSystm3());
        if (text.contains("미술관")) return "미술관";
        if (text.contains("박물관")) return "박물관";
        return "문화시설";
    }

    private static String touristCategory(TourContent content) {
        String text = joined(content.getTitle(), content.getLclsSystm2(), content.getLclsSystm3());
        if (text.contains("미술관")) return "미술관";
        if (text.contains("박물관")) return "박물관";
        if (text.contains("공원") || text.contains("산") || text.contains("계곡") || text.contains("해수욕장")) {
            return "자연관광";
        }
        return "관광지";
    }

    private static String joined(String... values) {
        return java.util.Arrays.stream(values).filter(v -> v != null && !v.isBlank())
                .map(CoursePlaceClassifier::normalized).reduce("", (left, right) -> left + " " + right);
    }

    private static String normalized(String value) {
        return value(value).strip().toLowerCase(Locale.ROOT);
    }

    private static String value(String value) {
        return value == null ? "" : value;
    }
}
