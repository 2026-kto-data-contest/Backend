package com.jeontongjuro.backend.liquortype;

/**
 * 한글 텍스트 키워드 매칭 유틸. 한글엔 정규식 {@code \b}(단어경계)가 통하지 않으므로 직접 구현한다.
 * <p>
 * wordBoundary 판정: 키워드 출현 위치의 <b>바로 앞·뒤 문자가 한글이 아닐 때만</b> 매칭으로 인정한다.
 * 예) 키워드 '진'(gin) → "퍼플 진"은 매칭(앞=공백, 뒤=문자열끝)이지만 "진맥소주"·"진도홍주"는
 * 뒤 문자가 한글('맥','도')이라 비매칭. 문자열 시작/끝은 경계로 본다.
 */
final class HangulMatcher {

    private HangulMatcher() {
    }

    /** 한글(음절 AC00–D7A3 또는 자모 영역) 여부. */
    static boolean isHangul(char c) {
        return (c >= '가' && c <= '힣')          // 완성형 음절
                || (c >= 'ᄀ' && c <= 'ᇿ')      // 한글 자모
                || (c >= '㄰' && c <= '㆏')      // 호환용 자모
                || (c >= 'ꥠ' && c <= '꥿')      // 자모 확장 A
                || (c >= 'ힰ' && c <= '퟿');     // 자모 확장 B
    }

    /**
     * text에 keyword가 포함되는가. wordBoundary=true면 앞·뒤 한글 비접촉 출현이 하나라도 있어야 한다.
     * text·keyword가 null이거나 keyword가 빈 문자열이면 false.
     */
    static boolean contains(String text, String keyword, boolean wordBoundary) {
        if (text == null || keyword == null || keyword.isEmpty()) {
            return false;
        }
        if (!wordBoundary) {
            return text.contains(keyword);
        }
        int len = keyword.length();
        int from = 0;
        int idx;
        while ((idx = text.indexOf(keyword, from)) >= 0) {
            boolean leftOk = idx == 0 || !isHangul(text.charAt(idx - 1));
            int afterPos = idx + len;
            boolean rightOk = afterPos >= text.length() || !isHangul(text.charAt(afterPos));
            if (leftOk && rightOk) {
                return true;
            }
            from = idx + 1;
        }
        return false;
    }
}
