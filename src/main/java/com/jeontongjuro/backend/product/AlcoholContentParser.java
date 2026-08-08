package com.jeontongjuro.backend.product;

import java.math.BigDecimal;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * product_raw.alcohol_content(원문 TEXT) → 도수 min/max 파싱(#35). Spring/DB 무의존 순수 함수.
 * <p>
 * 전역 매치 정규식 {@code ([0-9]+(?:\.[0-9]+)?)}으로 모든 숫자값을 뽑아 최솟값·최댓값으로 접는다.
 * ★비캡처 그룹 {@code (?:...)}이 필수 — 소수부를 별도 그룹으로 물면 값이 깨진다(원본 프롬프트 실측 경고).
 * <ul>
 *   <li>매치 0개(빈 문자열·비숫자 원문) → {@code (null, null)} — 파싱 실패는 정상 상태(호출부에서 카운트).</li>
 *   <li>값이 0 이하 또는 100 초과 → {@link IllegalStateException} fail-fast(좌표 범위 검증과 동일 정책).</li>
 *   <li>단일값 → {@code min == max}.</li>
 * </ul>
 */
public final class AlcoholContentParser {

    private static final Pattern NUMBER = Pattern.compile("[0-9]+(?:\\.[0-9]+)?");
    private static final BigDecimal MAX_INCLUSIVE = new BigDecimal("100");

    private AlcoholContentParser() {
    }

    /** 파싱 결과. 매치 0개면 min/max 모두 null. */
    public record AlcoholRange(BigDecimal min, BigDecimal max) {

        static AlcoholRange empty() {
            return new AlcoholRange(null, null);
        }
    }

    /** 원문을 파싱해 (min, max)를 돌려준다. 범위 위반 값은 fail-fast. */
    public static AlcoholRange parse(String raw) {
        if (raw == null) {
            return AlcoholRange.empty();
        }
        Matcher m = NUMBER.matcher(raw);
        BigDecimal min = null;
        BigDecimal max = null;
        while (m.find()) {
            BigDecimal value = new BigDecimal(m.group());
            if (value.signum() <= 0 || value.compareTo(MAX_INCLUSIVE) > 0) {
                throw new IllegalStateException(
                        "도수 파싱 범위 위반(0 이하 또는 100 초과): value=" + value + " (원문='" + raw + "')");
            }
            if (min == null || value.compareTo(min) < 0) {
                min = value;
            }
            if (max == null || value.compareTo(max) > 0) {
                max = value;
            }
        }
        return new AlcoholRange(min, max);
    }
}
