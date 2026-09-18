package com.jeontongjuro.backend.product.query;

import com.jeontongjuro.backend.liquortype.LiquorType;
import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.web.util.HtmlUtils;

/**
 * 양조장 상세 화면의 제품 카드 응답 DTO(엔티티 직노출 금지 — 명시 프로젝션).
 * <p>
 * 한 카드 = 판매중단·원본오류 제외 및 중복 병합을 거친 노출 제품 1건. 병합된 카드의 스칼라(도수·용량)는
 * 대표 행에서 통째로 가져오고(필드별로 섞지 않는다 — 존재하지 않는 SKU 방지), 텍스트는 필드별로 병합한다.
 * <p>
 * 결측 규약: 배열({@code liquorTypes})은 없으면 빈 배열, 스칼라·문자열은 없으면 null.
 * <ul>
 *   <li>{@code alcoholMin/Max} — 도수 정보가 없으면 null. 단일 도수 제품은 min==max.</li>
 *   <li>{@code volume} — 원문 그대로(파싱·정규화 안 함). 복수 표기도 원문 유지. 없으면 null.</li>
 *   <li>{@code description} — 절단 되돌림/미노출 처리 후 HTML 엔티티를 디코딩한 값. 노출할 문장이 없으면 null.</li>
 *   <li>{@code awardBadge} — 최상위 등급 라벨 또는 {@code "수상"}. 수상 이력이 없으면 null.</li>
 * </ul>
 */
public record ProductCardResponse(
        @Schema(description = "제품 식별자(병합 후 대표 행의 source_row_ref)", example = "441",
                requiredMode = Schema.RequiredMode.REQUIRED)
        Integer productId,
        @Schema(description = "화면에 표시할 제품명(병합 대표명, 원문)", example = "진맥 소주",
                requiredMode = Schema.RequiredMode.REQUIRED)
        String productName,
        @Schema(description = "최소 도수(%). 도수 정보가 없으면 null. 단일 도수면 최대와 같음", example = "40.0",
                nullable = true)
        BigDecimal alcoholMin,
        @Schema(description = "최대 도수(%). 도수 정보가 없으면 null", example = "40.0", nullable = true)
        BigDecimal alcoholMax,
        @Schema(description = "용량 원문(파싱하지 않음, 복수 표기 가능). 없으면 null", example = "200ml",
                nullable = true)
        String volume,
        // ★목록·상세와 타입 통일: List<LiquorType>(enum). 값 문자열은 enum명과 1:1이라 wire JSON은 불변(["증류주"]),
        //   OpenAPI엔 주종 enum 값 목록이 드러나 프론트 코드젠이 문자열 대신 타입을 얻는다.
        @Schema(description = "취급 주종 목록(탁주·약주·청주·증류주·과실주·기타). 없으면 빈 배열",
                example = "[\"증류주\"]", requiredMode = Schema.RequiredMode.REQUIRED)
        List<LiquorType> liquorTypes,
        @Schema(description = "기능명세서의 주종별 제품 맛 태그. 기타 또는 주종 미상은 빈 배열",
                example = "[\"묵직함\",\"드라이함\"]", requiredMode = Schema.RequiredMode.REQUIRED)
        List<ProductFlavorTag> flavorTags,
        @Schema(description = "제품 소개(절단 되돌림/미노출 처리 후). 노출할 문장이 없으면 null", nullable = true)
        String description,
        @Schema(description = "최상위 수상 등급 뱃지 또는 '수상'. 수상 이력이 없으면 null", example = "대상",
                nullable = true)
        String awardBadge) {

    /** 설명 HTML 엔티티 디코딩 횟수. 관측된 최대 인코딩 깊이가 2라서 2회 고정이다(무한 반복 금지 — 표준 생성자 주석 참고). */
    private static final int UNESCAPE_PASSES = 2;

    /**
     * 표준 생성자 — {@code description}의 HTML 엔티티를 디코딩한다(응답 레벨 파생).
     * <p>
     * aT 원본이 이미 인코딩된 상태로 저장돼 있다({@code &quot;} 1중 · {@code &amp;quot;}·{@code &amp;#039;}
     * 2중이 한 양조장 안에서도 섞인다). 우리 파이프라인이 건 것이 아니라 원본 훼손이므로
     * <b>DB 컬럼({@code product_raw.description})은 고치지 않는다</b> —
     * {@link com.jeontongjuro.backend.brewery.query.BreweryDetailResponse#withHttpScheme homepage_url 스킴 보정}과
     * 같은 사상으로 raw 원문을 보존하고 화면에 나가는 값만 파생한다.
     * <p>
     * ★디코딩은 반드시 {@link DescriptionTruncationPolicy} 절단 판정이 <b>끝난 뒤</b>여야 한다.
     * 엔티티가 길이를 부풀려 78/79 게이트에 도달시키고 있어서({@code "&amp;quot;} 8자 · {@code "&quot;} 6자로 센다),
     * DB·파이프라인 단에서 먼저 디코딩하면 게이트를 통과해 <b>잘린 꼬리가 그대로 노출된다</b>.
     * 이 생성자는 정책을 거친 값만 받으므로 그 순서가 코드 배치로 강제된다.
     * <p>
     * ★2회 고정이고 무한 반복이 아니다. 표시 범위 실측상 인코딩 깊이는 최대 2이고
     * 2회가 멱등이다(3회 결과 = 2회 결과). 무한 반복은 종료 조건이 데이터에 의존해 테스트로 못 박을 수 없고,
     * 같은 aT 원본 계열에 리터럴 앰퍼샌드 실사용례가 있어(예: {@code "와인&재즈 페스티벌"})
     * 의도적으로 이스케이프된 {@code &amp;amp;}까지 끝까지 벗겨 원문을 훼손할 수 있다.
     * 깊이 3이 새로 들어오면 조용히 틀리는 대신 화면에 {@code &quot;}가 보여 눈에 띄게 틀린다.
     */
    public ProductCardResponse {
        description = unescapeEntities(description);
    }

    /** null 안전 디코딩. {@code description} 외의 필드는 건드리지 않는다. */
    private static String unescapeEntities(String value) {
        if (value == null) {
            return null;
        }
        String decoded = value;
        for (int i = 0; i < UNESCAPE_PASSES; i++) {
            decoded = HtmlUtils.htmlUnescape(decoded);
        }
        return decoded;
    }

    /** flavorTags 추가 전 호출부와의 소스 호환용 생성자. 태그는 주종에서 동일 규칙으로 계산한다. */
    public ProductCardResponse(
            Integer productId,
            String productName,
            BigDecimal alcoholMin,
            BigDecimal alcoholMax,
            String volume,
            List<LiquorType> liquorTypes,
            String description,
            String awardBadge
    ) {
        this(productId, productName, alcoholMin, alcoholMax, volume, liquorTypes,
                ProductFlavorTag.from(liquorTypes), description, awardBadge);
    }
}
