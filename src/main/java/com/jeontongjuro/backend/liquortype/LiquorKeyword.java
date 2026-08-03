package com.jeontongjuro.backend.liquortype;

import java.util.Set;

/**
 * 키워드 사전 한 줄(liquor_keyword_seed.json의 keywords 항목 1건). 코드는 이 데이터를 해석만 한다.
 *
 * @param keyword     매칭 단어
 * @param liquorType  이 단어가 지시하는 주종
 * @param scope       매칭 대상 필드명 집합(business_name / product_name / characteristics)
 * @param wordBoundary 앞·뒤 한글 비접촉 출현만 인정할지(짧고 오탐 많은 단어용)
 */
public record LiquorKeyword(String keyword, LiquorType liquorType, Set<String> scope, boolean wordBoundary) {

    public static final String SCOPE_BUSINESS_NAME = "business_name";
    public static final String SCOPE_PRODUCT_NAME = "product_name";
    public static final String SCOPE_CHARACTERISTICS = "characteristics";
}
