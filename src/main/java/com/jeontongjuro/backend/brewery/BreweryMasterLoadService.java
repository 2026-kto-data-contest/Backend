package com.jeontongjuro.backend.brewery;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jeontongjuro.backend.pipeline.collect.raw.BreweryRaw;
import java.io.IOException;
import java.io.InputStream;
import java.text.Normalizer;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * brewery 마스터 적재 서비스(3c-1).
 * <p>
 * 소스: (1) 채번원장 {@code src/main/resources/brewery_id_ledger.json}(BRW·상호명·norm, 불변 봉인),
 * (2) 골든 brewery raw 속성 행(주소·홈페이지·조회수·방문여부). ★라이브 API·라이브 DB 미사용 —
 * raw 속성은 호출자가 골든 픽스처에서 역직렬화해 주입한다(의존 역전, 테스트가 골든 주입).
 * <p>
 * 조인키 = 상호명 NFC 정규화 매칭(BusinessNameNormalizer의 norm이 아니라 상호명 자체의 NFC).
 * 원장 59 상호명이 raw에 전부 매칭돼야 하며, 하나라도 미매칭이면 그 상호명을 들고 멈춘다(원장↔raw 드리프트 신호).
 * <p>
 * 멱등: 이미 적재된 brewery_id는 건너뛴다(PK 최종 방어).
 */
@Service
public class BreweryMasterLoadService {

    private static final String LEDGER_CLASSPATH = "/brewery_id_ledger.json";

    private final BreweryRepository breweryRepository;
    private final ObjectMapper objectMapper;

    public BreweryMasterLoadService(BreweryRepository breweryRepository, ObjectMapper objectMapper) {
        this.breweryRepository = breweryRepository;
        this.objectMapper = objectMapper;
    }

    public record LoadResult(int ledgerRows, int loaded, int skippedExisting) {
    }

    /** 상호명 NFC 정규화 — 조인 매칭키. (정규화명 norm이 아님) */
    private static String nfc(String value) {
        return value == null ? null : Normalizer.normalize(value, Normalizer.Form.NFC);
    }

    /**
     * 원장 + 골든 raw 속성 행을 조인해 brewery 마스터를 적재한다.
     *
     * @param attributeRows 골든 brewery raw 속성 행(상호명·주소·홈페이지·조회수·방문여부). 호출자가 골든에서 주입.
     */
    @Transactional
    public LoadResult load(List<BreweryRaw> attributeRows) {
        // raw 속성 인덱스: NFC(상호명) → 행. 상호명 NFC는 골든 59에서 유니크(3b 실측)이므로 1:1.
        Map<String, BreweryRaw> byNfcName = new LinkedHashMap<>();
        for (BreweryRaw row : attributeRows) {
            String key = nfc(row.getBusinessName());
            BreweryRaw prev = byNfcName.put(key, row);
            if (prev != null) {
                throw new IllegalStateException(
                        "골든 raw 상호명 NFC 중복 — 조인 모호(3b 유니크 전제 위반): '" + key + "'");
            }
        }

        JsonNode entries = readLedgerEntries();
        List<Brewery> toInsert = new ArrayList<>();
        int skipped = 0;
        int ledgerRows = 0;
        for (JsonNode e : entries) {
            ledgerRows++;
            String breweryId = e.get("brewery_id").asText();
            String businessName = e.get("business_name").asText();
            String norm = e.get("norm").asText();

            BreweryRaw attr = byNfcName.get(nfc(businessName));
            if (attr == null) {
                // ★임의 처리 금지 — 멈추고 그 상호명 보고(원장↔raw 드리프트)
                throw new IllegalStateException(
                        "원장↔raw 드리프트: 원장 상호명 '" + businessName + "' (" + breweryId
                                + ")이 골든 brewery raw에서 NFC 매칭되지 않음. 적재 중단.");
            }

            // 멱등: 이미 적재된 BRW는 건너뜀(PK 방어)
            if (breweryRepository.existsById(breweryId)) {
                skipped++;
                continue;
            }

            toInsert.add(Brewery.seed(
                    breweryId,
                    businessName,
                    norm,
                    attr.getAddress(),
                    attr.getHomepageUrl(),
                    attr.getViewCount(),
                    VisitState.fromRaw(attr.getReservationVisitYn()),
                    VisitState.fromRaw(attr.getAnytimeVisitYn())));
        }
        breweryRepository.saveAll(toInsert);
        return new LoadResult(ledgerRows, toInsert.size(), skipped);
    }

    private JsonNode readLedgerEntries() {
        try (InputStream in = getClass().getResourceAsStream(LEDGER_CLASSPATH)) {
            if (in == null) {
                throw new IllegalStateException("채번원장 리소스 없음: " + LEDGER_CLASSPATH);
            }
            JsonNode entries = objectMapper.readTree(in).get("entries");
            if (entries == null || !entries.isArray()) {
                throw new IllegalStateException("채번원장 형식 오류: entries 배열 없음 — " + LEDGER_CLASSPATH);
            }
            return entries;
        } catch (IOException ex) {
            throw new IllegalStateException("채번원장 읽기 실패: " + LEDGER_CLASSPATH, ex);
        }
    }
}
