package com.jeontongjuro.backend.brewery;

import java.util.Set;

/** 운영 사유로 일시적으로 서비스에서 제외하는 양조장 정책. 원본·마스터 데이터는 보존한다. */
public final class BreweryVisibilityPolicy {

    private static final Set<String> EXCLUDED_BREWERY_IDS = Set.of("BRW-040");

    private BreweryVisibilityPolicy() {
    }

    public static boolean isVisible(String breweryId) {
        return breweryId != null && !EXCLUDED_BREWERY_IDS.contains(breweryId);
    }

    public static Set<String> excludedBreweryIds() {
        return EXCLUDED_BREWERY_IDS;
    }
}
