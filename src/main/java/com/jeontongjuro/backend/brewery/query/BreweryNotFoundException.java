package com.jeontongjuro.backend.brewery.query;

/**
 * 상세 조회 시 존재하지 않는 breweryId를 받았을 때 던지는 예외(404 매핑).
 * <p>
 * ★리스트 조회 PR에는 404 발생 상황이 없어 핸들러를 두지 않았으나, 상세 API는 경로 변수로 특정 자원을
 * 지목하므로 "없음"이 정상 흐름의 일부다. {@code GlobalExceptionHandler}가 {@code BREWERY_NOT_FOUND} 코드로 변환한다.
 */
public class BreweryNotFoundException extends RuntimeException {

    public BreweryNotFoundException(String message) {
        super(message);
    }
}
