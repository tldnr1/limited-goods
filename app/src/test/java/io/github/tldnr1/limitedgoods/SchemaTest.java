package io.github.tldnr1.limitedgoods;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest
class SchemaTest {

	@Autowired
	JdbcTemplate jdbc;

	long eventId;
	long itemId;

	@BeforeEach
	void setUp() {
		eventId = jdbc.queryForObject("insert into event (sale_starts_at) values (now()) returning id", Long.class);
		itemId = jdbc.queryForObject(
				"insert into item (event_id, sale_quantity, per_buyer_limit, price) values (?, 10, 2, 1000) returning id",
				Long.class, eventId);
		jdbc.update("insert into item_stock (item_id, remaining) values (?, 10)", itemId);
	}

	@Test
	void 구매자의_잡아_둔_구매는_행사마다_하나다() {
		insertPurchase("buyer-1", "CONFIRMED", null);
		insertPurchase("buyer-1", "HELD", null);

		assertThatThrownBy(() -> insertPurchase("buyer-1", "HELD", null))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 구매마다_결과_대기_결제는_하나다() {
		long purchaseId = insertPurchase("buyer-1", "HELD", null);
		insertPayment(purchaseId, "REJECTED", "key-1", "order-1");
		insertPayment(purchaseId, "PENDING", "key-2", "order-2");

		assertThatThrownBy(() -> insertPayment(purchaseId, "PENDING", "key-3", "order-3"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 남은_수량은_음수가_되지_않는다() {
		assertThatThrownBy(() -> jdbc.update("update item_stock set remaining = remaining - 11 where item_id = ?", itemId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void 실패한_구매에만_실패_이유가_있다() {
		assertThatCode(() -> insertPurchase("buyer-1", "FAILED", "EXPIRED")).doesNotThrowAnyException();
		assertThatThrownBy(() -> insertPurchase("buyer-2", "FAILED", null))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertPurchase("buyer-3", "HELD", "EXPIRED"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private long insertPurchase(String buyerId, String status, String failureReason) {
		return jdbc.queryForObject("""
				insert into purchase (event_id, buyer_id, status, failure_reason, arrived_at, held_at, payment_deadline_at)
				values (?, ?, ?, ?, now(), now(), now() + interval '5 minutes') returning id
				""", Long.class, eventId, buyerId, status, failureReason);
	}

	private void insertPayment(long purchaseId, String status, String clientRequestKey, String pgOrderId) {
		jdbc.update("""
				insert into payment (purchase_id, status, client_request_key, pg_order_id, confirm_idempotency_key,
				                     amount, arrived_at, confirm_deadline_at)
				values (?, ?, ?, ?, ?, 1000, now(), now() + interval '1 minute')
				""", purchaseId, status, clientRequestKey, pgOrderId, pgOrderId);
	}

}
