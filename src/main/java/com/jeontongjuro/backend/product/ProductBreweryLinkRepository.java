package com.jeontongjuro.backend.product;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductBreweryLinkRepository extends JpaRepository<ProductBreweryLink, Long> {

    boolean existsBySourceRowRef(Integer sourceRowRef);

    List<ProductBreweryLink> findByJoinSource(JoinSource joinSource);
}
