package io.github.tldnr1.limitedgoods.purchase.application;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.SortedMap;
import java.util.TreeMap;

import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import io.github.tldnr1.limitedgoods.purchase.domain.PurchaseRepository;
import io.github.tldnr1.limitedgoods.stock.application.StockService;

/**
 * 결제 시작 기한이 지난 구매를 실패(기한 만료)로 바꾸고 수량을 반환한다(P5, P6, T2).
 * 결과 대기 결제가 있는 구매는 건너뛰므로, 그런 구매가 앞에 쌓여도 뒤의 구매가 밀리지 않게 ID 커서로 끝까지 훑는다.
 */
@Service
public class PurchaseExpiry {

	private final PurchaseRepository purchases;
	private final PaymentProgress payments;
	private final StockService stock;
	private final TransactionTemplate tx;
	private final PurchaseProperties properties;
	private final Clock clock;

	PurchaseExpiry(PurchaseRepository purchases, PaymentProgress payments, StockService stock, TransactionTemplate tx,
			PurchaseProperties properties, Clock clock) {
		this.purchases = purchases;
		this.payments = payments;
		this.stock = stock;
		this.tx = tx;
		this.properties = properties;
		this.clock = clock;
	}

	/**
	 * @return 이번 실행에서 만료한 구매 수
	 */
	public int run() {
		Instant now = clock.instant();
		long afterId = 0;
		int expired = 0;
		while (true) {
			long cursor = afterId;
			Batch batch = tx.execute(status -> expireBatch(now, cursor));
			expired += batch.expired();
			if (batch.size() < properties.expiryBatchSize()) {
				return expired;
			}
			afterId = batch.lastId();
		}
	}

	private Batch expireBatch(Instant now, long afterId) {
		List<Long> ids = purchases.lockExpired(now, afterId, properties.expiryBatchSize());
		SortedMap<Long, Integer> giveBack = new TreeMap<>();
		int expired = 0;
		for (long id : ids) {
			// 구매 행을 잠근 뒤라 결제 수락이 끼어들 수 없다(T2).
			if (payments.hasPendingPayment(id)) {
				continue;
			}
			if (purchases.expire(id) == 1) {
				expired++;
				purchases.findLines(id)
					.forEach(line -> giveBack.merge(line.getItemId(), line.getQuantity().intValue(), Integer::sum));
			}
		}
		// 여러 구매의 반환을 품목별로 합쳐 품목 ID 순서로 갱신한다. 구매 시작과 같은 순서라 교착이 생기지 않는다.
		stock.giveBack(giveBack);
		return new Batch(ids.size(), ids.isEmpty() ? afterId : ids.getLast(), expired);
	}

	private record Batch(int size, long lastId, int expired) {
	}

}
