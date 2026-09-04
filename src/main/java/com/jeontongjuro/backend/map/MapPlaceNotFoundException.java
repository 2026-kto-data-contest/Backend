package com.jeontongjuro.backend.map;

/**
 * 지도 장소 상세 조회에서 요청한 장소를 찾을 수 없을 때 던지는 예외(404 매핑).
 * <p>
 * 다음 네 경우를 하나의 코드({@code MAP_PLACE_NOT_FOUND})로 수렴시킨다:
 * <ul>
 *   <li>{@code category=BREWERY} 인데 해당 {@code BRW-xxx} 양조장이 없음</li>
 *   <li>그 외 category 인데 해당 {@code content_id} 의 관광 콘텐츠가 없음</li>
 *   <li>콘텐츠 분류 결과가 목록에 노출되지 않는 유형(ETC)</li>
 *   <li>요청한 category 와 실제 분류 결과가 다름 — 목록에 그 조합으로 존재하지 않는 장소다</li>
 * </ul>
 * ★세 번째·네 번째를 400이 아니라 404로 두는 이유: 파라미터 자체는 허용 집합 안의 값이고,
 * "그 조합의 자원이 없다"는 뜻이므로 자원 부재로 보는 편이 목록 API와의 계약에 맞다.
 */
public class MapPlaceNotFoundException extends RuntimeException {

    public MapPlaceNotFoundException(String message) {
        super(message);
    }
}
