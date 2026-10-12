package io.github.tldnr1.limitedgoods.purchase.application;

import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.tldnr1.limitedgoods.common.ApiException;
import io.github.tldnr1.limitedgoods.event.application.SaleInfo;
import io.github.tldnr1.limitedgoods.event.application.SaleInfoQuery;
import io.github.tldnr1.limitedgoods.purchase.domain.ItemQuantity;
import io.github.tldnr1.limitedgoods.purchase.domain.Purchase;
import io.github.tldnr1.limitedgoods.purchase.domain.PurchaseRepository;
import io.github.tldnr1.limitedgoods.purchase.domain.PurchaseStatus;
import io.github.tldnr1.limitedgoods.stock.application.StockService;

/**
 * 구매 시작. 한 트랜잭션에서 구매 행 → 품목 → 1인 한도 → 수량 잡기 순서로 실행하고, 막히면 전부 롤백해 구매를 남기지 않는다.
 * 경합이 모이는 품목 행은 마지막에 잠가 잠금을 쥐는 시간을 줄인다.
 */
@Service
public class HoldPurchaseService {

	/** 진행 중 구매가 삽입과 조회 사이에 끝나는 경우를 다시 시도하는 횟수 */
	private static final int MAX_ATTEMPTS = 3;

	private final SaleInfoQuery saleInfo;
	private final PurchaseRepository purchases;
	private final StockService stock;
	private final PurchaseProperties properties;
	private final Clock clock;

	HoldPurchaseService(SaleInfoQuery saleInfo, PurchaseRepository purchases, StockService stock,
			PurchaseProperties properties, Clock clock) {
		this.saleInfo = saleInfo;
		this.purchases = purchases;
		this.stock = stock;
		this.properties = properties;
		this.clock = clock;
	}

	@Transactional
	public HoldResult hold(HoldCommand command) {
		SaleInfo sale = saleInfo.get(command.eventId());
		if (!sale.items().keySet().containsAll(command.quantities().keySet())) {
			throw ApiException.invalidRequest("행사에 없는 품목입니다.");
		}
		if (!sale.hasStartedAt(command.arrivedAt())) {
			throw new ApiException(HttpStatus.CONFLICT, "NOT_STARTED", "판매 시작 전입니다.");
		}

		for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
			Instant now = now();
			Optional<Long> inserted = purchases.insertHeld(command.eventId(), command.buyerId(), command.arrivedAt(),
					now, now.plus(properties.paymentWindow()));
			if (inserted.isPresent()) {
				holdItems(inserted.get(), command, sale);
				// 기한은 수량을 모두 잡은 뒤부터 센다. 품목 행 잠금을 기다린 시간만큼 결제할 시간이 줄지 않게 한다.
				Instant heldAt = now();
				Instant deadline = heldAt.plus(properties.paymentWindow());
				purchases.startPaymentWindow(inserted.get(), heldAt, deadline);
				return new HoldResult(inserted.get(), deadline, true);
			}
			// 이미 진행 중인 구매가 있다. 그 사이 만료되거나 확정되어 없으면 삽입부터 다시 한다.
			Optional<Purchase> held = purchases.findByEventIdAndBuyerIdAndStatus(command.eventId(), command.buyerId(),
					PurchaseStatus.HELD);
			if (held.isPresent()) {
				return new HoldResult(held.get().getId(), held.get().getPaymentDeadlineAt(), false);
			}
		}
		throw new IllegalStateException("진행 중 구매를 확인하지 못했습니다: event=%d, buyer=%s"
			.formatted(command.eventId(), command.buyerId()));
	}

	/** DB(timestamptz)가 마이크로초까지 저장하므로 맞춰 자른다. 응답과 저장된 기한이 같아야 한다. */
	private Instant now() {
		return clock.instant().truncatedTo(ChronoUnit.MICROS);
	}

	private void holdItems(long purchaseId, HoldCommand command, SaleInfo sale) {
		command.quantities().forEach((itemId, quantity) -> purchases.insertLine(purchaseId, itemId, quantity));

		// 진행 중 구매는 이 구매 하나뿐이라, 합계에 들어가는 다른 행은 동시에 바뀌지 않는다(ADR-0005).
		for (ItemQuantity sum : purchases.sumHeldOrConfirmed(command.eventId(), command.buyerId())) {
			SaleInfo.ItemTerms terms = sale.items().get(sum.getItemId());
			if (sum.getQuantity() > terms.perBuyerLimit()) {
				throw new ApiException(HttpStatus.CONFLICT, "LIMIT_EXCEEDED", "1인 구매 한도를 넘습니다.");
			}
		}

		stock.take(command.quantities());
	}

}
