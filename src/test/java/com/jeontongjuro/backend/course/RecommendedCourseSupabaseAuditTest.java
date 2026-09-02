package com.jeontongjuro.backend.course;

import static org.assertj.core.api.Assertions.assertThat;

import com.jeontongjuro.backend.brewery.Brewery;
import com.jeontongjuro.backend.brewery.BreweryRepository;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;

/**
 * 운영 Supabase 읽기 전용 전수 감사. 평상시 CI에서는 외부 DB를 읽지 않으며 명시적으로
 * {@code COURSE_AUDIT_ENABLED=true}를 설정했을 때만 59개 양조장의 코스를 재생성한다.
 */
@SpringBootTest
@TestPropertySource(properties = "spring.sql.init.mode=never")
@EnabledIfEnvironmentVariable(named = "COURSE_AUDIT_ENABLED", matches = "true",
        disabledReason = "운영 Supabase 전수 감사는 COURSE_AUDIT_ENABLED=true일 때만 실행")
class RecommendedCourseSupabaseAuditTest {

    private static final Pattern RAW_CATEGORY_CODE = Pattern.compile("(?:A|B)\\d{8}|(?:FD|VE|HS)\\d{2,6}");
    private static final Map<String, Set<String>> ALLOWED_SUBCATEGORIES = Map.of(
            "음식점", Set.of("한식", "일식", "양식", "중식", "아시아", "분식", "기타"),
            "관광지", Set.of("미술관", "박물관", "자연관광", "역사·유적", "체험", "공원", "전통시장"),
            "카페", Set.of("베이커리", "디저트", "전통찻집", "카페"),
            "숙소", Set.of("호텔", "펜션", "한옥", "게스트하우스", "민박", "기타"));

    @Autowired
    private BreweryRepository breweryRepository;
    @Autowired
    private RecommendedCourseService recommendedCourseService;

    @Test
    void allFiftyNineBreweriesSatisfyCourseContract() {
        List<Brewery> breweries = breweryRepository.findAll().stream()
                .sorted(java.util.Comparator.comparing(Brewery::getBreweryId)).toList();
        assertThat(breweries).as("기능명세 기준 Supabase 양조장 수").hasSize(59);

        int completeCourses = 0;
        for (Brewery brewery : breweries) {
            RecommendedCourseResponse course = recommendedCourseService.findByBreweryId(brewery.getBreweryId());
            assertCourseContract(brewery, course);
            if (course.stops().size() == 9) completeCourses++;
        }
        assertThat(completeCourses).as("전역 후보가 충분한 현재 데이터의 9곳 완성 코스 수").isEqualTo(59);
    }

    private void assertCourseContract(Brewery brewery, RecommendedCourseResponse course) {
        List<CourseStopResponse> stops = course.stops();
        assertThat(course.courseId()).isEqualTo(brewery.getBreweryId());
        assertThat(course.centerBreweryId()).isEqualTo(brewery.getBreweryId());
        assertThat(course.title()).isEqualTo(brewery.getBusinessName() + " 코스");
        assertThat(stops).isNotEmpty().hasSizeLessThanOrEqualTo(9);
        assertThat(stops.get(0).type()).isEqualTo(CourseStopType.BREWERY);
        assertThat(stops.get(0).contentId()).isEqualTo(brewery.getBreweryId());
        assertThat(stops).extracting(CourseStopResponse::order)
                .containsExactlyElementsOf(java.util.stream.IntStream.rangeClosed(1, stops.size()).boxed().toList());
        assertThat(stops).extracting(CourseStopResponse::contentId).doesNotHaveDuplicates();

        Map<String, Integer> sectionCounts = new java.util.HashMap<>();
        for (CourseStopResponse stop : stops.subList(1, stops.size())) {
            sectionCounts.merge(stop.categoryName(), 1, Integer::sum);
            assertThat(stop.latitude()).as("%s 위도", stop.contentId()).isNotNull();
            assertThat(stop.longitude()).as("%s 경도", stop.contentId()).isNotNull();
            assertThat(stop.distanceMeters()).as("%s 거리", stop.contentId()).isNotNull().isGreaterThanOrEqualTo(0);
            assertThat(stop.placeUrl()).as("%s 카카오맵 링크", stop.contentId()).contains("map.kakao.com/");
            if (stop.subcategoryName() != null) {
                assertThat(RAW_CATEGORY_CODE.matcher(stop.subcategoryName()).find())
                        .as("%s 원본 분류 코드 미노출", stop.contentId()).isFalse();
                assertThat(ALLOWED_SUBCATEGORIES.get(stop.categoryName()))
                        .as("%s 허용 카테고리 목록", stop.categoryName())
                        .contains(stop.subcategoryName());
            }
        }
        assertThat(sectionCounts.values()).allSatisfy(count -> assertThat(count).isLessThanOrEqualTo(2));
    }
}
