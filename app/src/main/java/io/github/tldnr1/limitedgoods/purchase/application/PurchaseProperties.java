package io.github.tldnr1.limitedgoods.purchase.application;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param paymentWindow 수량을 잡은 뒤 결제를 시작할 수 있는 시간 (P1, P6)
 * @param expiryBatchSize 만료 재처리가 한 트랜잭션에서 잠그는 구매 수
 */
@ConfigurationProperties("limited-goods.purchase")
public record PurchaseProperties(Duration paymentWindow, int expiryBatchSize) {
}
