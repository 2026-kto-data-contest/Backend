package com.jeontongjuro.backend.brewery;

/**
 * 양조장 주소 → 시군구 라벨 순수 파서. {@link BreweryRegionParser}의 SSOT(ALIAS_TO_SIDO)를 재사용하되,
 * 그 파서의 반환 계약(sido+region)은 건드리지 않는다 — 시군구는 별도 산출값이라 이 클래스에 둔다.
 * <p>
 * 규칙(스펙 확정):
 * <ol>
 *   <li>{@code ALIAS_TO_SIDO}로 주소 앞의 시도 프리픽스를 벗긴다(최장 프리픽스 우선 — alias 삽입 순서 그대로).</li>
 *   <li>남은 문자열의 첫 공백 토큰을 취한다(2단계 자치구가 있어도 상위 시/군만 — "안산시 단원구" → "안산시").</li>
 *   <li>끝의 시/군/구 접미어 1글자를 제거한다(원본에 접미어가 없으면 그대로 둔다).</li>
 *   <li>결과가 빈 문자열이면 null.</li>
 * </ol>
 */
public final class BrewerySigunguParser {

    private BrewerySigunguParser() {
    }

    /**
     * 주소 → 시군구 라벨. 시도 prefix를 못 벗기거나(alias 밖 주소) 뒤에 남는 토큰이 없으면 null
     * (조용한 null — {@link BreweryRegionParser#parse}와 달리 이 파서는 응답 파생값이라 예외로 죽지 않는다).
     */
    public static String parse(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        String head = address.strip();
        String matchedAlias = null;
        for (String alias : BreweryRegionParser.sidoAliases()) {
            if (head.startsWith(alias)) {
                matchedAlias = alias;
                break;
            }
        }
        if (matchedAlias == null) {
            return null;
        }
        String rest = head.substring(matchedAlias.length()).strip();
        if (rest.isEmpty()) {
            return null;
        }
        String token = firstToken(rest);
        String result = stripSuffix(token);
        return result.isEmpty() ? null : result;
    }

    private static String firstToken(String s) {
        String[] parts = s.split("\\s+", 2);
        return parts[0];
    }

    private static String stripSuffix(String token) {
        if (token.endsWith("시") || token.endsWith("군") || token.endsWith("구")) {
            return token.substring(0, token.length() - 1);
        }
        return token;
    }
}
