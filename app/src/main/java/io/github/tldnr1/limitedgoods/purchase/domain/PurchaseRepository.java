package io.github.tldnr1.limitedgoods.purchase.domain;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

public interface PurchaseRepository extends Repository<Purchase, Long> {

	Optional<Purchase> findById(Long id);

	Optional<Purchase> findByEventIdAndBuyerIdAndStatus(Long eventId, String buyerId, PurchaseStatus status);

	/**
	 * 잡아 둔 구매를 만든다. 같은 구매자의 진행 중 구매가 있으면 오류 없이 아무것도 넣지 않고 빈 값을 돌려준다(Q3, ADR-0005).
	 * 진행 중 구매를 만드는 다른 트랜잭션이 있으면 그 트랜잭션이 끝날 때까지 기다린다.
	 * 여기서 넣는 잡아 둔 시각과 기한은 임시 값이고, 수량을 잡은 뒤 {@link #startPaymentWindow}로 정한다.
	 */
	@Query(value = """
			insert into purchase (event_id, buyer_id, status, arrived_at, held_at, payment_deadline_at)
			values (:eventId, :buyerId, 'HELD', :arrivedAt, :heldAt, :paymentDeadlineAt)
			on conflict (event_id, buyer_id) where status = 'HELD' do nothing
			returning id
			""", nativeQuery = true)
	Optional<Long> insertHeld(@Param("eventId") long eventId, @Param("buyerId") String buyerId,
			@Param("arrivedAt") Instant arrivedAt, @Param("heldAt") Instant heldAt,
			@Param("paymentDeadlineAt") Instant paymentDeadlineAt);

	/**
	 * 수량을 모두 잡은 시각으로 잡아 둔 시각과 결제 시작 기한을 정한다. 수량 잠금을 기다린 시간이 기한을 줄이지 않게 한다.
	 */
	@Modifying
	@Query(value = "update purchase set held_at = :heldAt, payment_deadline_at = :paymentDeadlineAt where id = :id",
			nativeQuery = true)
	void startPaymentWindow(@Param("id") long id, @Param("heldAt") Instant heldAt,
			@Param("paymentDeadlineAt") Instant paymentDeadlineAt);

	@Modifying
	@Query(value = "insert into purchase_line (purchase_id, item_id, quantity) values (:purchaseId, :itemId, :quantity)",
			nativeQuery = true)
	void insertLine(@Param("purchaseId") long purchaseId, @Param("itemId") long itemId, @Param("quantity") int quantity);

	/**
	 * 1인 한도 검사용: 이 구매자가 잡아 두었거나 확정한 품목별 수량 합계.
	 */
	@Query(value = """
			select pl.item_id as "itemId", sum(pl.quantity) as "quantity"
			from purchase_line pl join purchase p on p.id = pl.purchase_id
			where p.event_id = :eventId and p.buyer_id = :buyerId and p.status in ('HELD', 'CONFIRMED')
			group by pl.item_id
			""", nativeQuery = true)
	List<ItemQuantity> sumHeldOrConfirmed(@Param("eventId") long eventId, @Param("buyerId") String buyerId);

	@Query(value = """
			select item_id as "itemId", quantity as "quantity" from purchase_line where purchase_id = :purchaseId
			""", nativeQuery = true)
	List<ItemQuantity> findLines(@Param("purchaseId") long purchaseId);

	/**
	 * 결제 시작 기한이 지난 잡아 둔 구매를 ID 순서로 잠근다. 다른 트랜잭션이 잠근 행은 건너뛴다(ADR-0002).
	 */
	@Query(value = """
			select id from purchase
			where status = 'HELD' and payment_deadline_at <= :now and id > :afterId
			order by id
			limit :batchSize
			for update skip locked
			""", nativeQuery = true)
	List<Long> lockExpired(@Param("now") Instant now, @Param("afterId") long afterId, @Param("batchSize") int batchSize);

	/**
	 * 잡아 둠에서 실패(기한 만료)로 바꾼다. 1을 돌려준 트랜잭션만 수량을 반환한다(Q4, Q5).
	 */
	@Modifying
	@Query(value = "update purchase set status = 'FAILED', failure_reason = 'EXPIRED' where id = :id and status = 'HELD'",
			nativeQuery = true)
	int expire(@Param("id") long id);

}
