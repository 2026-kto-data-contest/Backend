package com.jeontongjuro.backend.tour;

import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * TourAPI 캐싱·매칭 검수 리포트(LiquorAuditReportService 선례: docs/audit/ + 콘솔). ProcessRunner가 실행
 * 후 호출한다(오케스트레이터 밖 — 통합 테스트에서 안 돈다). 두 축:
 * <ul>
 *   <li>(1) ★unmatched 후보 — content_id가 비는 양조장별 반경 200m 이내 콘텐츠 목록
 *       (content_id·title·distance_m·contenttypeid). 자동 확정하지 않고 사람이 시드 추가 여부를 판단한다
 *       (교정3 — BRW-005/030/025/020이 자연 포함된다).</li>
 *   <li>(2) §9 통계 — tour_content 분포, brewery_nearby 통계, 0건 양조장, first_image 결측률,
 *       cpyrht 분포, 매칭 수, 자기제외 파생 건수.</li>
 * </ul>
 */
@Service
public class TourAuditReportService {

    private static final double CANDIDATE_RADIUS_M = 200.0;

    private final BreweryRepository breweryRepository;
    private final TourContentRepository tourContentRepository;
    private final BreweryNearbyRepository nearbyRepository;

    public TourAuditReportService(BreweryRepository breweryRepository,
                                  TourContentRepository tourContentRepository,
                                  BreweryNearbyRepository nearbyRepository) {
        this.breweryRepository = breweryRepository;
        this.tourContentRepository = tourContentRepository;
        this.nearbyRepository = nearbyRepository;
    }

    @Transactional(readOnly = true)
    public String render() {
        List<Brewery> breweries = breweryRepository.findAll();
        breweries.sort(Comparator.comparing(Brewery::getBreweryId));
        Map<String, TourContent> contentById = new HashMap<>();
        for (TourContent c : tourContentRepository.findAll()) {
            contentById.put(c.getContentId(), c);
        }
        List<BreweryNearby> nearby = nearbyRepository.findAll();
        Map<String, List<BreweryNearby>> nearbyByBrewery = new TreeMap<>();
        for (BreweryNearby n : nearby) {
            nearbyByBrewery.computeIfAbsent(n.getBreweryId(), k -> new ArrayList<>()).add(n);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# TourAPI 캐싱·매칭 검수 리포트\n\n");
        appendUnmatchedCandidates(sb, breweries, contentById, nearbyByBrewery);
        appendStats(sb, breweries, contentById, nearby, nearbyByBrewery);
        return sb.toString();
    }

    /** docs/audit/&lt;date&gt;_tour_audit.md로 기록하고 경로 반환. */
    public Path writeReport(LocalDate date) {
        String content = render();
        Path path = Path.of("docs", "audit", date.toString().replace("-", "") + "_tour_audit.md");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("TourAPI 검수 리포트 기록 실패: " + path, ex);
        }
        return path;
    }

    private void appendUnmatchedCandidates(StringBuilder sb, List<Brewery> breweries,
                                           Map<String, TourContent> contentById,
                                           Map<String, List<BreweryNearby>> nearbyByBrewery) {
        List<Brewery> unmatched = breweries.stream().filter(b -> b.getContentId() == null).toList();
        sb.append("## (1) unmatched 양조장 — 반경 200m 이내 콘텐츠 후보 (사람 판단용, 자동확정 없음)\n\n");
        sb.append("content_id가 비는 양조장 **").append(unmatched.size()).append("곳**. 아래 목록을 보고 ")
                .append("tour_match_seed.json에 추가할지 사람이 판단한다(BRW-005/030/025/020 등 포함).\n\n");
        for (Brewery b : unmatched) {
            List<BreweryNearby> within = new ArrayList<>();
            for (BreweryNearby n : nearbyByBrewery.getOrDefault(b.getBreweryId(), List.of())) {
                if (n.getDistanceM() != null && n.getDistanceM().doubleValue() <= CANDIDATE_RADIUS_M) {
                    within.add(n);
                }
            }
            within.sort(Comparator.comparing(n -> n.getDistanceM().doubleValue()));
            sb.append("### ").append(b.getBreweryId()).append(" · ").append(nz(b.getBusinessName()))
                    .append(" — 200m 이내 ").append(within.size()).append("건\n\n");
            if (within.isEmpty()) {
                sb.append("_해당 없음._\n\n");
                continue;
            }
            sb.append("| content_id | title | distance_m | contenttypeid |\n|---|---|---|---|\n");
            for (BreweryNearby n : within) {
                TourContent c = contentById.get(n.getContentId());
                sb.append("| ").append(n.getContentId())
                        .append(" | ").append(c != null ? nz(c.getTitle()) : "(미상)")
                        .append(" | ").append(fmt(n.getDistanceM()))
                        .append(" | ").append(c != null ? nz(c.getContentTypeId()) : "")
                        .append(" |\n");
            }
            sb.append('\n');
        }
    }

