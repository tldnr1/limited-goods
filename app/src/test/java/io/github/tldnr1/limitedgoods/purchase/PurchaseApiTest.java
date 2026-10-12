package io.github.tldnr1.limitedgoods.purchase;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import io.github.tldnr1.limitedgoods.purchase.application.PurchaseExpiry;
import io.github.tldnr1.limitedgoods.support.IntegrationTest;

/**
 * HTTP 응답 형식: 처리했으면 2xx와 결과, 막혔으면 4xx와 code (04 API).
 */
class PurchaseApiTest extends IntegrationTest {

	@Autowired
	MockMvc mvc;

	@Autowired
	PurchaseExpiry expiry;

	long eventId;
	long itemId;

	@BeforeEach
	void setUp() {
		eventId = createEvent(SALE_STARTS_AT);
		itemId = createItem(eventId, 1, 1);
	}

	@Test
	void 새_구매는_201_진행_중인_구매가_있으면_200으로_같은_구매를_돌려준다() throws Exception {
		// 기한은 DB 정밀도(마이크로초)로 저장되므로, 처음 응답과 다시 읽은 값이 같아야 한다.
		clock.set(SALE_STARTS_AT.plusNanos(123_456_789));
		holdRequest("buyer-1", body(itemId, 1)).andExpect(status().isCreated())
			.andExpect(jsonPath("$.purchaseId").value(1))
			.andExpect(jsonPath("$.paymentDeadlineAt").value("2026-10-14T01:05:00.123456Z"));

		holdRequest("buyer-1", body(itemId, 1)).andExpect(status().isOk())
			.andExpect(jsonPath("$.purchaseId").value(1))
			.andExpect(jsonPath("$.paymentDeadlineAt").value("2026-10-14T01:05:00.123456Z"));
	}

	@Test
	void 규칙에_막히면_409와_code를_돌려준다() throws Exception {
		holdRequest("buyer-1", body(itemId, 2)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("LIMIT_EXCEEDED"))
			.andExpect(jsonPath("$.message").isNotEmpty());

		holdRequest("buyer-1", body(itemId, 1)).andExpect(status().isCreated());
		holdRequest("buyer-2", body(itemId, 1)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("SOLD_OUT"));

		clock.set(SALE_STARTS_AT.minusSeconds(1));
		holdRequest("buyer-3", body(itemId, 1)).andExpect(status().isConflict())
			.andExpect(jsonPath("$.code").value("NOT_STARTED"));
	}

	@Test
	void 형식이_잘못된_요청은_400으로_거절한다() throws Exception {
		mvc.perform(post("/events/{eventId}/purchases", eventId).contentType(MediaType.APPLICATION_JSON)
			.content(body(itemId, 1))).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		holdRequest("b".repeat(65), body(itemId, 1)).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		holdRequest("buyer-1", "{\"items\": []}").andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		holdRequest("buyer-1", body(itemId, 0)).andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		holdRequest("buyer-1", "{\"items\": [{\"itemId\": %d, \"quantity\": 1}, {\"itemId\": %d, \"quantity\": 1}]}"
			.formatted(itemId, itemId)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		holdRequest("buyer-1", "{\"items\": ").andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		holdRequest("buyer-1", body(999, 1)).andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	void 소수점_품목_ID와_수량은_정수로_바꾸지_않고_400으로_거절한다() throws Exception {
		holdRequest("buyer-1", "{\"items\": [{\"itemId\": %d.9, \"quantity\": 1}]}".formatted(itemId))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		holdRequest("buyer-1", "{\"items\": [{\"itemId\": %d, \"quantity\": 1.9}]}".formatted(itemId))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		// 값이 정수와 같아도 소수점 표기는 받지 않는다.
		holdRequest("buyer-1", "{\"items\": [{\"itemId\": %d, \"quantity\": 1.0}]}".formatted(itemId))
			.andExpect(status().isBadRequest())
			.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		assertThat(jdbc.queryForObject("select count(*) from purchase", Integer.class)).isZero();
		assertThat(remaining(itemId)).isEqualTo(1);
	}

	@Test
	void 없는_행사는_404를_돌려준다() throws Exception {
		mvc.perform(post("/events/{eventId}/purchases", 999).header("X-Buyer-Id", "buyer-1")
			.contentType(MediaType.APPLICATION_JSON)
			.content(body(itemId, 1))).andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("EVENT_NOT_FOUND"));
	}

	@Test
	void 구매_상태를_조회한다() throws Exception {
		holdRequest("buyer-1", body(itemId, 1));

		getRequest(1, "buyer-1").andExpect(status().isOk()).andExpect(jsonPath("$.status").value("PAYMENT_WAITING"));
		getRequest(1, "buyer-2").andExpect(status().isNotFound())
			.andExpect(jsonPath("$.code").value("PURCHASE_NOT_FOUND"));

		insertPendingPayment(1);
		getRequest(1, "buyer-1").andExpect(jsonPath("$.status").value("PAYMENT_CHECKING"));
	}

	@Test
	void 기한이_지나_만료된_구매는_실패로_보인다() throws Exception {
		holdRequest("buyer-1", body(itemId, 1));
		clock.advance(Duration.ofMinutes(5));
		expiry.run();

		getRequest(1, "buyer-1").andExpect(jsonPath("$.status").value("FAILED_EXPIRED"));
	}

	private ResultActions holdRequest(String buyerId, String body) throws Exception {
		return mvc.perform(post("/events/{eventId}/purchases", eventId).header("X-Buyer-Id", buyerId)
			.contentType(MediaType.APPLICATION_JSON)
			.content(body));
	}

	private ResultActions getRequest(long purchaseId, String buyerId) throws Exception {
		return mvc.perform(get("/purchases/{purchaseId}", purchaseId).header("X-Buyer-Id", buyerId));
	}

	private static String body(long itemId, int quantity) {
		return "{\"items\": [{\"itemId\": %d, \"quantity\": %d}]}".formatted(itemId, quantity);
	}

}
