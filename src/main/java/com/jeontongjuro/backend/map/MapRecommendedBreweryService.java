package com.jeontongjuro.backend.map;

import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.brewery.query.BreweryListItemResponse;
import com.jeontongjuro.backend.global.web.PageResponse;
import com.jeontongjuro.backend.recommendation.RecommendedBreweryService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 지도 「전통주로에서 추천하는 양조장」 섹션 조회 서비스. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapRecommendedBreweryService {

    static final int DEFAULT_SIZE = 4;

    private final RecommendedBreweryService recommendedBreweryService;
    private final BreweryRepository breweryRepository;

    public PageResponse<MapRecommendedBreweryResponse> recommend(Long memberId, int page, int size) {
        int requestedSize = size < 1 ? DEFAULT_SIZE : size;
        PageResponse<BreweryListItemResponse> recommended =
                recommendedBreweryService.recommend(memberId, page, requestedSize);

        Map<String, Brewery> breweriesById = new HashMap<>();
        breweryRepository.findAllById(recommended.content().stream()
                        .map(BreweryListItemResponse::breweryId).toList())
                .forEach(brewery -> breweriesById.put(brewery.getBreweryId(), brewery));

        List<MapRecommendedBreweryResponse> content = recommended.content().stream()
                .map(item -> toResponse(item, breweriesById.get(item.breweryId())))
                .toList();
        return PageResponse.of(content, recommended.page(), recommended.size(), recommended.totalElements());
    }

    private MapRecommendedBreweryResponse toResponse(BreweryListItemResponse item, Brewery brewery) {
        if (brewery == null) {
            throw new IllegalStateException("추천 양조장 마스터를 찾을 수 없습니다: " + item.breweryId());
        }
        return new MapRecommendedBreweryResponse(
                item.breweryId(), item.businessName(), brewery.getAddress(), brewery.getLatitude(),
                brewery.getLongitude(), item.liquorTypes(), item.featureTags(), item.mainImage());
    }
}
