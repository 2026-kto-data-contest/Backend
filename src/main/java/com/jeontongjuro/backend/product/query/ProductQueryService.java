package com.jeontongjuro.backend.product.query;

import com.jeontongjuro.backend.brewery.BreweryRepository;
import com.jeontongjuro.backend.brewery.query.BreweryNotFoundException;
import com.jeontongjuro.backend.global.web.PageResponse;
import com.jeontongjuro.backend.liquortype.LiquorType;
import com.jeontongjuro.backend.liquortype.ProductLiquorTypeRepository;
import com.jeontongjuro.backend.product.ProductBreweryLink;
import com.jeontongjuro.backend.product.ProductBreweryLinkRepository;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 양조장 제품 목록 조회 서비스. {@code GET /api/v1/breweries/{breweryId}/products}의 진입점.
 * <p>
 * 파이프라인(순서가 중요하다):
 * <ol>
 *   <li>로드 — breweryId로 링크를 읽고, 그 source_row_ref로 raw 프로젝션을 배치 로딩해 조인한다.</li>
 *   <li>판매중단 제외 — {@code sale_yn='N'} 제거. ★중복 병합보다 먼저 해야 판매중단 짝이 자동 해소된다.</li>
 *   <li>원본오류 제외 — {@link ProductExclusionSeed}(시드 파일)에 있는 source_row_index 제거.</li>
 *   <li>중복 병합 — 제품명 공백 정규화로 그룹핑, 필드별 병합(스칼라는 대표 행 통째, 텍스트는 완결성·non-empty).</li>
 *   <li>설명 처리 — {@link DescriptionTruncationPolicy}로 되돌림/미노출.</li>
 *   <li>수상 뱃지 — {@link AwardGradeParser}로 최상위 등급.</li>
 *   <li>주종 매핑 — 제품별 주종을 배치 로딩해 붙인다(suppressed_from_tab은 주종 탭 전용 플래그라 미적용).</li>
 *   <li>정렬 — 수상 보유 우선(내림차순), 그다음 productId(대표 source_row_ref) 오름차순.</li>
 *   <li>페이지네이션 — 인메모리(양조장당 제품 수가 작다).</li>
 * </ol>
 * <p>
 * ★{@code totalElements}는 "판매중단 제외 · 원본오류 제외 · 중복 병합 후" 기준 건수다(링크 원본 행수가 아니다).
 */
@Service
@Transactional(readOnly = true)
public class ProductQueryService {

    /** 페이지 기본 크기(size 미지정·1 미만 시). */
    static final int DEFAULT_SIZE = 5;
    /** 페이지 최대 크기. 초과 요청은 400이 아니라 이 값으로 클램프한다. */
    static final int MAX_SIZE = 100;

    private static final String SALE_YN_STOPPED = "N";

    private final BreweryRepository breweryRepository;
    private final ProductBreweryLinkRepository linkRepository;
    private final ProductRawQueryRepository rawRepository;
    private final ProductLiquorTypeRepository liquorTypeRepository;
    private final ProductExclusionSeed exclusionSeed;

    public ProductQueryService(BreweryRepository breweryRepository,
                               ProductBreweryLinkRepository linkRepository,
                               ProductRawQueryRepository rawRepository,
                               ProductLiquorTypeRepository liquorTypeRepository,
                               ProductExclusionSeed exclusionSeed) {
        this.breweryRepository = breweryRepository;
        this.linkRepository = linkRepository;
        this.rawRepository = rawRepository;
        this.liquorTypeRepository = liquorTypeRepository;
        this.exclusionSeed = exclusionSeed;
    }

    public PageResponse<ProductCardResponse> listProducts(String breweryId, int page, int size) {
        if (!breweryRepository.existsById(breweryId)) {
            throw new BreweryNotFoundException("양조장을 찾을 수 없습니다: " + breweryId);
        }
        int clampedPage = clampPage(page);
        int clampedSize = clampSize(size);

        List<ProductCardResponse> all = buildCards(breweryId);

        long totalElements = all.size();
        int from = Math.min(clampedPage * clampedSize, all.size());
        int to = Math.min(from + clampedSize, all.size());
        List<ProductCardResponse> pageContent = all.subList(from, to);
        return PageResponse.of(pageContent, clampedPage, clampedSize, totalElements);
    }

