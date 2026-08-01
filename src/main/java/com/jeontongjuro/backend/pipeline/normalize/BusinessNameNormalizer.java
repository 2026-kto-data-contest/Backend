package com.jeontongjuro.backend.pipeline.normalize;

import java.text.Normalizer;
import java.util.List;

/**
 * T2-a 정규화·매칭 규칙 첫 구현. 원문: {@code ~/docs/audit/20260728_dump/파생규칙_원문.md} §T2-a.
 * 규칙 수정 근거(파트 A): {@code docs/audit/20260730_join_decision_ledger.md}.
 * <p>
 * 순서 고정: (1) NFC 유니코드 정규화 → (2) 공백 제거 → (3) 법인격 제거 → (4) 말미 접미어 최장일치 1회 제거.
 * NFC를 맨 앞에 두는 이유: 원본 중 일부 값이 NFD(자모 분해) 인코딩이라 이후 단계의 문자열 비교가
 * 코드포인트 불일치로 깨진다(풍정사계 하(夏) 사례).
 * <p>
 * ★{@link #TRAILING_SUFFIXES} 확대 금지. 목록 밖 토큰(예: "합동", "와이너리", "브루어리")을
 * 추가하면 서로 다른 업체가 동일 norm으로 오병합될 수 있다(예: 태인합동주조장 vs 태인양조장 —
 * "합동"을 접미어로 추가하지 않는 한 둘은 의도적으로 다른 norm을 유지한다).
 */
public final class BusinessNameNormalizer {

    /** 법인격 지시어 — 문자열 내 위치 무관, 전체 제거. */
    private static final List<String> CORPORATE_DESIGNATORS = List.of(
            "농업회사법인", "영농조합법인", "주식회사", "유한회사", "(주)", "(유)", "㈜");

    /**
     * 말미 접미어 — 최장 일치 1건만 제거(중첩 반복 제거 금지).
     * 길이 내림차순으로 나열되어 있어야 longest-match 순회가 성립한다.
     * 원문 목록(양조장/영농조합/양조/주조/도가/명가/농원/농장/주가) + 파트 A 신규(주조장/양조원).
     */
    private static final List<String> TRAILING_SUFFIXES = List.of(
            "영농조합", "주조장", "양조원", "양조장", "양조", "주조", "도가", "명가", "농원", "농장", "주가");

    private BusinessNameNormalizer() {
    }

    public static String normalize(String rawName) {
        if (rawName == null) {
            return null;
        }
        String value = Normalizer.normalize(rawName, Normalizer.Form.NFC);
        value = value.replaceAll("\\s+", "");
        for (String designator : CORPORATE_DESIGNATORS) {
            value = value.replace(designator, "");
        }
        for (String suffix : TRAILING_SUFFIXES) {
            if (value.endsWith(suffix)) {
                value = value.substring(0, value.length() - suffix.length());
                break;
            }
        }
        return value;
    }
}
