package com.jeontongjuro.backend.brewery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 14단계(#50) — 농림부 '찾아가는 양조장' 지정현황({@code nonglim_seed.json})을 brewery에 편입한다.
 * 설립연도·대표자·선정연도·특징서술 4개만 UPDATE한다.
 * <p>
 * ★기준일 오염 방어: 이 시드는 2019-12-31 데이터다. 소재지·주종·업체명은 <b>시드가 애초에 담지 않아</b>
 * 컬럼으로 만들 수도, 마스터(2024-12-31 주소·주종 진실원천)를 덮을 수도 없다(예술주조 홍천→춘천 이전 실증).
 * 로더는 brewery_id로만 조인하고 파생 4값만 대입한다.
 * <p>
 * 이름(정규화) 확정 매칭 36곳만 적재한다. 주소만 일치하고 상호가 다른 2건(제이엘·명가원)은 시드 {@code _meta.heldBack}에
 * 보류로 기록했고 entries엔 없다(틀린 값 부착 위험 &gt; 1곳 결측). 사람 확인 후 additive.
 * <p>
 * 마스터 로드는 기존 brewery_id를 skip하므로 이 값들은 UPDATE 경로로만 채워진다(백필 함정 방어). 멱등:
 * {@code founded_year}가 이미 있으면 건너뛴다. {@code @Transactional} 단일 커밋.
 */
@Service
public class NonglimSeedLoadService {

    private static final Logger log = LoggerFactory.getLogger(NonglimSeedLoadService.class);
    private static final String SEED_CLASSPATH = "/nonglim_seed.json";

    private final BreweryRepository breweryRepository;
    private final ObjectMapper objectMapper;

    public NonglimSeedLoadService(BreweryRepository breweryRepository, ObjectMapper objectMapper) {
        this.breweryRepository = breweryRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * @param seedRows        시드 행수(기대 36)
     * @param applied         founded_year가 비어 있어 새로 채운 수
     * @param skippedExisting 이미 채워져 건너뛴 수(멱등 재실행)
     */
    public record LoadResult(int seedRows, int applied, int skippedExisting) {
    }

    @Transactional
    public LoadResult load() {
        JsonNode entries = readSeedEntries();

        // FK 선검증 — 참조 BRW가 전부 brewery에 실재해야.
        Set<String> missing = new LinkedHashSet<>();
        for (JsonNode e : entries) {
            String breweryId = e.get("breweryId").asText();
            if (!breweryRepository.existsById(breweryId)) {
                missing.add(breweryId);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "nonglim 시드 FK 위반: 참조 BRW가 brewery에 없음 " + missing + ". 적재 중단(brewery 선적재 필요).");
        }

        int seedRows = 0;
        int applied = 0;
        int skippedExisting = 0;
        for (JsonNode e : entries) {
            seedRows++;
            String breweryId = e.get("breweryId").asText();
            Brewery b = breweryRepository.findById(breweryId).orElseThrow();
            if (b.getFoundedYear() != null) {
                skippedExisting++;
                continue;
            }
            b.applyNonglim(
                    e.get("foundedYear").asInt(),
                    e.get("representativeName").asText(),
                    e.get("designatedYear").asInt(),
                    e.get("designationNote").asText());
            applied++;
        }
        log.info("[nonglim] 시드 {} · 적용 {} · 기존 skip {}", seedRows, applied, skippedExisting);
        return new LoadResult(seedRows, applied, skippedExisting);
    }

    private JsonNode readSeedEntries() {
        try (InputStream in = getClass().getResourceAsStream(SEED_CLASSPATH)) {
            if (in == null) {
                throw new IllegalStateException("nonglim 시드 리소스 없음: " + SEED_CLASSPATH);
            }
            JsonNode entries = objectMapper.readTree(in).get("entries");
            if (entries == null || !entries.isArray()) {
                throw new IllegalStateException("nonglim 시드 형식 오류: entries 배열 없음 — " + SEED_CLASSPATH);
            }
            return entries;
        } catch (IOException ex) {
            throw new IllegalStateException("nonglim 시드 읽기 실패: " + SEED_CLASSPATH, ex);
        }
    }
}
