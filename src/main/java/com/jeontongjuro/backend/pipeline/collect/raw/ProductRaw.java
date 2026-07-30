package com.jeontongjuro.backend.pipeline.collect.raw;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 전통주정보(dataset 15048755) RAW. 원본 12필드를 파싱·정제 없이 원문 그대로 보관한다.
 * 한글 키 ↔ 영문 컬럼 매핑 계약: docs/audit/20260729_raw_field_mapping.md
 * 알콜도수·용량 등 숫자성 값도 문자열 원문 그대로 저장한다(변환·분해 금지).
 */
@Entity
@Table(name = "product_raw",
        uniqueConstraints = @UniqueConstraint(name = "uq_product_raw_snapshot_row",
                columnNames = {"snapshot_date", "source_row_index"}))
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProductRaw extends AbstractRawRow {

    @JsonProperty("성분")
    @Column(name = "ingredients", columnDefinition = "text")
    private String ingredients;

    @JsonProperty("수상경력")
    @Column(name = "awards", columnDefinition = "text")
    private String awards;

    /** 숫자성 문자열("6", "6.5")이지만 원본 타입이 string이므로 변환 없이 보존. */
    @JsonProperty("알콜도수")
    @Column(name = "alcohol_content", columnDefinition = "text")
    private String alcoholContent;

    /**
     * ★소속 양조장 지시 필드(원본 키 '양조장', 실측 null 61건).
     * 파생층에서 brewery_raw.business_name(상호명)과 조인하는 입구다. 이 층에서는 조인하지 않는다.
     */
    @JsonProperty("양조장")
    @Column(name = "brewery_name", columnDefinition = "text")
    private String breweryName;

    @JsonProperty("양조장주소")
    @Column(name = "brewery_address", columnDefinition = "text")
    private String breweryAddress;

    /** 복수 표기("750ml / 1700ml")도 분해 없이 원문 그대로. */
    @JsonProperty("용량")
    @Column(name = "volume", columnDefinition = "text")
    private String volume;

    @JsonProperty("제품명")
    @Column(name = "product_name", columnDefinition = "text")
    private String productName;

    @JsonProperty("제품소개")
    @Column(name = "description", columnDefinition = "text")
    private String description;

    @JsonProperty("특이사항")
    @Column(name = "special_note", columnDefinition = "text")
    private String specialNote;

    @JsonProperty("특징")
    @Column(name = "characteristics", columnDefinition = "text")
    private String characteristics;

    @JsonProperty("판매여부")
    @Column(name = "sale_yn", columnDefinition = "text")
    private String saleYn;

    @JsonProperty("홈페이지주소")
    @Column(name = "homepage_url", columnDefinition = "text")
    private String homepageUrl;
}
