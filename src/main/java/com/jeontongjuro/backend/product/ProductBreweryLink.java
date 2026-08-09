package com.jeontongjuro.backend.product;

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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * product→brewery 조인 결과 파생층(3c-2). product raw 1215건 중 brewery 59에 실제 연결되는
 * 행(AUTO+override)만 담는다 — UNMATCHED는 이번엔 적재하지 않는다(raw에 이미 무손실 존재).
 * <p>
 * brewery_id는 ★nullable — 미래 1215 전체 확장(UNMATCHED 포함) 대비 스키마이며, 이번 적재분은
 * 전부 non-null이다. MANUAL wins: override가 AUTO 매칭 결과를 덮어쓴 뒤 최종값만 담는다(중간값 보존 없음).
 */
@Entity
@Table(name = "product_brewery_link",
        uniqueConstraints = @UniqueConstraint(name = "uq_product_brewery_link_source_row",
                columnNames = {"source_row_ref"}),
        indexes = @Index(name = "ix_product_brewery_link_brewery", columnList = "brewery_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductBreweryLink {

    /** 서러게이트 PK(의미 없음). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 원본 product raw 추적 참조 = source_row_index(전역 0-based 위치). */
    @Column(name = "source_row_ref", nullable = false)
    private Integer sourceRowRef;

    @Column(name = "product_name", nullable = false, columnDefinition = "text")
    private String productName;

    /** 원본 '양조장' 필드(원문, null 가능 — ROW_PIN 대상은 null). */
    @Column(name = "brewery_name_raw", columnDefinition = "text")
    private String breweryNameRaw;

    /** breweryNameRaw의 정규화 결과(3d 재계산 방지용 저장값). breweryNameRaw가 null이면 null. */
    @Column(name = "product_norm", columnDefinition = "text")
    private String productNorm;

    /** 연결 확정 BRW. brewery.brewery_id로의 FK. ★nullable(미래 UNMATCHED 확장 대비, 이번 적재분은 non-null). */
    @Column(name = "brewery_id", columnDefinition = "text")
    private String breweryId;

    /** 연결 경로(MANUAL wins 최종값). */
    @Enumerated(EnumType.STRING)
    @Column(name = "join_source", nullable = false, columnDefinition = "text")
    private JoinSource joinSource;

    /** 도수 파싱 최솟값(#35). ★nullable — 파싱 실패 시 null. 단일값 제품은 min==max. */
    @Column(name = "alcohol_min", precision = 4, scale = 1)
    private BigDecimal alcoholMin;

    /** 도수 파싱 최댓값(#35). ★nullable. 저/중/고 구간은 DB에 저장하지 않고 조회 시점에 계산한다. */
    @Column(name = "alcohol_max", precision = 4, scale = 1)
    private BigDecimal alcoholMax;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static ProductBreweryLink of(Integer sourceRowRef, String productName, String breweryNameRaw,
                                        String productNorm, String breweryId, JoinSource joinSource,
                                        BigDecimal alcoholMin, BigDecimal alcoholMax) {
        ProductBreweryLink link = new ProductBreweryLink();
        link.sourceRowRef = sourceRowRef;
        link.productName = productName;
        link.breweryNameRaw = breweryNameRaw;
        link.productNorm = productNorm;
        link.breweryId = breweryId;
        link.joinSource = joinSource;
        link.alcoholMin = alcoholMin;
        link.alcoholMax = alcoholMax;
        return link;
    }

    /**
     * 도수 백필(#35). 증분 조인이 기존 링크를 skippedExisting으로 건너뛰므로, 이미 링크가 존재하는 DB는
     * 신규 적재 경로를 못 타 alcohol_min/max가 null로 남는다. 이를 채우는 멱등 경로 — 호출 측에서 둘 다
     * null일 때만 부른다. @Transactional 안에서 managed 엔티티를 갱신하면 dirty checking으로 flush된다.
     */
    public void backfillAlcohol(BigDecimal alcoholMin, BigDecimal alcoholMax) {
        this.alcoholMin = alcoholMin;
        this.alcoholMax = alcoholMax;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }
}
