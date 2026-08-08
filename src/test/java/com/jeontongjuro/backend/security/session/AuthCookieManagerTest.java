package com.jeontongjuro.backend.security.session;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletResponse;

class AuthCookieManagerTest {

    @Test
    void productionCookieUsesConfiguredSameSiteAndSecure() {
        AuthCookieManager manager = new AuthCookieManager(
                new AuthProperties(Duration.ofDays(14), true, "None"));
        MockHttpServletResponse response = new MockHttpServletResponse();

        manager.addSession(response, "session-token", 3600);

        assertThat(response.getHeader("Set-Cookie"))
                .contains("JT_SESSION=session-token")
                .contains("Secure")
                .contains("HttpOnly")
                .contains("SameSite=None");
    }
}