    private void appendStats(StringBuilder sb, List<Brewery> breweries,
                             Map<String, TourContent> contentById, List<BreweryNearby> nearby,
                             Map<String, List<BreweryNearby>> nearbyByBrewery) {
        sb.append("## (2) §9 통계\n\n");

        // tour_content 분포.
        Map<String, Integer> byType = new TreeMap<>();
        int firstImageNull = 0;
        Map<String, Integer> byCpyrht = new TreeMap<>();
        for (TourContent c : contentById.values()) {
            byType.merge(nz(c.getContentTypeId()), 1, Integer::sum);
            if (c.getFirstImage() == null) {
                firstImageNull++;
            }
            byCpyrht.merge(c.getCpyrhtDivCd() == null ? "(null)" : c.getCpyrhtDivCd(), 1, Integer::sum);
        }
        int contentTotal = contentById.size();
        sb.append("- tour_content 총 **").append(contentTotal).append("행**\n");
        sb.append("  - content_type_id 분포: ").append(byType).append('\n');
        sb.append("  - first_image 결측 ").append(firstImageNull).append("행 (")
                .append(pct(firstImageNull, contentTotal)).append(")\n");
        sb.append("  - cpyrht_div_cd 분포: ").append(byCpyrht).append('\n');

        // brewery_nearby 통계.
        int withContent = nearbyByBrewery.size();
        int emptyRadius = breweries.size() - withContent;
        sb.append("- brewery_nearby 총 **").append(nearby.size()).append("행**, ")
                .append("withContent=").append(withContent).append(" emptyRadius=").append(emptyRadius)
                .append(" (합=").append(withContent + emptyRadius).append("==").append(breweries.size()).append(")\n");
        sb.append("  - 양조장별 반경내 건수(min/median/max): ")
                .append(minMedianMax(nearbyByBrewery)).append('\n');

        // 0건 양조장.
        List<String> empties = new ArrayList<>();
        for (Brewery b : breweries) {
            if (!nearbyByBrewery.containsKey(b.getBreweryId())) {
                empties.add(b.getBreweryId());
            }
        }
        sb.append("  - 반경 0건 양조장(").append(empties.size()).append("): ").append(empties).append('\n');

        // 매칭 수 + 자기제외 파생.
        long matched = breweries.stream().filter(b -> b.getContentId() != null).count();
        long unmatched = breweries.size() - matched;
        sb.append("- 매칭: matched=").append(matched).append(" unmatched=").append(unmatched)
                .append(" (합=").append(matched + unmatched).append("==").append(breweries.size()).append(")\n");

        // 자기제외 파생 건수: brewery_nearby.content_id = 해당 brewery.content_id.
        Map<String, String> matchByBrewery = new HashMap<>();
        for (Brewery b : breweries) {
            if (b.getContentId() != null) {
                matchByBrewery.put(b.getBreweryId(), b.getContentId());
            }
        }
        int selfExclusion = 0;
        for (BreweryNearby n : nearby) {
            String own = matchByBrewery.get(n.getBreweryId());
            if (own != null && own.equals(n.getContentId())) {
                selfExclusion++;
            }
        }
        sb.append("- 자기제외 파생 건수(brewery_nearby.content_id = brewery.content_id): ")
                .append(selfExclusion).append(" — 캐시에서 자기 자신을 거를 대상 수(파생, 플래그 컬럼 없음)\n");
        sb.append('\n');
    }

    private static String minMedianMax(Map<String, List<BreweryNearby>> nearbyByBrewery) {
        List<Integer> counts = new ArrayList<>();
        for (List<BreweryNearby> v : nearbyByBrewery.values()) {
            counts.add(v.size());
        }
        if (counts.isEmpty()) {
            return "(없음)";
        }
        counts.sort(Comparator.naturalOrder());
        int min = counts.get(0);
        int max = counts.get(counts.size() - 1);
        int median = counts.get(counts.size() / 2);
        return min + " / " + median + " / " + max;
    }

    private static String fmt(BigDecimal d) {
        return d == null ? "" : String.valueOf(Math.round(d.doubleValue()));
    }

    private static String pct(int part, int total) {
        if (total == 0) {
            return "0%";
        }
        return String.format("%.1f%%", 100.0 * part / total);
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
