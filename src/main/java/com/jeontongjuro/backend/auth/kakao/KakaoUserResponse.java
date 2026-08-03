package com.jeontongjuro.backend.auth.kakao;

import com.fasterxml.jackson.annotation.JsonProperty;

public record KakaoUserResponse(Long id, @JsonProperty("kakao_account") KakaoAccount account) {

    public record KakaoAccount(Profile profile, String email) {
    }

    public record Profile(String nickname) {
    }

    public String nickname() {
        return account != null && account.profile() != null ? account.profile().nickname() : null;
    }

    public String email() {
        return account != null ? account.email() : null;
    }
}
