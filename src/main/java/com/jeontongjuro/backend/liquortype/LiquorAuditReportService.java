package com.jeontongjuro.backend.liquortype;

import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.pipeline.collect.raw.ProductRaw;
import com.jeontongjuro.backend.pipeline.collect.raw.ProductRawRepository;
import com.jeontongjuro.backend.product.ProductBreweryLink;
import com.jeontongjuro.backend.product.ProductBreweryLinkRepository;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 주종 롤업 검수 리포트 산출(3d, 이슈 #15 1차). 파이프라인 실행 후 사람이 읽을 3섹션 리포트를 만든다.
 * <ul>
 *   <li>(1) 미커버 양조장 — ★핵심 검수 대상. 한 제품도 태깅 안 된 양조장. 소속 제품의 product_name + description
 *       원문을 함께 출력해 사람이 이것만 보고 주종을 판정할 수 있게 한다(description은 여기서만 사용).</li>
 *   <li>(2) 향미이중 후보 — suppressed_from_tab=true 행(제품명·주종·매칭 키워드).</li>
 *   <li>(3) 무태깅 제품 — 양조장은 커버됐으나 개별 제품이 무태깅(참고·부채. 이번 검수 대상 아님).</li>
 *   <li>(4) 오탐 제외 — 3차(이슈 #20) liquor_exclusion_seed.json으로 AUTO 삽입을 억제한 (제품, 주종, 근거) 목록.
 *       exclusion은 DB에 남지 않으므로 이 리포트가 유일한 가시화 경로다.</li>
 * </ul>
 * docs/audit/ 하위에 파일로도 남긴다(레포 상대경로 — 해당 디렉터리는 로컬 산출물이라 커밋 대상 아님).
 */
@Service
public class LiquorAuditReportService {

    private final ProductLiquorTypeRepository liquorTypeRepository;
    private final ProductBreweryLinkRepository linkRepository;
    private final BreweryRepository breweryRepository;
    private final ProductRawRepository productRawRepository;
    private final LiquorExclusionSeedLoadService exclusionSeedLoadService;

    public LiquorAuditReportService(ProductLiquorTypeRepository liquorTypeRepository,
                                    ProductBreweryLinkRepository linkRepository,
                                    BreweryRepository breweryRepository,
                                    ProductRawRepository productRawRepository,
                                    LiquorExclusionSeedLoadService exclusionSeedLoadService) {
        this.liquorTypeRepository = liquorTypeRepository;
        this.linkRepository = linkRepository;
        this.breweryRepository = breweryRepository;
        this.productRawRepository = productRawRepository;
        this.exclusionSeedLoadService = exclusionSeedLoadService;
    }

    /** 3섹션 마크다운 리포트를 문자열로 산출(파일 미기록 — 테스트·미리보기용). */
    @Transactional(readOnly = true)
    public String render(LocalDate snapshotDate) {
        Map<String, String> businessNameById = new HashMap<>();
        for (Brewery b : breweryRepository.findAll()) {
            businessNameById.put(b.getBreweryId(), b.getBusinessName());
        }

        // 연결된 제품(link) — rowRef 기준 정렬 보관
        List<ProductBreweryLink> links = new ArrayList<>(linkRepository.findAll());
        links.sort(Comparator.comparing(ProductBreweryLink::getSourceRowRef));

        // 원문 description·product_name (섹션1 전용) — rowRef -> raw
        Map<Integer, ProductRaw> rawByRowRef = new HashMap<>();
        for (ProductRaw p : productRawRepository.findAllBySnapshotDateOrderBySourceRowIndexAsc(snapshotDate)) {
            rawByRowRef.put(p.getSourceRowIndex(), p);
        }

        List<ProductLiquorType> tags = liquorTypeRepository.findAll();
        Set<String> taggedBreweryIds = new HashSet<>();
        Set<Integer> taggedRowRefs = new HashSet<>();
        for (ProductLiquorType t : tags) {
            taggedBreweryIds.add(t.getBreweryId());
            taggedRowRefs.add(t.getSourceRowRef());
        }

        StringBuilder sb = new StringBuilder();
        sb.append("# 주종 롤업 검수 리포트 (snapshot ").append(snapshotDate).append(")\n\n");
        sb.append("> ★이 리포트는 추론 결과의 검수 대상 추출용이다. AUTO행 recheck_flag=true(미검증), ")
                .append("MANUAL 시드행 recheck_flag=false(검수 완료).\n")
                .append("> 분포 수치는 보고용이며 골든이 아니다. brewery.liquor_status는 별도 7단계가 갱신한다 ")
                .append("(조인 제품 전건 태깅 & 전건 recheck_flag=false인 양조장만 TAGGED).\n\n");

        appendUncoveredBreweries(sb, links, businessNameById, taggedBreweryIds, rawByRowRef);
        appendFlavorDoubles(sb, tags, links, businessNameById);
        appendUntaggedProducts(sb, links, businessNameById, taggedBreweryIds, taggedRowRefs);
        appendExclusions(sb, links);
        return sb.toString();
    }

    /** 리포트를 docs/audit/&lt;snapshot&gt;_liquor_rollup_audit.md로 기록하고 경로를 반환한다. */
    public Path writeReport(LocalDate snapshotDate) {
        String content = render(snapshotDate);
        Path path = Path.of("docs", "audit",
                snapshotDate.toString().replace("-", "") + "_liquor_rollup_audit.md");
        try {
            Files.createDirectories(path.getParent());
            Files.writeString(path, content, StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new UncheckedIOException("주종 검수 리포트 기록 실패: " + path, ex);
        }
        return path;
    }

    private void appendUncoveredBreweries(StringBuilder sb, List<ProductBreweryLink> links,
                                          Map<String, String> businessNameById, Set<String> taggedBreweryIds,
                                          Map<Integer, ProductRaw> rawByRowRef) {
        // brewery_id -> 소속 제품(link) 목록
        Map<String, List<ProductBreweryLink>> productsByBrewery = new TreeMap<>();
        for (ProductBreweryLink l : links) {
            if (l.getBreweryId() != null) {
                productsByBrewery.computeIfAbsent(l.getBreweryId(), k -> new ArrayList<>()).add(l);
            }
        }
        List<String> uncovered = new ArrayList<>();
        for (String breweryId : productsByBrewery.keySet()) {
            if (!taggedBreweryIds.contains(breweryId)) {
                uncovered.add(breweryId);
            }
        }

        sb.append("## (1) 미커버 양조장 — ★핵심 검수 대상\n\n");
        sb.append("한 제품도 태깅되지 않은 양조장 **").append(uncovered.size()).append("곳**. ")
                .append("아래 product_name + 제품소개(description) 원문만 보고 주종을 판정해 ")
                .append("liquor_manual_seed.json에 채운다(source_row_ref 그대로 사용).\n\n");
        for (String breweryId : uncovered) {
            List<ProductBreweryLink> products = productsByBrewery.get(breweryId);
            sb.append("### ").append(breweryId).append(" · ")
                    .append(businessNameById.getOrDefault(breweryId, "(상호 미상)"))
                    .append(" — 제품 ").append(products.size()).append("건\n\n");
            for (ProductBreweryLink l : products) {
                ProductRaw raw = rawByRowRef.get(l.getSourceRowRef());
                String description = raw != null ? raw.getDescription() : null;
                sb.append("- `source_row_ref=").append(l.getSourceRowRef()).append("` **")
                        .append(nz(l.getProductName())).append("**\n");
                sb.append("  - 제품소개: ").append(oneLine(description)).append('\n');
            }
            sb.append('\n');
        }
    }

    private void appendFlavorDoubles(StringBuilder sb, List<ProductLiquorType> tags,
                                     List<ProductBreweryLink> links, Map<String, String> businessNameById) {
        Map<Integer, String> productNameByRowRef = new HashMap<>();
        for (ProductBreweryLink l : links) {
            productNameByRowRef.put(l.getSourceRowRef(), l.getProductName());
        }
        List<ProductLiquorType> suppressed = new ArrayList<>();
        for (ProductLiquorType t : tags) {
            if (t.isSuppressedFromTab()) {
                suppressed.add(t);
            }
        }
        suppressed.sort(Comparator.comparing(ProductLiquorType::getSourceRowRef)
                .thenComparing(t -> t.getLiquorType().name()));

        sb.append("## (2) 향미이중 후보 — 검수 대상\n\n");
        sb.append("제품명에 향미어 + 주종어가 동시에 있어 suppressed_from_tab=true로 표시된 행 **")
                .append(suppressed.size()).append("건** (행은 남아 있음).\n\n");
        if (suppressed.isEmpty()) {
            sb.append("_해당 없음._\n\n");
            return;
        }
        sb.append("| source_row_ref | 양조장 | 제품명 | 태깅 주종 | 매칭 키워드 |\n");
        sb.append("|---|---|---|---|---|\n");
        for (ProductLiquorType t : suppressed) {
            sb.append("| ").append(t.getSourceRowRef())
                    .append(" | ").append(nz(businessNameById.get(t.getBreweryId())))
                    .append(" | ").append(nz(productNameByRowRef.get(t.getSourceRowRef())))
                    .append(" | ").append(t.getLiquorType().name())
                    .append(" | ").append(nz(t.getMatchedKeyword()))
                    .append(" |\n");
        }
        sb.append('\n');
    }

    private void appendUntaggedProducts(StringBuilder sb, List<ProductBreweryLink> links,
                                        Map<String, String> businessNameById, Set<String> taggedBreweryIds,
                                        Set<Integer> taggedRowRefs) {
        List<ProductBreweryLink> untagged = new ArrayList<>();
        for (ProductBreweryLink l : links) {
            if (l.getBreweryId() == null) {
                continue;
            }
            // 양조장은 커버됐으나(≥1 태깅 제품 보유) 이 제품 자체는 무태깅
            if (taggedBreweryIds.contains(l.getBreweryId()) && !taggedRowRefs.contains(l.getSourceRowRef())) {
                untagged.add(l);
            }
        }

        sb.append("## (3) 무태깅 제품 — 참고용(이번 검수 대상 아님)\n\n");
        sb.append("양조장은 커버됐으나 개별 제품이 무태깅인 건 **").append(untagged.size())
                .append("건**. 양조장 필터엔 영향 없어 이번 범위 밖(부채 기록).\n\n");
        if (untagged.isEmpty()) {
            sb.append("_해당 없음._\n\n");
            return;
        }
        sb.append("| source_row_ref | 양조장 | 제품명 |\n");
        sb.append("|---|---|---|\n");
        for (ProductBreweryLink l : untagged) {
            sb.append("| ").append(l.getSourceRowRef())
                    .append(" | ").append(nz(businessNameById.get(l.getBreweryId())))
                    .append(" | ").append(nz(l.getProductName()))
                    .append(" |\n");
        }
        sb.append('\n');
    }

    private void appendExclusions(StringBuilder sb, List<ProductBreweryLink> links) {
        Map<Integer, String> productNameByRowRef = new HashMap<>();
        for (ProductBreweryLink l : links) {
            productNameByRowRef.put(l.getSourceRowRef(), l.getProductName());
        }
        List<LiquorExclusionSeedLoadService.ExclusionEntry> exclusions =
                new ArrayList<>(exclusionSeedLoadService.loadEntries());
        exclusions.sort(Comparator.comparing(LiquorExclusionSeedLoadService.ExclusionEntry::sourceRowRef)
                .thenComparing(e -> e.liquorType().name()));

        sb.append("## (4) 오탐 제외 — liquor_exclusion_seed.json\n\n");
        sb.append("AUTO가 매칭했으나 삽입 전 필터로 걸러 DB에 남기지 않은 (제품, 주종) **")
                .append(exclusions.size()).append("건** (DB에 행이 없으므로 이 리포트가 유일한 가시화 경로).\n\n");
        if (exclusions.isEmpty()) {
            sb.append("_해당 없음._\n\n");
            return;
        }
        sb.append("| source_row_ref | 제품명 | 제외 주종 | 근거 |\n");
        sb.append("|---|---|---|---|\n");
        for (LiquorExclusionSeedLoadService.ExclusionEntry e : exclusions) {
            sb.append("| ").append(e.sourceRowRef())
                    .append(" | ").append(nz(productNameByRowRef.get(e.sourceRowRef())))
                    .append(" | ").append(e.liquorType().name())
                    .append(" | ").append(oneLine(e.reason()))
                    .append(" |\n");
        }
        sb.append('\n');
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }

    private static String oneLine(String s) {
        if (s == null || s.isBlank()) {
            return "_(없음)_";
        }
        return s.replace("\r", " ").replace("\n", " ").replace("|", "\\|").trim();
    }
}
