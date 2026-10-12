package io.github.tldnr1.limitedgoods.purchase;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import io.github.tldnr1.limitedgoods.purchase.application.HoldCommand;
import io.github.tldnr1.limitedgoods.purchase.application.HoldResult;
import io.github.tldnr1.limitedgoods.purchase.application.PurchaseExpiry;
import io.github.tldnr1.limitedgoods.support.IntegrationTest;

/**
 * 시나리오 A의 동시 실행. 각 요청은 출발 신호에 맞춰 실제로 동시에 DB에 들어간다.
 */
class HoldPurchaseConcurrencyTest extends IntegrationTest {

	@Autowired
	PurchaseExpiry expiry;

	@Autowired
	DataSource dataSource;

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

	@Test
	void 수량_잠금을_기다린_시간만큼_결제_시작_기한이_줄지_않는다() throws Exception {
		long eventId = createEvent(SALE_STARTS_AT);
		long itemId = createItem(eventId, 1, 1);
		Duration waited = Duration.ofSeconds(30);
		ExecutorService pool = Executors.newSingleThreadExecutor();
		// 다른 트랜잭션이 품목 행을 잠근 동안 구매 시작이 수량 잡기에서 기다리게 한다.
		try (Connection locker = dataSource.getConnection()) {
			locker.setAutoCommit(false);
			locker.createStatement().execute("select * from item_stock where item_id = " + itemId + " for update");

			Future<HoldResult> held = pool.submit(() -> holdPurchase
				.hold(new HoldCommand(eventId, "buyer-1", new TreeMap<>(Map.of(itemId, 1)), SALE_STARTS_AT)));
			awaitLockWaiters(1);
			clock.advance(waited);
			locker.rollback();

			Instant expected = SALE_STARTS_AT.plus(waited).plus(Duration.ofMinutes(5));
			HoldResult result = held.get(10, TimeUnit.SECONDS);
			assertThat(result.paymentDeadlineAt()).isEqualTo(expected);
			assertThat(jdbc.queryForObject("select payment_deadline_at from purchase where id = ?", Timestamp.class,
					result.purchaseId()).toInstant()).isEqualTo(expected);
		}
		finally {
			pool.shutdownNow();
		}
	}

	private void awaitLockWaiters(int count) throws InterruptedException {
		for (int i = 0; i < 100; i++) {
			Integer waiting = jdbc.queryForObject(
					"select count(*) from pg_stat_activity where datname = current_database() and wait_event_type = 'Lock'",
					Integer.class);
			if (waiting == count) {
				return;
			}
			Thread.sleep(50);
		}
		throw new AssertionError("잠금을 기다리는 트랜잭션이 %d개가 되지 않았습니다.".formatted(count));
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
