package com.jeontongjuro.backend.auth.kakao;

import com.jeontongjuro.backend.auth.exception.AuthException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class KakaoClient {

    private final RestClient restClient;
    private final KakaoProperties properties;

    public KakaoClient(RestClient.Builder builder, KakaoProperties properties) {
        this.restClient = builder.build();
        this.properties = properties;
    }

    public KakaoUserResponse getUser(String authorizationCode) {
        properties.validateConfigured();
        try {
            KakaoTokenResponse token = requestToken(authorizationCode);
            if (token == null || token.accessToken() == null) {
                throw kakaoFailure();
            }
            KakaoUserResponse user = restClient.get()
                    .uri("https://kapi.kakao.com/v2/user/me")
                    .header(HttpHeaders.AUTHORIZATION, "Bearer " + token.accessToken())
                    .retrieve()
                    .body(KakaoUserResponse.class);
            if (user == null || user.id() == null) {
                throw kakaoFailure();
            }
            return user;
        } catch (RestClientException exception) {
            throw kakaoFailure();
        }
    }

    private KakaoTokenResponse requestToken(String authorizationCode) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("grant_type", "authorization_code");
        form.add("client_id", properties.restApiKey());
        form.add("redirect_uri", properties.redirectUri());
        form.add("code", authorizationCode);
        if (properties.clientSecret() != null && !properties.clientSecret().isBlank()) {
            form.add("client_secret", properties.clientSecret());
        }
        return restClient.post()
                .uri("https://kauth.kakao.com/oauth/token")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .body(KakaoTokenResponse.class);
    }

    private AuthException kakaoFailure() {
        return new AuthException(HttpStatus.BAD_GATEWAY, "KAKAO_AUTH_FAILED", "카카오 로그인 처리에 실패했습니다.");
    }
}
