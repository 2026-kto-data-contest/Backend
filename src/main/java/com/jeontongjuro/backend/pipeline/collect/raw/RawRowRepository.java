package com.jeontongjuro.backend.pipeline.collect.raw;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.repository.NoRepositoryBean;

/** RAW 두 테이블 공통 조회 계약(스냅샷 축 기준). */
@NoRepositoryBean
public interface RawRowRepository<T extends AbstractRawRow> extends JpaRepository<T, Long> {

    boolean existsBySnapshotDate(LocalDate snapshotDate);

    long countBySnapshotDate(LocalDate snapshotDate);

    Optional<T> findBySnapshotDateAndSourceRowIndex(LocalDate snapshotDate, int sourceRowIndex);

    List<T> findAllBySnapshotDateOrderBySourceRowIndexAsc(LocalDate snapshotDate);
}