    /** ①~⑧: 이 양조장의 노출 카드 전체(정렬 완료)를 만든다. 페이지네이션(⑨)은 호출자가 한다. */
    private List<ProductCardResponse> buildCards(String breweryId) {
        // ① 로드
        List<ProductBreweryLink> links = linkRepository.findByBreweryId(breweryId);
        if (links.isEmpty()) {
            return List.of();
        }
        Map<Integer, ProductRawView> rawByRef = loadRawByRef(links);

        // ①~③ 조인 + 판매중단 제외 + 원본오류 제외
        List<RawProduct> kept = filterKept(links, rawByRef);
        if (kept.isEmpty()) {
            return List.of();
        }

        // ④ 중복 병합 — 제품명 공백 정규화로 그룹핑(삽입 순서 유지)
        Map<String, List<RawProduct>> groups = groupByNormalizedName(kept);

        // ⑦ 주종 배치 로딩 — 남은 모든 ref 기준(병합 그룹은 멤버 ref들의 합집합으로 노출)
        Map<Integer, List<LiquorType>> typesByRef = loadLiquorTypes(kept);

        // ④~⑦ 그룹별 병합 → 카드
        List<ProductCardResponse> cards = new ArrayList<>(groups.size());
        for (List<RawProduct> group : groups.values()) {
            cards.add(mergeToCard(group, typesByRef));
        }

        // ⑧ 정렬: 수상 보유 우선(내림차순), productId 오름차순
        cards.sort(Comparator
                .comparing((ProductCardResponse c) -> c.awardBadge() != null).reversed()
                .thenComparing(ProductCardResponse::productId));
        return cards;
    }

    /** 한 중복 그룹을 카드 1건으로 병합한다. */
    private ProductCardResponse mergeToCard(List<RawProduct> group, Map<Integer, List<LiquorType>> typesByRef) {
        // 대표 행: 완결 설명 보유 → 수상 보유 → source_row_ref 작은 쪽
        RawProduct representative = representativeOf(group);

        // 설명: 완결성 우선(완결 > 되돌림가능 > 없음), 동점이면 ref 작은 쪽
        RawProduct descSource = group.stream()
                .max(Comparator
                        .comparingInt(ProductQueryService::descriptionCompleteness)
                        .thenComparing(p -> -p.link().getSourceRowRef()))
                .orElseThrow();
        String description = DescriptionTruncationPolicy.apply(descSource.raw().getDescription());

        // 수상: non-empty 우선, 동점이면 ref 작은 쪽
        String awards = group.stream()
                .filter(ProductQueryService::hasAwards)
                .min(Comparator.comparing(p -> p.link().getSourceRowRef()))
                .map(p -> p.raw().getAwards())
                .orElse(null);
        String awardBadge = AwardGradeParser.parse(awards);

        // 스칼라(도수·용량)는 대표 행에서 통째로 — 필드별로 섞지 않는다
        BigDecimal alcoholMin = representative.link().getAlcoholMin();
        BigDecimal alcoholMax = representative.link().getAlcoholMax();
        String volume = representative.raw().getVolume();

        // 주종: 그룹 멤버 ref들의 노출 주종 합집합(distinct), LiquorType 선언 순서 정렬
        List<LiquorType> liquorTypes = mergeLiquorTypes(group, typesByRef);

        return new ProductCardResponse(
                representative.link().getSourceRowRef(),
                representative.link().getProductName(),
                alcoholMin,
                alcoholMax,
                volume,
                liquorTypes,
                description,
                awardBadge);
    }

