package io.github.tldnr1.limitedgoods.support;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.IntFunction;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import io.github.tldnr1.limitedgoods.TestcontainersConfiguration;
import io.github.tldnr1.limitedgoods.common.ApiException;
import io.github.tldnr1.limitedgoods.purchase.application.HoldCommand;
import io.github.tldnr1.limitedgoods.purchase.application.HoldPurchaseService;
import io.github.tldnr1.limitedgoods.purchase.application.HoldResult;

/**
 * 실제 PostgreSQL로 실행하는 테스트의 공통 기반. 테스트마다 DB를 비우고, 끝나면 수량 불변식을 검사한다.
 * 재처리 스케줄러는 끄고 테스트가 직접 실행한다. 만료 재처리의 커서 이동을 확인하도록 한 번에 2건씩 처리한다.
 */
@Import({ TestcontainersConfiguration.class, IntegrationTest.ClockConfiguration.class })
@SpringBootTest(properties = { "limited-goods.reprocessing.enabled=false",
		"limited-goods.purchase.expiry-batch-size=2" })
@AutoConfigureMockMvc
public abstract class IntegrationTest {

	protected static final Instant SALE_STARTS_AT = Instant.parse("2026-10-14T01:00:00Z");

	@TestConfiguration(proxyBeanMethods = false)
	static class ClockConfiguration {

		@Bean
		@Primary
		MutableClock mutableClock() {
			return new MutableClock(SALE_STARTS_AT);
		}

	}

	@Autowired
	protected JdbcTemplate jdbc;

	@Autowired
	protected MutableClock clock;

	@Autowired
	protected HoldPurchaseService holdPurchase;

	@BeforeEach
	void resetDatabase() {
		jdbc.execute("truncate payment, purchase_line, purchase, item_stock, item, event restart identity cascade");
		clock.set(SALE_STARTS_AT);
	}

	/**
	 * 수량 불변식: 판매 수량 = 남은 수량 + 잡아 둔 수량 + 확정 수량 (03, ADR-0006)
	 */
	@AfterEach
	void stockInvariantHolds() {
		List<Map<String, Object>> broken = jdbc.queryForList("""
				select i.id, i.sale_quantity, s.remaining,
				       coalesce(sum(pl.quantity) filter (where p.status in ('HELD', 'CONFIRMED')), 0) as taken
				from item i
				join item_stock s on s.item_id = i.id
				left join purchase_line pl on pl.item_id = i.id
				left join purchase p on p.id = pl.purchase_id
				group by i.id, i.sale_quantity, s.remaining
				having i.sale_quantity <> s.remaining + coalesce(sum(pl.quantity) filter (where p.status in ('HELD', 'CONFIRMED')), 0)
				""");
		assertThat(broken).as("수량 불변식이 깨진 품목").isEmpty();
	}

	protected long createEvent(Instant saleStartsAt) {
		return jdbc.queryForObject("insert into event (sale_starts_at) values (?) returning id", Long.class,
				Timestamp.from(saleStartsAt));
	}

	protected long createItem(long eventId, int saleQuantity, int perBuyerLimit) {
		long itemId = jdbc.queryForObject(
				"insert into item (event_id, sale_quantity, per_buyer_limit, price) values (?, ?, ?, 1000) returning id",
				Long.class, eventId, saleQuantity, perBuyerLimit);
		jdbc.update("insert into item_stock (item_id, remaining) values (?, ?)", itemId, saleQuantity);
		return itemId;
	}

	protected int remaining(long itemId) {
		return jdbc.queryForObject("select remaining from item_stock where item_id = ?", Integer.class, itemId);
	}

	protected void insertPendingPayment(long purchaseId) {
		jdbc.update("""
				insert into payment (purchase_id, status, client_request_key, pg_order_id, confirm_idempotency_key,
				                     amount, arrived_at, confirm_deadline_at)
				values (?, 'PENDING', ?, ?, ?, 1000, now(), now() + interval '1 minute')
				""", purchaseId, "key-" + purchaseId, "order-" + purchaseId, "order-" + purchaseId);
	}

	/**
	 * 구매 시작의 결과. 처리됐으면 CREATED 또는 EXISTING과 구매 ID, 막혔으면 오류 코드다.
	 */
	protected record Outcome(String result, Long purchaseId) {
	}

	protected Outcome hold(long eventId, String buyerId, long itemId, int quantity) {
		try {
			HoldResult r = holdPurchase.hold(new HoldCommand(eventId, buyerId, new TreeMap<>(Map.of(itemId, quantity)),
					clock.instant()));
			return new Outcome(r.created() ? "CREATED" : "EXISTING", r.purchaseId());
		}
		catch (ApiException e) {
			return new Outcome(e.code(), null);
		}
	}

	/**
	 * 작업 n개를 각자의 스레드에서 출발 신호에 맞춰 함께 시작한다. 모든 스레드가 출발선에 섰는지 확인한 뒤 신호를 보낸다.
	 */
	protected <T> List<T> concurrently(int n, IntFunction<T> task) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(n);
		try {
			CountDownLatch ready = new CountDownLatch(n);
			CountDownLatch start = new CountDownLatch(1);
			List<Future<T>> futures = new ArrayList<>();
			for (int i = 0; i < n; i++) {
				int index = i;
				futures.add(pool.submit(() -> {
					ready.countDown();
					start.await();
					return task.apply(index);
				}));
			}
			assertThat(ready.await(10, TimeUnit.SECONDS)).as("모든 스레드가 출발선에 섬").isTrue();
			start.countDown();
			List<T> results = new ArrayList<>();
			for (Future<T> f : futures) {
				results.add(f.get(60, TimeUnit.SECONDS));
			}
			return results;
		}
		finally {
			pool.shutdownNow();
		}
	}

}
