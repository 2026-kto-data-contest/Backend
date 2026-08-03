package com.jeontongjuro.backend.security.session;

import java.util.Optional;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AuthSessionRepository extends JpaRepository<AuthSession, Long> {

    @EntityGraph(attributePaths = "member")
    Optional<AuthSession> findByTokenHash(String tokenHash);
}
