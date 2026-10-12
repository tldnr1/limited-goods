package io.github.tldnr1.limitedgoods.purchase;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.tldnr1.limitedgoods.purchase.application.PurchaseExpiry;
import io.github.tldnr1.limitedgoods.support.IntegrationTest;

class PurchaseExpiryTest extends IntegrationTest {

	@Autowired
	PurchaseExpiry expiry;

	@Test
	void A6_결제_시작_기한을_넘기면_수량이_돌아오고_다른_구매자가_살_수_있다() {
		long eventId = createEvent(SALE_STARTS_AT);
		long itemId = createItem(eventId, 1, 1);
		Outcome held = hold(eventId, "buyer-1", itemId, 1);
		clock.advance(Duration.ofMinutes(5));

		assertThat(expiry.run()).isEqualTo(1);

		assertThat(status(held.purchaseId())).isEqualTo("FAILED/EXPIRED");
		assertThat(remaining(itemId)).isEqualTo(1);
		assertThat(hold(eventId, "buyer-2", itemId, 1).result()).isEqualTo("CREATED");
	}

	@Test
	void 기한_직전에는_만료하지_않는다() {
		long eventId = createEvent(SALE_STARTS_AT);
		long itemId = createItem(eventId, 1, 1);
		Outcome held = hold(eventId, "buyer-1", itemId, 1);
		clock.advance(Duration.ofMinutes(5).minusMillis(1));

		assertThat(expiry.run()).isZero();
		assertThat(status(held.purchaseId())).isEqualTo("HELD/null");
	}

	@Test
	void 결과_대기_결제가_있는_구매는_건너뛰고_뒤의_구매는_만료한다() {
		long eventId = createEvent(SALE_STARTS_AT);
		long itemId = createItem(eventId, 10, 1);
		// 한 번에 2건씩 처리하므로, 앞의 3건이 결과 대기여도 4번째까지 훑어야 한다.
		for (int i = 0; i < 3; i++) {
			insertPendingPayment(hold(eventId, "paying-" + i, itemId, 1).purchaseId());
		}
		Outcome idle = hold(eventId, "idle", itemId, 1);
		clock.advance(Duration.ofMinutes(5));

		assertThat(expiry.run()).isEqualTo(1);

		assertThat(status(idle.purchaseId())).isEqualTo("FAILED/EXPIRED");
		assertThat(jdbc.queryForObject("select count(*) from purchase where status = 'HELD'", Integer.class))
			.isEqualTo(3);
	}

	@Test
	void 만료를_다시_실행해도_수량은_한_번만_돌아온다() {
		long eventId = createEvent(SALE_STARTS_AT);
		long itemId = createItem(eventId, 3, 1);
		hold(eventId, "buyer-1", itemId, 1);
		clock.advance(Duration.ofMinutes(5));

		assertThat(expiry.run()).isEqualTo(1);
		assertThat(expiry.run()).isZero();
		assertThat(remaining(itemId)).isEqualTo(3);
	}

	private String status(long purchaseId) {
		return jdbc.queryForObject("select status || '/' || coalesce(failure_reason, 'null') from purchase where id = ?",
				String.class, purchaseId);
	}

}
