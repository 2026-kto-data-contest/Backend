package com.jeontongjuro.backend.liquortype;

import com.jeontongjuro.backend.global.error.InvalidQueryParameterException;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 주종 6종. {@code @Enumerated(EnumType.STRING)}으로 저장하므로 상수명이 그대로 DB 값이 되고,
 * schema.sql의 {@code ck_product_liquor_type_type CHECK (... IN ('탁주',...))}와 1:1 대응한다.
 * (한글 enum 상수명은 CHECK 제약의 한글 값과 매핑을 어긋남 없이 맞추기 위한 의도적 선택 — 별도 컨버터 불필요.)
 * <p>
 * ★{@link #기타}는 AUTO 추론이 만들지 않는다 — "어느 키워드에도 안 걸림(판정 안 됨)"과 "기타로 판정함"은 다르다.
 * 기타는 사람 판정(2차 MANUAL)만 부여한다. 여기선 enum 값으로 정의만 해 둔다.
 */
public enum LiquorType {
    탁주,
    약주,
    청주,
    증류주,
    과실주,
    기타;

    /**
     * 조회 API가 필터 값으로 허용하는 5종. ★{@link #기타}는 제외한다 — enum 값·DB CHECK엔 존재하지만
     * (사람 판정이 붙을 수 있어 스키마엔 남긴다) API 계약에서만 배제한다. {@code valueOf("기타")}는 성공하므로
     * 이 집합으로 명시 배제해야 400이 난다.
     */
    private static final Set<LiquorType> QUERYABLE = EnumSet.of(탁주, 약주, 청주, 증류주, 과실주);

    /**
     * liquorType 파라미터 원소 하나를 조회 허용 주종으로 변환(다중 값이므로 원소별 검증). 정의 밖 값·기타·빈값은
     * 전부 {@link InvalidQueryParameterException}(400)으로 매핑된다 — Region.from과 동일한 처리 형식.
     *
     * @throws InvalidQueryParameterException 허용 5종 밖의 값(기타·맥주·빈 문자열·공백 등)
     */
    public static LiquorType from(String raw) {
        if (raw != null && !raw.isBlank()) {
            try {
                LiquorType parsed = LiquorType.valueOf(raw);
                if (QUERYABLE.contains(parsed)) {
                    return parsed;
                }
            } catch (IllegalArgumentException ignored) {
                // 아래 공통 400으로 흡수(기타·정의 밖 값 동일 메시지)
            }
        }
        throw new InvalidQueryParameterException(
                "허용되지 않은 liquorType 값입니다: '" + raw + "' (허용: " + queryableLabels() + ")");
    }

    private static String queryableLabels() {
        return QUERYABLE.stream().map(Enum::name).collect(Collectors.joining(", "));
    }
}
