package io.github.tldnr1.limitedgoods.purchase.application;

import java.time.Instant;

/**
 * @param created 새로 잡았으면 true, 이미 진행 중인 구매를 돌려줬으면 false (Q3)
 */
public record HoldResult(long purchaseId, Instant paymentDeadlineAt, boolean created) {
}
