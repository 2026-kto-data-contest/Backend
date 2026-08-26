package com.jeontongjuro.backend.metadata;

import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.brewery.query.Region;
import com.jeontongjuro.backend.liquortype.LiquorType;
import com.jeontongjuro.backend.liquortype.ProductLiquorTypeRepository;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 홈 화면 양조장 필터 칩(주종·지역) 메타데이터 조회. 무인자 — 필터 파라미터를 받지 않는다.
 * <p>
 * 각 축의 개수는 전체 59 기준 독립 집계다(지역 선택 상태에서 주종 개수를 재계산하는 동적 필터가 아니다).
 * GROUP BY 집계 2쿼리(지역 1 + 주종 1)로 6+8=14개 칩을 전부 구한다.
 */
@Service
@Transactional(readOnly = true)
public class BreweryFilterMetadataService {

    /** 응답 배열 순서(명세 나열 순서 고정) = 탁주→약주→청주→증류주→과실주→기타. LiquorType 선언 순서와 우연히 같지만,
     *  이 순서는 enum 선언과 무관한 명세 계약이라 명시 리스트로 고정한다. */
    private static final List<LiquorType> LIQUOR_TYPE_ORDER = List.of(
            LiquorType.탁주, LiquorType.약주, LiquorType.청주, LiquorType.증류주, LiquorType.과실주, LiquorType.기타);

    /** 응답 배열 순서(명세 나열 순서 고정) = 수도권→강원→충청→전라→경상→부산→울산→제주.
     *  ★Region enum 선언 순서(수도권·충청·전라·경상·부산·울산·강원·제주)와 다르다 — enum은 건드리지 않고
     *  여기서 명세 순서로 재배열한다. */
    private static final List<Region> REGION_ORDER = List.of(
            Region.수도권, Region.강원, Region.충청, Region.전라, Region.경상, Region.부산, Region.울산, Region.제주);

    private final BreweryRepository breweryRepository;
    private final ProductLiquorTypeRepository liquorTypeRepository;

    public BreweryFilterMetadataService(BreweryRepository breweryRepository,
                                        ProductLiquorTypeRepository liquorTypeRepository) {
        this.breweryRepository = breweryRepository;
        this.liquorTypeRepository = liquorTypeRepository;
    }

    public BreweryFilterMetadataResponse filters() {
        Map<LiquorType, Long> liquorCounts = liquorTypeCounts();
        Map<Region, Long> regionCounts = regionCounts();

        List<LiquorTypeFilterOption> liquorTypes = LIQUOR_TYPE_ORDER.stream()
                .map(type -> new LiquorTypeFilterOption(type, liquorCounts.getOrDefault(type, 0L)))
                .toList();
        List<RegionFilterOption> regions = REGION_ORDER.stream()
                .map(region -> new RegionFilterOption(region, regionCounts.getOrDefault(region, 0L)))
                .toList();

        return new BreweryFilterMetadataResponse(liquorTypes, regions);
    }

    /** GROUP BY 결과에 없는 주종(취급 브루어리 0, 예: 기타)은 맵에도 없다 — 호출자가 0으로 채운다. */
    private Map<LiquorType, Long> liquorTypeCounts() {
        Map<LiquorType, Long> counts = new EnumMap<>(LiquorType.class);
        for (Object[] row : liquorTypeRepository.countDistinctBreweriesByLiquorType()) {
            counts.put((LiquorType) row[0], (Long) row[1]);
        }
        return counts;
    }

    /**
     * region 컬럼은 Region enum이 아니라 원문 String(Region.name() 저장값)이라 valueOf로 변환한다.
     * region이 아직 파싱되지 않은 행(null)은 8칩 어디에도 속하지 않으므로 건너뛴다.
     */
    private Map<Region, Long> regionCounts() {
        Map<Region, Long> counts = new EnumMap<>(Region.class);
        for (Object[] row : breweryRepository.countGroupByRegion()) {
            String raw = (String) row[0];
            if (raw == null) {
                continue;
            }
            counts.put(Region.valueOf(raw), (Long) row[1]);
        }
        return counts;
    }
}
