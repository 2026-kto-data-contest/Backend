package com.jeontongjuro.backend.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import org.junit.jupiter.api.Test;

class AuthPropertiesTest {

    @Test
    void normalizesSameSiteValue() {
        AuthProperties properties = new AuthProperties(Duration.ofDays(14), true, "none");

        assertThat(properties.cookieSameSite()).isEqualTo("None");
    }

    @Test
    void sameSiteNoneRequiresSecureCookie() {
        assertThatThrownBy(() -> new AuthProperties(Duration.ofDays(14), false, "None"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cookie-secure=true");
    }
}
