package com.jeontongjuro.backend.tour;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 9단계 근접 캐싱 산물 — 한 양조장 반경(20km 실측) 내 콘텐츠 1행. 복합 PK (brewery_id, content_id).
 * <p>
 * ★자기 자신 제외 플래그 컬럼을 두지 않는다 — {@code brewery_nearby.content_id = brewery.content_id}
 * 파생으로 판정한다. 양조장 A가 양조장 B의 반경에 섞이는 것은 정상(코스 시나리오)이라 파생이라야 보존된다.
 * {@code distanceM}은 TourAPI 응답 dist(m) — 전제 12(200m 이내) 검증의 캐시 커버분 근거다.
 */
@Entity
@Table(name = "brewery_nearby", indexes = {
        @Index(name = "ix_brewery_nearby_brewery", columnList = "brewery_id"),
        @Index(name = "ix_brewery_nearby_content", columnList = "content_id")
})
@IdClass(BreweryNearbyId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BreweryNearby {

    @Id
    @Column(name = "brewery_id", columnDefinition = "text")
    private String breweryId;

    @Id
    @Column(name = "content_id", columnDefinition = "text")
    private String contentId;

    /** TourAPI 응답 dist(m). 정렬·200m 검증 근거. */
    @Column(name = "distance_m")
    private BigDecimal distanceM;

    /** 이 캐싱에 쓴 반경(tour.api.radius-m 스냅샷). */
    @Column(name = "radius_m", nullable = false)
    private Integer radiusM;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public static BreweryNearby create(String breweryId, String contentId,
                                       BigDecimal distanceM, int radiusM) {
        BreweryNearby n = new BreweryNearby();
        n.breweryId = breweryId;
        n.contentId = contentId;
        n.distanceM = distanceM;
        n.radiusM = radiusM;
        return n;
    }

    @PrePersist
    void onCreate() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }
}
