package com.jeontongjuro.backend.security.session;

import com.jeontongjuro.backend.member.Member;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class SessionService {

    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final AuthSessionRepository authSessionRepository;
    private final AuthProperties authProperties;

    @Transactional
    public String create(Member member) {
        byte[] randomBytes = new byte[32];
        SECURE_RANDOM.nextBytes(randomBytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(randomBytes);
        LocalDateTime expiresAt = LocalDateTime.now(ZoneOffset.UTC).plus(authProperties.sessionDuration());
        authSessionRepository.save(AuthSession.create(member, hash(rawToken), expiresAt));
        return rawToken;
    }

    @Transactional(readOnly = true)
    public Optional<Member> authenticate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        return authSessionRepository.findByTokenHash(hash(rawToken))
                .filter(session -> session.isActive(now))
                .map(AuthSession::getMember);
    }

    @Transactional
    public void revoke(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return;
        }
        authSessionRepository.findByTokenHash(hash(rawToken)).ifPresent(AuthSession::revoke);
    }

    public long maxAgeSeconds() {
        return authProperties.sessionDuration().toSeconds();
    }

    static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 알고리즘을 사용할 수 없습니다.", exception);
        }
    }
}
