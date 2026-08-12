package com.jeontongjuro.backend.experience;

import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BreweryExperienceRepository extends JpaRepository<BreweryExperience, Long> {

    /** 상세 API 단건 조회 — 한 양조장의 체험을 program_name 오름차순으로(응답 배열 결정론화). */
    List<BreweryExperience> findByBreweryIdOrderByProgramNameAsc(String breweryId);

    /** 상세 API 배치 로딩용(리스트 재사용 대비) — brewery_id 집합으로 한 번에 읽어 N+1 회피. */
    List<BreweryExperience> findByBreweryIdIn(Collection<String> breweryIds);
}
