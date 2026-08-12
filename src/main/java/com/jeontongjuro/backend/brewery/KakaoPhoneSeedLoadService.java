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
 * 13단계(#50) — 카카오 전화 시드({@code kakao_phone_seed.json})를 brewery.phone에 편입한다.
 * <p>
 * 카카오 상호검색+200m 좌표 게이트는 비결정적이라 파이프라인에서 매번 호출하면 결과가 흔들린다 → 시드로 고정한다
 * (experience_match_seed 선례). {@code status=PHONE} 행만 적용하고 NO_MATCH·NO_PHONE는 무시한다(시드엔
 * 판별력 위해 남아 있으나 로더는 건드리지 않는다).
 * <p>
 * ★전화 우선순위 TOUR &gt; KAKAO를 순서로 강제한다 — 12단계(관광공사)가 먼저 채운 phone은 절대 덮지 않고
 * ({@code phone != null}이면 skip) 비어 있는 곳만 KAKAO로 채운다. 그래서 이 단계는 12단계 뒤에 온다.
 * <p>
 * 마스터 로드는 기존 brewery_id를 skip하므로 phone은 여기 UPDATE 경로로만 채워진다(백필 함정 방어).
 * {@code @Transactional} 단일 커밋.
 */
@Service
public class KakaoPhoneSeedLoadService {

    private static final Logger log = LoggerFactory.getLogger(KakaoPhoneSeedLoadService.class);
    private static final String SEED_CLASSPATH = "/kakao_phone_seed.json";
    private static final String STATUS_PHONE = "PHONE";

    private final BreweryRepository breweryRepository;
    private final ObjectMapper objectMapper;

    public KakaoPhoneSeedLoadService(BreweryRepository breweryRepository, ObjectMapper objectMapper) {
        this.breweryRepository = breweryRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * @param seedRows        시드 총 행수(기대 59)
     * @param phoneEntries    status=PHONE 행수(기대 51)
     * @param applied         phone이 비어 있어 KAKAO로 새로 채운 수
     * @param skippedTourWins 이미 phone이 있어(주로 TOUR) 건너뛴 수
     * @param nonPhone        status가 PHONE이 아니라 적용 대상이 아닌 행수(NO_MATCH·NO_PHONE)
     */
    public record LoadResult(int seedRows, int phoneEntries, int applied, int skippedTourWins, int nonPhone) {
    }

    @Transactional
    public LoadResult load() {
        JsonNode entries = readSeedEntries();

        // FK 선검증 — 시드가 참조하는 BRW가 전부 brewery에 실재해야(명확 메시지용 선검증).
        Set<String> missing = new LinkedHashSet<>();
        for (JsonNode e : entries) {
            String breweryId = e.get("breweryId").asText();
            if (!breweryRepository.existsById(breweryId)) {
                missing.add(breweryId);
            }
        }
        if (!missing.isEmpty()) {
            throw new IllegalStateException(
                    "kakao_phone 시드 FK 위반: 참조 BRW가 brewery에 없음 " + missing + ". 적재 중단(brewery 선적재 필요).");
        }

        int seedRows = 0;
        int phoneEntries = 0;
        int applied = 0;
        int skippedTourWins = 0;
        int nonPhone = 0;
        for (JsonNode e : entries) {
            seedRows++;
            String status = e.get("status").asText();
            if (!STATUS_PHONE.equals(status)) {
                nonPhone++;
                continue;
            }
            JsonNode phoneNode = e.get("phone");
            if (phoneNode == null || phoneNode.isNull() || phoneNode.asText().isBlank()) {
                nonPhone++; // status=PHONE인데 값 없음(방어) — 적용 대상 아님
                continue;
            }
            phoneEntries++;
            String breweryId = e.get("breweryId").asText();
            Brewery b = breweryRepository.findById(breweryId).orElseThrow();
            // TOUR 우선: 이미 채워진 phone은 덮지 않는다.
            if (b.getPhone() != null) {
                skippedTourWins++;
                continue;
            }
            b.applyPhone(phoneNode.asText(), PhoneSource.KAKAO);
            applied++;
        }
        log.info("[kakao-phone] 시드 {} · PHONE {} · 적용 {} · TOUR우선 skip {} · 비PHONE {}",
                seedRows, phoneEntries, applied, skippedTourWins, nonPhone);
        return new LoadResult(seedRows, phoneEntries, applied, skippedTourWins, nonPhone);
    }

    private JsonNode readSeedEntries() {
        try (InputStream in = getClass().getResourceAsStream(SEED_CLASSPATH)) {
            if (in == null) {
                throw new IllegalStateException("kakao_phone 시드 리소스 없음: " + SEED_CLASSPATH);
            }
            JsonNode entries = objectMapper.readTree(in).get("entries");
            if (entries == null || !entries.isArray()) {
                throw new IllegalStateException("kakao_phone 시드 형식 오류: entries 배열 없음 — " + SEED_CLASSPATH);
            }
            return entries;
        } catch (IOException ex) {
            throw new IllegalStateException("kakao_phone 시드 읽기 실패: " + SEED_CLASSPATH, ex);
        }
    }
}
