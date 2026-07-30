package com.jeontongjuro.backend.override;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 수기 보정 원장(3c-1 시드 9행). assignment §결정4 확정본을 그대로 적재한다(재판정·증감 없음).
 * <p>
 * 참조키는 brewery_id(이름 아님) — {@code brewery.brewery_id}로의 FK. 서비스는 적재 전 6개 BRW가
 * brewery에 실재하는지 검증하고, DB FK가 최종 방어선이다.
 * <p>
 * ★이번(3c-1)은 원장 "적재"까지다. 이 override를 실제 조인/재매핑에 적용하는 것은 후속 단계(3c-2)다.
 */
@Entity
@Table(name = "manual_override",
        uniqueConstraints = @UniqueConstraint(name = "uq_manual_override_match",
                columnNames = {"match_key_kind", "match_key"}),
        indexes = @Index(name = "ix_manual_override_brewery", columnList = "brewery_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ManualOverride {

    /** 서러게이트 PK(의미 없음). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(name = "override_type", nullable = false, columnDefinition = "text")
    private OverrideType overrideType;

    /** 매칭 대상 키(BREWERY_NORM=양조장명 norm / PRODUCT_NAME=제품명). */
    @Column(name = "match_key", nullable = false, columnDefinition = "text")
    private String matchKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "match_key_kind", nullable = false, columnDefinition = "text")
    private MatchKeyKind matchKeyKind;

    /** 연결 확정 BRW. brewery.brewery_id로의 FK(참조키=이름 아님). */
    @Column(name = "brewery_id", nullable = false, columnDefinition = "text")
    private String breweryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "reason", nullable = false, columnDefinition = "text")
    private OverrideReason reason;

    /** 재점검 필요 여부(조옥화 2행 true). */
    @Column(name = "recheck_flag", nullable = false)
    private boolean recheckFlag;

    /** 원본 product raw명(추적용). */
    @Column(name = "source_raw_name", nullable = false, columnDefinition = "text")
    private String sourceRawName;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static ManualOverride seed(OverrideType overrideType, String matchKey, MatchKeyKind matchKeyKind,
                                      String breweryId, OverrideReason reason, boolean recheckFlag,
                                      String sourceRawName) {
        ManualOverride o = new ManualOverride();
        o.overrideType = overrideType;
        o.matchKey = matchKey;
        o.matchKeyKind = matchKeyKind;
        o.breweryId = breweryId;
        o.reason = reason;
        o.recheckFlag = recheckFlag;
        o.sourceRawName = sourceRawName;
        return o;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }
}
