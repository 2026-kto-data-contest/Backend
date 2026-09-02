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
            "A05020500", "아시아",
            "A05020600", "기타",
            "A05020700", "기타");
    private static final Map<String, String> ACCOMMODATION_CATEGORIES = Map.ofEntries(
            Map.entry("B02010100", "관광호텔"),
            Map.entry("B02010200", "가족호텔"),
            Map.entry("B02010300", "콘도·리조트"),
            Map.entry("B02010400", "유스호스텔"),
            Map.entry("B02010500", "콘도·리조트"),
            Map.entry("B02010600", "유스호스텔"),
            Map.entry("B02010700", "펜션"),
            Map.entry("B02010800", "여관"),
            Map.entry("B02010900", "모텔"),
            Map.entry("B02011000", "민박"),
            Map.entry("B02011100", "게스트하우스"),
            Map.entry("B02011200", "홈스테이"),
            Map.entry("B02011300", "레지던스"),
            Map.entry("B02011400", "의료관광호텔"),
            Map.entry("B02011500", "소형호텔"),
            Map.entry("B02011600", "한옥"));

    private CoursePlaceClassifier() {
    }

    static CourseStopType typeOf(TourContent content) {
        return typeOf(content, null);
    }

    static CourseStopType typeOf(TourContent content, String externalCategory) {
        if ("39".equals(content.getContentTypeId())) {
            return isCafe(content, externalCategory) ? CourseStopType.CAFE : CourseStopType.RESTAURANT;
        }
        return switch (value(content.getContentTypeId())) {
            case "12", "15", "28" -> CourseStopType.TOURIST_ATTRACTION;
            case "14" -> culturalCategory(content) == null
                    ? CourseStopType.ETC : CourseStopType.CULTURAL_FACILITY;
            case "32" -> CourseStopType.ACCOMMODATION;
            case "38" -> isTraditionalMarket(content) ? CourseStopType.MARKET : CourseStopType.ETC;
            default -> CourseStopType.ETC;
        };
    }

    static String subcategoryOf(TourContent content, CourseStopType type) {
        return switch (type) {
            case RESTAURANT -> restaurantCategory(content);
            case CAFE -> cafeCategory(content);
            case ACCOMMODATION -> accommodationCategory(content);
            case CULTURAL_FACILITY -> culturalCategory(content);
            case MARKET -> "전통시장";
            case TOURIST_ATTRACTION -> touristCategory(content);
            default -> null;
        };
    }

    private static boolean isCafe(TourContent content, String externalCategory) {
        String external = normalized(externalCategory);
        if (!external.isBlank()) return containsCafeKeyword(external);
        String newClassification = joined(content.getLclsSystm1(), content.getLclsSystm2(), content.getLclsSystm3());
        if (!newClassification.isBlank()) {
            if (containsCafeKeyword(newClassification)
                    || value(content.getLclsSystm3()).toUpperCase(Locale.ROOT).startsWith("FD03")) return true;
            return containsCafeKeyword(normalized(content.getTitle()));
        }
        if (CAFE_OLD_CODE.equalsIgnoreCase(value(content.getCat3()))) return true;
        String title = normalized(content.getTitle());
        return containsCafeKeyword(title);
    }

    private static boolean containsCafeKeyword(String text) {
        return text.contains("카페") || text.contains("cafe") || text.contains("커피")
                || text.contains("coffee") || text.contains("베이커리") || text.contains("bakery")
                || text.contains("디저트") || text.contains("찻집") || text.contains("티룸");
    }

    private static boolean isTraditionalMarket(TourContent content) {
        String oldCode = value(content.getCat3()).toUpperCase(Locale.ROOT);
        String newCode = value(content.getLclsSystm3()).toUpperCase(Locale.ROOT);
        return oldCode.equals("A04010100") || oldCode.equals("A04010200")
                || newCode.equals("SH060100") || newCode.equals("SH060200");
    }

    private static String restaurantCategory(TourContent content) {
        String newCode = value(content.getLclsSystm3()).toUpperCase(Locale.ROOT);
        if (newCode.startsWith("FD0101") || newCode.startsWith("FD0102")) return "한식";
        if (newCode.startsWith("FD0201")) return "중식";
        if (newCode.startsWith("FD0202")) return "일식";
        if (newCode.startsWith("FD0203")) return "양식";
        if (newCode.startsWith("FD0204") || newCode.startsWith("FD0205")) return "아시아";

        String labels = joined(content.getTitle(), content.getLclsSystm3(), content.getLclsSystm2());
        if (labels.contains("한식")) return "한식";
        if (labels.contains("일식") || labels.contains("횟집")) return "일식";
        if (labels.contains("중식")) return "중식";
        if (labels.contains("양식") || labels.contains("서양식")) return "양식";
        if (labels.contains("아시아") || labels.contains("동남아") || labels.contains("인도")) return "아시아";
        if (labels.contains("분식") || labels.contains("떡볶이") || labels.contains("김밥")) return "분식";
        return RESTAURANT_CATEGORIES.getOrDefault(
                value(content.getCat3()).toUpperCase(Locale.ROOT), "기타");
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
        if (text.contains("민박") || text.contains("홈스테이")) return "민박";
        if (text.contains("리조트") || text.contains("콘도") || text.contains("모텔")
                || text.contains("여관") || text.contains("레지던스") || text.contains("호스텔")) return "기타";
        if (text.contains("호텔")) return "호텔";
        String raw = ACCOMMODATION_CATEGORIES.get(value(content.getCat3()).toUpperCase(Locale.ROOT));
        if (raw == null) return "기타";
        if (raw.contains("호텔")) return "호텔";
        if (raw.equals("펜션") || raw.equals("한옥") || raw.equals("게스트하우스") || raw.equals("민박")) {
            return raw;
        }
        if (raw.equals("홈스테이")) return "민박";
        return "기타";
    }

    private static String culturalCategory(TourContent content) {
        String text = joined(content.getTitle(), content.getLclsSystm2(), content.getLclsSystm3());
        if (text.contains("미술관")) return "미술관";
        if (text.contains("박물관")) return "박물관";
        if (text.contains("유적") || text.contains("역사") || text.contains("기념관")) return "역사·유적";
        String oldCode = value(content.getCat3()).toUpperCase(Locale.ROOT);
        return switch (oldCode) {
            case "A02060100" -> "박물관";
            case "A02060200" -> "역사·유적";
            case "A02060500" -> "미술관";
            default -> null;
        };
    }

    private static String touristCategory(TourContent content) {
        String text = joined(content.getTitle(), content.getLclsSystm2(), content.getLclsSystm3());
        if (text.contains("미술관")) return "미술관";
        if (text.contains("박물관")) return "박물관";
        if (text.contains("공원")) return "공원";
        if (text.contains("유적") || text.contains("역사") || text.contains("고택")
                || text.contains("사찰") || text.contains("성곽")) return "역사·유적";
        if ("15".equals(content.getContentTypeId()) || "28".equals(content.getContentTypeId())) return "체험";
        String newCode = value(content.getLclsSystm1()).toUpperCase(Locale.ROOT);
        String oldCode = value(content.getCat3()).toUpperCase(Locale.ROOT);
        if (newCode.equals("NA") || oldCode.startsWith("A01")) return "자연관광";
        if (newCode.equals("EX") || oldCode.startsWith("A0203") || oldCode.startsWith("A0204")) return "체험";
        if (newCode.equals("HS") || oldCode.startsWith("A0201") || oldCode.startsWith("A0205")) {
            return "역사·유적";
        }
        return "체험";
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
