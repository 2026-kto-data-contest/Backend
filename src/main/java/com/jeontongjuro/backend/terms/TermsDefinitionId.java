package com.jeontongjuro.backend.terms;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import java.io.Serializable;

@Embeddable
public record TermsDefinitionId(
        @Column(name = "code", columnDefinition = "text") String code,
        @Column(name = "version", columnDefinition = "text") String version
) implements Serializable {
}
