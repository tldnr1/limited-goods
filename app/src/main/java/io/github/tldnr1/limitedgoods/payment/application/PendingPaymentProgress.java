package io.github.tldnr1.limitedgoods.payment.application;

import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

import io.github.tldnr1.limitedgoods.purchase.application.PaymentProgress;

/**
 * 구매가 정의한 결제 진행 조회의 구현. 결제 엔티티와 저장소는 결제 구현 때 만들고, 지금은 결제 행을 직접 조회한다.
 */
@Component
class PendingPaymentProgress implements PaymentProgress {

	private final JdbcClient jdbc;

	PendingPaymentProgress(JdbcClient jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public boolean hasPendingPayment(long purchaseId) {
		return jdbc.sql("select exists (select 1 from payment where purchase_id = ? and status = 'PENDING')")
			.param(purchaseId)
			.query(Boolean.class)
			.single();
	}

}
