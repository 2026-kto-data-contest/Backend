package com.jeontongjuro.backend.brewery.query;

import com.jeontongjuro.backend.brewery.VisitState;
import com.jeontongjuro.backend.global.error.InvalidQueryParameterException;
import java.text.Normalizer;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 검증·정규화가 끝난 조회 조건(불변). 컨트롤러가 받은 원문 파라미터를 {@link #of}에서 한 번에 검증·변환해,
 * 이후 계층(Specification·서비스)은 "이미 안전한 값"만 다룬다.
 * <p>
 * 각 필드 null = 해당 필터 미적용. reservationVisit/alwaysVisit는 별 컬럼이므로 각각 받고,
 * 둘 다 지정되면 Specification 조합에서 AND로 걸린다.
 *
 * @param region          지역 칩(null 가능)
 * @param reservationVisit 예약방문 상태(null 가능)
 * @param alwaysVisit      상시방문 상태(null 가능)
 * @param keyword          상호명 부분일치 키워드(NFC 정규화된 값, null 가능)
 */
public record BrewerySearchCondition(
        Region region,
        VisitState reservationVisit,
        VisitState alwaysVisit,
        String keyword) {

    /**
     * 원문 파라미터 → 검증된 조건. 허용 집합 밖 값은 {@link InvalidQueryParameterException}(400).
     * keyword는 NFC 정규화만 하고 부분일치 매칭은 Specification이 담당한다.
     */
    public static BrewerySearchCondition of(String region, String reservationVisit,
                                            String alwaysVisit, String keyword) {
        return new BrewerySearchCondition(
                Region.from(region),
                parseVisit("reservationVisit", reservationVisit),
                parseVisit("alwaysVisit", alwaysVisit),
                normalizeKeyword(keyword));
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
