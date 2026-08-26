package com.jeontongjuro.backend.product.query;

import java.util.List;

/**
 * 수상경력(awards) 원문에서 노출용 최상위 등급 뱃지 하나를 뽑는 정책(순수 함수).
 * <p>
 * 파싱 순서(순서가 중요하다):
 * <ol>
 *   <li><b>대회명·제품명 선제거</b> — 등급어가 대회/제품 이름에 박혀 있으면 오판한다. 코퍼스 전수 확인으로
 *       추린 {@link #NAME_NOISE}만 공백으로 치환한다.
 *       (예: {@code "한국와인대상 실버상"}에서 대회명 {@code "한국와인대상"}을 안 지우면 실제 은상(실버)을
 *       대상으로 오판한다. {@code "골드와인 금상"}은 제품명 {@code "골드와인"}에 등급어 '골드'가 있다.)</li>
 *   <li><b>쉼표 분리 금지</b> — 대신 {@link Grade} 키워드를 부분 문자열로 스캔한다. 쉼표는 괄호 안 쉼표·
 *       연도 나열·무구분자 3유형에서 깨지므로 토큰 경계로 쓰지 않는다.</li>
 *   <li>매칭된 등급 중 <b>최상위 하나</b>를 반환한다.</li>
 * </ol>
 * <p>
 * ★등급 순위(rank)는 근사치다 — 서로 다른 대회의 상을 하나의 순위 축에 억지로 세운 것이라, 대회 간 상대
 * 가치는 정확하지 않을 수 있다. 같은 순위 안에서는 {@link Grade} 선언 순서가 앞선 것을 노출한다(결정론).
 * <p>
 * ★부분 문자열 스캔의 겹침은 순위가 흡수한다 — {@code "최우수상"} 안에 {@code "우수상"}이 들어 있지만
 * {@code 최우수상}(rank 2)이 {@code 우수상}(rank 3)보다 높아 항상 최우수상이 이긴다.
 * {@code "대통령표창"}은 {@code "대통령상"}을 부분 문자열로 갖지 않으므로 표창(rank 5)으로만 잡힌다.
 * <p>
 * awards가 null·공백이면 null(뱃지 없음). 매칭되는 등급이 하나도 없으면 fallback {@code "수상"}.
 * ★절단(78·79) awards도 그냥 파싱을 시도한다 — 매칭되면 그 등급, 없으면 "수상". "절단이면 무조건 수상"으로
 * 12건 정보를 통째로 버리지 않는다. 잘린 뒤에 더 높은 등급이 있었을 가능성은 감수한다.
 * <p>
 * ★대통령상은 등급 사전에 없다 — 양조장 단위 자료라 개별 술 카드에는 미부착한다(명세). awards에 "대통령상"만
 * 있으면 다른 등급어가 없으니 fallback {@code "수상"}이 된다. 양조장 단위 대통령상 표기는
 * {@code com.jeontongjuro.backend.feature.FeatureType#대통령상}이 별도로 담당한다.
 */
public final class AwardGradeParser {

    private AwardGradeParser() {
    }

    /** fallback 뱃지 — awards는 있으나 인식 가능한 등급어가 없을 때. */
    private static final String FALLBACK = "수상";

    /**
     * 등급어가 이름(대회·제품)에 박혀 오판을 부르는 노이즈. ★코퍼스(수상 보유 77건) 전수 확인으로 추린 목록:
     * 대회명 "대한민국(주류)대상"·"한국와인대상"과 제품명 "골드와인"만 실존한다. 여기 담긴 문자열은 실제 등급이
     * 아니라 이름의 일부이므로 스캔 전에 공백으로 치환한다. (진짜 등급 "대상"·"금상"·"골드"는 이름 밖에 별도로
     * 남아 정상 매칭된다 — 예: sr117은 "…주류대상" 제거 후에도 "3년 연속 대상"이 남는다.)
     */
    private static final List<String> NAME_NOISE = List.of(
            "대한민국주류대상", "대한민국 주류대상", "한국와인대상", "골드와인");

    /**
     * 등급 사전. rank 낮을수록 상위. 한글·음역·메달 표기를 한 등급에 모으고, 노출 라벨은 한글 표준으로 통일한다
     * (골드→금상, 실버→은상, 브론즈→동상). 선언 순서 = 동순위 내 우선순위.
     */
    private enum Grade {
        대상(1, "대상", "대상"),
        최우수상(2, "최우수상", "최우수상"),
        금상(2, "금상", "금상", "골드"),
        우수상(3, "우수상", "우수상"),
        은상(3, "은상", "은상", "실버"),
        장려상(4, "장려상", "장려상"),
        동상(4, "동상", "동상", "브론즈"),
        장관상(5, "장관상", "장관상"),
        표창(5, "표창", "표창"),
        선정(5, "선정", "선정");

        private final int rank;
        private final String label;
        private final String[] keywords;

        Grade(int rank, String label, String... keywords) {
            this.rank = rank;
            this.label = label;
            this.keywords = keywords;
        }

        boolean matches(String text) {
            for (String kw : keywords) {
                if (text.contains(kw)) {
                    return true;
                }
            }
            return false;
        }
    }

    /**
     * awards 원문에서 노출용 최상위 등급 뱃지를 반환한다.
     *
     * @param awards 수상경력 원문(null 가능)
     * @return 최상위 등급 라벨, 등급어가 없으면 {@code "수상"}, awards 자체가 없으면 null
     */
    public static String parse(String awards) {
        if (awards == null || awards.isBlank()) {
            return null;
        }
        String cleaned = awards;
        for (String noise : NAME_NOISE) {
            cleaned = cleaned.replace(noise, " ");
        }
        Grade best = null;
        for (Grade g : Grade.values()) {
            if (g.matches(cleaned) && (best == null || g.rank < best.rank)) {
                best = g;
            }
        }
        return best != null ? best.label : FALLBACK;
    }
}
