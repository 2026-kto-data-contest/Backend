package com.jeontongjuro.backend.geo;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 재현 가능한 주소 정규화 규칙 2건(코드 — 시드 아님, #28 3-4). 순수 함수라 자연히 결정적이다.
 * <ul>
 *   <li><b>쉼표 이후 절단</b>: {@code 경기 가평군 하면 대보간선로 26, 29} → {@code ... 26} (BRW-039)</li>
 *   <li><b>부번지 폴백</b>: {@code 경북 영천시 고경면 고도리 494-3} → {@code ... 494} (BRW-002)</li>
 * </ul>
 * 각 규칙을 독립 시도하고, 두 규칙을 모두 적용한 후보까지 순서대로 낸다(모두 실패해야 fail).
 */
public final class AddressNormalizer {

    /** 끝부분의 부번지 {@code -\d+}(예: 494-3의 -3)를 제거. 앞의 본번지는 남긴다. */
    private static final Pattern TRAILING_SUB_LOT = Pattern.compile("-\\d+\\s*$");

    private AddressNormalizer() {
    }

    /**
     * raw와 다른 정규화 후보들을 적용 순서대로(중복·raw 동일·공백 제외) 반환한다.
     * 순서: ① 쉼표 절단, ② 부번지 폴백, ③ 쉼표 절단 + 부번지 폴백.
     */
    public static List<String> candidates(String raw) {
        if (raw == null) {
            return List.of();
        }
        String commaCut = cutAfterComma(raw);
        List<String> ordered = new ArrayList<>();
        ordered.add(commaCut);
        ordered.add(dropTrailingSubLot(raw));
        ordered.add(dropTrailingSubLot(commaCut));

        Set<String> out = new LinkedHashSet<>();
        for (String c : ordered) {
            if (c != null && !c.isBlank() && !c.equals(raw)) {
                out.add(c);
            }
        }
        return new ArrayList<>(out);
    }

    private static String cutAfterComma(String s) {
        int idx = s.indexOf(',');
        return idx < 0 ? s : s.substring(0, idx).trim();
    }

    private static String dropTrailingSubLot(String s) {
        return TRAILING_SUB_LOT.matcher(s).replaceFirst("").trim();
    }
}
