package com.jeontongjuro.backend.tour;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

/**
 * TourApiClientImpl 파싱·페이징 단위(MockRestServiceServer — 실호출 없음, DB 불필요). 전제 9(items 빈값)·
 * 페이지네이션 경계·단건 객체·resultCode 오류를 검증한다.
 */
class TourApiClientImplTest {

    private static final BigDecimal LAT = new BigDecimal("37.566500");
    private static final BigDecimal LNG = new BigDecimal("126.978000");

    private record Fixture(TourApiClientImpl client, MockRestServiceServer server) {
    }

    /** numOfRows 지정으로 페이징 경계를 만든다. throttle 0. */
    private Fixture newClient(int numOfRows) {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).ignoreExpectOrder(true).build();
        TourProperties props = new TourProperties(
                "http://tour.test", "TEST-KEY", 20000, numOfRows, "app", 0L);
        return new Fixture(new TourApiClientImpl(props, builder, new ObjectMapper()), server);
    }

    private static String body(String itemsJson, int totalCount) {
        return "{\"response\":{\"header\":{\"resultCode\":\"0000\",\"resultMsg\":\"OK\"},"
                + "\"body\":{\"numOfRows\":100,\"pageNo\":1,\"totalCount\":" + totalCount
                + ",\"items\":" + itemsJson + "}}}";
    }

    private static String item(String contentId) {
        return "{\"contentid\":\"" + contentId + "\",\"contenttypeid\":\"12\",\"title\":\"t" + contentId
                + "\",\"mapx\":\"126.978\",\"mapy\":\"37.5665\",\"dist\":\"12.3\",\"firstimage\":\"\","
                + "\"modifiedtime\":\"20250101000000\"}";
    }

    @Test
    @DisplayName("전제9: items가 빈 문자열이면 빈 리스트(totalCount 0)")
    void emptyItems() {
        Fixture f = newClient(100);
        f.server.expect(queryParam("pageNo", "1"))
                .andRespond(withSuccess(body("\"\"", 0), MediaType.APPLICATION_JSON)
                        .header("X-RateLimit-Remaining", "900"));
        List<TourContentRow> rows = f.client.locationBasedList(LAT, LNG, 20000);
        assertThat(rows).isEmpty();
        f.server.verify();
    }

    @Test
    @DisplayName("페이지네이션 경계: totalCount=3, numOfRows=2 → 2페이지 병합 3건")
    void paginationBoundary() {
        Fixture f = newClient(2);
        f.server.expect(queryParam("pageNo", "1"))
                .andRespond(withSuccess(body("{\"item\":[" + item("a") + "," + item("b") + "]}", 3),
                        MediaType.APPLICATION_JSON).header("X-RateLimit-Remaining", "900"));
        f.server.expect(queryParam("pageNo", "2"))
                .andRespond(withSuccess(body("{\"item\":[" + item("c") + "]}", 3),
                        MediaType.APPLICATION_JSON).header("X-RateLimit-Remaining", "899"));
        List<TourContentRow> rows = f.client.locationBasedList(LAT, LNG, 20000);
        assertThat(rows).extracting(TourContentRow::contentId).containsExactly("a", "b", "c");
        assertThat(rows.get(0).distanceM()).isEqualTo(12.3);
        f.server.verify();
    }

    @Test
    @DisplayName("단건: items.item이 배열 아닌 객체 하나면 1건")
    void singleItemObject() {
        Fixture f = newClient(100);
        f.server.expect(queryParam("pageNo", "1"))
                .andRespond(withSuccess(body("{\"item\":" + item("solo") + "}", 1),
                        MediaType.APPLICATION_JSON).header("X-RateLimit-Remaining", "900"));
        List<TourContentRow> rows = f.client.locationBasedList(LAT, LNG, 20000);
        assertThat(rows).extracting(TourContentRow::contentId).containsExactly("solo");
    }

    @Test
    @DisplayName("resultCode 비-0000이면 fail-fast")
    void resultCodeError() {
        Fixture f = newClient(100);
        String err = "{\"response\":{\"header\":{\"resultCode\":\"22\",\"resultMsg\":\"LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR\"},"
                + "\"body\":{\"items\":\"\"}}}";
        f.server.expect(queryParam("pageNo", "1"))
                .andRespond(withSuccess(err, MediaType.APPLICATION_JSON).header("X-RateLimit-Remaining", "900"));
        assertThatThrownBy(() -> f.client.locationBasedList(LAT, LNG, 20000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("resultCode=22");
    }

    @Test
    @DisplayName("RateLimit 잔량이 임계 미만이면 소진 방어 중단")
    void rateLimitFloor() {
        Fixture f = newClient(100);
        f.server.expect(queryParam("pageNo", "1"))
                .andRespond(withSuccess(body("{\"item\":[" + item("a") + "]}", 1),
                        MediaType.APPLICATION_JSON).header("X-RateLimit-Remaining", "10"));
        assertThatThrownBy(() -> f.client.locationBasedList(LAT, LNG, 20000))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("예산 임계");
    }

    @Test
    @DisplayName("detailCommon2: 단건 상세 파싱")
    void detailCommon() {
        Fixture f = newClient(100);
        f.server.expect(queryParam("contentId", "999"))
                .andRespond(withSuccess(body("{\"item\":" + item("999") + "}", 1),
                        MediaType.APPLICATION_JSON).header("X-RateLimit-Remaining", "900"));
        Optional<TourContentRow> row = f.client.detailCommon("999");
        assertThat(row).isPresent();
        assertThat(row.get().contentId()).isEqualTo("999");
        assertThat(row.get().mapx()).isEqualTo("126.978");
    }
}
