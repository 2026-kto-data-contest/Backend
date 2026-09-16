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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
public class MapLiquorMenuService {
    private static final int DEFAULT_SIZE = 4;
    private static final int MAX_SIZE = 100;
    private final BreweryQueryService breweryQueryService;
    private final ProductQueryService productQueryService;
    private final BreweryRepository breweryRepository;

    public MapLiquorMenuService(BreweryQueryService breweryQueryService, ProductQueryService productQueryService,
                                BreweryRepository breweryRepository) {
        this.breweryQueryService = breweryQueryService;
        this.productQueryService = productQueryService;
        this.breweryRepository = breweryRepository;
    }

    public List<MapLiquorMenuResponse> menus() {
        return List.of(MapLiquorMenu.values()).stream()
                .map(m -> new MapLiquorMenuResponse(m.name(), m.displayName(), m.kind())).toList();
    }

    public PageResponse<MapLiquorMenuItemResponse> products(String menuValue, int page, int size) {
        MapLiquorMenu menu = MapLiquorMenu.parse(menuValue);
        int actualPage = Math.max(0, page);
        int actualSize = size < 1 ? DEFAULT_SIZE : Math.min(size, MAX_SIZE);
        Map<String, Brewery> breweries = new HashMap<>();
        List<BreweryListItemResponse> cards = breweryQueryService.searchAllCards();
        breweryRepository.findAllById(cards.stream().map(BreweryListItemResponse::breweryId).toList())
                .forEach(b -> breweries.put(b.getBreweryId(), b));
        List<MapLiquorMenuItemResponse> result = new ArrayList<>();
        for (BreweryListItemResponse card : cards) {
            Brewery brewery = breweries.get(card.breweryId());
            if (brewery == null || brewery.getLatitude() == null || brewery.getLongitude() == null) continue;
            for (ProductCardResponse product : productQueryService.listProducts(card.breweryId(), 0, MAX_SIZE).content()) {
                if (menu.matches(product)) result.add(new MapLiquorMenuItemResponse(product.productId(),
                        product.productName(), card.breweryId(), card.businessName(), product.liquorTypes(),
                        product.flavorTags(), product.alcoholMin(), product.alcoholMax(), product.volume(),
                        brewery.getAddress(), brewery.getLatitude(), brewery.getLongitude(), card.mainImage()));
            }
        }
        result.sort(Comparator.comparing(MapLiquorMenuItemResponse::breweryName)
                .thenComparing(MapLiquorMenuItemResponse::productName)
                .thenComparing(MapLiquorMenuItemResponse::productId));
        int from = (int) Math.min((long) actualPage * actualSize, result.size());
        int to = Math.min(from + actualSize, result.size());
        return PageResponse.of(result.subList(from, to), actualPage, actualSize, result.size());
    }
}
