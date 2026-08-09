package com.jeontongjuro.backend.brewery.query;

import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.VisitState;
import com.jeontongjuro.backend.liquortype.LiquorType;
import com.jeontongjuro.backend.liquortype.ProductLiquorType;
import com.jeontongjuro.backend.product.ProductBreweryLink;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import jakarta.persistence.criteria.Subquery;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

/**
 * brewery 조회 필터 조합(Specification). 각 필터가 독립 Specification 하나이고, 지정된 것만 AND로 합친다
 * (미지정 필터는 조건 미적용).
 * <p>
 * ★확장점: 후속 PR의 특징 필터 등은 여기 정적 팩토리를 하나 더 추가해
 * {@link #build}의 리스트에 끼워 넣기만 하면 된다 — 조회 파이프라인 변경 없이 조건만 얹는 구조.
 * 주종({@link #liquorTypes})·도수({@link #alcoholRange}) 필터가 그 선례이며, 둘 다 1:N 자식 테이블을
 * {@code EXISTS} 상관 서브쿼리로 판정해 페이징을 깨지 않는다(brewery는 단일 테이블이라 외부 조인 없음).
 */
public final class BreweryQuerySpecifications {

    private BreweryQuerySpecifications() {
    }

    /** 조건의 지정된 필터만 모아 AND 결합. 전부 미지정이면 무제약(전체 조회). */
    public static Specification<Brewery> build(BrewerySearchCondition cond) {
        List<Specification<Brewery>> specs = new ArrayList<>();
        addIfPresent(specs, region(cond.region()));
        addIfPresent(specs, reservationVisit(cond.reservationVisit()));
        addIfPresent(specs, alwaysVisit(cond.alwaysVisit()));
        addIfPresent(specs, keywordContains(cond.keyword()));
        addIfPresent(specs, liquorTypes(cond.liquorTypes()));
        addIfPresent(specs, alcoholRange(cond.minAbv(), cond.maxAbv()));
        return Specification.allOf(specs);
    }

    /**
     * 도수 범위 필터(요청 범위와 제품 도수 범위가 <b>겹치는</b> 제품을 하나라도 파는 양조장). 방식A(중복 허용):
     * <pre>겹침 ⇔ product.alcohol_max &gt;= minAbv  AND  product.alcohol_min &lt;= maxAbv</pre>
     * ★완전 포함(alcohol_min&gt;=minAbv AND alcohol_max&lt;=maxAbv)이 아니다 — min25/max54 제품이 "20~30" 요청에서
     * 빠지면 안 된다. minAbv만 주면 앞 조건만, maxAbv만 주면 뒤 조건만 적용한다.
     * <p>
     * {@link #liquorTypes}와 동일하게 product_brewery_link(1:N)를 {@code join} 대신 {@code EXISTS} 상관
     * 서브쿼리로 판정해 페이징·totalElements 붕괴를 막는다. breweryId는 연관 매핑이 아닌 단순 String 컬럼이라
     * 문자열끼리 상관시킨다. alcohol_min/max가 null인 링크는 비교가 NULL(=거짓)이 되어 자연히 제외된다
     * (도수 미상 제품은 특정 도수 필터에 걸리지 않는다 — 의도된 동작).
     */
    public static Specification<Brewery> alcoholRange(BigDecimal minAbv, BigDecimal maxAbv) {
        if (minAbv == null && maxAbv == null) {
            return null;
        }
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<ProductBreweryLink> link = sub.from(ProductBreweryLink.class);
            sub.select(cb.literal(1L));
            List<Predicate> preds = new ArrayList<>();
            preds.add(cb.equal(link.get("breweryId"), root.get("breweryId")));
            if (minAbv != null) {
                preds.add(cb.greaterThanOrEqualTo(link.<BigDecimal>get("alcoholMax"), minAbv));
            }
            if (maxAbv != null) {
                preds.add(cb.lessThanOrEqualTo(link.<BigDecimal>get("alcoholMin"), maxAbv));
            }
            sub.where(preds.toArray(new Predicate[0]));
            return cb.exists(sub);
        };
    }

    /**
     * 주종 보유 필터(지정 주종 중 하나라도 태깅된 양조장). ★product_liquor_type과 brewery는 1:N이라
     * join으로 걸면 결과 행이 곱해져 페이징·totalElements가 깨진다. 외부 root는 brewery 단독으로 두고
     * {@code EXISTS} 상관 서브쿼리로 존재만 판정해 중복을 원천 차단한다(count 쿼리도 brewery 단독 → 정확).
     * <p>
     * ProductLiquorType.breweryId는 연관 매핑이 아닌 단순 String 컬럼이라 {@code root.join}을 쓸 수 없다 →
     * breweryId 문자열끼리 상관시킨다. 여러 주종은 {@code IN}으로 OR 결합.
     */
    public static Specification<Brewery> liquorTypes(List<LiquorType> types) {
        if (types == null || types.isEmpty()) {
            return null;
        }
        return (root, query, cb) -> {
            Subquery<Long> sub = query.subquery(Long.class);
            Root<ProductLiquorType> plt = sub.from(ProductLiquorType.class);
            sub.select(cb.literal(1L));
            sub.where(
                    cb.equal(plt.get("breweryId"), root.get("breweryId")),
                    plt.get("liquorType").in(types));
            return cb.exists(sub);
        };
    }

    /** region 칩 정확 일치. region 컬럼 저장값 == 칩 name(). */
    public static Specification<Brewery> region(Region region) {
        if (region == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("region"), region.name());
    }

    public static Specification<Brewery> reservationVisit(VisitState state) {
        if (state == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("reservationVisitState"), state);
    }

    public static Specification<Brewery> alwaysVisit(VisitState state) {
        if (state == null) {
            return null;
        }
        return (root, query, cb) -> cb.equal(root.get("alwaysVisitState"), state);
    }

    /** 상호명 부분일치(대소문자 무시). LIKE 와일드카드(%,_,\)는 이스케이프해 리터럴로 매칭. */
    public static Specification<Brewery> keywordContains(String keyword) {
        if (keyword == null || keyword.isBlank()) {
            return null;
        }
        String pattern = "%" + escapeLike(keyword.toLowerCase()) + "%";
        return (root, query, cb) ->
                cb.like(cb.lower(root.get("businessName")), pattern, '\\');
    }

    private static void addIfPresent(List<Specification<Brewery>> specs, Specification<Brewery> spec) {
        if (spec != null) {
            specs.add(spec);
        }
    }

    private static String escapeLike(String raw) {
        return raw.replace("\\", "\\\\")
                .replace("%", "\\%")
                .replace("_", "\\_");
    }
}
