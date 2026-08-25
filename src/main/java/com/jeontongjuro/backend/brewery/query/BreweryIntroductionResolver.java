package com.jeontongjuro.backend.brewery.query;

/**
 * 양조장 소개 텍스트 우선순위 병합(상세 화면 '양조장 소개' 규칙 재사용, additive 목록 필드용).
 * <ol>
 *   <li>서술형이 있으면 그대로: 관광공사 overview.</li>
 *   <li>없으면 나열형: 농림부 designation_note.</li>
 *   <li>둘 다 없으면 미노출(null).</li>
 * </ol>
 * 혼용 금지 — 상위 1종만 반환한다. 말줄임(...)은 프론트가 CSS로 처리하므로 여기서 자르지 않는다.
 */
public final class BreweryIntroductionResolver {

    private BreweryIntroductionResolver() {
    }

    public static String resolve(String overview, String designationNote) {
        if (overview != null && !overview.isBlank()) {
            return overview;
        }
        if (designationNote != null && !designationNote.isBlank()) {
            return designationNote;
        }
        return null;
    }
}
