package com.jeontongjuro.backend.liquortype;

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
 * 주종 추론 태깅 파생층(3d, 이슈 #15 1차). product_brewery_link로 연결된 제품(366)만 대상으로,
 * 키워드 사전 매칭 결과를 담는다. ★"확정"이 아니라 "추론+검수 대상 추출" — 전 AUTO행 recheck_flag=true.
 * <p>
 * 다대다: 한 제품이 여러 주종 키워드에 걸리면 (source_row_ref, liquor_type)로 여러 행이 정상.
 * MANUAL wins: 같은 (제품, 주종)에 MANUAL이 있으면 AUTO는 그 조합을 적재하지 않는다(uq가 최종 방어선).
 */
@Entity
@Table(name = "product_liquor_type",
        uniqueConstraints = @UniqueConstraint(name = "uq_product_liquor_type_row_type",
                columnNames = {"source_row_ref", "liquor_type"}),
        indexes = @Index(name = "ix_product_liquor_type_brewery", columnList = "brewery_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductLiquorType {

    /** 서러게이트 PK(의미 없음). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 원본 product raw 추적 참조 = product_brewery_link.source_row_ref(전역 0-based source_row_index). */
    @Column(name = "source_row_ref", nullable = false)
    private Integer sourceRowRef;

    /** ★조회 편의 비정규화(양조장별 집계를 link 재조인 없이). brewery.brewery_id로의 FK. */
    @Column(name = "brewery_id", nullable = false, columnDefinition = "text")
    private String breweryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "liquor_type", nullable = false, columnDefinition = "text")
    private LiquorType liquorType;

    @Enumerated(EnumType.STRING)
    @Column(name = "source", nullable = false, columnDefinition = "text")
    private LiquorTagSource source;

    /** 재점검 필요. AUTO 전건 true(미검증), MANUAL false. */
    @Column(name = "recheck_flag", nullable = false)
    private boolean recheckFlag;

    /** C-4 향미이중 억제 표시(행은 남기고 표시만 — 최종 판정은 검수). */
    @Column(name = "suppressed_from_tab", nullable = false)
    private boolean suppressedFromTab;

    /** 어느 키워드에 걸렸는지(검수·디버깅 근거). MANUAL은 null. */
    @Column(name = "matched_keyword", columnDefinition = "text")
    private String matchedKeyword;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** AUTO 추론 태그. recheck_flag=true 고정. */
    public static ProductLiquorType auto(Integer sourceRowRef, String breweryId, LiquorType liquorType,
                                         boolean suppressedFromTab, String matchedKeyword) {
        ProductLiquorType t = new ProductLiquorType();
        t.sourceRowRef = sourceRowRef;
        t.breweryId = breweryId;
        t.liquorType = liquorType;
        t.source = LiquorTagSource.AUTO;
        t.recheckFlag = true;
        t.suppressedFromTab = suppressedFromTab;
        t.matchedKeyword = matchedKeyword;
        return t;
    }

    /** MANUAL 수기 태그. recheck_flag=false, matched_keyword=null 고정. */
    public static ProductLiquorType manual(Integer sourceRowRef, String breweryId, LiquorType liquorType) {
        ProductLiquorType t = new ProductLiquorType();
        t.sourceRowRef = sourceRowRef;
        t.breweryId = breweryId;
        t.liquorType = liquorType;
        t.source = LiquorTagSource.MANUAL;
        t.recheckFlag = false;
        t.suppressedFromTab = false;
        t.matchedKeyword = null;
        return t;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }
}
