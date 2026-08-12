package com.jeontongjuro.backend.tour;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * TourAPI 콘텐츠 캐시 dimension(9단계 근접 캐싱 산물 + 10단계 시드 접지). PK = contentid 자연키.
 * <p>
 * ★컬럼 생성 ≠ 값 확정: {@code overview}·{@code overviewFetchedAt}는 컬럼만 두고 이번 사이클엔 채우지
 * 않는다(둘 다 null 유지). detailCommon2로 접지할 때도 overview는 저장하지 않는다 — 일부만 채우면
 * overviewFetchedAt이 "안 받음/받았는데 없음"을 구분 못 해 판별력을 잃기 때문(수정4).
 * <p>
 * ★content_type_id에 enum/CHECK를 두지 않는다(교정4 — 표본 1곳 관측 8종으로 타 지역 미관측 타입 배제 금지).
 * 좌표는 산출측({@link TourGeoValidator})이 범위 검증·scale한 값만 대입한다(mapx=경도·mapy=위도 축 확정).
 */
@Entity
@Table(name = "tour_content", indexes = {
        @Index(name = "ix_tour_content_type", columnList = "content_type_id")
})
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TourContent {

    @Id
    @Column(name = "content_id", columnDefinition = "text")
    private String contentId;

    @Column(name = "content_type_id", nullable = false, columnDefinition = "text")
    private String contentTypeId;

    @Column(name = "title", columnDefinition = "text")
    private String title;

    @Column(name = "addr1", columnDefinition = "text")
    private String addr1;

    @Column(name = "addr2", columnDefinition = "text")
    private String addr2;

    @Column(name = "zipcode", columnDefinition = "text")
    private String zipcode;

    @Column(name = "area_code", columnDefinition = "text")
    private String areaCode;

    @Column(name = "sigungu_code", columnDefinition = "text")
    private String sigunguCode;

    @Column(name = "cat1", columnDefinition = "text")
    private String cat1;

    @Column(name = "cat2", columnDefinition = "text")
    private String cat2;

    @Column(name = "cat3", columnDefinition = "text")
    private String cat3;

    @Column(name = "lcls_systm1", columnDefinition = "text")
    private String lclsSystm1;

    @Column(name = "lcls_systm2", columnDefinition = "text")
    private String lclsSystm2;

    @Column(name = "lcls_systm3", columnDefinition = "text")
    private String lclsSystm3;

    @Column(name = "ldong_regn_cd", columnDefinition = "text")
    private String lDongRegnCd;

    @Column(name = "ldong_signgu_cd", columnDefinition = "text")
    private String lDongSignguCd;

    /** mapy(위도). 범위 33~39 검증 통과분만. */
    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    /** mapx(경도). 범위 124~132 검증 통과분만. */
    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    @Column(name = "mlevel", columnDefinition = "text")
    private String mlevel;

    @Column(name = "first_image", columnDefinition = "text")
    private String firstImage;

    @Column(name = "first_image2", columnDefinition = "text")
    private String firstImage2;

    @Column(name = "cpyrht_div_cd", columnDefinition = "text")
    private String cpyrhtDivCd;

    @Column(name = "source_created_time", columnDefinition = "text")
    private String sourceCreatedTime;

    /** TourAPI modifiedtime 원문 — upsert 변경 감지 기준. */
    @Column(name = "source_modified_time", columnDefinition = "text")
    private String sourceModifiedTime;

    /** ★이번 미충전(null 유지). */
    @Column(name = "overview", columnDefinition = "text")
    private String overview;

    /** ★이번 미충전(null 유지 — 판별력 보존). */
    @Column(name = "overview_fetched_at")
    private java.time.OffsetDateTime overviewFetchedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** 신규 적재 — 원문 필드 + 검증 좌표. overview는 채우지 않는다. */
    public static TourContent create(TourContentRow row, BigDecimal latitude, BigDecimal longitude) {
        TourContent c = new TourContent();
        c.contentId = row.contentId();
        c.applyMutable(row, latitude, longitude);
        return c;
    }

    /**
     * upsert 갱신 — modifiedtime 변경 시 가변 필드 재적용(멱등: 호출자가 변경 없으면 호출 안 함).
     * overview·overviewFetchedAt은 손대지 않는다.
     */
    public void applyUpdate(TourContentRow row, BigDecimal latitude, BigDecimal longitude) {
        applyMutable(row, latitude, longitude);
    }

    /** modifiedtime이 저장값과 같은지(멱등 판정용). */
    public boolean isUnchanged(String modifiedTime) {
        return Objects.equals(this.sourceModifiedTime, modifiedTime);
    }

    /**
     * overview 백필(#50 12단계). 기존 행이 이미 존재해 upsert가 건너뛰는 함정을 피해 별도 UPDATE 경로로
     * 소개글을 채운다. {@code overviewFetchedAt}을 함께 찍어 "채웠음"을 표시한다 — 재실행 시 멱등 skip 근거.
     * ★upsert(applyUpdate)는 overview를 손대지 않으므로 여기서 채운 값이 재수집에도 보존된다.
     */
    public void backfillOverview(String overview, java.time.OffsetDateTime fetchedAt) {
        this.overview = overview;
        this.overviewFetchedAt = fetchedAt;
    }

    /** overview 백필 완료 여부(fetchedAt 존재 = 이미 채움 → 멱등 skip). */
    public boolean isOverviewFetched() {
        return this.overviewFetchedAt != null;
    }

    private void applyMutable(TourContentRow row, BigDecimal latitude, BigDecimal longitude) {
        this.contentTypeId = row.contentTypeId();
        this.title = row.title();
        this.addr1 = row.addr1();
        this.addr2 = row.addr2();
        this.zipcode = row.zipcode();
        this.areaCode = row.areaCode();
        this.sigunguCode = row.sigunguCode();
        this.cat1 = row.cat1();
        this.cat2 = row.cat2();
        this.cat3 = row.cat3();
        this.lclsSystm1 = row.lclsSystm1();
        this.lclsSystm2 = row.lclsSystm2();
        this.lclsSystm3 = row.lclsSystm3();
        this.lDongRegnCd = row.lDongRegnCd();
        this.lDongSignguCd = row.lDongSignguCd();
        this.latitude = latitude;
        this.longitude = longitude;
        this.mlevel = row.mlevel();
        this.firstImage = row.firstImage();
        this.firstImage2 = row.firstImage2();
        this.cpyrhtDivCd = row.cpyrhtDivCd();
        this.sourceCreatedTime = row.createdTime();
        this.sourceModifiedTime = row.modifiedTime();
    }

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now(ZoneOffset.UTC);
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = LocalDateTime.now(ZoneOffset.UTC);
    }
}
