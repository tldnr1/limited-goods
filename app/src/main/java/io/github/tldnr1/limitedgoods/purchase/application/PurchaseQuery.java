package io.github.tldnr1.limitedgoods.purchase.application;

import java.time.Instant;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.tldnr1.limitedgoods.common.ApiException;
import io.github.tldnr1.limitedgoods.purchase.domain.FailureReason;
import io.github.tldnr1.limitedgoods.purchase.domain.Purchase;
import io.github.tldnr1.limitedgoods.purchase.domain.PurchaseRepository;

/**
 * 구매자의 상태 조회. DB만 읽고 결제사를 부르지 않는다(ADR-0007).
 */
@Service
public class PurchaseQuery {

	public enum VisibleStatus {
		PAYMENT_WAITING, PAYMENT_CHECKING, CONFIRMED, FAILED_EXPIRED, FAILED_PAYMENT_UNKNOWN
	}

	public record PurchaseView(long purchaseId, VisibleStatus status, Instant paymentDeadlineAt) {
	}

	private final PurchaseRepository purchases;
	private final PaymentProgress payments;

	PurchaseQuery(PurchaseRepository purchases, PaymentProgress payments) {
		this.purchases = purchases;
		this.payments = payments;
	}

	@Transactional(readOnly = true)
	public PurchaseView get(long purchaseId, String buyerId) {
		Purchase purchase = purchases.findById(purchaseId)
			.filter(p -> p.isOwnedBy(buyerId))
			.orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "PURCHASE_NOT_FOUND", "구매가 없습니다."));
		return new PurchaseView(purchase.getId(), visibleStatus(purchase), purchase.getPaymentDeadlineAt());
	}

	private VisibleStatus visibleStatus(Purchase purchase) {
		return switch (purchase.getStatus()) {
			case HELD -> payments.hasPendingPayment(purchase.getId()) ? VisibleStatus.PAYMENT_CHECKING
					: VisibleStatus.PAYMENT_WAITING;
			case CONFIRMED -> VisibleStatus.CONFIRMED;
			case FAILED -> purchase.getFailureReason() == FailureReason.EXPIRED ? VisibleStatus.FAILED_EXPIRED
					: VisibleStatus.FAILED_PAYMENT_UNKNOWN;
		};
	}

}
