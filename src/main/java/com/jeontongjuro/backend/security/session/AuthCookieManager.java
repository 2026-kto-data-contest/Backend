package com.jeontongjuro.backend.security.session;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Duration;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

@Component
public class AuthCookieManager {

    public static final String SESSION_COOKIE = "JT_SESSION";
    public static final String OAUTH_STATE_COOKIE = "JT_OAUTH_STATE";
    public static final String RETURN_TO_COOKIE = "JT_RETURN_TO";

    private final AuthProperties properties;

    public AuthCookieManager(AuthProperties properties) {
        this.properties = properties;
    }

    public void addSession(HttpServletResponse response, String token, long maxAgeSeconds) {
        add(response, SESSION_COOKIE, token, Duration.ofSeconds(maxAgeSeconds), true);
    }

    public void addOAuthState(HttpServletResponse response, String state) {
        add(response, OAUTH_STATE_COOKIE, state, Duration.ofMinutes(5), true);
    }

    public void addReturnTo(HttpServletResponse response, String returnTo) {
        String encoded = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(returnTo.getBytes(StandardCharsets.UTF_8));
        add(response, RETURN_TO_COOKIE, encoded, Duration.ofMinutes(5), true);
    }

    public String readReturnTo(HttpServletRequest request) {
        String encoded = read(request, RETURN_TO_COOKIE);
        if (encoded == null) {
            return null;
        }
        try {
            return new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException exception) {
            return null;
        }
    }

    public void clearOAuthCookies(HttpServletResponse response) {
        delete(response, OAUTH_STATE_COOKIE);
        delete(response, RETURN_TO_COOKIE);
    }

    public void clearSession(HttpServletResponse response) {
        delete(response, SESSION_COOKIE);
    }

    public String read(HttpServletRequest request, String name) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }
        return Arrays.stream(cookies)
                .filter(cookie -> name.equals(cookie.getName()))
                .map(Cookie::getValue)
                .findFirst()
                .orElse(null);
    }

    private void add(HttpServletResponse response, String name, String value, Duration maxAge, boolean httpOnly) {
        ResponseCookie cookie = ResponseCookie.from(name, value)
                .httpOnly(httpOnly)
                .secure(properties.cookieSecure())
                .sameSite("Lax")
                .path("/")
                .maxAge(maxAge)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void delete(HttpServletResponse response, String name) {
        add(response, name, "", Duration.ZERO, true);
    }
}
