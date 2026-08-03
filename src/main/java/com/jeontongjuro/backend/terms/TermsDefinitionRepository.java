package com.jeontongjuro.backend.terms;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TermsDefinitionRepository extends JpaRepository<TermsDefinition, TermsDefinitionId> {

    List<TermsDefinition> findByActiveTrueOrderByDisplayOrderAsc();
}
