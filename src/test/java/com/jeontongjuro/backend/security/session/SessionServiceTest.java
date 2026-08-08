package com.jeontongjuro.backend.security.session;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.jeontongjuro.backend.member.Member;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class SessionServiceTest {

    private final AuthSessionRepository repository = mock(AuthSessionRepository.class);
    private final SessionService service =
            new SessionService(repository, new AuthProperties(Duration.ofDays(14), false, "Lax"));

    @Test
    void storesOnlyHashWhenCreatingSession() {
        Member member = mock(Member.class);

        String rawToken = service.create(member);

        ArgumentCaptor<AuthSession> captor = ArgumentCaptor.forClass(AuthSession.class);
        verify(repository).save(captor.capture());
        assertThat(rawToken).isNotBlank();
        assertThat(captor.getValue().getTokenHash()).isEqualTo(SessionService.hash(rawToken));
        assertThat(captor.getValue().getTokenHash()).doesNotContain(rawToken);
    }

    @Test
    void rejectsExpiredSession() {
        AuthSession expired = mock(AuthSession.class);
        when(repository.findByTokenHash(any())).thenReturn(Optional.of(expired));
        when(expired.isActive(any(LocalDateTime.class))).thenReturn(false);

        assertThat(service.authenticate("expired-token")).isEmpty();
    }
}
