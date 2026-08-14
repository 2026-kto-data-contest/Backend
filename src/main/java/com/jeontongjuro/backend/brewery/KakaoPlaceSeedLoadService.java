package com.jeontongjuro.backend.brewery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 16단계(#54) — 카카오 place 시드({@code kakao_place_seed.json})를 brewery.kakao_place_url에 편입한다.
 * <p>
 * 카카오 상호검색+200m 좌표 게이트는 비결정적이라 파이프라인에서 매번 호출하면 결과가 흔들린다 → 시드로 고정한다
 * (kakao_phone_seed 선례). {@code status=PLACE} 행만 적용하고 NO_MATCH는 무시한다(시드엔 판별력 위해 남아
 * 있으나 로더는 건드리지 않는다). 로더는 {@code breweryId/status/placeUrl}만 읽는다.
 * <p>
 * ★phone(TOUR&gt;KAKAO 우선순위)과 달리 placeUrl은 경쟁 소스가 없어 조건 없이 대입한다. 다만 재실행 멱등을 위해
 * 이미 같은 값이면 건드리지 않고 {@code unchanged}로 센다(값이 바뀌었을 때만 적용). placeUrl은 카카오가 준
 * http:// 원문 그대로 저장한다(접속 시 https 리다이렉트 — 변환 금지).
 * <p>
 * 마스터 로드는 기존 brewery_id를 skip하므로 kakao_place_url은 여기 UPDATE 경로로만 채워진다(백필 함정 방어).
 * 1단계 마스터 FK에만 의존한다(4~15단계와 독립). {@code @Transactional} 단일 커밋.
 */
@Service
public class KakaoPlaceSeedLoadService {

    private static final Logger log = LoggerFactory.getLogger(KakaoPlaceSeedLoadService.class);
    private static final String SEED_CLASSPATH = "/kakao_place_seed.json";
    private static final String STATUS_PLACE = "PLACE";

    private final BreweryRepository breweryRepository;
    private final ObjectMapper objectMapper;

    public KakaoPlaceSeedLoadService(BreweryRepository breweryRepository, ObjectMapper objectMapper) {
        this.breweryRepository = breweryRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * @param seedRows     시드 총 행수(기대 59)
     * @param placeEntries status=PLACE이고 placeUrl 보유한 적용 대상 행수(기대 55)
     * @param applied      kakao_place_url을 새로(또는 다른 값으로) 채운 수
     * @param unchanged    이미 같은 URL이라 건드리지 않은 수(멱등 스킵)
     * @param nonPlace     status가 PLACE가 아니거나 placeUrl이 없어 적용 대상이 아닌 행수(NO_MATCH 등, 기대 4)
     */
    public record LoadResult(int seedRows, int placeEntries, int applied, int unchanged, int nonPlace) {
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
                    "kakao_place 시드 FK 위반: 참조 BRW가 brewery에 없음 " + missing + ". 적재 중단(brewery 선적재 필요).");
        }

        int seedRows = 0;
        int placeEntries = 0;
        int applied = 0;
        int unchanged = 0;
        int nonPlace = 0;
        for (JsonNode e : entries) {
            seedRows++;
            String status = e.get("status").asText();
            if (!STATUS_PLACE.equals(status)) {
                nonPlace++;
                continue;
            }
            JsonNode urlNode = e.get("placeUrl");
            if (urlNode == null || urlNode.isNull() || urlNode.asText().isBlank()) {
                nonPlace++; // status=PLACE인데 URL 없음(방어) — 적용 대상 아님
                continue;
            }
            placeEntries++;
            String breweryId = e.get("breweryId").asText();
            String placeUrl = urlNode.asText();
            Brewery b = breweryRepository.findById(breweryId).orElseThrow();
            // 경쟁 소스 없음 — 조건 없이 대입하되, 이미 같은 값이면 멱등 스킵(재실행 시 applied=0).
            if (Objects.equals(b.getKakaoPlaceUrl(), placeUrl)) {
                unchanged++;
                continue;
            }
            b.applyKakaoPlaceUrl(placeUrl);
            applied++;
        }
        log.info("[kakao-place] 시드 {} · PLACE {} · 적용 {} · 불변 {} · 비PLACE {}",
                seedRows, placeEntries, applied, unchanged, nonPlace);
        return new LoadResult(seedRows, placeEntries, applied, unchanged, nonPlace);
    }

    private JsonNode readSeedEntries() {
        try (InputStream in = getClass().getResourceAsStream(SEED_CLASSPATH)) {
            if (in == null) {
                throw new IllegalStateException("kakao_place 시드 리소스 없음: " + SEED_CLASSPATH);
            }
            JsonNode entries = objectMapper.readTree(in).get("entries");
            if (entries == null || !entries.isArray()) {
                throw new IllegalStateException("kakao_place 시드 형식 오류: entries 배열 없음 — " + SEED_CLASSPATH);
            }
            return entries;
        } catch (IOException ex) {
            throw new IllegalStateException("kakao_place 시드 읽기 실패: " + SEED_CLASSPATH, ex);
        }
    }
}
