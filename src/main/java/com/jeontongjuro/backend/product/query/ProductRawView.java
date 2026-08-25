package com.jeontongjuro.backend.product.query;

/**
 * product_raw 읽기 전용 프로젝션(Spring Data 인터페이스 프로젝션). 제품 카드가 실제로 쓰는 컬럼만 노출해
 * 조회 계층을 파이프라인 소유 엔티티({@code ProductRaw})의 전체 표면에서 분리한다(강결합 완화).
 * <p>
 * getter 이름은 {@code ProductRaw}/{@code AbstractRawRow}의 프로퍼티명과 일치해야 매핑된다.
 */
public interface ProductRawView {

    Integer getSourceRowIndex();

    String getProductName();

    String getDescription();

    String getAwards();

    String getVolume();

    String getSaleYn();

    /** 전통주정보 '특징' 필드 원문(맛 태그 매칭 소스). {@link SensoryTagMatcher}가 소비한다. */
    String getCharacteristics();
}
