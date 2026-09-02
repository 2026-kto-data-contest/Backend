package com.jeontongjuro.backend.course;

import com.jeontongjuro.backend.tour.TourContent;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/** 술 설명의 안주 키워드와 관광공사 음식점 세부 분류를 연결하는 결정론적 규칙. */
final class FoodPairingMatcher {

    private static final Map<String, PairingRule> RULES = rules();

    private FoodPairingMatcher() {
    }

    private static final Pattern JEON_FOOD_CONTEXT = Pattern.compile(
            "(?:파전|감자전|해물전|김치전|부추전|녹두전|빈대떡|\\b전\\s*(?:요리|안주|한접시|집))");
    private static final Pattern SASHIMI_FOOD_CONTEXT = Pattern.compile(
            "(?:생선회|모둠회|모듬회|회무침|회덮밥|횟감|횟집|(?:^|[\\s,·/])회(?:$|[\\s,·/]))");

    static Optional<String> pairingComment(List<String> descriptions, TourContent restaurant, String breweryName) {
        return pairingComment(descriptions, restaurant, breweryName, null);
    }

    static Optional<String> pairingComment(List<String> descriptions, TourContent restaurant,
                                           String breweryName, String externalCategory) {
        String source = String.join(" ", descriptions).toLowerCase(Locale.ROOT);
        String restaurantText = String.join(" ", nonNull(
                restaurant.getTitle(), restaurant.getCat1(), restaurant.getCat2(), restaurant.getCat3(),
                restaurant.getLclsSystm1(), restaurant.getLclsSystm2(), restaurant.getLclsSystm3(),
                externalCategory))
                .toLowerCase(Locale.ROOT);
        for (Map.Entry<String, PairingRule> entry : RULES.entrySet()) {
            if (!matchesSource(entry.getKey(), source)) continue;
            PairingRule rule = entry.getValue();
            if (rule.restaurantTokens().stream().anyMatch(restaurantText::contains)) {
                return Optional.of(breweryName + "의 " + entry.getKey()
                        + " 페어링과 어울리는 " + rule.label() + " 음식점");
            }
        }
        return Optional.empty();
    }

    private static Map<String, PairingRule> rules() {
        Map<String, PairingRule> rules = new LinkedHashMap<>();
        rules.put("파전", new PairingRule("한식", List.of("한식", "a05020100", "전", "빈대떡")));
        rules.put("전", new PairingRule("한식", List.of("한식", "a05020100", "전", "빈대떡")));
        rules.put("삼합", new PairingRule("한식", List.of("한식", "a05020100", "삼합", "보쌈")));
        rules.put("보쌈", new PairingRule("한식", List.of("한식", "a05020100", "보쌈")));
        rules.put("한우", new PairingRule("한식", List.of("한식", "a05020100", "한우", "고기")));
        rules.put("고기", new PairingRule("한식", List.of("한식", "a05020100", "고기", "갈비", "한우")));
        rules.put("회", new PairingRule("회", List.of("일식", "a05020300", "회", "횟집", "생선")));
        rules.put("치즈", new PairingRule("양식", List.of("서양식", "a05020200", "치즈", "파스타")));
        return java.util.Collections.unmodifiableMap(rules);
    }

    private static boolean matchesSource(String keyword, String source) {
        if ("전".equals(keyword)) return JEON_FOOD_CONTEXT.matcher(source).find();
        if ("회".equals(keyword)) return SASHIMI_FOOD_CONTEXT.matcher(source).find();
        return source.contains(keyword);
    }

    private static List<String> nonNull(String... values) {
        return java.util.Arrays.stream(values).filter(v -> v != null && !v.isBlank()).toList();
    }

    private record PairingRule(String label, List<String> restaurantTokens) {
    }
}
