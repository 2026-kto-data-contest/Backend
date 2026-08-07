package com.jeontongjuro.backend.tour;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * TourAPI 콘텐츠 캐시 리포지토리. PK = contentid(String).
 */
public interface TourContentRepository extends JpaRepository<TourContent, String> {
}
