package com.jeontongjuro.backend.feature;

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
 * 양조장 특징 태그 파생층(이슈 #43). 양조장 grain: 한 양조장이 특징 F를 가지면 (brewery_id, feature_type) 1행.
 * 여러 제품이 같은 특징에 걸려도 대표 1건(source_row_ref·matched_keyword)만 근거로 보관한다.
 * <p>
 * ★grain 트레이드오프: 수상이력·유기농·대통령상은 제품 단위 의미가 있으나 대표 1건으로 축소된다.
 * product_raw가 원천이라 필요 시 제품 grain 재파생이 가능하다(이 PR은 배지 노출이 목적이라 양조장 grain).
 * <p>
 * ★확정 파생 + 삭제형 diff: {@link FeatureRollupService}가 목표 집합과 현재 집합을 비교해 삽입/삭제하므로
 * 규칙 변경·원본 갱신 시 유령 행이 남지 않는다(주종의 삽입 전용과 다르다 — 검수/MANUAL 없음이 근거).
 */
@Entity
@Table(name = "brewery_feature_tag",
        uniqueConstraints = @UniqueConstraint(name = "uq_brewery_feature_tag_brewery_type",
                columnNames = {"brewery_id", "feature_type"}),
        indexes = @Index(name = "ix_brewery_feature_tag_brewery", columnList = "brewery_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BreweryFeatureTag {

    /** 서러게이트 PK(의미 없음). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 태그 소유 양조장 → brewery.brewery_id로의 FK. */
    @Column(name = "brewery_id", nullable = false, columnDefinition = "text")
    private String breweryId;

    @Enumerated(EnumType.STRING)
    @Column(name = "feature_type", nullable = false, columnDefinition = "text")
    private FeatureType featureType;

    /** 이 특징을 유발한 대표 제품 raw 참조(= product_brewery_link.source_row_ref). 디버깅·검증 근거. */
    @Column(name = "source_row_ref", nullable = false)
    private Integer sourceRowRef;

    /**
     * 어느 키워드에 걸렸는지(디버깅 근거). 키워드 기반 특징은 해당 키워드('식품명인'·'유기농'·'무형문화재'·'대통령상'),
     * 수상이력은 존재(presence) 규칙이라 키워드 개념이 없어 null.
     */
    @Column(name = "matched_keyword", columnDefinition = "text")
    private String matchedKeyword;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 규칙 파생 태그 생성. */
    public static BreweryFeatureTag of(String breweryId, FeatureType featureType,
                                       Integer sourceRowRef, String matchedKeyword) {
        BreweryFeatureTag t = new BreweryFeatureTag();
        t.breweryId = breweryId;
        t.featureType = featureType;
        t.sourceRowRef = sourceRowRef;
        t.matchedKeyword = matchedKeyword;
        return t;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }
}
