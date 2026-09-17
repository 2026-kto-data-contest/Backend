package com.jeontongjuro.backend.product.query;

/** 표준 맛 태그 8종(기획 확정). 나열 순서는 카드 응답과 온보딩 맛 취향에 공통으로 사용한다. */
public enum SensoryTag {
    상큼함,
    달콤함,
    드라이,
    산미,
    부드러움,
    묵직함,
    깔끔함,
    향긋함;

    public static SensoryTag from(String raw) {
        if (raw != null && !raw.isBlank()) {
            try {
                return valueOf(raw.strip());
            } catch (IllegalArgumentException ignored) {
                // 아래의 공통 오류 메시지를 사용한다.
            }
        }
        throw new IllegalArgumentException("허용되지 않은 맛 취향입니다: '" + raw + "'");
    }
}
