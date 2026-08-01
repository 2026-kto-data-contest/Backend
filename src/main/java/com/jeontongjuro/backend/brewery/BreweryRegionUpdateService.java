package com.jeontongjuro.backend.brewery;

import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * brewery.sido/region 파생값 UPDATE. 마스터 로드(3c-1)와 분리된 별도 단계로, 골든 주소 원문(brewery.address,
 * NOT NULL)을 {@link BreweryRegionParser}로 파싱해 sido·region을 채운다(컬럼 생성 ≠ 값 확정 원칙).
 * <p>
 * 순수 파서 산출이라 자연히 멱등하다 — 재실행하면 이미 채워진 행은 같은 값으로 재확정되고,
 * 060~ 신규 양조장이 추가된 뒤 재실행하면 빈 행이 채워진다. 미분류 시도가 나오면 파서가 예외로
 * 시끄럽게 실패하므로, 조용히 null이 새어드는 일은 없다.
 */
@Service
public class BreweryRegionUpdateService {

    private final BreweryRepository breweryRepository;

    public BreweryRegionUpdateService(BreweryRepository breweryRepository) {
        this.breweryRepository = breweryRepository;
    }

    /**
     * @param total    처리 대상 brewery 수
     * @param changed  sido 또는 region 값이 실제로 바뀐 행 수
     * @param unchanged 이미 같은 값이라 변화 없던 행 수(멱등 재실행 시 증가)
     */
    public record UpdateResult(int total, int changed, int unchanged) {
    }

    @Transactional
    public UpdateResult apply() {
        int changed = 0;
        int unchanged = 0;
        for (Brewery b : breweryRepository.findAll()) {
            BreweryRegionParser.RegionResult r = BreweryRegionParser.parse(b.getAddress());
            if (Objects.equals(b.getSido(), r.sido()) && Objects.equals(b.getRegion(), r.region())) {
                unchanged++;
                continue;
            }
            b.applyRegion(r.sido(), r.region());
            changed++;
        }
        return new UpdateResult(changed + unchanged, changed, unchanged);
    }
}
