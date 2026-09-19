package com.jeontongjuro.backend.product.query;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * {@code product_raw.characteristics}(전통주정보 '특징' 필드) 원문에서 맛 태그 8종을 부분일치로 매칭한다.
 * <p>
 * 키워드셋 출처: 정찰 세션 산출값(데이터 검수 후 확정 전까지 이 목록을 임의로 늘리거나 줄이지 않는다).
 * 원문에 포함된 부정 표현과 음식 설명은 술의 맛을 뜻하지 않으므로 해당 키워드의 오탐으로 취급해 배제한다.
 * <p>
 * 매칭 결과는 8종 선언 순서({@link SensoryTag}) 고정으로 반환한다. '+N' 절단은 프론트 몫이라 여기서
 * 개수를 자르지 않는다 — 매칭된 태그를 전부 반환한다.
 */
public final class SensoryTagMatcher {

    private static final Map<SensoryTag, List<String>> KEYWORDS = buildKeywords();

    private SensoryTagMatcher() {
    }

    /** characteristics가 null·blank이거나 매칭 태그가 없으면 빈 배열. */
    public static List<SensoryTag> match(String characteristics) {
        if (characteristics == null || characteristics.isBlank()) {
            return List.of();
        }
        List<SensoryTag> matched = new ArrayList<>();
        for (SensoryTag tag : SensoryTag.values()) {
            for (String keyword : KEYWORDS.get(tag)) {
                if (containsValidKeyword(tag, characteristics, keyword)) {
                    matched.add(tag);
                    break;
                }
            }
        }
        return matched;
    }

    private static boolean containsValidKeyword(SensoryTag tag, String source, String keyword) {
        int fromIndex = 0;
        while (fromIndex < source.length()) {
            int keywordIndex = source.indexOf(keyword, fromIndex);
            if (keywordIndex < 0) {
                return false;
            }
            if (!isNegated(source, keywordIndex, keyword.length())
                    && !isFoodDescription(tag, source, keywordIndex, keyword.length())) {
                return true;
            }
            fromIndex = keywordIndex + keyword.length();
        }
        return false;
    }

    private static boolean isNegated(String source, int keywordIndex, int keywordLength) {
        int contextStart = keywordIndex;
        int contextEnd = Math.min(source.length(), keywordIndex + keywordLength + 24);
        String context = removeWhitespace(source.substring(contextStart, contextEnd));
        return context.contains("적지만")
                || context.contains("적어")
                || context.contains("적고")
                || context.contains("적은")
                || context.contains("적지않")
                || context.contains("거의")
                || context.contains("느껴지지않")
                || context.contains("없다")
                || context.contains("없으며")
                || context.contains("없고")
                || context.contains("않다")
                || context.contains("않으며")
                || context.contains("아니다")
                || context.contains("아닌");
    }

    private static boolean isFoodDescription(SensoryTag tag, String source, int keywordIndex, int keywordLength) {
        if (tag != SensoryTag.부드러움) {
            return false;
        }
        int contextEnd = Math.min(source.length(), keywordIndex + keywordLength + 32);
        String followingText = removeWhitespace(source.substring(keywordIndex + keywordLength, contextEnd));
        return followingText.contains("생선회")
                || followingText.contains("어패류")
                || followingText.contains("해산물")
                || followingText.contains("음식")
                || followingText.contains("요리")
                || followingText.contains("안주")
                || followingText.contains("고기")
                || followingText.contains("갈비")
                || followingText.contains("수육")
                || followingText.contains("굴찜")
                || followingText.contains("조개찜")
                || followingText.contains("대게찜")
                || followingText.contains("김치전")
                || followingText.contains("한식");
    }

    private static String removeWhitespace(String value) {
        return value.replaceAll("\\s+", "");
    }

    private static Map<SensoryTag, List<String>> buildKeywords() {
        Map<SensoryTag, List<String>> m = new LinkedHashMap<>();
        m.put(SensoryTag.상큼함, List.of("상큼"));
        m.put(SensoryTag.달콤함, List.of("달콤", "단맛", "달달"));
        m.put(SensoryTag.드라이, List.of("드라이"));
        m.put(SensoryTag.산미, List.of("산미", "새콤"));
        // ㅂ-irregular 활용형 전부 포함(부드럽다→부드러운/부드러움/부드러워/부드러웠 — 부분문자열 불일치 방지).
        m.put(SensoryTag.부드러움, List.of("부드럽", "부드러운", "부드러움", "부드러워", "부드러웠"));
        m.put(SensoryTag.묵직함, List.of("묵직"));
        m.put(SensoryTag.깔끔함, List.of("깔끔"));
        m.put(SensoryTag.향긋함, List.of("향긋"));
        return m;
    }
}
