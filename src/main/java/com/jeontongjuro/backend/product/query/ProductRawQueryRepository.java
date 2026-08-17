package com.jeontongjuro.backend.product.query;

import com.jeontongjuro.backend.pipeline.collect.raw.ProductRaw;
import java.util.Collection;
import java.util.List;
import org.springframework.data.repository.Repository;

/**
 * 제품 카드 조회 전용 product_raw 읽기 리포지토리. 쓰기 메서드가 없는 {@link Repository} 상속으로
 * 조회 계층에서 raw 테이블을 읽기 전용으로만 접근한다(적재는 파이프라인 소유).
 * <p>
 * 링크({@code product_brewery_link.source_row_ref})가 스냅샷 축이 없는 단일 활성 스냅샷 전제이므로,
 * source_row_index로 바로 조회한다(현행 1스냅샷·1:1 확인됨).
 */
public interface ProductRawQueryRepository extends Repository<ProductRaw, Long> {

    /** 링크의 source_row_ref 집합으로 raw 프로젝션을 배치 로딩(N+1 회피). */
    List<ProductRawView> findBySourceRowIndexIn(Collection<Integer> sourceRowIndexes);
}
