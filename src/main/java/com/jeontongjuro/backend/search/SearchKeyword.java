package com.jeontongjuro.backend.search;

import com.jeontongjuro.backend.global.error.InvalidQueryParameterException;
import java.text.Normalizer;
import java.util.regex.Pattern;

/**
 * 검색(자동완성·통합검색) 공용 키워드 정규화. 입력 검색어와 매칭 대상(상호명·제품명)을 <b>같은 규칙</b>으로
 * 정규화해, 특수문자를 포함한 이름도 입력과 동일한 형태로 비교되게 한다.
 * <p>
 * 규칙: 허용 문자(한글·영문·숫자·공백) 밖(이모지·특수문자)은 제거 → 앞뒤 공백 제거 → NFC → 소문자.
 * 이 규칙은 원래 {@code SearchSuggestionService}·{@code BreweryQuerySpecifications}가 각자 재현하던 것을
 * 한곳으로 모은 것이다(두 벌로 갈라지면 "자동완성이 내려준 텍스트로 재검색 시 0건" 같은 불일치가 난다).
 * <p>
 * ★매칭용 정규화일 뿐 저장·표시값은 원문 그대로 유지한다(DB 컬럼·응답 displayName 불변).
 */
public final class SearchKeyword {

    /** 검색어 최대 길이(앞뒤 공백 트림 후 기준). 초과 시 400. */
    public static final int MAX_LENGTH = 20;

    /** 허용 문자(한글·영문·숫자·공백) 밖은 제거한다(이모지·특수문자는 입력은 되지만 검색 실행 시 무시). */
    private static final Pattern DISALLOWED_CHARS = Pattern.compile("[^\\p{IsHangul}a-zA-Z0-9\\s]");

    private SearchKeyword() {
    }

    /**
     * 입력 검색어 → 매칭용 needle. 앞뒤 공백을 트림한 뒤 길이를 검증(트림 후 {@link #MAX_LENGTH} 초과는 400)하고,
     * {@link #normalizeTarget}와 동일한 규칙으로 정규화한다.
     * <p>
     * 트림 후 빈 값이거나 허용 문자 제거 후 빈 값이면 빈 문자열("")을 반환한다 — 호출자는 이를 "검색 실행 없음"
     * (빈 결과)으로 처리한다. 명세: 빈 검색은 에러가 아니라 동작 없음.
     */
    public static String normalizeForMatch(String raw) {
        String stripped = raw == null ? "" : raw.strip();
        if (stripped.length() > MAX_LENGTH) {
            throw new InvalidQueryParameterException(
                    "검색어는 최대 " + MAX_LENGTH + "자까지 입력할 수 있습니다.");
        }
        if (stripped.isEmpty()) {
            return "";
        }
        return normalizeTarget(stripped);
    }

    /**
     * 매칭 대상(상호명·제품명) 정규화 — 허용 문자 밖 제거 → 앞뒤 공백 제거 → NFC → 소문자.
     * {@link #normalizeForMatch}와 동일 규칙이라, 어떤 문자열 x든 (길이 제한을 통과하는 한)
     * {@code normalizeForMatch(x).equals(normalizeTarget(x))}가 성립한다 → 자동완성이 내려준 이름을
     * 그대로 재검색해도 자기 자신에 매칭된다.
     */
    public static String normalizeTarget(String name) {
        String filtered = DISALLOWED_CHARS.matcher(name == null ? "" : name).replaceAll("").strip();
        return Normalizer.normalize(filtered, Normalizer.Form.NFC).toLowerCase();
    }
}
