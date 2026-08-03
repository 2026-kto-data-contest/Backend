package com.jeontongjuro.backend.product;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductBreweryLinkRepository extends JpaRepository<ProductBreweryLink, Long> {

    boolean existsBySourceRowRef(Integer sourceRowRef);

    List<ProductBreweryLink> findByJoinSource(JoinSource joinSource);

    /** 주종 MANUAL 시드 적재 시 제품 참조(source_row_ref)로 연결 확정 brewery_id를 도출한다(uq라 최대 1건). */
    Optional<ProductBreweryLink> findBySourceRowRef(Integer sourceRowRef);
}
