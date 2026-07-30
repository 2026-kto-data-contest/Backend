package com.jeontongjuro.backend.brewery;

import org.springframework.data.jpa.repository.JpaRepository;

/** brewery 마스터 dimension 리포지토리. PK = BRW-xxx(String). */
public interface BreweryRepository extends JpaRepository<Brewery, String> {
}
