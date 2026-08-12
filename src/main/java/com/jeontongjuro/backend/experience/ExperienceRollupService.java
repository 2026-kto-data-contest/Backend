package com.jeontongjuro.backend.experience;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * aT 체험 프로그램 편입 서비스(이슈 #52, 파이프라인 15단계). 라이브 odcloud 응답을 experience_match_seed로
 * 매칭해 {@code brewery_experience}에 값 인지 삭제형 diff로 반영한다.
 * <p>
 * ★두 실패 경로를 명확히 구분한다:
 * <ul>
 *   <li><b>odcloud 호출 실패</b>({@link ExperienceApiException}) → 15단계만 skip, 기존 행 보존, 나머지 완주.
 *       체험은 기준일 2021-09-17 고정 정적 파생이라 외부 API 장애가 파이프라인 전체를 죽이면 안 된다.
 *       skip 사유는 {@link RollupResult#skipReason()}로 남긴다.</li>
 *   <li><b>시드 미매칭</b>(API 성공했으나 시드에 없는 양조장명) → {@code IllegalStateException} fail-fast.
 *       원본이 갱신됐다는 신호라 조용히 skip하지 않고 사람이 봐야 한다.</li>
 * </ul>
 * <p>
 * 값 인지 diff(특징 태그의 삽입 전용 diff와 다른 점): payload(내용·장소·소요시간·비용)가 있어 키가 같고 값이
 * 바뀐 행은 갱신(updated)한다. update 없이는 옛 가격이 유령으로 영구히 남는다. 키 중복(자연키 붕괴)도 fail-fast.
 * <p>
 * {@code @Transactional} 단일 커밋(단계 독립 커밋·멱등 재개). API 호출은 tour-detail·nearby·geocoding과 동일하게
 * 트랜잭션 안에서 일어난다(1회 호출). skip 경로는 DB를 건드리지 않는다.
 */
@Service
public class ExperienceRollupService {

    private static final Logger log = LoggerFactory.getLogger(ExperienceRollupService.class);

    private final ExperienceApiClient apiClient;
    private final ExperienceMatchSeedLoadService seedLoadService;
    private final BreweryExperienceRepository experienceRepository;

    public ExperienceRollupService(ExperienceApiClient apiClient,
                                   ExperienceMatchSeedLoadService seedLoadService,
                                   BreweryExperienceRepository experienceRepository) {
        this.apiClient = apiClient;
        this.seedLoadService = seedLoadService;
        this.experienceRepository = experienceRepository;
    }

    /**
     * @param skipped         odcloud 호출 실패로 15단계를 건너뛰었는가(true면 아래 diff 카운터는 전부 0, 기존 행 보존)
     * @param skipReason      skip 사유(skipped=false면 null)
     * @param seedRows        시드 매칭 건수(30)
     * @param apiRows         API 응답 행수(원본 갱신 감지 — 기대 52)
     * @param targetBreweries 체험이 하나 이상 붙는 양조장 수(기대 30)
     * @param inserted        신규 삽입 행수
     * @param updated         키 동일·payload 변경으로 갱신한 행수
     * @param deleted         목표에 없어 삭제한 유령 행수(원본 갱신·오적재 정리)
     * @param unchanged       키·payload 동일해 유지한 행수(멱등 확인)
     */
    public record RollupResult(boolean skipped, String skipReason, int seedRows, int apiRows,
                               int targetBreweries, int inserted, int updated, int deleted, int unchanged) {

        /** odcloud 호출 실패 skip — 기존 행 보존, diff 카운터 0. */
        static RollupResult skip(int seedRows, String reason) {
            return new RollupResult(true, reason, seedRows, 0, 0, 0, 0, 0, 0);
        }
    }

    /** 자연키 (brewery_id, program_name). */
    private record Key(String breweryId, String programName) {
    }

    @Transactional
    public RollupResult rollup() {
        // 1. 라이브 수집 — 실패는 skip 신호(파이프라인 나머지 완주, 기존 brewery_experience 보존).
        List<ExperienceRow> rows;
        try {
            rows = apiClient.fetchAll();
        } catch (ExperienceApiException ex) {
            log.warn("[experience] odcloud 호출 실패 — 15단계 skip(기존 행 보존, 나머지 단계 완주): {}",
                    ex.getMessage());
            return RollupResult.skip(seedLoadService.size(), ex.getMessage());
        }

        // 2. 목표 집합 산출 — 시드 매칭. 미매칭·키중복은 fail-fast(원본 이상 신호, 사람 확인).
        Map<Key, ExperienceRow> target = new LinkedHashMap<>();
        for (ExperienceRow r : rows) {
            if (r.programName() == null) {
                throw new IllegalStateException(
                        "체험 프로그램명 결측 — 자연키 구성 불가(원본 이상). 양조장명='" + r.breweryName() + "'");
            }
            String breweryId = seedLoadService.breweryIdOf(r.breweryName());
            if (breweryId == null) {
                throw new IllegalStateException(
                        "체험 시드 미매칭: 양조장명 '" + r.breweryName()
                                + "' — 원본 갱신 신호(사람 확인 필요). experience_match_seed 갱신 후 재실행하라.");
            }
            Key key = new Key(breweryId, r.programName());
            if (target.putIfAbsent(key, r) != null) {
                throw new IllegalStateException(
                        "체험 자연키 중복: (" + breweryId + ", '" + r.programName()
                                + "') — (brewery_id, program_name) 유일성 위반(원본 이상). 사람 확인 필요.");
            }
        }

        // 3. 현재 집합(DB) 로드.
        List<BreweryExperience> current = experienceRepository.findAll();
        Map<Key, BreweryExperience> currentByKey = new HashMap<>();
        for (BreweryExperience e : current) {
            currentByKey.put(new Key(e.getBreweryId(), e.getProgramName()), e);
        }

        // 4. 값 인지 diff — 목표에만=삽입, 키동일·값상이=갱신, 키동일·값동일=unchanged, 현재에만=삭제.
        List<BreweryExperience> toInsert = new ArrayList<>();
        List<BreweryExperience> toUpdate = new ArrayList<>();
        int unchanged = 0;
        for (Map.Entry<Key, ExperienceRow> e : target.entrySet()) {
            ExperienceRow r = e.getValue();
            BreweryExperience existing = currentByKey.get(e.getKey());
            if (existing == null) {
                toInsert.add(BreweryExperience.of(e.getKey().breweryId(), r.programName(),
                        r.content(), r.place(), r.duration(), r.cost()));
            } else if (existing.samePayload(r.content(), r.place(), r.duration(), r.cost())) {
                unchanged++;
            } else {
                existing.updatePayload(r.content(), r.place(), r.duration(), r.cost());
                toUpdate.add(existing);
            }
        }
        List<BreweryExperience> toDelete = new ArrayList<>();
        for (BreweryExperience e : current) {
            if (!target.containsKey(new Key(e.getBreweryId(), e.getProgramName()))) {
                toDelete.add(e);
            }
        }

        experienceRepository.deleteAll(toDelete);
        experienceRepository.saveAll(toInsert);
        experienceRepository.saveAll(toUpdate);

        int targetBreweries = (int) target.keySet().stream()
                .map(Key::breweryId).distinct().count();
        log.info("[experience] 편입 완료 api={} target={} ins={} upd={} del={} unch={}",
                rows.size(), target.size(), toInsert.size(), toUpdate.size(), toDelete.size(), unchanged);
        return new RollupResult(false, null, seedLoadService.size(), rows.size(),
                targetBreweries, toInsert.size(), toUpdate.size(), toDelete.size(), unchanged);
    }
}
