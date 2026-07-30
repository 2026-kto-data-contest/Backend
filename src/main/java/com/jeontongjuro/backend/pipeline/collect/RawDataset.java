package com.jeontongjuro.backend.pipeline.collect;

/** 수집 대상 데이터셋 식별자. */
public enum RawDataset {
    /** 찾아가는양조장정보 (dataset_id 15048756) → brewery_raw */
    BREWERY,
    /** 전통주정보 (dataset_id 15048755) → product_raw */
    PRODUCT
}
