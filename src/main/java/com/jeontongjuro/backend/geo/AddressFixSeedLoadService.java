package com.jeontongjuro.backend.geo;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * address_fix_seed.json 로더(#28 3-3/4). raw 주소 오류를 보정한다 — 좌표가 아니라 <b>주소</b>를 고치며,
 * 좌표는 언제나 카카오가 계산한다. append-only 정책.
 * <p>
 * 기존 시드 로더({@code ManualOverrideSeedLoadService})처럼 {@code JsonNode} 직접 접근, 주입
 * {@link ObjectMapper} 사용. 로더가 파싱하는 것은 {@code brewery_id}와 {@code fixed_address}뿐이다
 * ({@code _meta}·{@code reason}·{@code raw_address}는 기록용).
 * <p>
 * ★{@code raw_address}는 스테일 감지용이다 — {@link #resolve}에서 현재 {@code brewery.address}와 대조해
 * 다르면 WARN 후 계속 진행한다(fail 아님, 기존 override 스테일 경고와 동일한 WARN-and-continue).
 * 새 uddi로 raw가 바뀌면 시드 재검증이 필요하다는 신호다.
 */
@Service
public class AddressFixSeedLoadService {

    private static final Logger log = LoggerFactory.getLogger(AddressFixSeedLoadService.class);
    private static final String SEED_CLASSPATH = "/address_fix_seed.json";

    private final ObjectMapper objectMapper;

    /** brewery_id → (fixed_address, raw_address). 삽입 순서 보존(로그 가독성). */
    private final Map<String, Fix> fixes = new LinkedHashMap<>();

    public AddressFixSeedLoadService(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    private record Fix(String fixedAddress, String rawAddress) {
    }

    @PostConstruct
    void load() {
        JsonNode arr = readFixes();
        for (JsonNode f : arr) {
            String breweryId = f.get("brewery_id").asText();
            String fixedAddress = f.get("fixed_address").asText();
            String rawAddress = f.path("raw_address").asText(null);
            fixes.put(breweryId, new Fix(fixedAddress, rawAddress));
        }
        log.info("[geo] address_fix_seed 로드 {}건: {}", fixes.size(), fixes.keySet());
    }

    /** 시드에 보정 대상이 아니면 empty. */
    public boolean has(String breweryId) {
        return fixes.containsKey(breweryId);
    }

    /**
     * 보정 주소를 반환한다. 시드의 {@code raw_address}가 현재 {@code brewery.address}와 다르면 WARN 후 계속.
     *
     * @param breweryId        대상 양조장
     * @param currentRawAddress 현재 brewery.address(스테일 대조용)
     * @return 보정 주소, 시드 대상 아니면 empty
     */
    public Optional<String> resolve(String breweryId, String currentRawAddress) {
        Fix fix = fixes.get(breweryId);
        if (fix == null) {
            return Optional.empty();
        }
        if (fix.rawAddress() != null && !Objects.equals(fix.rawAddress(), currentRawAddress)) {
            log.warn("[geo] address_fix_seed 스테일 후보 brewery_id={} seed.raw='{}' != 현재 address='{}' "
                    + "→ 시드 재검증 필요(보정은 그대로 적용, 계속 진행)",
                    breweryId, fix.rawAddress(), currentRawAddress);
        }
        return Optional.of(fix.fixedAddress());
    }

    private JsonNode readFixes() {
        try (InputStream in = getClass().getResourceAsStream(SEED_CLASSPATH)) {
            if (in == null) {
                throw new IllegalStateException("address_fix_seed 리소스 없음: " + SEED_CLASSPATH);
            }
            JsonNode fixesNode = objectMapper.readTree(in).get("fixes");
            if (fixesNode == null || !fixesNode.isArray()) {
                throw new IllegalStateException("address_fix_seed 형식 오류: fixes 배열 없음 — " + SEED_CLASSPATH);
            }
            return fixesNode;
        } catch (IOException ex) {
            throw new IllegalStateException("address_fix_seed 읽기 실패: " + SEED_CLASSPATH, ex);
        }
    }
}
