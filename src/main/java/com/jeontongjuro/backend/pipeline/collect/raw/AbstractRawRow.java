package com.jeontongjuro.backend.pipeline.collect.raw;

import jakarta.persistence.Column;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.MappedSuperclass;
import jakarta.persistence.PrePersist;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import lombok.Getter;

/**
 * RAW 적재층 공통 컬럼. 원본 무손실 보관 전용이며 정규화·조인 로직은 이 층에 두지 않는다.
 */
@MappedSuperclass
@Getter
public abstract class AbstractRawRow {

    /** 서러게이트 PK(의미 없음). schema.sql의 BIGSERIAL. */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * 논리 스냅샷 라벨(B안). 벽시계 파생값이 아니라 배치가 통제하는 값이다:
     * 명시 주입된 라벨 우선, 미주입 시 collected_at의 UTC 날짜(RawCollectService 참조).
     * KST 등 로컬 타임존 파생 금지. 스냅샷 축.
     */
    @Column(name = "snapshot_date", nullable = false)
    private LocalDate snapshotDate;

    /**
     * 원본 data 배열을 페이지 순서대로 이어붙인 전역 0-based 위치.
     * 페이지 내 순서·페이지 간 순서 모두 원본 totalCount 순서와 일치해야 하며,
     * 수집 도착 순서로 매기면 안 된다(순서 보장은 RawSnapshotSource 계약).
     */
    @Column(name = "source_row_index", nullable = false)
    private Integer sourceRowIndex;

    /** 원본 기준일(원본이 언제자 데이터인가). 현재는 자리만 두고 채우지 않는다(null 허용). */
    @Column(name = "source_reference_date")
    private LocalDate sourceReferenceDate;

    /**
     * 원본을 odcloud에서 수집한 실제 벽시계 시각(UTC, manifest.collected_at 유래).
     * created_at(우리 DB 적재 시각)과 다른 개념 — 병합 금지.
     * 멱등 스킵 시 기존 행의 이 값을 재실행 시각으로 갱신하지 않는다(append-only, 최초값 보존; updatable=false).
     */
    @Column(name = "collected_at", nullable = false, updatable = false)
    private LocalDateTime collectedAt;

    /** 우리 DB 테이블에 적재된 시각(UTC, audit). collected_at(수집 시각)과 다른 개념. */
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /** 적재 키·수집 시각 부여 — Jackson 역직렬화(원본 필드) 후 수집 서비스가 호출한다. */
    public void assignLoadKey(LocalDate snapshotDate, int sourceRowIndex, Instant collectedAt) {
        this.snapshotDate = snapshotDate;
        this.sourceRowIndex = sourceRowIndex;
        this.collectedAt = LocalDateTime.ofInstant(collectedAt, ZoneOffset.UTC);
    }

    @PrePersist
    void stampCreatedAt() {
        if (createdAt == null) {
            // 로컬 타임존 의존 금지 — UTC 고정(도커/CI 환경 간 어긋남 차단)
            createdAt = LocalDateTime.now(ZoneOffset.UTC);
        }
    }
}
