package com.jeontongjuro.backend.testsupport;

import com.jeontongjuro.backend.tour.TourApiClient;
import com.jeontongjuro.backend.tour.TourContentRow;
import com.jeontongjuro.backend.tour.TourIntro;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

/**
 * 테스트는 TourAPI를 실제 호출하면 안 된다 — 운영 {@code TourApiClientImpl}을 @Primary 스텁으로 덮는다
 * ({@link StubGeocodingConfig}와 동일 패턴). StubGeocoding이 전 양조장에 서울 시청 부근 고정 좌표를 주므로
 * 여기서도 그에 맞춰:
 * <ul>
 *   <li>locationBasedList2 → 질의 좌표에 콘텐츠 1건(거리 0). 전 양조장 withContent, emptyRadius=0.</li>
 *   <li>detailCommon2 → 요청 content_id를 질의 좌표(=양조장 좌표)로 반환 → 시드 접지 시 거리 0 ≤200m로
 *       200m 검증 통과(fail-fast 없이 매칭 성립).</li>
 * </ul>
 * ★네트워크·범위 이탈 없이 항등식·멱등·200m 검증 경로만 실증한다(실측 분포는 스텁이 재현하지 않는다).
 */
@TestConfiguration
public class StubTourApiConfig {

    @Bean
    @Primary
    public TourApiClient stubTourApiClient() {
        return new TourApiClient() {
            @Override
            public List<TourContentRow> locationBasedList(BigDecimal latitude, BigDecimal longitude, int radiusM) {
                return List.of(row("STUB-NEARBY-1", latitude, longitude, 0.0));
            }

            @Override
            public Optional<TourContentRow> detailCommon(String contentId) {
                // 시드 접지: 스텁은 전 양조장이 같은 좌표라 서울 시청 부근으로 반환(거리 0).
                return Optional.of(row(contentId,
                        new BigDecimal("37.566500"), new BigDecimal("126.978000"), null));
            }

            @Override
            public Optional<String> detailOverview(String contentId) {
                // 12단계 백필: 매칭된 콘텐츠 전건 소개글 보유(결정적). 실측 결측 분포는 라이브 verify 몫.
                return Optional.of("스텁 소개글: " + contentId);
            }

            @Override
            public Optional<TourIntro> detailIntro(String contentId, String contentTypeId) {
                // 12단계 상세: 운영시간·전화(infocenter) 등 결정적 반환. 커버리지(운영시간 19·전화 TOUR 19)는
                // '어느 양조장이 content_id를 갖는가'로 결정되며 이는 실제 시드·매칭이 재현한다.
                return Optional.of(new TourIntro(
                        contentId, "09:00~18:00", "매주 월요일", "02-1234-5678", "가능", "50명"));
            }
        };
    }

    private static TourContentRow row(String id, BigDecimal lat, BigDecimal lng, Double dist) {
        return new TourContentRow(
                id, "12", "스텁콘텐츠",
                "서울특별시 중구", null, null, null, null,
                null, null, null,
                null, null, null,
                null, null,
                lng.toPlainString(), lat.toPlainString(), "6",
                "http://stub/image.jpg", null, "Type3",
                "20200101000000", "20200101000000", dist);
    }
}
