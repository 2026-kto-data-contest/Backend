package com.jeontongjuro.backend.brewery;

import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * brewery.liquor_status 전이(3d 7단계). 4단계 {@link BreweryJoinStatusUpdateService}와 대칭 구조 —
 * "확정 양조장 id 집합"(조인 제품 전건 태깅 & 전건 recheck_flag=false)을 받아 liquor_status만 갱신한다.
 * 집합 산출은 {@code LiquorRollupStatusService}(주종층)가 하고, 이 서비스는 상태 대입·멱등만 책임진다.
 * <p>
 * 2축 원칙 강제: UNJOINED → NA, JOINED → (확정) TAGGED / (그 외) UNTAGGED. JOINED인데 NA로 남는
 * 모순은 발생할 수 없다. 멱등: 이미 목표 값이면 스킵(unchanged) — region/join 갱신 서비스와 동일 패턴.
 */
@Service
public class BreweryLiquorStatusUpdateService {

    private final BreweryRepository breweryRepository;

    public BreweryLiquorStatusUpdateService(BreweryRepository breweryRepository) {
        this.breweryRepository = breweryRepository;
    }

    /**
     * @param total     처리 대상 brewery 수
     * @param tagged    TAGGED로 확정된 행 수
     * @param untagged  UNTAGGED로 확정된 행 수
     * @param na        NA로 확정된 행 수(미조인)
     * @param changed   liquor_status가 실제로 바뀐 행 수
     * @param unchanged 이미 같은 값이라 변화 없던 행 수(멱등 재실행 시 증가)
     */
    public record UpdateResult(int total, int tagged, int untagged, int na, int changed, int unchanged) {
    }

    @Transactional
    public UpdateResult applyLiquorRollup(Set<String> confirmedBreweryIds) {
        int tagged = 0;
        int untagged = 0;
        int na = 0;
        int changed = 0;
        int unchanged = 0;
        for (Brewery b : breweryRepository.findAll()) {
            LiquorStatus target;
            if (b.getJoinStatus() == JoinStatus.UNJOINED) {
                target = LiquorStatus.NA; // 2축: 미조인은 항상 NA
            } else {
                target = confirmedBreweryIds.contains(b.getBreweryId())
                        ? LiquorStatus.TAGGED
                        : LiquorStatus.UNTAGGED;
            }
            switch (target) {
                case TAGGED -> tagged++;
                case UNTAGGED -> untagged++;
                case NA -> na++;
            }
            if (b.getLiquorStatus() == target) {
                unchanged++;
            } else {
                b.applyLiquorStatus(target);
                changed++;
            }
        }
        return new UpdateResult(tagged + untagged + na, tagged, untagged, na, changed, unchanged);
    }
}
