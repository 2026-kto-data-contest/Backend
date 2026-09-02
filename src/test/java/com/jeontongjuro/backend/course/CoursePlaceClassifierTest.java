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
    void kakaoCafeCategoryOverridesRestaurantCodesAndGenericTitle() {
        TourContent genericRestaurant = content("메이비", "39", "A05020200", "FD020300");

        assertThat(CoursePlaceClassifier.typeOf(genericRestaurant, "테마카페"))
                .isEqualTo(CourseStopType.CAFE);
    }

    @Test
    void kakaoRestaurantCategoryOverridesStaleCafeCode() {
        TourContent eelRestaurant = content("장수천한방민물장어", "39", "A05020900", "FD050100");

        assertThat(CoursePlaceClassifier.typeOf(eelRestaurant, "장어구이"))
                .isEqualTo(CourseStopType.RESTAURANT);
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

    @Test
    void restaurantCategoriesFollowTheProductCategoryVocabulary() {
        assertThat(CoursePlaceClassifier.subcategoryOf(
                content("동남아 식당", "39", "A05020700", "FD020400"), CourseStopType.RESTAURANT))
                .isEqualTo("아시아");
        assertThat(CoursePlaceClassifier.subcategoryOf(
                content("김밥집", "39", null, null), CourseStopType.RESTAURANT))
                .isEqualTo("분식");
        assertThat(CoursePlaceClassifier.subcategoryOf(
                content("이색 식당", "39", "A05020700", null), CourseStopType.RESTAURANT))
                .isEqualTo("기타");
    }

    @Test
    void actualAccommodationCodesDoNotReturnEmptySubcategories() {
        assertThat(CoursePlaceClassifier.subcategoryOf(
                content("숙소", "32", "B02010900", null), CourseStopType.ACCOMMODATION)).isEqualTo("기타");
        assertThat(CoursePlaceClassifier.subcategoryOf(
                content("숙소", "32", "B02011100", null), CourseStopType.ACCOMMODATION)).isEqualTo("게스트하우스");
        assertThat(CoursePlaceClassifier.subcategoryOf(
                content("숙소", "32", "B02011200", null), CourseStopType.ACCOMMODATION)).isEqualTo("민박");
        assertThat(CoursePlaceClassifier.subcategoryOf(
                content("숙소", "32", "B02011300", null), CourseStopType.ACCOMMODATION)).isEqualTo("기타");
    }

    @Test
    void touristCategoriesUseOnlyTheProductVocabulary() {
        assertThat(CoursePlaceClassifier.subcategoryOf(
                content("수목원", "12", "A01010400", "NA010100"), CourseStopType.TOURIST_ATTRACTION))
                .isEqualTo("자연관광");
        assertThat(CoursePlaceClassifier.subcategoryOf(
                content("역사 유적지", "12", "A02010700", "HS010900"), CourseStopType.TOURIST_ATTRACTION))
                .isEqualTo("역사·유적");
        assertThat(CoursePlaceClassifier.subcategoryOf(
                content("지역 축제", "15", "A02070200", "EV010200"), CourseStopType.TOURIST_ATTRACTION))
                .isEqualTo("체험");
        assertThat(CoursePlaceClassifier.subcategoryOf(
                content("호수공원", "12", "A01010900", "NA010400"), CourseStopType.TOURIST_ATTRACTION))
                .isEqualTo("공원");
    }

    @Test
    void unsupportedCulturalFacilitiesAreExcluded() {
        assertThat(CoursePlaceClassifier.typeOf(
                content("시립도서관", "14", "A02060900", "VE090300"))).isEqualTo(CourseStopType.ETC);
        assertThat(CoursePlaceClassifier.subcategoryOf(
                content("국립박물관", "14", "A02060100", "VE070100"), CourseStopType.CULTURAL_FACILITY))
                .isEqualTo("박물관");
        assertThat(CoursePlaceClassifier.subcategoryOf(
                content("시립미술관", "14", "A02060500", "VE070600"), CourseStopType.CULTURAL_FACILITY))
                .isEqualTo("미술관");
    }

    @Test
    void onlyTraditionalMarketsAreIncludedFromShoppingContent() {
        TourContent traditionalMarket = content("함창전통시장", "38", "A04010100", "SH060100");
        TourContent localMarket = content("문경중앙시장", "38", "A04010200", "SH060200");
        TourContent specialtyStore = content("곶감 직판장", "38", "A04010900", "SH050300");

        assertThat(CoursePlaceClassifier.typeOf(traditionalMarket)).isEqualTo(CourseStopType.MARKET);
        assertThat(CoursePlaceClassifier.typeOf(localMarket)).isEqualTo(CourseStopType.MARKET);
        assertThat(CoursePlaceClassifier.subcategoryOf(traditionalMarket, CourseStopType.MARKET))
                .isEqualTo("전통시장");
        assertThat(CoursePlaceClassifier.typeOf(specialtyStore)).isEqualTo(CourseStopType.ETC);
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
