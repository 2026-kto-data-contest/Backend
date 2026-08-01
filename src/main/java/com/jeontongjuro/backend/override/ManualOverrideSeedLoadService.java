package com.jeontongjuro.backend.override;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * manual_override 시드 적재 서비스(3c-1).
 * <p>
 * 소스: {@code src/main/resources/manual_override_seed.json}(assignment §결정4 확정 9행).
 * 재판정·증감 없이 그대로 적재한다.
 * <p>
 * 적재 전 각 행의 brewery_id가 brewery 테이블에 실재하는지 검증하고, 없으면 그 BRW를 들고 멈춘다
 * (참조 무결성 — DB FK가 최종 방어선이나 명확한 메시지를 위해 선검증). 멱등: (match_key_kind, match_key)가
 * 이미 있으면 건너뛴다.
 */
@Service
public class ManualOverrideSeedLoadService {

    private static final String SEED_CLASSPATH = "/manual_override_seed.json";

    private final ManualOverrideRepository overrideRepository;
    private final BreweryRepository breweryRepository;
    private final ObjectMapper objectMapper;

    public ManualOverrideSeedLoadService(ManualOverrideRepository overrideRepository,
                                         BreweryRepository breweryRepository,
                                         ObjectMapper objectMapper) {
        this.overrideRepository = overrideRepository;
        this.breweryRepository = breweryRepository;
        this.objectMapper = objectMapper;
    }

    public record LoadResult(int seedRows, int loaded, int skippedExisting) {
    }

    @Transactional
    public LoadResult load() {
        JsonNode entries = readSeedEntries();

        // FK 선검증 — 참조하는 BRW가 전부 brewery에 실재해야. 하나라도 없으면 멈추고 그 BRW 보고.
        Set<String> missing = new LinkedHashSet<>();
        for (JsonNode e : entries) {
            String breweryId = e.get("brewery_id").asText();
            if (!breweryRepository.existsById(breweryId)) {
                missing.add(breweryId);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "manual_override FK 위반: 참조 BRW가 brewery에 없음 " + missing + ". 적재 중단(brewery 선적재 필요).");
        }

        List<ManualOverride> toInsert = new ArrayList<>();
        int skipped = 0;
        int seedRows = 0;
        for (JsonNode e : entries) {
            seedRows++;
            MatchKeyKind matchKeyKind = MatchKeyKind.valueOf(e.get("match_key_kind").asText());
            String matchKey = e.get("match_key").asText();

            // 멱등: 같은 (kind, key) 이미 적재됐으면 건너뜀
            if (overrideRepository.existsByMatchKeyKindAndMatchKey(matchKeyKind, matchKey)) {
                skipped++;
                continue;
            }

            toInsert.add(ManualOverride.seed(
                    OverrideType.valueOf(e.get("override_type").asText()),
                    matchKey,
                    matchKeyKind,
                    e.get("brewery_id").asText(),
                    OverrideReason.valueOf(e.get("reason").asText()),
                    e.get("recheck_flag").asBoolean(),
                    e.get("source_raw_name").asText()));
        }
        overrideRepository.saveAll(toInsert);
        return new LoadResult(seedRows, toInsert.size(), skipped);
    }

    private JsonNode readSeedEntries() {
        try (InputStream in = getClass().getResourceAsStream(SEED_CLASSPATH)) {
            if (in == null) {
                throw new IllegalStateException("override 시드 리소스 없음: " + SEED_CLASSPATH);
            }
            JsonNode entries = objectMapper.readTree(in).get("entries");
            if (entries == null || !entries.isArray()) {
                throw new IllegalStateException("override 시드 형식 오류: entries 배열 없음 — " + SEED_CLASSPATH);
            }
            return entries;
        } catch (IOException ex) {
            throw new IllegalStateException("override 시드 읽기 실패: " + SEED_CLASSPATH, ex);
        }
    }
}
