package com.jeontongjuro.backend.experience;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Objects;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * aT 전통주 체험 프로그램 파생층(이슈 #52, 파이프라인 15단계). 양조장 1:N. 자연키 (brewery_id, program_name).
 * 공공데이터 15089109(기준일 2021-09-17)를 {@code experience_match_seed.json}으로 매칭해 편입한다.
 * <p>
 * ★저장 필드는 프로그램명·내용·장소·소요시간·비용뿐이다. 원본의 주소·연락처·홈페이지·주종·상시/예약방문은
 * 2021 값이라 마스터(2024-12-31)를 오염시키므로 <b>컬럼 자체를 만들지 않는다</b>(구조로 봉쇄). 전화번호도 금지.
 * <p>
 * ★삭제형 diff + 값 인지 갱신({@link ExperienceRollupService}): 목표 집합(API)과 현재 집합(DB)을 비교해
 * 삽입/삭제하고, 키가 같고 payload가 바뀐 행은 갱신한다. 특징 태그(payload 없음)와 달리 내용·비용이 변할 수
 * 있어 update 경로가 필요하다(update 없이는 옛 가격이 유령으로 남는다).
 * <p>
 * cost는 0(무료)과 null(미입력)을 엄격히 구분한다. duration은 원문 "H:MM" 문자열 보존(분 변환 안 함).
 */
@Entity
@Table(name = "brewery_experience",
        uniqueConstraints = @UniqueConstraint(name = "uq_brewery_experience_brewery_program",
                columnNames = {"brewery_id", "program_name"}),
        indexes = @Index(name = "ix_brewery_experience_brewery", columnList = "brewery_id"))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class BreweryExperience {

    /** 서러게이트 PK(의미 없음). 자연키는 (brewery_id, program_name). */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 체험 소유 양조장 → brewery.brewery_id로의 FK. */
    @Column(name = "brewery_id", nullable = false, columnDefinition = "text")
    private String breweryId;

    /** 체험프로그램명(원문). 자연키 일부. */
    @Column(name = "program_name", nullable = false, columnDefinition = "text")
    private String programName;

    /** 내용(원문). 실측 결측 0이나 방어적 nullable. 빈 문자열은 로더가 null로 정규화. */
    @Column(name = "content", columnDefinition = "text")
    private String content;

    /** 장소(원문). 빈 문자열은 null로 정규화. */
    @Column(name = "place", columnDefinition = "text")
    private String place;

    /** 소요시간 원문 "H:MM"(예 "2:00"·"8:00"). 결측 → null. 분 변환하지 않는다(원문이 더 정확·프론트 표기). */
    @Column(name = "duration", columnDefinition = "text")
    private String duration;

    /** 투어비용(원). 0=무료·null=미입력을 엄격 구분(둘을 같게 다루지 않는다). 원본 최대 350000. */
    @Column(name = "cost")
    private Integer cost;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    /** API 파생 체험 1건 신규 생성. content/place/duration은 로더가 빈 문자열→null 정규화 후 넘긴다. */
    public static BreweryExperience of(String breweryId, String programName,
                                       String content, String place, String duration, Integer cost) {
        BreweryExperience e = new BreweryExperience();
        e.breweryId = breweryId;
        e.programName = programName;
        e.content = content;
        e.place = place;
        e.duration = duration;
        e.cost = cost;
        return e;
    }

    /**
     * payload(내용·장소·소요시간·비용) 동등성 — diff의 unchanged/updated 판정 기준. 자연키(brewery_id·program_name)는
     * 이미 같은 행끼리 비교하므로 제외한다. cost의 0 vs null도 {@link Objects#equals}로 정확히 구분된다.
     */
    public boolean samePayload(String content, String place, String duration, Integer cost) {
        return Objects.equals(this.content, content)
                && Objects.equals(this.place, place)
                && Objects.equals(this.duration, duration)
                && Objects.equals(this.cost, cost);
    }

    /** 값 인지 diff의 update 경로 — payload를 목표 값으로 덮어쓴다(자연키는 불변). @PreUpdate가 updated_at을 올린다. */
    public void updatePayload(String content, String place, String duration, Integer cost) {
        this.content = content;
        this.place = place;
        this.duration = duration;
        this.cost = cost;
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
