package com.jeontongjuro.backend.brewery;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 파생층 brewery 마스터 dimension(3c-1). 시드 = 채번원장(brewery_id_ledger.json) + 골든 raw 속성.
 * <p>
 * ★컬럼 생성 ≠ 값 확정. 이 엔티티는 "원장·raw에서 그대로 오는 값"만 적재한다:
 * brewery_id·business_name·norm(원장), address·homepageUrl·viewCount(raw 원문),
 * reservation/always visit(3-state 변환). 계산이 필요한 파생 컬럼(sido·region 주소파싱,
 * joinStatus 조인, liquorStatus 주종롤업)은 <b>컬럼만</b> 두고 이번엔 계산하지 않는다 —
 * 초기값(UNJOINED/NA)·nullable로 적재하고 후속 단계가 UPDATE한다.
 * <p>
 * PK는 BRW-xxx 자연키 직접 PK(서러게이트 없음·불변·append-only). business_name은 개명 가능한
 * 표시 자연키라 UNIQUE 제약을 걸지 않는다(조회 인덱스만).
 */
@Entity
@Table(name = "brewery", indexes = {
        @Index(name = "ix_brewery_business_name", columnList = "business_name"),
        @Index(name = "ix_brewery_norm", columnList = "norm")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Brewery {

    /** BRW-xxx 자연키 PK(원장). 서러게이트 금지·불변. */
    @Id
    @Column(name = "brewery_id", columnDefinition = "text")
    private String breweryId;

    /** 상호명(골든 원문 NFC). UNIQUE 금지(개명 대비), 조회 인덱스만. */
    @Column(name = "business_name", nullable = false, columnDefinition = "text")
    private String businessName;

    /** 원장 norm(정규화명) — 조인 매칭키 재현용. */
    @Column(name = "norm", nullable = false, columnDefinition = "text")
    private String norm;

    /** 골든 raw 주소 원문(무손실, 골든 null 0건). */
    @Column(name = "address", nullable = false, columnDefinition = "text")
    private String address;

    /** 골든 raw 홈페이지(null 1건 존재). */
    @Column(name = "homepage_url", columnDefinition = "text")
    private String homepageUrl;

    /** 골든 raw 조회수(원본 number). */
    @Column(name = "view_count", nullable = false)
    private Long viewCount;

    /** 예약방문 3-state(raw null→UNKNOWN). */
    @Enumerated(EnumType.STRING)
    @Column(name = "reservation_visit_state", nullable = false, columnDefinition = "text")
    private VisitState reservationVisitState;

    /** 상시방문 3-state(raw null→UNKNOWN). */
    @Enumerated(EnumType.STRING)
    @Column(name = "always_visit_state", nullable = false, columnDefinition = "text")
    private VisitState alwaysVisitState;

    // ── 계산 파생 자리 (★이번 미계산, 후속 UPDATE) ─────────────────────────
    /** 주소 파싱 결과 자리. ★이번엔 파싱하지 않는다(다음 단계). */
    @Column(name = "sido", columnDefinition = "text")
    private String sido;

    /** 광역권 매핑 자리. ★이번엔 매핑하지 않는다(다음 단계). */
    @Column(name = "region", columnDefinition = "text")
    private String region;

    /** 초기값 UNJOINED. 3c-2 조인이 UPDATE. */
    @Enumerated(EnumType.STRING)
    @Column(name = "join_status", nullable = false, columnDefinition = "text")
    private JoinStatus joinStatus;

    /** 초기값 NA. 3d 주종롤업이 UPDATE. */
    @Enumerated(EnumType.STRING)
    @Column(name = "liquor_status", nullable = false, columnDefinition = "text")
    private LiquorStatus liquorStatus;

    /** 소스 미확정(C-10) 격리 자리. */
    @Column(name = "image_url", columnDefinition = "text")
    private String imageUrl;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /**
     * 적재 팩토리 — 원장·raw에서 그대로 오는 값만 채운다. 계산 파생은 초기값(UNJOINED/NA)·null로 둔다.
     */
    public static Brewery seed(String breweryId, String businessName, String norm,
                               String address, String homepageUrl, Long viewCount,
                               VisitState reservationVisitState, VisitState alwaysVisitState) {
        Brewery b = new Brewery();
        b.breweryId = breweryId;
        b.businessName = businessName;
        b.norm = norm;
        b.address = address;
        b.homepageUrl = homepageUrl;
        b.viewCount = viewCount;
        b.reservationVisitState = reservationVisitState;
        b.alwaysVisitState = alwaysVisitState;
        // 계산 파생: 컬럼만 — 초기값. 후속 단계가 UPDATE.
        b.sido = null;
        b.region = null;
        b.joinStatus = JoinStatus.UNJOINED;
        b.liquorStatus = LiquorStatus.NA;
        b.imageUrl = null;
        return b;
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (createdAt == null) {
            createdAt = now;
        }
        if (updatedAt == null) {
            updatedAt = now;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
