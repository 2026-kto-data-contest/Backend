package com.jeontongjuro.backend.auth.kakao;

import com.fasterxml.jackson.annotation.JsonProperty;

record KakaoTokenResponse(
        @JsonProperty("access_token") String accessToken,
        @JsonProperty("token_type") String tokenType,
        @JsonProperty("expires_in") Integer expiresIn
) {
}