    /**
     * 양조장 목록 조회의 맛 태그 배치 지원 — 여러 양조장의 "대표 제품 1종"(제품 목록 API 카드 순서 기준 첫 카드)의
     * 특징(characteristics) 원문을 한 번에 계산해 breweryId별로 반환한다. {@link #buildCards}와 동일한
     * 제외(②③)·중복 병합(④)·정렬(⑧) 규칙을 배치로 재사용해 양조장마다 따로 조회하는 N+1을 피한다.
     * <p>
     * 설명·주종 등 카드의 나머지 필드는 계산하지 않는다(호출자가 특징 텍스트만 필요). 정렬 판정에 쓰는
     * "수상 뱃지 보유"는 "그룹에 non-blank awards 행이 하나라도 있는가"로 축약한다 — {@link AwardGradeParser#parse}는
     * awards가 non-blank이면 항상 non-null을 반환하는 계약이 확인돼 있어 완전히 동치다.
     * <p>
     * 노출 카드가 없는(kept가 빈) 양조장은 결과 Map에 없다 — 호출자가 없으면 특징 없음으로 처리한다.
     */
    public Map<String, String> representativeCharacteristicsByBreweryId(Collection<String> breweryIds) {
        if (breweryIds.isEmpty()) {
            return Map.of();
        }
        List<ProductBreweryLink> allLinks = linkRepository.findByBreweryIdIn(breweryIds);
        if (allLinks.isEmpty()) {
            return Map.of();
        }
        Map<Integer, ProductRawView> rawByRef = loadRawByRef(allLinks);

        Map<String, List<ProductBreweryLink>> linksByBrewery = new LinkedHashMap<>();
        for (ProductBreweryLink link : allLinks) {
            linksByBrewery.computeIfAbsent(link.getBreweryId(), k -> new ArrayList<>()).add(link);
        }

        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, List<ProductBreweryLink>> e : linksByBrewery.entrySet()) {
            List<RawProduct> kept = filterKept(e.getValue(), rawByRef);
            if (kept.isEmpty()) {
                continue;
            }
            Map<String, List<RawProduct>> groups = groupByNormalizedName(kept);
            RawProduct firstCardRepresentative = groups.values().stream()
                    .map(group -> new GroupOrderKey(
                            group.stream().anyMatch(ProductQueryService::hasAwards),
                            representativeOf(group)))
                    .min(Comparator
                            .comparing(GroupOrderKey::hasAwardBadge).reversed()
                            .thenComparing(k -> k.representative().link().getSourceRowRef()))
                    .map(GroupOrderKey::representative)
                    .orElseThrow();
            result.put(e.getKey(), firstCardRepresentative.raw().getCharacteristics());
        }
        return result;
    }

    /** ①~③ 조인 + 판매중단 제외 + 원본오류 제외. */
    private List<RawProduct> filterKept(List<ProductBreweryLink> links, Map<Integer, ProductRawView> rawByRef) {
        List<RawProduct> kept = new ArrayList<>();
        for (ProductBreweryLink link : links) {
            Integer ref = link.getSourceRowRef();
            ProductRawView raw = rawByRef.get(ref);
            if (raw == null) {
                continue; // 링크에 대응하는 raw가 없으면 노출 불가(정상 데이터에선 발생하지 않음)
            }
            if (SALE_YN_STOPPED.equals(raw.getSaleYn())) {
                continue; // ② 판매중단
            }
            if (exclusionSeed.isExcluded(ref)) {
                continue; // ③ 원본오류
            }
            kept.add(new RawProduct(link, raw));
        }
        return kept;
    }

    /** ④ 제품명 공백 정규화로 그룹핑(삽입 순서 유지). */
    private static Map<String, List<RawProduct>> groupByNormalizedName(List<RawProduct> kept) {
        Map<String, List<RawProduct>> groups = new LinkedHashMap<>();
        for (RawProduct p : kept) {
            groups.computeIfAbsent(normalizeName(p.link().getProductName()), k -> new ArrayList<>()).add(p);
        }
        return groups;
    }

    /** 그룹 대표 행: 완결 설명 보유 → 수상 보유 → source_row_ref 작은 쪽. */
    private static RawProduct representativeOf(List<RawProduct> group) {
        return group.stream()
                .min(Comparator
                        .comparing((RawProduct p) -> hasCompletableDescription(p) ? 0 : 1)
                        .thenComparing(p -> hasAwards(p) ? 0 : 1)
                        .thenComparing(p -> p.link().getSourceRowRef()))
                .orElseThrow();
    }

    private List<LiquorType> mergeLiquorTypes(List<RawProduct> group, Map<Integer, List<LiquorType>> typesByRef) {
        List<LiquorType> merged = new ArrayList<>();
        for (RawProduct p : group) {
            for (LiquorType t : typesByRef.getOrDefault(p.link().getSourceRowRef(), List.of())) {
                if (!merged.contains(t)) {
                    merged.add(t);
                }
            }
        }
        // ★enum 그대로 반환한다(응답 DTO가 List<LiquorType>). Jackson이 name()으로 직렬화하므로 wire JSON은 불변.
        merged.sort(Comparator.comparingInt(Enum::ordinal));
        return merged;
    }

    private Map<Integer, ProductRawView> loadRawByRef(List<ProductBreweryLink> links) {
        List<Integer> refs = links.stream().map(ProductBreweryLink::getSourceRowRef).toList();
        Map<Integer, ProductRawView> byRef = new HashMap<>();
        for (ProductRawView raw : rawRepository.findBySourceRowIndexIn(refs)) {
            byRef.put(raw.getSourceRowIndex(), raw);
        }
        return byRef;
    }

    private Map<Integer, List<LiquorType>> loadLiquorTypes(List<RawProduct> kept) {
        List<Integer> refs = kept.stream().map(p -> p.link().getSourceRowRef()).toList();
        Map<Integer, List<LiquorType>> byRef = new HashMap<>();
        for (Object[] row : liquorTypeRepository.findTypesBySourceRowRefIn(refs)) {
            Integer ref = (Integer) row[0];
            LiquorType type = (LiquorType) row[1];
            byRef.computeIfAbsent(ref, k -> new ArrayList<>()).add(type);
        }
        return byRef;
    }

    /**
     * 검색 자동완성용 — 전 양조장의 노출 제품(판매중단·원본오류 제외, 중복 병합 적용 후) 이름 전체를 반환한다.
     * {@link #buildCards}와 동일한 제외(②③)·중복 병합(④) 규칙을 그룹핑 단위(양조장별)까지 그대로 재사용해
     * 노출 모집단을 카드 API와 일치시킨다. 정렬·설명·주종 등 카드의 나머지 계산은 하지 않는다(호출자는
     * 제품명·id만 필요).
     * <p>
     * 링크 전체 조회 1쿼리 + raw 배치 조회 1쿼리로 고정된다(양조장 수·결과 건수와 무관 — N+1 없음).
     */
    public List<ProductNameSuggestion> allDisplayedProductNames() {
        List<ProductBreweryLink> allLinks = linkRepository.findAll();
        if (allLinks.isEmpty()) {
            return List.of();
        }
        Map<Integer, ProductRawView> rawByRef = loadRawByRef(allLinks);

        Map<String, List<ProductBreweryLink>> linksByBrewery = new LinkedHashMap<>();
        for (ProductBreweryLink link : allLinks) {
            linksByBrewery.computeIfAbsent(link.getBreweryId(), k -> new ArrayList<>()).add(link);
        }

        List<ProductNameSuggestion> result = new ArrayList<>();
        for (List<ProductBreweryLink> links : linksByBrewery.values()) {
            List<RawProduct> kept = filterKept(links, rawByRef);
            if (kept.isEmpty()) {
                continue;
            }
            Map<String, List<RawProduct>> groups = groupByNormalizedName(kept);
            for (List<RawProduct> group : groups.values()) {
                RawProduct representative = representativeOf(group);
                result.add(new ProductNameSuggestion(
                        representative.link().getSourceRowRef(),
                        representative.link().getProductName()));
            }
        }
        return result;
    }

    /** 완결성 점수: 완결(절단 아님·비어있지 않음)=2 > 되돌림 가능(절단이나 문장 끝 있음)=1 > 없음=0. */
    private static int descriptionCompleteness(RawProduct p) {
        String desc = p.raw().getDescription();
        if (desc == null || desc.isEmpty()) {
            return 0;
        }
        if (!DescriptionTruncationPolicy.TRUNCATION_LENGTHS.contains(desc.length())) {
            return 2;
        }
        return DescriptionTruncationPolicy.apply(desc) != null ? 1 : 0;
    }

    private static boolean hasCompletableDescription(RawProduct p) {
        return DescriptionTruncationPolicy.apply(p.raw().getDescription()) != null;
    }

    private static boolean hasAwards(RawProduct p) {
        String awards = p.raw().getAwards();
        return awards != null && !awards.isBlank();
    }

    /** 제품명 공백 전부 제거(공백 변형 병합용). ★도수·괄호는 건드리지 않는다(서로 다른 SKU 오병합 방지). */
    private static String normalizeName(String productName) {
        return productName == null ? "" : productName.replaceAll("\\s", "");
    }

    /** 음수 페이지는 0으로 클램프. */
    private static int clampPage(int page) {
        return Math.max(0, page);
    }

    /** size 클램프: 1 미만은 기본값(5), MAX_SIZE 초과는 MAX_SIZE로. */
    private static int clampSize(int size) {
        if (size < 1) {
            return DEFAULT_SIZE;
        }
        return Math.min(size, MAX_SIZE);
    }

    /** 병합 전 link+raw 묶음(내부 운반용). */
    private record RawProduct(ProductBreweryLink link, ProductRawView raw) {
    }

    /** 그룹의 정렬 판정 키(내부 운반용) — representativeCharacteristicsByBreweryId 전용. */
    private record GroupOrderKey(boolean hasAwardBadge, RawProduct representative) {
    }
}
