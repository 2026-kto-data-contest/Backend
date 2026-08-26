package com.jeontongjuro.backend.product.query;

import java.util.Set;

/**
 * 제품 소개(description) 절단 판정·되돌림 정책(순수 함수). 조회 시점에 매 제품에 적용한다.
 * <p>
 * 원본 소개는 수집 단계에서 특정 길이로 잘려 저장된 경우가 있어, 문장 중간에서 끊긴 문구를 그대로 노출하면
 * 안 된다. 규칙:
 * <ol>
 *   <li>null·빈 문자열 → null(미노출).</li>
 *   <li>길이가 {@link #TRUNCATION_LENGTHS}(78·79) 밖 → 절단 아님. 원문 그대로 반환.</li>
 *   <li>길이가 78·79 → 절단으로 보고 <b>마지막 문장 끝</b>까지 되돌린다.</li>
 * </ol>
 * <p>
 * ★"문장 끝" = 마침표·느낌표·물음표({@code . ! ?}) 바로 뒤에 공백이 오거나, 그 문자가 문자열 맨 끝인 위치.
 * 그 문자까지(포함)를 남기고 뒤를 버린다. 문장 끝이 하나도 없으면 null(미노출)을 반환한다.
 * <ul>
 *   <li>소수점 회피는 {@code '.'}에만 해당한다 — {@code "5.8"}의 마침표는 뒤가 숫자라 공백도 문자열 끝도
 *       아니어서 무시된다(예: {@code "…막걸리. 순수령 5.8도와"} → {@code "…막걸리."}). {@code !}·{@code ?}는
 *       숫자 뒤에 붙는 경우가 없어 별도 회피 규칙이 필요 없다 — 같은 "뒤가 공백/끝" 판정을 그대로 쓴다.</li>
 *   <li>맨 끝 문장부호 케이스(이미 완결된 문장이 78·79자인 경우)는 되돌림 결과가 원문과 같다.</li>
 * </ul>
 * <p>
 * ★{@code reverse()}로 마지막 위치를 찾지 않는다 — 뒤집으면 {@code ". "} 패턴도 뒤집혀 오작동한다.
 * 인덱스를 뒤에서 앞으로 훑어 마지막 문장 끝을 찾는다.
 * <p>
 * ★금지: 잘린 뒤를 만들어 붙이기, 무구두점 종결어미(다/니다) 완결 인정, 공백 없는 내부 마침표 인정.
 * 이 세 가지는 구현하지 않는다(플래그·옵션도 두지 않는다).
 */
public final class DescriptionTruncationPolicy {

    /** 절단으로 판정하는 길이 집합. ★기획이 번복 가능한 값이라 상수로 분리한다. */
    static final Set<Integer> TRUNCATION_LENGTHS = Set.of(78, 79);

    private DescriptionTruncationPolicy() {
    }

    /**
     * 소개 문구에 절단 정책을 적용한 노출용 문자열을 반환한다.
     *
     * @param description 원본 소개(null 가능)
     * @return 노출용 소개. 미노출이면 null.
     */
    public static String apply(String description) {
        if (description == null || description.isEmpty()) {
            return null;
        }
        if (!TRUNCATION_LENGTHS.contains(description.length())) {
            return description;
        }
        int end = lastSentenceEnd(description);
        if (end < 0) {
            return null;
        }
        return description.substring(0, end + 1);
    }

    /** 문장 종결 부호. */
    private static final Set<Character> SENTENCE_END_CHARS = Set.of('.', '!', '?');

    /**
     * 마지막 "문장 끝"(. ! ?)의 인덱스. 뒤에서 앞으로 훑어 첫 번째로 만나는, 뒤가 공백이거나 문자열 끝인
     * 문장 종결 부호를 반환한다. 없으면 -1.
     */
    private static int lastSentenceEnd(String s) {
        for (int i = s.length() - 1; i >= 0; i--) {
            if (SENTENCE_END_CHARS.contains(s.charAt(i))
                    && (i == s.length() - 1 || s.charAt(i + 1) == ' ')) {
                return i;
            }
        }
        return -1;
    }
}
