package com.jeontongjuro.backend.feature;

import com.jeontongjuro.backend.pipeline.collect.raw.ProductRaw;
import com.jeontongjuro.backend.product.ProductBreweryLink;
import com.jeontongjuro.backend.product.ProductBreweryLinkRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 양조장 특징 롤업 서비스(이슈 #43, 파이프라인 11단계). product_brewery_link로 연결된 제품만 대상으로,
 * 제품 서술 컬럼(product_raw)에 확정 규칙을 적용해 특징 5종을 양조장으로 롤업한다.
 * <p>
 * 규칙(★키워드·근거 컬럼을 임의로 넓히지 않는다):
 * <ul>
 *   <li>수상이력: awards가 공백 제외 비어있지 않음(presence, {@code coalesce(btrim(awards),'')<>''})</li>
 *   <li>식품명인: special_note에 '식품명인' 포함</li>
 *   <li>유기농: description·ingredients·characteristics 중 하나에 '유기농' 포함</li>
 *   <li>무형문화재: special_note에 '무형문화재' 포함</li>
 *   <li>대통령상: awards에 '대통령상' 포함(★'대통령'으로 넓히면 '대통령표창' 등 오탐 — BRW-058)</li>
 * </ul>
 * <p>
 * ★삭제형 diff(삽입 전용 아님): 목표 집합(규칙 산출)과 현재 집합(DB)을 비교해 삽입/삭제한다.
 * 삽입 전용이면 규칙 축소·원본 갱신 시 이전 태그가 유령으로 남아 골든이 틀어진다(부채 #11 tour_content 유령
 * 46건과 동일 클래스). 특징은 100% 규칙 파생이고 MANUAL 시드·검수 워크플로가 없어 지울 사람 손 작업이 없으므로
 * 삭제형이 안전하다(주종의 삽입 전용과 결정적으로 다른 근거).
 * <p>
 * 멱등: 목표와 현재가 같으면 삽입·삭제 0, 전부 unchanged. {@code @Transactional}로 자기 커밋(오케스트레이터
 * 무트랜잭션 원칙 — 단계별 독립 커밋·멱등 재개).
 */
@Service
public class FeatureRollupService {

    private final BreweryFeatureTagRepository featureTagRepository;
    private final ProductBreweryLinkRepository linkRepository;

    public FeatureRollupService(BreweryFeatureTagRepository featureTagRepository,
                                ProductBreweryLinkRepository linkRepository) {
        this.featureTagRepository = featureTagRepository;
        this.linkRepository = linkRepository;
    }

    /**
     * @param targetBreweries 특징이 하나 이상 붙는(목표 집합) 양조장 수
     * @param inserted        신규 삽입 (양조장,특징) 태그 수
     * @param deleted         목표에 없어 삭제한 유령 태그 수(규칙 축소·원본 갱신·오적재 정리)
     * @param unchanged       목표·현재 동일해 유지한 태그 수(멱등 확인)
     */
    public record RollupResult(int targetBreweries, int inserted, int deleted, int unchanged) {
    }

    /** 특징 유발 근거 — (특징, 대표 키워드). 수상이력은 presence라 키워드 null. */
    private record Match(FeatureType type, String keyword) {
    }

    /** (양조장, 특징) diff 키. */
    private record TagKey(String breweryId, FeatureType type) {
    }

    /** 목표 태그 값 — 대표 제품 참조·키워드(최초 매칭 제품 기준, 결정론). */
    private record TagValue(Integer sourceRowRef, String keyword) {
    }

    @Transactional
    public RollupResult rollup(List<ProductRaw> productRows) {
        // 1. link로 연결된 제품 → brewery_id 매핑(미연결 제품은 대상 아님).
        Map<Integer, String> breweryIdByRowRef = new HashMap<>();
        for (ProductBreweryLink link : linkRepository.findAll()) {
            if (link.getBreweryId() != null) {
                breweryIdByRowRef.put(link.getSourceRowRef(), link.getBreweryId());
            }
        }

        // 2. 목표 집합 산출 — 제품을 source_row_index 순서로 순회, (양조장,특징)의 대표는 첫 매칭 제품(결정론).
        //    ★productRows는 orderBySourceRowIndexAsc로 공급된다(오케스트레이터·테스트 하니스 공통).
        Map<TagKey, TagValue> target = new LinkedHashMap<>();
        for (ProductRaw p : productRows) {
            String breweryId = breweryIdByRowRef.get(p.getSourceRowIndex());
            if (breweryId == null) {
                continue; // 미연결 제품
            }
            for (Match m : matchesOf(p)) {
                target.putIfAbsent(new TagKey(breweryId, m.type()),
                        new TagValue(p.getSourceRowIndex(), m.keyword()));
            }
        }

        // 3. 현재 집합(DB) 로드.
        List<BreweryFeatureTag> current = featureTagRepository.findAll();
        Map<TagKey, BreweryFeatureTag> currentByKey = new HashMap<>();
        for (BreweryFeatureTag t : current) {
            currentByKey.put(new TagKey(t.getBreweryId(), t.getFeatureType()), t);
        }

        // 4. diff — 목표엔 있고 현재에 없으면 삽입, 현재엔 있고 목표에 없으면 삭제, 교집합은 unchanged.
        List<BreweryFeatureTag> toInsert = new ArrayList<>();
        int unchanged = 0;
        for (Map.Entry<TagKey, TagValue> e : target.entrySet()) {
            if (currentByKey.containsKey(e.getKey())) {
                unchanged++;
            } else {
                toInsert.add(BreweryFeatureTag.of(e.getKey().breweryId(), e.getKey().type(),
                        e.getValue().sourceRowRef(), e.getValue().keyword()));
            }
        }
        List<BreweryFeatureTag> toDelete = new ArrayList<>();
        for (BreweryFeatureTag t : current) {
            if (!target.containsKey(new TagKey(t.getBreweryId(), t.getFeatureType()))) {
                toDelete.add(t);
            }
        }

        featureTagRepository.deleteAll(toDelete);
        featureTagRepository.saveAll(toInsert);

        int targetBreweries = (int) target.keySet().stream()
                .map(TagKey::breweryId).distinct().count();
        return new RollupResult(targetBreweries, toInsert.size(), toDelete.size(), unchanged);
    }

    /** 한 제품이 유발하는 특징 목록(enum 순서 고정 — 대표 결정론). */
    private List<Match> matchesOf(ProductRaw p) {
        List<Match> out = new ArrayList<>();
        if (hasText(p.getAwards())) {
            out.add(new Match(FeatureType.수상이력, null)); // presence — 키워드 없음
        }
        if (contains(p.getSpecialNote(), "식품명인")) {
            out.add(new Match(FeatureType.식품명인, "식품명인"));
        }
        if (contains(p.getDescription(), "유기농") || contains(p.getIngredients(), "유기농")
                || contains(p.getCharacteristics(), "유기농")) {
            out.add(new Match(FeatureType.유기농, "유기농"));
        }
        if (contains(p.getSpecialNote(), "무형문화재")) {
            out.add(new Match(FeatureType.무형문화재, "무형문화재"));
        }
        if (contains(p.getAwards(), "대통령상")) {
            out.add(new Match(FeatureType.대통령상, "대통령상"));
        }
        return out;
    }

    /** {@code coalesce(btrim(v),'') <> ''} 대응 — null·공백-only는 false. */
    private static boolean hasText(String v) {
        return v != null && !v.isBlank();
    }

    /** {@code v LIKE '%kw%'} 대응 — 단순 부분문자열(단어경계 없음). null은 false. */
    private static boolean contains(String v, String kw) {
        return v != null && v.contains(kw);
    }
}
