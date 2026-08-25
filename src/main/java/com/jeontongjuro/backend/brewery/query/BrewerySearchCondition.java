package com.jeontongjuro.backend.brewery.query;

import com.jeontongjuro.backend.brewery.VisitState;
import com.jeontongjuro.backend.global.error.InvalidQueryParameterException;
import com.jeontongjuro.backend.liquortype.LiquorType;
import java.math.BigDecimal;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 검증·정규화가 끝난 조회 조건(불변). 컨트롤러가 받은 원문 파라미터를 {@link #of}에서 한 번에 검증·변환해,
 * 이후 계층(Specification·서비스)은 "이미 안전한 값"만 다룬다.
 * <p>
 * 각 필드 null = 해당 필터 미적용. reservationVisit/alwaysVisit는 별 컬럼이므로 각각 받고,
 * 둘 다 지정되면 Specification 조합에서 AND로 걸린다.
 *
 * @param regions         지역 칩 목록(다중, 비어 있으면 필터 미적용). 여러 값은 Specification에서 OR로 걸린다
 *                        (liquorTypes와 동일 처리 형식).
 * @param reservationVisit 예약방문 상태(null 가능)
 * @param alwaysVisit      상시방문 상태(null 가능)
 * @param keyword          상호명 부분일치 키워드(NFC 정규화된 값, null 가능)
 * @param liquorTypes      주종 필터(허용 5종, 비어 있으면 필터 미적용). 여러 값은 Specification에서 OR로 걸린다.
 * @param minAbv           도수 하한(null 가능). 판정은 "제품 도수 범위와 요청 범위의 겹침"(방식A)이라
 *                         제품 alcohol_max &gt;= minAbv를 요구한다. Specification 참조.
 * @param maxAbv           도수 상한(null 가능). 겹침 판정에서 제품 alcohol_min &lt;= maxAbv를 요구한다.
 */
public record BrewerySearchCondition(
        List<Region> regions,
        VisitState reservationVisit,
        VisitState alwaysVisit,
        String keyword,
        List<LiquorType> liquorTypes,
        BigDecimal minAbv,
        BigDecimal maxAbv) {

    /** 도수 파라미터 허용 상한(도수 %). 초과는 400. AlcoholContentParser의 저장 상한과 동일한 물리 한계. */
    private static final BigDecimal ABV_MAX = new BigDecimal("100");

    /**
     * 원문 파라미터 → 검증된 조건. 허용 집합 밖 값은 {@link InvalidQueryParameterException}(400).
     * keyword는 NFC 정규화만 하고 부분일치 매칭은 Specification이 담당한다.
     * region·liquorType은 다중 값이라 원소별로 검증하고, 하나라도 정의 밖이면 400
     * (region은 트림 후 blank 원소만은 liquorType과 달리 기존 단일값 시절 blank→null 정책을 유지해 건너뛴다).
     * 도수(minAbv·maxAbv)는 타입 바인딩(BigDecimal)은 컨트롤러/스프링이 하고(파싱 불가는 400 타입불일치 경로),
     * 값 범위(음수·100 초과·min&gt;max)는 여기서 검증한다.
     */
    public static BrewerySearchCondition of(List<String> region, String reservationVisit,
                                            String alwaysVisit, String keyword,
                                            List<String> liquorType,
                                            BigDecimal minAbv, BigDecimal maxAbv) {
        validateAbv(minAbv, maxAbv);
        return new BrewerySearchCondition(
                parseRegions(region),
                parseVisit("reservationVisit", strip(reservationVisit)),
                parseVisit("alwaysVisit", strip(alwaysVisit)),
                normalizeKeyword(keyword),
                parseLiquorTypes(liquorType),
                minAbv,
                maxAbv);
    }

    /**
     * 필터 파라미터 앞뒤 공백 정규화(부채 #21). 값 검증 직전에 딱 한 곳(이 팩토리)에서 수행해 트림 규칙이
     * 계층마다 갈라지지 않게 한다. ★앞뒤 공백만 제거하고 중간 공백은 보존하므로 "수도 권" 같은 값은 여전히 400이다.
     * <p>
     * 트림 후 빈 문자열/blank의 취급은 기존 검증 의미를 바꾸지 않는다: region·visit는 {@code Region.from}·
     * {@code parseVisit}의 blank→null 규약(필터 미적용)을, liquorType은 {@code LiquorType.from}의 빈값→400
     * 설계를 그대로 탄다. 이 정규화는 "유효값에 붙은 패딩"만 관용하고, 유효/무효 판정 자체는 건드리지 않는다.
     */
    private static String strip(String raw) {
        return raw == null ? null : raw.strip();
    }

    /**
     * 도수 범위 검증. 각 값은 0 이상 100 이하여야 하고, 둘 다 있으면 minAbv &le; maxAbv여야 한다.
     * ★버킷(저/중/고) 경계값과 무관한 숫자 범위 검증이다 — 경계값 기획 확정과 독립.
     */
    private static void validateAbv(BigDecimal minAbv, BigDecimal maxAbv) {
        validateAbvBound("minAbv", minAbv);
        validateAbvBound("maxAbv", maxAbv);
        if (minAbv != null && maxAbv != null && minAbv.compareTo(maxAbv) > 0) {
            throw new InvalidQueryParameterException(
                    "minAbv는 maxAbv보다 클 수 없습니다: minAbv=" + minAbv + ", maxAbv=" + maxAbv);
        }
    }

    private static void validateAbvBound(String paramName, BigDecimal value) {
        if (value == null) {
            return;
        }
        if (value.compareTo(BigDecimal.ZERO) < 0) {
            throw new InvalidQueryParameterException(
                    "허용되지 않은 " + paramName + " 값입니다: '" + value.toPlainString() + "' (0 이상이어야 합니다)");
        }
        if (value.compareTo(ABV_MAX) > 0) {
            throw new InvalidQueryParameterException(
                    "허용되지 않은 " + paramName + " 값입니다: '" + value.toPlainString() + "' (100 이하여야 합니다)");
        }
    }

    /**
     * region 원문 목록 → 검증된 지역 칩 목록. null·빈 목록은 "필터 미적용"이므로 빈 목록 반환.
     * ★liquorType과 달리 트림 후 blank인 원소는 400이 아니라 건너뛴다(기존 단일값 시절 blank→null=필터
     * 미적용 정책을 다중값 형태에서도 유지 — region만의 예외, liquorType은 blank도 400인 기존 설계 유지).
     * blank가 아닌 값은 원소별로 {@link Region#from}에 위임해 정의 밖이면 400.
     */
    private static List<Region> parseRegions(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<Region> parsed = new ArrayList<>(raw.size());
        for (String value : raw) {
            String stripped = strip(value);
            if (stripped == null || stripped.isBlank()) {
                continue;
            }
            parsed.add(Region.from(stripped));
        }
        return parsed;
    }

    /** 주종 원문 목록 → 검증된 주종 목록. null·빈 목록은 "필터 미적용"이므로 빈 목록 반환. 원소별 검증(하나라도 틀리면 400). */
    private static List<LiquorType> parseLiquorTypes(List<String> raw) {
        if (raw == null || raw.isEmpty()) {
            return List.of();
        }
        List<LiquorType> parsed = new ArrayList<>(raw.size());
        for (String value : raw) {
            // 다중값은 원소별로 앞뒤 공백을 제거한다("탁주 "·" 약주" 각각). 트림 후 빈값·정의 밖 값은
            // LiquorType.from이 기존대로 400으로 매핑한다(중간 공백 "탁 주"도 400 유지).
            parsed.add(LiquorType.from(strip(value)));
        }
        return parsed;
    }

    /** Y/N/UNKNOWN 만 허용. ★VisitState.fromRaw(미입력→UNKNOWN 흡수)는 검증용으로 쓰면 안 된다 — 정확 매칭. */
    private static VisitState parseVisit(String paramName, String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return VisitState.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new InvalidQueryParameterException(
                    "허용되지 않은 " + paramName + " 값입니다: '" + raw + "' (허용: " + visitLabels() + ")");
        }
    }

    private static String normalizeKeyword(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        // 저장값은 전행 NFC(STEP 0 실측) → 입력 keyword도 NFC로 맞춰 결합 문자 불일치를 예방한다.
        return Normalizer.normalize(keyword.strip(), Normalizer.Form.NFC);
    }

    private static String visitLabels() {
        return Arrays.stream(VisitState.values()).map(Enum::name).collect(Collectors.joining(", "));
    }
}
