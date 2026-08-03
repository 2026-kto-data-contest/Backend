package com.jeontongjuro.backend.liquortype;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeontongjuro.backend.product.ProductBreweryLink;
import com.jeontongjuro.backend.product.ProductBreweryLinkRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주종 수기 시드 적재 서비스(3d MANUAL). {@code src/main/resources/liquor_manual_seed.json}을 읽어
 * source=MANUAL·recheck_flag=false로 적재한다. ★1차(이슈 #15)에는 entries가 비어 0건 처리되며,
 * 2차 검수 후 JSON만 채우면 그대로 동작한다.
 * <p>
 * MANUAL wins: AUTO 추론보다 먼저 실행해 (제품, 주종)을 선점한다 — 이후 AUTO는 같은 조합을 건너뛴다.
 * brewery_id는 product_brewery_link(source_row_ref)에서 도출한다(참조 무결성 선검증 — link에 없으면 멈춤).
 * 멱등: 같은 (source_row_ref, liquor_type)이 이미 있으면 건너뛴다.
 */
@Service
public class LiquorManualSeedLoadService {

    private static final String SEED_CLASSPATH = "/liquor_manual_seed.json";

    private final ProductLiquorTypeRepository liquorTypeRepository;
    private final ProductBreweryLinkRepository linkRepository;
    private final ObjectMapper objectMapper;

    public LiquorManualSeedLoadService(ProductLiquorTypeRepository liquorTypeRepository,
                                       ProductBreweryLinkRepository linkRepository,
                                       ObjectMapper objectMapper) {
        this.liquorTypeRepository = liquorTypeRepository;
        this.linkRepository = linkRepository;
        this.objectMapper = objectMapper;
    }

    public record LoadResult(int seedRows, int loaded, int skippedExisting) {
    }

    @Transactional
    public LoadResult load() {
        JsonNode entries = readSeedEntries();

        // 참조 무결성 선검증 — 참조 제품(source_row_ref)이 전부 link에 실재해야(미조인 제품엔 태깅 불가).
        Set<Integer> missing = new LinkedHashSet<>();
        for (JsonNode e : entries) {
            int rowRef = e.get("source_row_ref").asInt();
            if (linkRepository.findBySourceRowRef(rowRef).isEmpty()) {
                missing.add(rowRef);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "주종 MANUAL 시드 참조 위반: link에 없는 source_row_ref " + missing
                            + " (미조인 제품엔 태깅 불가). 적재 중단.");
        }

        List<ProductLiquorType> toInsert = new ArrayList<>();
        int skipped = 0;
        int seedRows = 0;
        for (JsonNode e : entries) {
            seedRows++;
            int rowRef = e.get("source_row_ref").asInt();
            LiquorType type = LiquorType.valueOf(e.get("liquorType").asText());

            if (liquorTypeRepository.existsBySourceRowRefAndLiquorType(rowRef, type)) {
                skipped++;
                continue;
            }
            Optional<ProductBreweryLink> link = linkRepository.findBySourceRowRef(rowRef);
            String breweryId = link.orElseThrow().getBreweryId();
            toInsert.add(ProductLiquorType.manual(rowRef, breweryId, type));
        }
        liquorTypeRepository.saveAll(toInsert);
        return new LoadResult(seedRows, toInsert.size(), skipped);
    }

    private JsonNode readSeedEntries() {
        try (InputStream in = getClass().getResourceAsStream(SEED_CLASSPATH)) {
            if (in == null) {
                throw new IllegalStateException("주종 MANUAL 시드 리소스 없음: " + SEED_CLASSPATH);
            }
            JsonNode entries = objectMapper.readTree(in).get("entries");
            if (entries == null || !entries.isArray()) {
                throw new IllegalStateException("주종 MANUAL 시드 형식 오류: entries 배열 없음 — " + SEED_CLASSPATH);
            }
            return entries;
        } catch (IOException ex) {
            throw new IllegalStateException("주종 MANUAL 시드 읽기 실패: " + SEED_CLASSPATH, ex);
        }
    }
}
