package com.jeontongjuro.backend.product;

import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.override.ManualOverride;
import com.jeontongjuro.backend.override.ManualOverrideRepository;
import com.jeontongjuro.backend.override.MatchKeyKind;
import com.jeontongjuro.backend.pipeline.collect.raw.ProductRaw;
import com.jeontongjuro.backend.pipeline.normalize.BusinessNameNormalizer;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * product→brewery 조인 서비스(3c-2). 대상 product raw 1215건을 훑되, brewery 59에 실제 연결되는
 * 행(AUTO+override)만 {@link ProductBreweryLink}로 적재한다. ★주소 파싱·주종 롤업은 하지 않는다.
 * <p>
 * 순서 = MANUAL wins: (a) AUTO(양조장 필드 norm == brewery norm) → (b) override NAME_MAP(양조장 필드 norm ==
 * 시드 match_key)이 AUTO 결과를 덮어씀 → (c) override ROW_PIN(양조장 필드 null인 행을 제품명 NFC 완전일치로 고정,
 * AUTO 불가 대상이므로 덮어쓸 AUTO 없음). 세 경로 모두 실패하면 UNMATCHED — 이번엔 미적재.
 */
@Service
public class ProductBreweryJoinService {

    private final ProductBreweryLinkRepository linkRepository;
    private final BreweryRepository breweryRepository;
    private final ManualOverrideRepository overrideRepository;

    public ProductBreweryJoinService(ProductBreweryLinkRepository linkRepository,
                                     BreweryRepository breweryRepository,
                                     ManualOverrideRepository overrideRepository) {
        this.linkRepository = linkRepository;
        this.breweryRepository = breweryRepository;
        this.overrideRepository = overrideRepository;
    }

    public record JoinResult(
            int productRowsScanned,
            int linked,
            int skippedExisting,
            int autoLinked,
            int overrideNameLinked,
            int overrideRowLinked,
            int autoOverrideConflicts,
            Map<Long, Integer> overrideHitCounts
    ) {
    }

    @Transactional
    public JoinResult join(List<ProductRaw> productRows) {
        Map<String, String> breweryIdByNorm = new HashMap<>();
        for (Brewery b : breweryRepository.findAll()) {
            String prev = breweryIdByNorm.put(b.getNorm(), b.getBreweryId());
            if (prev != null) {
                throw new IllegalStateException(
                        "brewery norm 중복(조인 매칭키 유일성 위반): norm=" + b.getNorm()
                                + " (" + prev + ", " + b.getBreweryId() + ")");
            }
        }

        Map<String, ManualOverride> nameMapByNorm = new HashMap<>();
        Map<String, ManualOverride> rowPinByProductNameNfc = new HashMap<>();
        for (ManualOverride o : overrideRepository.findAll()) {
            if (o.getMatchKeyKind() == MatchKeyKind.BREWERY_NORM) {
                nameMapByNorm.put(o.getMatchKey(), o);
            } else {
                rowPinByProductNameNfc.put(nfc(o.getMatchKey()), o);
            }
        }

        List<ProductBreweryLink> toInsert = new ArrayList<>();
        Map<Long, Integer> overrideHitCounts = new HashMap<>();
        int skipped = 0;
        int auto = 0;
        int overrideName = 0;
        int overrideRow = 0;
        int conflicts = 0;

        for (ProductRaw p : productRows) {
            String breweryNameRaw = p.getBreweryName();
            String productNorm = BusinessNameNormalizer.normalize(breweryNameRaw);

            String autoBreweryId = productNorm != null ? breweryIdByNorm.get(productNorm) : null;
            ManualOverride nameOverride = productNorm != null ? nameMapByNorm.get(productNorm) : null;
            ManualOverride rowOverride = breweryNameRaw == null
                    ? rowPinByProductNameNfc.get(nfc(p.getProductName()))
                    : null;

            String finalBreweryId;
            JoinSource joinSource;
            ManualOverride hitOverride;

            if (rowOverride != null) {
                finalBreweryId = rowOverride.getBreweryId();
                joinSource = JoinSource.OVERRIDE_ROW;
                hitOverride = rowOverride;
                overrideRow++;
            } else if (nameOverride != null) {
                finalBreweryId = nameOverride.getBreweryId();
                joinSource = JoinSource.OVERRIDE_NAME;
                hitOverride = nameOverride;
                overrideName++;
                if (autoBreweryId != null && !autoBreweryId.equals(finalBreweryId)) {
                    conflicts++;
                }
            } else if (autoBreweryId != null) {
                finalBreweryId = autoBreweryId;
                joinSource = JoinSource.AUTO;
                hitOverride = null;
                auto++;
            } else {
                continue; // UNMATCHED — 이번엔 적재하지 않음(스키마 값만 정의)
            }

            if (hitOverride != null) {
                overrideHitCounts.merge(hitOverride.getId(), 1, Integer::sum);
            }

            if (linkRepository.existsBySourceRowRef(p.getSourceRowIndex())) {
                skipped++;
                continue;
            }

            toInsert.add(ProductBreweryLink.of(p.getSourceRowIndex(), p.getProductName(),
                    breweryNameRaw, productNorm, finalBreweryId, joinSource));
        }

        linkRepository.saveAll(toInsert);
        return new JoinResult(productRows.size(), toInsert.size(), skipped, auto, overrideName, overrideRow,
                conflicts, overrideHitCounts);
    }

    /** 조인으로 연결된(AUTO∪MANUAL) 고유 brewery_id 집합. brewery join_status UPDATE 입력. */
    public Set<String> linkedBreweryIds() {
        Set<String> ids = new HashSet<>();
        for (ProductBreweryLink link : linkRepository.findAll()) {
            if (link.getBreweryId() != null) {
                ids.add(link.getBreweryId());
            }
        }
        return ids;
    }

    private static String nfc(String value) {
        return value == null ? null : Normalizer.normalize(value, Normalizer.Form.NFC);
    }
}
