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
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
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

    // ── 좌표 파생 자리 (#28 8단계 지오코딩이 UPDATE) ───────────────────────
    /** 위도(카카오 y). 한국 범위 33~39 검증 통과분만 저장. ★address는 손대지 않는다. */
    @Column(name = "latitude", precision = 9, scale = 6)
    private BigDecimal latitude;

    /** 경도(카카오 x). 한국 범위 124~132 검증 통과분만 저장. */
    @Column(name = "longitude", precision = 9, scale = 6)
    private BigDecimal longitude;

    /** 좌표 출처(폴백 단계 식별). */
    @Enumerated(EnumType.STRING)
    @Column(name = "coord_source", length = 32)
    private CoordSource coordSource;

    /** 카카오 호출로 좌표 확정한 시각(UTC). */
    @Column(name = "geocoded_at")
    private OffsetDateTime geocodedAt;

    // ── 콘텐츠 매칭 파생 자리 (10단계 TourAPI 매칭이 UPDATE) ────────────────────
    /**
     * 확정 매칭 TourAPI contentid → tour_content.content_id(nullable — 미매칭 정상). ★source 컬럼을 두지
     * 않는다: 실사용 경로가 시드(MANUAL) 하나뿐이라 별도 컬럼은 {@code content_id IS NOT NULL}과 동치라
     * 무정보(수정2). 후속에 자동 확정 경로가 생기면 그때 additive로 추가한다.
     */
    @Column(name = "content_id", columnDefinition = "text")
    private String contentId;

    /** 매칭 확정 시각(UTC). "언제 확정했는가"가 정보다. */
    @Column(name = "content_matched_at")
    private OffsetDateTime contentMatchedAt;

    // ── 상세 필드 편입 자리 (#50) ──────────────────────────────────────────
    //   운영시간~수용인원·전화: 12단계(관광공사 detailIntro2)·13단계(카카오 시드)가 UPDATE.
    //   설립연도~특징서술: 14단계(농림부 2019 지정현황 시드)가 UPDATE. ★전부 전용 UPDATE 단계 —
    //   마스터 로드(1단계)는 기존 brewery_id를 skip하므로 여기서 채우면 영원히 null(백필 함정, #36 선례).
    /** 대표 전화(관광공사 우선, 카카오 보충). 없으면 null(union 52/59). */
    @Column(name = "phone", columnDefinition = "text")
    private String phone;

    /** 전화 출처(TOUR/KAKAO). phone이 null이면 null. */
    @Enumerated(EnumType.STRING)
    @Column(name = "phone_source", columnDefinition = "text")
    private PhoneSource phoneSource;

    /** 운영시간 원문(관광공사 usetime). 정형화 불가라 원문 보존, {@code <br>}만 개행 정규화. 19/59. */
    @Column(name = "operating_hours", columnDefinition = "text")
    private String operatingHours;

    /** 휴무 원문(관광공사 restdate). 17/59. */
    @Column(name = "rest_date", columnDefinition = "text")
    private String restDate;

    /** 주차 안내(관광공사 parking). 17/59. */
    @Column(name = "parking_info", columnDefinition = "text")
    private String parkingInfo;

    /** 수용인원 원문(관광공사 accomcount). ★"최대 80명" 형태라 TEXT — 숫자 아님. 5/59. */
    @Column(name = "accom_count", columnDefinition = "text")
    private String accomCount;

    /** 설립연도(농림부 2019, 4자리). 36/59. */
    @Column(name = "founded_year")
    private Integer foundedYear;

    /** 대표자명(농림부 2019). 36/59. */
    @Column(name = "representative_name", columnDefinition = "text")
    private String representativeName;

    /** 찾아가는 양조장 선정연도(농림부 2019, 4자리). 36/59. */
    @Column(name = "designated_year")
    private Integer designatedYear;

    /** 농림부 '특징' 서술 원문(2019). 36/59. */
    @Column(name = "designation_note", columnDefinition = "text")
    private String designationNote;

    /** 카카오 place 딥링크 URL(#54). 16단계 카카오 place 시드가 UPDATE. 없으면 null(PLACE 55/NO_MATCH 4). */
    @Column(name = "kakao_place_url", columnDefinition = "text")
    private String kakaoPlaceUrl;

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
        return b;
    }

    /**
     * 주소 파싱 결과 확정(파생값 UPDATE). SSOT = {@link BreweryRegionParser}. 순수함수 산출이라
     * 같은 주소면 항상 같은 값 → 재실행해도 결과 동일(멱등). 060~ 신규 양조장이 추가돼도 재실행하면 채워진다.
     */
    public void applyRegion(String sido, String region) {
        this.sido = sido;
        this.region = region;
    }

    /**
     * 3c-2 조인 결과 첫 확정(파생값 UPDATE). 2축 원칙: JOINED면 반드시 UNTAGGED(주종롤업 전),
     * JOINED인데 NA로 남는 모순 금지. 주종 롤업(TAGGED 판정)은 3d 몫 — 여기선 하지 않는다.
     */
    public void markJoined() {
        this.joinStatus = JoinStatus.JOINED;
        this.liquorStatus = LiquorStatus.UNTAGGED;
    }

    /**
     * 주종 롤업 결과 liquor_status 확정(파생값 UPDATE, 3d 7단계). ★2축 원칙을 이 메서드는 강제하지 않는다 —
     * 호출자(BreweryLiquorStatusUpdateService)가 UNJOINED→NA / JOINED→TAGGED|UNTAGGED만 넘긴다.
     * 순수 대입이라 같은 값이면 재실행해도 동일(멱등은 호출자가 unchanged로 스킵).
     */
    public void applyLiquorStatus(LiquorStatus liquorStatus) {
        this.liquorStatus = liquorStatus;
    }

    /**
     * 좌표 확정(파생값 UPDATE, #28 8단계). 호출자(BreweryCoordinateUpdateService)는 이미 좌표가 있으면
     * 이 메서드를 호출하지 않는다(멱등 skip). 범위 검증(위도 33~39·경도 124~132)은 산출측
     * (GeocodingService)이 통과시킨 값만 넘긴다 — 여기서는 순수 대입만 한다.
     */
    public void applyCoordinate(BigDecimal latitude, BigDecimal longitude,
                                CoordSource coordSource, OffsetDateTime geocodedAt) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.coordSource = coordSource;
        this.geocodedAt = geocodedAt;
    }

    /**
     * 콘텐츠 매칭 확정(파생값 UPDATE, 10단계). 순수 대입 — 호출자(BreweryContentMatchUpdateService)가 이미
     * 매칭이면 호출하지 않는다(멱등 skip). content_id의 tour_content 존재(FK)·200m 좌표 검증은 산출측
     * (TourMatchResolveService)이 통과시킨 값만 넘긴다. ★applyCoordinate와 대칭(대입 계층 원칙).
     */
    public void applyContentMatch(String contentId, OffsetDateTime contentMatchedAt) {
        this.contentId = contentId;
        this.contentMatchedAt = contentMatchedAt;
    }

    /**
     * 관광공사 detailIntro2 상세 확정(파생값 UPDATE, 12단계). 운영시간·휴무·주차·수용인원 순수 대입.
     * 값 없음은 호출자가 빈 문자열→null 정규화해 넘긴다(빈 문자열 저장 금지). 전화는 {@link #applyPhone}로 분리
     * (출처 기록이 필요하고 카카오 보충 경로와 대칭이라서).
     */
    public void applyTourDetail(String operatingHours, String restDate,
                                String parkingInfo, String accomCount) {
        this.operatingHours = operatingHours;
        this.restDate = restDate;
        this.parkingInfo = parkingInfo;
        this.accomCount = accomCount;
    }

    /**
     * 전화 확정(파생값 UPDATE). 출처를 함께 기록한다(TOUR 우선). 우선순위는 호출자가 강제한다 —
     * 12단계(TOUR)가 먼저 채우고, 13단계(카카오)는 {@code phone == null}일 때만 이 메서드를 호출한다.
     * 여기서는 순수 대입만 하며 우선순위 판정을 하지 않는다(applyCoordinate 대입 계층 원칙과 동일).
     */
    public void applyPhone(String phone, PhoneSource phoneSource) {
        this.phone = phone;
        this.phoneSource = phoneSource;
    }

    /**
     * 농림부 2019 지정현황 확정(파생값 UPDATE, 14단계). 설립연도·대표자·선정연도·특징서술 순수 대입.
     * ★소재지·주종·업체명은 받지 않는다(2019값이 마스터 오염 — 시드가 애초에 담지 않아 구조적으로 불가).
     */
    public void applyNonglim(Integer foundedYear, String representativeName,
                             Integer designatedYear, String designationNote) {
        this.foundedYear = foundedYear;
        this.representativeName = representativeName;
        this.designatedYear = designatedYear;
        this.designationNote = designationNote;
    }

    /**
     * 카카오 place URL 확정(파생값 UPDATE, 16단계). 순수 대입 — 경쟁 소스가 없어 우선순위 판정이 없다.
     * 멱등 스킵(같은 값이면 미적용)은 호출자(KakaoPlaceSeedLoadService)가 판단한다(대입 계층 원칙, applyPhone 대칭).
     */
    public void applyKakaoPlaceUrl(String kakaoPlaceUrl) {
        this.kakaoPlaceUrl = kakaoPlaceUrl;
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
