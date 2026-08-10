package com.jeontongjuro.backend.feature;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BreweryFeatureTagRepository extends JpaRepository<BreweryFeatureTag, Long> {

    /** 조회 API 배치 로딩용 — 페이지의 brewery_id 집합으로 태그를 한 번에 읽어 N+1을 피한다. */
    List<BreweryFeatureTag> findByBreweryIdIn(Collection<String> breweryIds);
}
