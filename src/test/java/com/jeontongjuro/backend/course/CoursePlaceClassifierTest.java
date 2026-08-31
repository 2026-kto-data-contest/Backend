package com.jeontongjuro.backend.course;

import static org.assertj.core.api.Assertions.assertThat;

import com.jeontongjuro.backend.tour.TourContent;
import com.jeontongjuro.backend.tour.TourContentRow;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class CoursePlaceClassifierTest {

    @Test
    void knownMisclassifiedCafeNamesAreSeparatedFromRestaurants() {
        assertThat(type("메이비카페", "A05020200", "FD030200")).isEqualTo(CourseStopType.CAFE);
        assertThat(type("온더스톤 브런치카페 제주성산점", "A05020200", "FD020300"))
                .isEqualTo(CourseStopType.CAFE);
        assertThat(type("카페, 선비꽃", "A05020100", "FD010100")).isEqualTo(CourseStopType.CAFE);
        assertThat(type("카페칠곡상회", "A05020100", "FD010100")).isEqualTo(CourseStopType.CAFE);
        assertThat(type("커피상회 휴", "A05020100", "FD010100")).isEqualTo(CourseStopType.CAFE);
        assertThat(type("포카페", "A05020700", "FD020400")).isEqualTo(CourseStopType.CAFE);
    }

    @Test
    void userFacingSubcategoriesNeverExposeRawCodes() {
        TourContent korean = content("한식당", "39", "A05020100", "FD010100");
        TourContent museum = content("근현대 박물관", "14", null, null);
        TourContent pension = content("산골 펜션", "32", "B02010700", null);

        assertThat(CoursePlaceClassifier.subcategoryOf(korean, CourseStopType.RESTAURANT)).isEqualTo("한식");
        assertThat(CoursePlaceClassifier.subcategoryOf(museum, CourseStopType.CULTURAL_FACILITY)).isEqualTo("박물관");
        assertThat(CoursePlaceClassifier.subcategoryOf(pension, CourseStopType.ACCOMMODATION)).isEqualTo("펜션");
    }

    private CourseStopType type(String title, String cat3, String lcls3) {
        return CoursePlaceClassifier.typeOf(content(title, "39", cat3, lcls3));
    }

    private TourContent content(String title, String contentTypeId, String cat3, String lcls3) {
        TourContentRow row = new TourContentRow(title, contentTypeId, title, "주소", null, null,
                null, null, null, null, cat3, null, null, lcls3, null, null,
                "127.1", "36.1", null, null, null, null, null, null, null);
        return TourContent.create(row, new BigDecimal("36.100000"), new BigDecimal("127.100000"));
    }
}
