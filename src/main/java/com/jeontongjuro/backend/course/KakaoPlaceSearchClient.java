package com.jeontongjuro.backend.course;

import java.math.BigDecimal;
import java.util.Optional;

public interface KakaoPlaceSearchClient {

    Optional<KakaoPlaceMatch> findPlace(String name, BigDecimal latitude, BigDecimal longitude);
}
