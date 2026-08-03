package com.jeontongjuro.backend.brewery;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

/**
 * brewery 마스터 dimension 리포지토리. PK = BRW-xxx(String).
 * <p>
 * {@link JpaSpecificationExecutor}: 리스트 조회 API의 동적 필터 조합(region·visit·keyword 등)을
 * Specification으로 얹기 위한 확장 — 후속 주종·도수 필터도 Specification 추가만으로 붙는다.
 */
public interface BreweryRepository extends JpaRepository<Brewery, String>, JpaSpecificationExecutor<Brewery> {
}
