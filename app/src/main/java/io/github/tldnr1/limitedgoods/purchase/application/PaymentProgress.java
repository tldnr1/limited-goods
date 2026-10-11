package io.github.tldnr1.limitedgoods.purchase.application;

/**
 * 구매가 알아야 하는 결제 정보. 구매가 정의하고 결제 모듈이 구현해 의존 방향(결제 → 구매)을 지킨다.
 */
public interface PaymentProgress {

	/**
	 * 결과를 기다리는 결제가 있는가. 만료는 이런 결제가 없을 때만 수량을 푼다(T2).
	 */
	boolean hasPendingPayment(long purchaseId);

}
