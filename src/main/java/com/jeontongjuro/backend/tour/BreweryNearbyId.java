package com.jeontongjuro.backend.tour;

import java.io.Serializable;
import java.util.Objects;

/**
 * {@link BreweryNearby} 복합 PK(brewery_id, content_id) 식별자. JPA @IdClass 요건: 공개 no-arg 생성자,
 * equals/hashCode, Serializable. 필드명·타입은 엔티티의 @Id 필드와 1:1 대응.
 */
public class BreweryNearbyId implements Serializable {

    private String breweryId;
    private String contentId;

    public BreweryNearbyId() {
    }

    public BreweryNearbyId(String breweryId, String contentId) {
        this.breweryId = breweryId;
        this.contentId = contentId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof BreweryNearbyId that)) {
            return false;
        }
        return Objects.equals(breweryId, that.breweryId) && Objects.equals(contentId, that.contentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(breweryId, contentId);
    }
}
