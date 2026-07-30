package com.jeontongjuro.backend.brewery;

import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * brewery.join_status/liquor_status 첫 확정 UPDATE(3c-2). 제품이 1개 이상 연결된(AUTO∪MANUAL)
 * brewery만 JOINED로 올리고, 2축 원칙에 따라 liquor_status를 UNTAGGED로 맞춘다. 0건인 brewery는
 * 시드 초기값(UNJOINED/NA)을 그대로 둔다 — 주소 파싱·주종 롤업은 이 단계에서 하지 않는다.
 */
@Service
public class BreweryJoinStatusUpdateService {

    private final BreweryRepository breweryRepository;

    public BreweryJoinStatusUpdateService(BreweryRepository breweryRepository) {
        this.breweryRepository = breweryRepository;
    }

    public record UpdateResult(int candidateBreweries, int updatedToJoined, int alreadyJoined) {
    }

    @Transactional
    public UpdateResult applyJoinResult(Set<String> linkedBreweryIds) {
        int updated = 0;
        int already = 0;
        for (Brewery b : breweryRepository.findAll()) {
            if (!linkedBreweryIds.contains(b.getBreweryId())) {
                continue;
            }
            if (b.getJoinStatus() == JoinStatus.JOINED) {
                already++;
                continue;
            }
            b.markJoined();
            updated++;
        }
        return new UpdateResult(linkedBreweryIds.size(), updated, already);
    }
}
