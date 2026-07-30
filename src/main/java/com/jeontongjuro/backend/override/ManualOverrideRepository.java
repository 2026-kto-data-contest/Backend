package com.jeontongjuro.backend.override;

import org.springframework.data.jpa.repository.JpaRepository;

/** 수기 보정 원장 리포지토리. */
public interface ManualOverrideRepository extends JpaRepository<ManualOverride, Long> {

    boolean existsByMatchKeyKindAndMatchKey(MatchKeyKind matchKeyKind, String matchKey);
}
