package com.jeontongjuro.backend.terms;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "terms_definition")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TermsDefinition {

    @EmbeddedId
    private TermsDefinitionId id;

    @Column(nullable = false, columnDefinition = "text")
    private String title;

    @Column(nullable = false)
    private boolean required;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "content_url", columnDefinition = "text")
    private String contentUrl;

    @Column(nullable = false)
    private boolean active;
}
