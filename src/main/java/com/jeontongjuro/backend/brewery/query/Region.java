package com.jeontongjuro.backend.brewery.query;

import com.jeontongjuro.backend.global.error.InvalidQueryParameterException;
import java.util.Arrays;
import java.util.stream.Collectors;

/**
 * 서비스 지역 필터가 쓰는 8칩(region) 값 목록. ★brewery.region 컬럼 저장값과 1:1 대응한다
 * (부산·울산은 경상에서 독립한 별도 칩 — {@code BreweryRegionParser}의 롤업표와 동일 정의).
 * <p>
 * enum 상수명 자체가 저장값(NFC 한글)이라 {@link #name()}이 곧 DB 비교값이다.
 * "경기" 같은 시도명은 여기 없다 → region 파라미터로 들어오면 400.
 */
public enum Region {
    수도권,
    충청,
    전라,
    경상,
    부산,
    울산,
    강원,
    제주;

    /**
     * region 파라미터 문자열을 칩으로 변환. null·공백은 "필터 미적용"이므로 null 반환.
     *
     * @throws InvalidQueryParameterException 정의된 8칩 밖의 값인 경우(400)
     */
    public static Region from(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Region.valueOf(raw);
        } catch (IllegalArgumentException e) {
            throw new InvalidQueryParameterException(
                    "허용되지 않은 region 값입니다: '" + raw + "' (허용: " + labels() + ")");
        }
    }

    private static String labels() {
        return Arrays.stream(values()).map(Enum::name).collect(Collectors.joining(", "));
    }
}
