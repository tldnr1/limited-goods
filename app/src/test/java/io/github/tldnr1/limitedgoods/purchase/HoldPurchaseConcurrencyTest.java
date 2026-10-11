package io.github.tldnr1.limitedgoods.purchase;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.tldnr1.limitedgoods.purchase.application.PurchaseExpiry;
import io.github.tldnr1.limitedgoods.support.IntegrationTest;

/**
 * 시나리오 A의 동시 실행. 각 요청은 출발 신호에 맞춰 실제로 동시에 DB에 들어간다.
 */
class HoldPurchaseConcurrencyTest extends IntegrationTest {

	@Autowired
	PurchaseExpiry expiry;

	@Test
	void A1_수량보다_많은_구매자가_동시에_시도하면_판매_수량만큼만_성공한다() throws Exception {
		long eventId = createEvent(SALE_STARTS_AT);
		long itemId = createItem(eventId, 10, 1);

		List<Outcome> outcomes = concurrently(50, i -> hold(eventId, "buyer-" + i, itemId, 1));

		assertThat(outcomes).filteredOn(o -> o.result().equals("CREATED")).hasSize(10);
		assertThat(outcomes).filteredOn(o -> o.result().equals("SOLD_OUT")).hasSize(40);
		assertThat(remaining(itemId)).isZero();
	}

	@Test
	void A2_마지막_1개에_동시에_시도하면_1명만_성공한다() throws Exception {
		long eventId = createEvent(SALE_STARTS_AT);
		long itemId = createItem(eventId, 1, 1);

		List<Outcome> outcomes = concurrently(20, i -> hold(eventId, "buyer-" + i, itemId, 1));

		assertThat(outcomes).filteredOn(o -> o.result().equals("CREATED")).hasSize(1);
		assertThat(outcomes).filteredOn(o -> o.result().equals("SOLD_OUT")).hasSize(19);
	}

	@Test
	void A3_같은_구매자가_동시에_여러_번_보내면_구매_하나로_처리한다() throws Exception {
		long eventId = createEvent(SALE_STARTS_AT);
		long itemId = createItem(eventId, 10, 2);

		List<Outcome> outcomes = concurrently(20, i -> hold(eventId, "buyer-1", itemId, 1));

		assertThat(outcomes).filteredOn(o -> o.result().equals("CREATED")).hasSize(1);
		assertThat(outcomes).filteredOn(o -> o.result().equals("EXISTING")).hasSize(19);
		assertThat(outcomes).extracting(Outcome::purchaseId).containsOnly(outcomes.getFirst().purchaseId());
		assertThat(purchaseCount("buyer-1")).isEqualTo(1);
		assertThat(remaining(itemId)).isEqualTo(9);
	}

	@Test
	void A4_같은_구매자가_구매를_나눠_동시에_보내도_합계는_한도_이하다() throws Exception {
		long eventId = createEvent(SALE_STARTS_AT);
		long itemId = createItem(eventId, 10, 2);
		Outcome first = hold(eventId, "buyer-1", itemId, 1);
		jdbc.update("update purchase set status = 'CONFIRMED' where id = ?", first.purchaseId());

		// 이미 1개를 확정했으니 1개는 더 살 수 있고 2개는 한도를 넘는다.
		List<Outcome> outcomes = concurrently(20, i -> hold(eventId, "buyer-1", itemId, i % 2 == 0 ? 1 : 2));

		assertThat(outcomes).filteredOn(o -> o.result().equals("CREATED")).hasSize(1);
		assertThat(boughtQuantity("buyer-1")).isLessThanOrEqualTo(2);
	}

	@Test
	void A7_수량이_돌아오는_순간_새_구매가_동시에_들어와도_수량이_어긋나지_않는다() throws Exception {
		long eventId = createEvent(SALE_STARTS_AT);
		long itemId = createItem(eventId, 5, 1);
		for (int i = 0; i < 5; i++) {
			hold(eventId, "old-" + i, itemId, 1);
		}
		clock.advance(Duration.ofMinutes(5));

		// 만료 재처리 2개와 새 구매 10개를 함께 출발시킨다.
		List<Object> results = concurrently(12, i -> i < 2 ? expiry.run() : hold(eventId, "new-" + i, itemId, 1));

		int expired = results.stream().filter(Integer.class::isInstance).mapToInt(Integer.class::cast).sum();
		assertThat(expired).as("두 재처리가 겹쳐도 만료는 한 번씩").isEqualTo(5);
		List<String> holds = results.stream()
			.filter(Outcome.class::isInstance)
			.map(r -> ((Outcome) r).result())
			.toList();
		// 새 구매가 만료보다 먼저 실행되면 품절을 받는다. 어느 순서든 돌아온 5개보다 더 팔리지 않는다.
		assertThat(holds).isSubsetOf("CREATED", "SOLD_OUT");
		assertThat(holds.stream().filter("CREATED"::equals).count() + remaining(itemId)).isEqualTo(5);
		// 불변식은 공통 검사(@AfterEach)가 확인한다.
	}

	private int purchaseCount(String buyerId) {
		return jdbc.queryForObject("select count(*) from purchase where buyer_id = ?", Integer.class, buyerId);
	}

	private int boughtQuantity(String buyerId) {
		return jdbc.queryForObject("""
				select coalesce(sum(pl.quantity), 0) from purchase_line pl join purchase p on p.id = pl.purchase_id
				where p.buyer_id = ? and p.status in ('HELD', 'CONFIRMED')
				""", Integer.class, buyerId);
	}

}
