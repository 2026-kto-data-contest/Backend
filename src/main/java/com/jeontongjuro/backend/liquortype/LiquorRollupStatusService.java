package com.jeontongjuro.backend.liquortype;

import com.jeontongjuro.backend.product.ProductBreweryLink;
import com.jeontongjuro.backend.product.ProductBreweryLinkRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주종 롤업 상태 산출(3d 7단계 입력). "검수 완료된 양조장" 집합을 계산해 반환한다 —
 * {@link com.jeontongjuro.backend.brewery.BreweryLiquorStatusUpdateService}가 이 집합만 받아
 * liquor_status를 갱신한다(조인 4단계의 {@code linkedBreweryIds} → applyJoinResult와 동일한 분업).
 * <p>
 * 판정 기준(정확히 이대로): 어떤 양조장의 <b>조인 제품이 전부</b> {@link ProductLiquorType}에 태깅되어 있고,
 * 그 태그가 <b>전부 recheck_flag=false</b>이면 그 양조장을 "확정(TAGGED 후보)"으로 본다.
 * 미태깅 제품이 하나라도 있거나 recheck_flag=true 태그가 하나라도 있으면 제외한다.
 * <p>
 * ★비즈니스 로직을 orchestrator에 두지 않으려는 분리다(orchestrator는 얇은 순서 조정자). 읽기 전용.
 */
@Service
public class LiquorRollupStatusService {

    private final ProductBreweryLinkRepository linkRepository;
    private final ProductLiquorTypeRepository liquorTypeRepository;

    public LiquorRollupStatusService(ProductBreweryLinkRepository linkRepository,
                                     ProductLiquorTypeRepository liquorTypeRepository) {
        this.linkRepository = linkRepository;
        this.liquorTypeRepository = liquorTypeRepository;
    }

    /**
     * 조인 제품 전건이 태깅 & 전건 recheck_flag=false인 양조장 id 집합.
     * 조인 제품이 0건인 양조장(미조인)은 애초에 등장하지 않으므로 자동 제외된다.
     */
    @Transactional(readOnly = true)
    public Set<String> confirmedBreweryIds() {
        // brewery_id -> 조인 제품 source_row_ref 목록
        Map<String, List<Integer>> refsByBrewery = new HashMap<>();
        for (ProductBreweryLink l : linkRepository.findAll()) {
            if (l.getBreweryId() != null) {
                refsByBrewery.computeIfAbsent(l.getBreweryId(), k -> new ArrayList<>())
                        .add(l.getSourceRowRef());
            }
        }
        // source_row_ref -> 그 제품의 태그들
        Map<Integer, List<ProductLiquorType>> tagsByRef = new HashMap<>();
        for (ProductLiquorType t : liquorTypeRepository.findAll()) {
            tagsByRef.computeIfAbsent(t.getSourceRowRef(), k -> new ArrayList<>()).add(t);
        }

        Set<String> confirmed = new HashSet<>();
        for (Map.Entry<String, List<Integer>> e : refsByBrewery.entrySet()) {
            if (allProductsTaggedAndConfirmed(e.getValue(), tagsByRef)) {
                confirmed.add(e.getKey());
            }
        }
        return confirmed;
    }

    private boolean allProductsTaggedAndConfirmed(List<Integer> refs,
                                                  Map<Integer, List<ProductLiquorType>> tagsByRef) {
        for (Integer ref : refs) {
            List<ProductLiquorType> tags = tagsByRef.get(ref);
            if (tags == null || tags.isEmpty()) {
                return false; // 미태깅 제품 존재 → 확정 아님
            }
            for (ProductLiquorType t : tags) {
                if (t.isRecheckFlag()) {
                    return false; // 미검증(recheck) 태그 존재 → 확정 아님
                }
            }
        }
        return true;
    }
}
