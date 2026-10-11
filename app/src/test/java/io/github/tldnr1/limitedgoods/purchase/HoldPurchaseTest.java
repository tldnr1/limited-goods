package io.github.tldnr1.limitedgoods.purchase;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import io.github.tldnr1.limitedgoods.support.IntegrationTest;

class HoldPurchaseTest extends IntegrationTest {

	@Test
	void A5_판매_시작_전의_시도는_거절하고_구매를_남기지_않는다() {
		long eventId = createEvent(SALE_STARTS_AT);
		long itemId = createItem(eventId, 10, 1);
		clock.set(SALE_STARTS_AT.minusMillis(1));

		assertThat(hold(eventId, "buyer-1", itemId, 1).result()).isEqualTo("NOT_STARTED");
		assertThat(jdbc.queryForObject("select count(*) from purchase", Integer.class)).isZero();
	}

	@Test
	void 판매_시작_시각에_도착한_시도는_받는다() {
		long eventId = createEvent(SALE_STARTS_AT);
		long itemId = createItem(eventId, 10, 1);

		assertThat(hold(eventId, "buyer-1", itemId, 1).result()).isEqualTo("CREATED");
	}

	@Test
	void A8_시간이_겹치지_않게_차례로_온_구매자는_먼저_온_순서대로_수량을_받는다() {
		long eventId = createEvent(SALE_STARTS_AT);
		long itemId = createItem(eventId, 3, 1);

		List<String> results = new ArrayList<>();
		for (int i = 0; i < 6; i++) {
			results.add(hold(eventId, "buyer-" + i, itemId, 1).result());
			clock.advance(Duration.ofMillis(1));
		}

		assertThat(results).containsExactly("CREATED", "CREATED", "CREATED", "SOLD_OUT", "SOLD_OUT", "SOLD_OUT");
	}

	@Test
	void 한_번에_한도보다_많이_사면_한도_초과로_거절한다() {
		long eventId = createEvent(SALE_STARTS_AT);
		long itemId = createItem(eventId, 10, 2);

		assertThat(hold(eventId, "buyer-1", itemId, 3).result()).isEqualTo("LIMIT_EXCEEDED");
		assertThat(remaining(itemId)).isEqualTo(10);
	}

	@Test
	void 확정한_수량과_합쳐_한도를_넘으면_거절한다() {
		long eventId = createEvent(SALE_STARTS_AT);
		long itemId = createItem(eventId, 10, 2);
		Outcome first = hold(eventId, "buyer-1", itemId, 2);
		jdbc.update("update purchase set status = 'CONFIRMED' where id = ?", first.purchaseId());

		assertThat(hold(eventId, "buyer-1", itemId, 1).result()).isEqualTo("LIMIT_EXCEEDED");
	}

	@Test
	void 품절로_거절된_시도는_구매를_남기지_않는다() {
		long eventId = createEvent(SALE_STARTS_AT);
		long itemId = createItem(eventId, 1, 1);
		hold(eventId, "buyer-1", itemId, 1);

		assertThat(hold(eventId, "buyer-2", itemId, 1).result()).isEqualTo("SOLD_OUT");
		assertThat(jdbc.queryForObject("select count(*) from purchase where buyer_id = 'buyer-2'", Integer.class))
			.isZero();
	}

}
