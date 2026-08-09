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
import java.util.Optional;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

    private static final Logger log = LoggerFactory.getLogger(ProductBreweryJoinService.class);

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
            Map<Long, Integer> overrideHitCounts,
            int alcoholParsed,
            int alcoholFailed,
            int alcoholBackfilled
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
        int alcoholParsed = 0;
        int alcoholFailed = 0;
        int alcoholBackfilled = 0;

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

            Optional<ProductBreweryLink> existing = linkRepository.findBySourceRowRef(p.getSourceRowIndex());
            if (existing.isPresent()) {
                skipped++;
                // 백필(#35): 증분 조인은 기존 링크를 재적재하지 않으므로, 이미 링크가 존재하는 DB는 신규 경로를
                // 못 타 도수가 null로 남는다. 재적재/TRUNCATE 없이 코드가 스스로 채운다.
                //  - alcohol_min/max가 둘 다 null일 때만 파싱해 채움(하나라도 있으면 멱등 유지 — 건드리지 않음)
                //  - 파싱이 (null,null)이면 채울 값이 없으므로 백필 카운트 제외(매 실행 재시도되나 행 수가 작아 무해).
                //    별도 플래그 컬럼은 만들지 않는다(현재 파싱 실패 0건이라 정보를 담지 못함).
                ProductBreweryLink link = existing.get();
                if (link.getAlcoholMin() == null && link.getAlcoholMax() == null) {
                    AlcoholContentParser.AlcoholRange range = AlcoholContentParser.parse(p.getAlcoholContent());
                    if (range.min() != null) {
                        link.backfillAlcohol(range.min(), range.max());
                        alcoholBackfilled++;
                    }
                }
                continue;
            }

            // 도수 파싱은 3단계에 흡수(#35) — 별도 파이프라인 단계 없음. 신규 적재 행에 대해서만 카운트해
            // alcoholParsed + alcoholFailed == linked(=toInsert.size()) 항등식을 유지한다(skippedExisting 제외).
            AlcoholContentParser.AlcoholRange alcohol = AlcoholContentParser.parse(p.getAlcoholContent());
            if (alcohol.min() != null) {
                alcoholParsed++;
            } else {
                alcoholFailed++;
                log.warn("도수 파싱 실패(매치 0개 — null 적재, 진행): source_row_ref={} 원문='{}'",
                        p.getSourceRowIndex(), p.getAlcoholContent());
            }

            toInsert.add(ProductBreweryLink.of(p.getSourceRowIndex(), p.getProductName(),
                    breweryNameRaw, productNorm, finalBreweryId, joinSource, alcohol.min(), alcohol.max()));
        }

        linkRepository.saveAll(toInsert);
        return new JoinResult(productRows.size(), toInsert.size(), skipped, auto, overrideName, overrideRow,
                conflicts, overrideHitCounts, alcoholParsed, alcoholFailed, alcoholBackfilled);
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
