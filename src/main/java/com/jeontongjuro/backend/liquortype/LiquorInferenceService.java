package com.jeontongjuro.backend.liquortype;

import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.pipeline.collect.raw.ProductRaw;
import com.jeontongjuro.backend.product.ProductBreweryLink;
import com.jeontongjuro.backend.product.ProductBreweryLinkRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주종 AUTO 추론 서비스(3d, 이슈 #15 1차). product_brewery_link로 연결된 제품(366)만 대상으로,
 * {@link LiquorKeywordDictionary} 규칙을 해석해 {@link ProductLiquorType}에 적재한다.
 * <p>
 * 원칙(요약 지시):
 * <ul>
 *   <li>입력 텍스트 = 양조장 business_name + 제품 product_name + characteristics (★description 제외).
 *       각 키워드는 자신의 scope 필드에서만 찾는다.</li>
 *   <li>한 제품이 여러 주종에 걸리면 여러 행(다대다). 같은 주종은 사전 순서상 첫 키워드를 근거로 1행.</li>
 *   <li>전건 source=AUTO·recheck_flag=true·matched_keyword 기록.</li>
 *   <li>★미커버(어느 키워드에도 안 걸림)는 행을 만들지 않는다 — '기타'로 밀어넣지 않는다.</li>
 *   <li>C-4 향미이중: 제품명에 향미어+주종어가 동시에 있으면 그 제품 태그를 suppressed_from_tab=true로
 *       표시만 하고 행은 남긴다.</li>
 *   <li>멱등 + MANUAL wins: (제품, 주종)이 이미 있으면(MANUAL이든 이전 AUTO든) 건너뛴다.</li>
 * </ul>
 */
@Service
public class LiquorInferenceService {

    private final LiquorKeywordDictionary dictionary;
    private final ProductLiquorTypeRepository liquorTypeRepository;
    private final ProductBreweryLinkRepository linkRepository;
    private final BreweryRepository breweryRepository;

    public LiquorInferenceService(LiquorKeywordDictionary dictionary,
                                  ProductLiquorTypeRepository liquorTypeRepository,
                                  ProductBreweryLinkRepository linkRepository,
                                  BreweryRepository breweryRepository) {
        this.dictionary = dictionary;
        this.liquorTypeRepository = liquorTypeRepository;
        this.linkRepository = linkRepository;
        this.breweryRepository = breweryRepository;
    }

    /**
     * @param targetProducts   추론 대상(link로 연결된) 제품 수
     * @param tagRowsInserted  신규 적재 태그 행 수
     * @param skippedExisting  이미 있어 건너뛴 (제품,주종) 조합 수(멱등·MANUAL wins)
     * @param suppressedTags   suppressed_from_tab=true로 적재된 신규 행 수(C-4)
     * @param uncoveredProducts 어느 키워드에도 안 걸린 대상 제품 수(행 미생성 — 참고·부채)
     */
    public record InferResult(int targetProducts, int tagRowsInserted, int skippedExisting,
                              int suppressedTags, int uncoveredProducts) {
    }

    @Transactional
    public InferResult infer(List<ProductRaw> productRows) {
        Map<Integer, String> breweryIdByRowRef = new HashMap<>();
        for (ProductBreweryLink link : linkRepository.findAll()) {
            if (link.getBreweryId() != null) {
                breweryIdByRowRef.put(link.getSourceRowRef(), link.getBreweryId());
            }
        }
        Map<String, String> businessNameByBreweryId = new HashMap<>();
        for (Brewery b : breweryRepository.findAll()) {
            businessNameByBreweryId.put(b.getBreweryId(), b.getBusinessName());
        }

        List<ProductLiquorType> toInsert = new ArrayList<>();
        int targetProducts = 0;
        int skipped = 0;
        int suppressed = 0;
        int uncovered = 0;

        for (ProductRaw p : productRows) {
            Integer rowRef = p.getSourceRowIndex();
            String breweryId = breweryIdByRowRef.get(rowRef);
            if (breweryId == null) {
                continue; // link로 연결되지 않은 제품 — 대상 아님
            }
            targetProducts++;

            String businessName = businessNameByBreweryId.get(breweryId);
            String productName = p.getProductName();
            String characteristics = p.getCharacteristics();

            // 사전 순서 보존 → 같은 주종은 첫 매칭 키워드를 근거로 기록
            Map<LiquorType, String> firstKeywordByType = new LinkedHashMap<>();
            boolean liquorKeywordInName = false;
            for (LiquorKeyword kw : dictionary.keywords()) {
                if (matches(kw, businessName, productName, characteristics)) {
                    firstKeywordByType.putIfAbsent(kw.liquorType(), kw.keyword());
                    if (kw.scope().contains(LiquorKeyword.SCOPE_PRODUCT_NAME)
                            && HangulMatcher.contains(productName, kw.keyword(), kw.wordBoundary())) {
                        liquorKeywordInName = true;
                    }
                }
            }

            if (firstKeywordByType.isEmpty()) {
                uncovered++;
                continue; // ★미커버 — 행 생성 안 함
            }

            // C-4 향미이중: 제품명에 향미어 + 주종어가 동시 → 이 제품 전 태그 억제 표시
            boolean suppress = liquorKeywordInName && flavorWordInName(productName);

            for (Map.Entry<LiquorType, String> entry : firstKeywordByType.entrySet()) {
                LiquorType type = entry.getKey();
                if (liquorTypeRepository.existsBySourceRowRefAndLiquorType(rowRef, type)) {
                    skipped++; // 멱등·MANUAL wins
                    continue;
                }
                toInsert.add(ProductLiquorType.auto(rowRef, breweryId, type, suppress, entry.getValue()));
                if (suppress) {
                    suppressed++;
                }
            }
        }

        liquorTypeRepository.saveAll(toInsert);
        return new InferResult(targetProducts, toInsert.size(), skipped, suppressed, uncovered);
    }

    /** 키워드가 자신의 scope 필드 중 하나라도에 (단어경계 규칙대로) 출현하는가. */
    private boolean matches(LiquorKeyword kw, String businessName, String productName, String characteristics) {
        for (String scope : kw.scope()) {
            String field = switch (scope) {
                case LiquorKeyword.SCOPE_BUSINESS_NAME -> businessName;
                case LiquorKeyword.SCOPE_PRODUCT_NAME -> productName;
                case LiquorKeyword.SCOPE_CHARACTERISTICS -> characteristics;
                default -> null;
            };
            if (HangulMatcher.contains(field, kw.keyword(), kw.wordBoundary())) {
                return true;
            }
        }
        return false;
    }

    private boolean flavorWordInName(String productName) {
        if (productName == null) {
            return false;
        }
        for (String flavor : dictionary.flavorWords()) {
            if (productName.contains(flavor)) {
                return true;
            }
        }
        return false;
    }
}
