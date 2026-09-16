package com.jeontongjuro.backend.map;

import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.brewery.query.BreweryListItemResponse;
import com.jeontongjuro.backend.brewery.query.BreweryQueryService;
import com.jeontongjuro.backend.global.web.PageResponse;
import com.jeontongjuro.backend.product.query.ProductCardResponse;
import com.jeontongjuro.backend.product.query.ProductQueryService;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 지도 「수상받은 전통주」 섹션 조회 서비스. */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MapAwardedLiquorService {

    static final int DEFAULT_SIZE = 4;
    static final int MAX_SIZE = 100;

    private final BreweryQueryService breweryQueryService;
    private final ProductQueryService productQueryService;
    private final BreweryRepository breweryRepository;

    public PageResponse<MapAwardedLiquorResponse> list(int page, int size) {
        int clampedSize = size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        int clampedPage = Math.max(0, page);

        List<BreweryListItemResponse> breweries = breweryQueryService.searchAllCards();
        Map<String, Brewery> breweryById = loadBreweries(breweries);
        List<MapAwardedLiquorResponse> awarded = new ArrayList<>();

        for (BreweryListItemResponse breweryCard : breweries) {
            Brewery brewery = breweryById.get(breweryCard.breweryId());
            if (brewery == null || brewery.getLatitude() == null || brewery.getLongitude() == null) {
                continue;
            }
            productQueryService.listProducts(breweryCard.breweryId(), 0, MAX_SIZE).content().stream()
                    .filter(product -> product.awardBadge() != null)
                    .map(product -> toResponse(product, breweryCard, brewery))
                    .forEach(awarded::add);
        }

        awarded.sort(Comparator.comparing(MapAwardedLiquorResponse::breweryName)
                .thenComparing(MapAwardedLiquorResponse::productName)
                .thenComparing(MapAwardedLiquorResponse::productId));

        int from = (int) Math.min((long) clampedPage * clampedSize, awarded.size());
        int to = Math.min(from + clampedSize, awarded.size());
        return PageResponse.of(awarded.subList(from, to), clampedPage, clampedSize, awarded.size());
    }

    private Map<String, Brewery> loadBreweries(List<BreweryListItemResponse> cards) {
        Map<String, Brewery> byId = new HashMap<>();
        breweryRepository.findAllById(cards.stream().map(BreweryListItemResponse::breweryId).toList())
                .forEach(brewery -> byId.put(brewery.getBreweryId(), brewery));
        return byId;
    }

    private MapAwardedLiquorResponse toResponse(ProductCardResponse product,
                                                 BreweryListItemResponse breweryCard,
                                                 Brewery brewery) {
        return new MapAwardedLiquorResponse(
                product.productId(), product.productName(), brewery.getBreweryId(),
                breweryCard.businessName(), product.awardBadge(), product.liquorTypes(),
                product.alcoholMin(), product.alcoholMax(), product.volume(), brewery.getAddress(),
                brewery.getLatitude(), brewery.getLongitude());
    }
}
